package com.example.demo.messaging;

import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import tools.jackson.databind.json.JsonMapper;

public class LoanEventPublisher {
    private static final Logger log = LoggerFactory.getLogger(LoanEventPublisher.class);
    private final KafkaTemplate<String, String> kafka;
    private final Executor executor;
    private final String topic;
    private final JsonMapper json = JsonMapper.builder().build();

    public LoanEventPublisher(KafkaTemplate<String, String> kafka,
            @Qualifier("loanKafkaExecutor") Executor executor,
            @Value("${app.kafka.loan-topic:library.loan-events.v1}") String topic) {
        this.kafka = kafka;
        this.executor = executor;
        this.topic = topic;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCommitted(LoanEvent event) {
        try {
            // Metadata lookup can block send(); keep even that work off the HTTP thread.
            executor.execute(() -> send(event));
        } catch (RuntimeException rejected) {
            failed(event, rejected);
        }
    }

    private void send(LoanEvent event) {
        try {
            kafka.send(topic, event.loanId().toString(), json.writeValueAsString(event))
                    .whenComplete((result, failure) -> {
                        if (failure != null) {
                            failed(event, failure);
                        } else {
                            var metadata = result.getRecordMetadata();
                            log.info("[Kafka producer] SENT type={} eventId={} loanId={} topic={} partition={} offset={}",
                                    event.eventType(), event.eventId(), event.loanId(), topic,
                                    metadata.partition(), metadata.offset());
                        }
                    });
        } catch (RuntimeException failure) {
            failed(event, failure);
        }
    }

    private void failed(LoanEvent event, Throwable failure) {
        log.warn("[Kafka producer] FAILED type={} eventId={} loanId={} reason={}; MySQL is already committed. "
                        + "Basic demo has no durable resend.",
                event.eventType(), event.eventId(), event.loanId(), failure.toString());
    }
}
