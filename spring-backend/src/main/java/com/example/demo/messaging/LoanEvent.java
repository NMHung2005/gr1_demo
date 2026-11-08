package com.example.demo.messaging;

import java.time.Instant;
import java.util.UUID;
import com.example.demo.model.Loan;

/** Immutable snapshot: no JPA entities, credentials or tokens cross into Kafka. */
public record LoanEvent(String eventId, Type eventType, int schemaVersion,
        Long loanId, Long bookId, Long userId, String occurredAt, String dueDate, String returnDate) {
    public enum Type { LOAN_BORROWED, LOAN_RETURNED }

    public static LoanEvent from(Loan loan, Type type) {
        return new LoanEvent(UUID.randomUUID().toString(), type, 1,
                loan.getId(), loan.getBook().getId(), loan.getUser().getId(),
                Instant.now().toString(), loan.getDueDate().toString(),
                loan.getReturnDate() == null ? null : loan.getReturnDate().toString());
    }
}
