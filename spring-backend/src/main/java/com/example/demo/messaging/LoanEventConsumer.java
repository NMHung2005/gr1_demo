package com.example.demo.messaging;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import tools.jackson.databind.json.JsonMapper;

public class LoanEventConsumer {
    private static final Logger log = LoggerFactory.getLogger(LoanEventConsumer.class);
    private final JsonMapper json = JsonMapper.builder().build();

    @KafkaListener(topics = "${app.kafka.loan-topic:library.loan-events.v1}",
            groupId = "${spring.kafka.consumer.group-id:library-loan-logger}")
    public void receive(ConsumerRecord<String, String> record) {
        LoanEvent event = json.readValue(record.value(), LoanEvent.class);
        if (event.schemaVersion() != 1 || event.eventId() == null || event.eventType() == null
                || event.loanId() == null || event.bookId() == null || event.userId() == null) {
            throw new IllegalArgumentException("Unsupported or incomplete loan event");
        }
        log.info("[Kafka consumer] RECEIVED type={} eventId={} loanId={} bookId={} userId={} topic={} partition={} offset={}",
                event.eventType(), event.eventId(), event.loanId(), event.bookId(), event.userId(),
                record.topic(), record.partition(), record.offset());
    }
}
