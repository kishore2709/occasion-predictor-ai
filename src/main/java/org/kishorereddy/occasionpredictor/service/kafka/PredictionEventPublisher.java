package org.kishorereddy.occasionpredictor.service.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.kishorereddy.occasionpredictor.event.OccasionPredictedEvent;
import org.kishorereddy.occasionpredictor.event.PredictionFailedEvent;
import org.kishorereddy.occasionpredictor.event.PredictionRequestedEvent;
import org.kishorereddy.occasionpredictor.event.Topics;
import org.kishorereddy.occasionpredictor.model.PredictionRequest;
import org.kishorereddy.occasionpredictor.model.PredictionResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
public class PredictionEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(PredictionEventPublisher.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public PredictionEventPublisher(KafkaTemplate<String, String> kafkaTemplate,
                                    ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper  = objectMapper;
    }

    public void publishPredictionRequested(PredictionRequest request) {
        PredictionRequestedEvent event = new PredictionRequestedEvent(
                UUID.randomUUID().toString(),
                request.orderId(),
                request.recipientName(),
                request.recipientRelation(),
                request.productName(),
                request.productCategory(),
                request.orderDate(),
                request.giftMessage(),
                Instant.now()
        );
        send(Topics.PREDICTION_REQUESTED, request.orderId(), event);
    }

    public void publishOccasionPredicted(String predictionId, PredictionResponse response) {
        OccasionPredictedEvent event = new OccasionPredictedEvent(
                UUID.randomUUID().toString(),
                response.orderId(),
                predictionId,
                response.predictedOccasion().name(),
                response.confidenceScore(),
                response.reason(),
                response.evidence(),
                response.predictionSource(),
                Instant.now()
        );
        send(Topics.OCCASION_PREDICTED, response.orderId(), event);
    }

    public void publishPredictionFailed(PredictionRequest request, String errorMessage) {
        PredictionRequestedEvent original = new PredictionRequestedEvent(
                UUID.randomUUID().toString(),
                request.orderId(),
                request.recipientName(),
                request.recipientRelation(),
                request.productName(),
                request.productCategory(),
                request.orderDate(),
                request.giftMessage(),
                Instant.now()
        );
        publishPredictionFailed(original, errorMessage, 0);
    }

    public void publishPredictionFailed(PredictionRequestedEvent original, String errorMessage,
                                         int attemptCount) {
        PredictionFailedEvent event = new PredictionFailedEvent(
                UUID.randomUUID().toString(),
                original.orderId(),
                errorMessage,
                attemptCount,
                original,
                Instant.now()
        );
        String topic = attemptCount == 0 ? Topics.PREDICTION_FAILED : Topics.PREDICTION_RETRY;
        send(topic, original.orderId(), event);
    }

    public void publishToDlq(PredictionFailedEvent event) {
        send(Topics.PREDICTION_DLQ, event.orderId(), event);
    }

    private void send(String topic, String key, Object payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            kafkaTemplate.send(topic, key, json)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.error("Failed to publish to topic={} key={}: {}", topic, key, ex.getMessage());
                        } else {
                            log.debug("Published to topic={} key={} offset={}",
                                    topic, key, result.getRecordMetadata().offset());
                        }
                    });
        } catch (Exception e) {
            log.error("Serialization error for topic={}: {}", topic, e.getMessage());
        }
    }
}
