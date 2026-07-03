package org.kishorereddy.occasionpredictor.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.kishorereddy.occasionpredictor.event.OccasionPredictedEvent;
import org.kishorereddy.occasionpredictor.event.Topics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class NotificationConsumer {

    private static final Logger log = LoggerFactory.getLogger(NotificationConsumer.class);

    private final ObjectMapper objectMapper;

    public NotificationConsumer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = Topics.OCCASION_PREDICTED, groupId = "notification-group")
    public void handle(String message) throws Exception {
        OccasionPredictedEvent event = objectMapper.readValue(message, OccasionPredictedEvent.class);
        log.info("[Notification] orderId={} occasion={} — sending notification to customer",
                event.orderId(), event.occasion());
        // TODO: integrate with notification service (email/push/SMS)
        //   e.g. notificationService.send(event.orderId(), event.occasion(), event.reason())
    }
}
