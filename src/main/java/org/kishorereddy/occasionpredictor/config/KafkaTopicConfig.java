package org.kishorereddy.occasionpredictor.config;

import org.kishorereddy.occasionpredictor.event.Topics;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    @Bean
    public NewTopic predictionRequestedTopic() {
        return TopicBuilder.name(Topics.PREDICTION_REQUESTED).partitions(1).replicas(1).build();
    }

    @Bean
    public NewTopic occasionPredictedTopic() {
        // 3 partitions: reminder, notification, analytics consumers can scale independently
        return TopicBuilder.name(Topics.OCCASION_PREDICTED).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic predictionFailedTopic() {
        return TopicBuilder.name(Topics.PREDICTION_FAILED).partitions(1).replicas(1).build();
    }

    @Bean
    public NewTopic predictionRetryTopic() {
        return TopicBuilder.name(Topics.PREDICTION_RETRY).partitions(1).replicas(1).build();
    }

    @Bean
    public NewTopic predictionDlqTopic() {
        return TopicBuilder.name(Topics.PREDICTION_DLQ).partitions(1).replicas(1).build();
    }
}
