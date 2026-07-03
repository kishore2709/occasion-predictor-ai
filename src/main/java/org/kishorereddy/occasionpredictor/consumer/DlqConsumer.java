package org.kishorereddy.occasionpredictor.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.kishorereddy.occasionpredictor.event.PredictionFailedEvent;
import org.kishorereddy.occasionpredictor.event.Topics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Reads from {@code prediction.dlq} — messages that arrived here either:
 * (a) exhausted all prediction retries in {@link RetryConsumer}, or
 * (b) failed consumer-side processing after the shared {@code DefaultErrorHandler}
 *     exhausted its retries and routed the raw message here.
 */
@Component
public class DlqConsumer {

    private static final Logger log = LoggerFactory.getLogger(DlqConsumer.class);

    private final ObjectMapper objectMapper;

    public DlqConsumer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = Topics.PREDICTION_DLQ, groupId = "prediction-dlq-group")
    public void handle(String message) {
        try {
            PredictionFailedEvent failed = objectMapper.readValue(message, PredictionFailedEvent.class);
            log.error("[DLQ] Prediction permanently failed — orderId={} attempts={} error={}",
                    failed.orderId(), failed.attemptCount(), failed.errorMessage());
            // TODO: send alert (PagerDuty, Slack, email)
            // TODO: persist to failed_predictions table for manual review
        } catch (Exception e) {
            // Consumer-side failure routed by DefaultErrorHandler — raw payload may not be PredictionFailedEvent
            log.error("[DLQ] Unrecognised message in DLQ (consumer-side failure): {}", message);
        }
    }
}
