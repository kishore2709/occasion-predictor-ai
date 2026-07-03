package org.kishorereddy.occasionpredictor.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.kishorereddy.occasionpredictor.event.PredictionFailedEvent;
import org.kishorereddy.occasionpredictor.event.Topics;
import org.kishorereddy.occasionpredictor.model.PredictionRequest;
import org.kishorereddy.occasionpredictor.service.PredictionService;
import org.kishorereddy.occasionpredictor.service.kafka.PredictionEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Retries failed predictions.
 * Listens to both {@code prediction.failed} (first failure) and
 * {@code prediction.retry} (subsequent hops). The {@code attemptCount}
 * field embedded in {@link PredictionFailedEvent} tracks progress.
 * After {@code app.kafka.retry.max-attempts} attempts the event is
 * forwarded to {@code prediction.dlq}.
 */
@Component
public class RetryConsumer {

    private static final Logger log = LoggerFactory.getLogger(RetryConsumer.class);

    private final PredictionService predictionService;
    private final PredictionEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    @Value("${app.kafka.retry.max-attempts:3}")
    private int maxAttempts;

    @Value("${app.kafka.retry.backoff-ms:2000}")
    private long backoffMs;

    public RetryConsumer(PredictionService predictionService,
                         PredictionEventPublisher eventPublisher,
                         ObjectMapper objectMapper) {
        this.predictionService = predictionService;
        this.eventPublisher    = eventPublisher;
        this.objectMapper      = objectMapper;
    }

    @KafkaListener(
            topics  = {Topics.PREDICTION_FAILED, Topics.PREDICTION_RETRY},
            groupId = "prediction-retry-group"
    )
    public void handle(String message) throws Exception {
        PredictionFailedEvent failed = objectMapper.readValue(message, PredictionFailedEvent.class);
        int attempt = failed.attemptCount() + 1;

        log.warn("[Retry] orderId={} attempt={}/{} error={}",
                failed.orderId(), attempt, maxAttempts, failed.errorMessage());

        if (attempt > maxAttempts) {
            log.error("[Retry] orderId={} exhausted all {} attempts — routing to DLQ", failed.orderId(), maxAttempts);
            eventPublisher.publishToDlq(failed);
            return;
        }

        applyBackoff(attempt);

        try {
            PredictionRequest request = toRequest(failed);
            predictionService.predict(request);
            log.info("[Retry] orderId={} attempt={} succeeded", failed.orderId(), attempt);
        } catch (Exception e) {
            log.warn("[Retry] orderId={} attempt={} failed again: {}", failed.orderId(), attempt, e.getMessage());
            eventPublisher.publishPredictionFailed(failed.originalRequest(), e.getMessage(), attempt);
        }
    }

    private PredictionRequest toRequest(PredictionFailedEvent failed) {
        var orig = failed.originalRequest();
        return new PredictionRequest(
                orig.orderId(),
                orig.recipientName(),
                orig.recipientRelation(),
                orig.productName(),
                orig.productCategory(),
                orig.orderDate(),
                orig.giftMessage()
        );
    }

    private void applyBackoff(int attempt) {
        try {
            long delay = backoffMs * (long) Math.pow(2, attempt - 1);
            Thread.sleep(Math.min(delay, 30_000));
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}
