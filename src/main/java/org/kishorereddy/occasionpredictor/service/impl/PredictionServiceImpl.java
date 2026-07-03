package org.kishorereddy.occasionpredictor.service.impl;

import org.kishorereddy.occasionpredictor.entity.ModelVersion;
import org.kishorereddy.occasionpredictor.entity.Prediction;
import org.kishorereddy.occasionpredictor.entity.PredictionAudit;
import org.kishorereddy.occasionpredictor.entity.PromptVersion;
import org.kishorereddy.occasionpredictor.model.OccasionType;
import org.kishorereddy.occasionpredictor.model.PredictionRequest;
import org.kishorereddy.occasionpredictor.model.PredictionResponse;
import org.kishorereddy.occasionpredictor.repository.ModelVersionRepository;
import org.kishorereddy.occasionpredictor.repository.PredictionAuditRepository;
import org.kishorereddy.occasionpredictor.repository.PredictionRepository;
import org.kishorereddy.occasionpredictor.repository.PromptVersionRepository;
import org.kishorereddy.occasionpredictor.service.PredictionService;
import org.kishorereddy.occasionpredictor.service.PredictionWorkflow;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class PredictionServiceImpl implements PredictionService {

    private static final String DEFAULT_PROMPT_TEMPLATE = """
            You are a gift occasion classifier.

            Analyze the gift order below and determine the most likely gift occasion.
            Respond with ONLY this JSON object — no explanation, no markdown, no extra text:
            {"occasion":"OCCASION_NAME","confidence":0.85}

            Allowed values for occasion: BIRTHDAY, ANNIVERSARY, VALENTINES_DAY, MOTHERS_DAY,
            FATHERS_DAY, CHRISTMAS, THANKSGIVING, UNKNOWN

            Use UNKNOWN when information is insufficient. Confidence: 0.0 = uncertain, 1.0 = certain.

            Order Details:
            - Recipient Name: %s
            - Relation: %s
            - Product: %s
            - Category: %s
            - Order Date: %s
            - Gift Message: %s
            """;

    private final PredictionWorkflow predictionWorkflow;
    private final PredictionRepository predictionRepository;
    private final PredictionAuditRepository auditRepository;
    private final PromptVersionRepository promptVersionRepository;
    private final ModelVersionRepository modelVersionRepository;

    public PredictionServiceImpl(PredictionWorkflow predictionWorkflow,
                                 PredictionRepository predictionRepository,
                                 PredictionAuditRepository auditRepository,
                                 PromptVersionRepository promptVersionRepository,
                                 ModelVersionRepository modelVersionRepository) {
        this.predictionWorkflow = predictionWorkflow;
        this.predictionRepository = predictionRepository;
        this.auditRepository = auditRepository;
        this.promptVersionRepository = promptVersionRepository;
        this.modelVersionRepository = modelVersionRepository;
    }

    @Override
    public PredictionResponse predict(PredictionRequest request) {
        PromptVersion promptVersion = promptVersionRepository.findByActiveTrue().orElse(null);
        ModelVersion modelVersion = modelVersionRepository.findByActiveTrue().orElse(null);

        String template = promptVersion != null ? promptVersion.getTemplate() : DEFAULT_PROMPT_TEMPLATE;
        String prompt = template.formatted(
                request.recipientName(),
                request.recipientRelation(),
                request.productName(),
                request.productCategory(),
                request.orderDate(),
                request.giftMessage()
        );

        long startTime = System.currentTimeMillis();
        PredictionWorkflow.LlmResult result = predictionWorkflow.call(prompt);
        long latencyMs = System.currentTimeMillis() - startTime;

        String source = modelVersion != null
                ? modelVersion.getProvider() + "_" + modelVersion.getModelName()
                : "OLLAMA_CHAT_CLIENT";
        String reason = buildReason(result.occasion(), request);

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
        prediction.setReason(reason);
        prediction.setPredictionSource(source);
        prediction.setPromptVersion(promptVersion);
        prediction.setModelVersion(modelVersion);
        predictionRepository.save(prediction);

        PredictionAudit audit = new PredictionAudit();
        audit.setPrediction(prediction);
        audit.setRawPrompt(prompt);
        audit.setRawResponse(result.rawContent());
        audit.setLatencyMs(latencyMs);
        auditRepository.save(audit);

        return new PredictionResponse(request.orderId(), result.occasion(), result.confidence(), reason, source);
    }

    private String buildReason(OccasionType occasion, PredictionRequest request) {
        if (occasion == OccasionType.UNKNOWN) {
            return "Unable to determine a specific occasion for this gift.";
        }
        return "Based on the %s (for %s), this gift is ideal for %s.".formatted(
                request.productName(),
                request.recipientRelation(),
                occasion.name().replace("_", " ").toLowerCase()
        );
    }
}
