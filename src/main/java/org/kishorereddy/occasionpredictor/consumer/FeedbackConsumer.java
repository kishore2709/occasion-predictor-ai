package org.kishorereddy.occasionpredictor.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.kishorereddy.occasionpredictor.event.OccasionPredictedEvent;
import org.kishorereddy.occasionpredictor.event.Topics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class FeedbackConsumer {

    private static final Logger log = LoggerFactory.getLogger(FeedbackConsumer.class);

    private final ObjectMapper objectMapper;

    public FeedbackConsumer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = Topics.OCCASION_PREDICTED, groupId = "feedback-group")
    public void handle(String message) throws Exception {
        OccasionPredictedEvent event = objectMapper.readValue(message, OccasionPredictedEvent.class);
        log.info("[Feedback] orderId={} predictionId={} — queuing feedback collection",
                event.orderId(), event.predictionId());
        // TODO: queue a delayed feedback-collection job
        //   e.g. feedbackScheduler.scheduleAfterDelivery(event.orderId(), event.predictionId())
    }
}
