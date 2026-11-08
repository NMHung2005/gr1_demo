package com.example.demo.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.config.TopicConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import com.example.demo.messaging.LoanEventConsumer;
import com.example.demo.messaging.LoanEventPublisher;

@Configuration
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true")
@Import({LoanEventPublisher.class, LoanEventConsumer.class})
public class LoanKafkaConfig {
    @Bean
    public NewTopic loanEventsTopic(@Value("${app.kafka.loan-topic:library.loan-events.v1}") String topic) {
        return TopicBuilder.name(topic).partitions(1).replicas(1)
                .config(TopicConfig.RETENTION_MS_CONFIG, "86400000").build();
    }

    @Bean
    public ThreadPoolTaskExecutor loanKafkaExecutor() {
        var executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("loan-kafka-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(5);
        return executor;
    }
}
