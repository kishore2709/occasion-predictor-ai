package org.kishorereddy.occasionpredictor.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.kishorereddy.occasionpredictor.event.OccasionPredictedEvent;
import org.kishorereddy.occasionpredictor.event.Topics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class AnalyticsConsumer {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsConsumer.class);

    private final ObjectMapper objectMapper;

    public AnalyticsConsumer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = Topics.OCCASION_PREDICTED, groupId = "analytics-group")
    public void handle(String message) throws Exception {
        OccasionPredictedEvent event = objectMapper.readValue(message, OccasionPredictedEvent.class);
        log.info("[Analytics] orderId={} occasion={} confidence={} source={} — recording metrics",
                event.orderId(), event.occasion(), event.confidence(), event.predictionSource());
        // TODO: forward to analytics pipeline
        //   e.g. analyticsClient.record("occasion_predicted", Map.of("occasion", event.occasion(), ...))
    }
}
