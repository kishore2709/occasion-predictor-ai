package org.kishorereddy.occasionpredictor.service.cache;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.kishorereddy.occasionpredictor.model.PredictionResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

@Service
public class PredictionCacheService {

    private static final Logger log = LoggerFactory.getLogger(PredictionCacheService.class);

    private static final String PREDICTION_KEY = "prediction:";
    private static final String RAG_KEY        = "rag:";

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.cache.prediction-ttl-seconds:86400}")
    private long predictionTtl;

    @Value("${app.cache.rag-ttl-seconds:1800}")
    private long ragTtl;

    public PredictionCacheService(RedisTemplate<String, String> redisTemplate,
                                   ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper  = objectMapper;
    }

    // ── Prediction result cache (idempotency) ─────────────────────────────────

    public Optional<PredictionResponse> getCachedPrediction(String orderId) {
        try {
            String json = redisTemplate.opsForValue().get(PREDICTION_KEY + orderId);
            if (json == null) return Optional.empty();
            log.debug("Cache hit: prediction for orderId={}", orderId);
            return Optional.of(objectMapper.readValue(json, PredictionResponse.class));
        } catch (Exception e) {
            log.warn("Prediction cache read failed (orderId={}): {}", orderId, e.getMessage());
            return Optional.empty();
        }
    }

    public void cachePrediction(String orderId, PredictionResponse response) {
        try {
            String json = objectMapper.writeValueAsString(response);
            redisTemplate.opsForValue().set(PREDICTION_KEY + orderId, json,
                    Duration.ofSeconds(predictionTtl));
            log.debug("Cached prediction for orderId={} (ttl={}s)", orderId, predictionTtl);
        } catch (Exception e) {
            log.warn("Prediction cache write failed (orderId={}): {}", orderId, e.getMessage());
        }
    }

    // ── RAG retrieval cache ───────────────────────────────────────────────────

    public Optional<List<Document>> getCachedChunks(String queryHash) {
        try {
            String json = redisTemplate.opsForValue().get(RAG_KEY + queryHash);
            if (json == null) return Optional.empty();
            List<CachedChunk> cached = objectMapper.readValue(json, new TypeReference<>() {});
            log.debug("Cache hit: RAG chunks for query hash={}", queryHash);
            return Optional.of(cached.stream().map(CachedChunk::toDocument).toList());
        } catch (Exception e) {
            log.warn("RAG cache read failed (hash={}): {}", queryHash, e.getMessage());
            return Optional.empty();
        }
    }

    public void cacheChunks(String queryHash, List<Document> chunks) {
        try {
            List<CachedChunk> cached = chunks.stream().map(CachedChunk::from).toList();
            String json = objectMapper.writeValueAsString(cached);
            redisTemplate.opsForValue().set(RAG_KEY + queryHash, json,
                    Duration.ofSeconds(ragTtl));
            log.debug("Cached {} RAG chunks for query hash={} (ttl={}s)",
                    chunks.size(), queryHash, ragTtl);
        } catch (Exception e) {
            log.warn("RAG cache write failed (hash={}): {}", queryHash, e.getMessage());
        }
    }

    public String hashQuery(String query) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(query.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return String.valueOf(query.hashCode());
        }
    }
}
