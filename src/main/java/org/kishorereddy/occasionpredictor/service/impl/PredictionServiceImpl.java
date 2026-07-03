package org.kishorereddy.occasionpredictor.service.impl;

import org.kishorereddy.occasionpredictor.entity.ModelVersion;
import org.kishorereddy.occasionpredictor.entity.Prediction;
import org.kishorereddy.occasionpredictor.entity.PredictionAudit;
import org.kishorereddy.occasionpredictor.entity.PromptVersion;
import org.kishorereddy.occasionpredictor.model.PredictionRequest;
import org.kishorereddy.occasionpredictor.model.PredictionResponse;
import org.kishorereddy.occasionpredictor.repository.ModelVersionRepository;
import org.kishorereddy.occasionpredictor.repository.PredictionAuditRepository;
import org.kishorereddy.occasionpredictor.repository.PredictionRepository;
import org.kishorereddy.occasionpredictor.repository.PromptVersionRepository;
import org.kishorereddy.occasionpredictor.service.PredictionService;
import org.kishorereddy.occasionpredictor.service.PredictionWorkflow;
import org.kishorereddy.occasionpredictor.service.cache.PredictionCacheService;
import org.kishorereddy.occasionpredictor.service.kafka.PredictionEventPublisher;
import org.kishorereddy.occasionpredictor.service.rag.RagPromptBuilder;
import org.kishorereddy.occasionpredictor.service.rag.RagRetriever;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Transactional
public class PredictionServiceImpl implements PredictionService {

    private static final Logger log = LoggerFactory.getLogger(PredictionServiceImpl.class);

    // Used when no active prompt_version row exists in the DB.
    // Contains only system instructions — order details are appended by RagPromptBuilder.
    private static final String DEFAULT_SYSTEM_INSTRUCTIONS = """
            You are a gift occasion classifier with deep knowledge of gifting occasions.

            Analyze the provided gift order using the retrieved context rules below, then predict the occasion.

            Respond with ONLY this JSON object — no explanation, no markdown, no extra text:
            {"occasion":"OCCASION_NAME","confidence":0.85,"reason":"One sentence explaining the prediction.","evidence":["signal 1","signal 2"]}

            Rules:
            - occasion must be exactly one of: BIRTHDAY, ANNIVERSARY, VALENTINES_DAY, MOTHERS_DAY, FATHERS_DAY, CHRISTMAS, THANKSGIVING, UNKNOWN
            - confidence must be a decimal between 0.0 and 1.0
            - Use UNKNOWN with confidence below 0.4 when information is insufficient
            - reason must be a single sentence
            - evidence must be a JSON array of 1-3 short strings citing signals from the order and retrieved rules
            """;

    private final PredictionWorkflow predictionWorkflow;
    private final RagRetriever ragRetriever;
    private final RagPromptBuilder ragPromptBuilder;
    private final PredictionCacheService cacheService;
    private final PredictionEventPublisher eventPublisher;
    private final PredictionRepository predictionRepository;
    private final PredictionAuditRepository auditRepository;
    private final PromptVersionRepository promptVersionRepository;
    private final ModelVersionRepository modelVersionRepository;

    public PredictionServiceImpl(PredictionWorkflow predictionWorkflow,
                                 RagRetriever ragRetriever,
                                 RagPromptBuilder ragPromptBuilder,
                                 PredictionCacheService cacheService,
                                 PredictionEventPublisher eventPublisher,
                                 PredictionRepository predictionRepository,
                                 PredictionAuditRepository auditRepository,
                                 PromptVersionRepository promptVersionRepository,
                                 ModelVersionRepository modelVersionRepository) {
        this.predictionWorkflow      = predictionWorkflow;
        this.ragRetriever            = ragRetriever;
        this.ragPromptBuilder        = ragPromptBuilder;
        this.cacheService            = cacheService;
        this.eventPublisher          = eventPublisher;
        this.predictionRepository    = predictionRepository;
        this.auditRepository         = auditRepository;
        this.promptVersionRepository = promptVersionRepository;
        this.modelVersionRepository  = modelVersionRepository;
    }

    @Override
    public PredictionResponse predict(PredictionRequest request) {
        // Idempotency: same orderId always returns the same result
        Optional<PredictionResponse> cached = cacheService.getCachedPrediction(request.orderId());
        if (cached.isPresent()) {
            log.info("Returning cached prediction for orderId={}", request.orderId());
            return cached.get();
        }

        // Announce that a prediction has been requested (audit / replay use)
        eventPublisher.publishPredictionRequested(request);

        PromptVersion promptVersion = promptVersionRepository.findByActiveTrue().orElse(null);
        ModelVersion  modelVersion  = modelVersionRepository.findByActiveTrue().orElse(null);

        try {
            // 1. Retrieve relevant RAG chunks from pgvector (or Redis cache)
            List<Document> chunks = ragRetriever.retrieve(request);
            log.debug("order={} rag_chunks={}", request.orderId(), chunks.size());

            // 2. Build the final prompt: system instructions + context + order details
            String systemInstructions = promptVersion != null
                    ? promptVersion.getTemplate()
                    : DEFAULT_SYSTEM_INSTRUCTIONS;
            String prompt = ragPromptBuilder.build(systemInstructions, chunks, request);

            // 3. Call the LLM
            long startTime = System.currentTimeMillis();
            PredictionWorkflow.LlmResult result = predictionWorkflow.call(prompt);
            long latencyMs = System.currentTimeMillis() - startTime;

            String source = modelVersion != null
                    ? modelVersion.getProvider() + "_" + modelVersion.getModelName()
                    : "OLLAMA_CHAT_CLIENT";

            // 4. Persist prediction
            Prediction prediction = new Prediction();
            prediction.setOrderId(request.orderId());
            prediction.setRecipientName(request.recipientName());
            prediction.setRecipientRelation(request.recipientRelation());
            prediction.setProductName(request.productName());
            prediction.setProductCategory(request.productCategory());
            prediction.setOrderDate(request.orderDate());
            prediction.setGiftMessage(request.giftMessage());
            prediction.setPredictedOccasion(result.occasion());
            prediction.setConfidenceScore(result.confidence());
            prediction.setReason(result.reason());
            prediction.setEvidence(result.evidence());
            prediction.setPredictionSource(source);
            prediction.setPromptVersion(promptVersion);
            prediction.setModelVersion(modelVersion);
            predictionRepository.save(prediction);

            // 5. Persist audit row with chunk IDs so we can replay which context was used
            PredictionAudit audit = new PredictionAudit();
            audit.setPrediction(prediction);
            audit.setRawPrompt(prompt);
            audit.setRawResponse(result.rawContent());
            audit.setModelParameters(result.modelParameters());
            audit.setRagChunkIds(toJsonArray(chunks));
            audit.setLatencyMs(latencyMs);
            auditRepository.save(audit);

            PredictionResponse response = new PredictionResponse(
                    request.orderId(),
                    result.occasion(),
                    result.confidence(),
                    result.reason(),
                    source,
                    result.evidence()
            );

            // 6. Cache for idempotency
            cacheService.cachePrediction(request.orderId(), response);

            // 7. Publish occasion.predicted so downstream consumers can act independently
            // Note: published inside the transaction — use an outbox pattern in production
            //       to guarantee publish-only-after-commit.
            eventPublisher.publishOccasionPredicted(prediction.getId().toString(), response);

            return response;

        } catch (Exception e) {
            log.error("Prediction failed for orderId={}: {}", request.orderId(), e.getMessage());
            eventPublisher.publishPredictionFailed(request, e.getMessage());
            throw e;
        }
    }

    private String toJsonArray(List<Document> chunks) {
        if (chunks.isEmpty()) return "[]";
        return chunks.stream()
                .map(d -> {
                    Object originalId = d.getMetadata().get("_chunk_id");
                    String id = originalId != null ? originalId.toString() : d.getId();
                    return "\"" + id + "\"";
                })
                .collect(Collectors.joining(",", "[", "]"));
    }
}
