package org.kishorereddy.occasionpredictor.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.kishorereddy.occasionpredictor.event.OccasionPredictedEvent;
import org.kishorereddy.occasionpredictor.event.Topics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class ReminderSchedulingConsumer {

    private static final Logger log = LoggerFactory.getLogger(ReminderSchedulingConsumer.class);

    private final ObjectMapper objectMapper;

    public ReminderSchedulingConsumer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = Topics.OCCASION_PREDICTED, groupId = "reminder-scheduling-group")
    public void handle(String message) throws Exception {
        OccasionPredictedEvent event = objectMapper.readValue(message, OccasionPredictedEvent.class);
        log.info("[ReminderScheduling] orderId={} occasion={} confidence={:.2f} — scheduling reminder",
                event.orderId(), event.occasion(), event.confidence());
        // TODO: integrate with reminder scheduler service
        //   e.g. reminderScheduler.schedule(event.orderId(), event.occasion(), event.occurredAt())
    }
}
