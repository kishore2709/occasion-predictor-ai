package org.kishorereddy.occasionpredictor.service.rag;

import org.kishorereddy.occasionpredictor.model.PredictionRequest;
import org.kishorereddy.occasionpredictor.service.cache.PredictionCacheService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class RagRetriever {

    private static final Logger log = LoggerFactory.getLogger(RagRetriever.class);

    private final VectorStore vectorStore;
    private final PredictionCacheService cacheService;

    @Value("${app.rag.top-k:5}")
    private int topK;

    @Value("${app.rag.similarity-threshold:0.5}")
    private double similarityThreshold;

    public RagRetriever(VectorStore vectorStore, PredictionCacheService cacheService) {
        this.vectorStore  = vectorStore;
        this.cacheService = cacheService;
    }

    public List<Document> retrieve(PredictionRequest request) {
        String query     = buildQuery(request);
        String queryHash = cacheService.hashQuery(query);
        log.debug("RAG query for order {}: {}", request.orderId(), query);

        Optional<List<Document>> cached = cacheService.getCachedChunks(queryHash);
        if (cached.isPresent()) {
            log.debug("RAG cache hit: {} chunks (hash={})", cached.get().size(), queryHash);
            return cached.get();
        }

        List<Document> chunks = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(query)
                        .topK(topK)
                        .similarityThreshold(similarityThreshold)
                        .build()
        );

        log.debug("Retrieved {} chunks from pgvector (topK={}, threshold={})",
                chunks.size(), topK, similarityThreshold);
        if (log.isDebugEnabled()) {
            chunks.forEach(c -> log.debug("  chunk id={} section={} category={}",
                    c.getId(),
                    c.getMetadata().get("section"),
                    c.getMetadata().get("category")));
        }

        cacheService.cacheChunks(queryHash, chunks);
        return chunks;
    }

    private String buildQuery(PredictionRequest request) {
        StringBuilder sb = new StringBuilder();
        append(sb, "Recipient relation", request.recipientRelation());
        append(sb, "Product",            request.productName());
        append(sb, "Category",           request.productCategory());
        append(sb, "Order date",         request.orderDate());
        append(sb, "Gift message",       request.giftMessage());
        return sb.toString().trim();
    }

    private void append(StringBuilder sb, String label, String value) {
        if (value != null && !value.isBlank()) {
            sb.append(label).append(": ").append(value).append(". ");
        }
    }
}
