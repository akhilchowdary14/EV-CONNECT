package com.banking.model;

import com.banking.enums.TransferStatus;
import com.banking.exception.InvalidTransactionException;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Entity representing a money transfer operation between two distinct bank accounts.
 *
 * <p>DDD Classification: <b>Entity</b>.
 * Identified uniquely by {@code transferId} and tracks the lifecycle state
 * ({@code PENDING -> COMPLETED} or {@code PENDING -> FAILED}) of a specific transfer event.
 * References participating {@link BankAccount} aggregates by {@link AccountNumber} identity
 * to preserve aggregate encapsulation.</p>
 */
public final class FundTransfer {

    private static final AtomicLong SEQUENCE = new AtomicLong(0L);
    private static final DateTimeFormatter DISPLAY_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final String transferId;
    private final AccountNumber sourceAccountNumber;
    private final AccountNumber destinationAccountNumber;
    private final Money amount;
    private final LocalDateTime timestamp;
    private TransferStatus status;
    private String failureReason;

    /**
     * Constructs a new {@code FundTransfer} initialized in {@link TransferStatus#PENDING} state.
     */
    public FundTransfer(
            String transferId,
            AccountNumber sourceAccountNumber,
            AccountNumber destinationAccountNumber,
            Money amount,
            LocalDateTime timestamp) {

        if (transferId == null || transferId.isBlank()) {
            throw new InvalidTransactionException("Transfer ID must not be null or blank.");
        }
        if (sourceAccountNumber == null) {
            throw new InvalidTransactionException("Source account number must not be null.");
        }
        if (destinationAccountNumber == null) {
            throw new InvalidTransactionException("Destination account number must not be null.");
        }
        if (sourceAccountNumber.equals(destinationAccountNumber)) {
            throw new InvalidTransactionException(
                    "Source and destination accounts must be different (" + sourceAccountNumber + ").");
        }
        if (amount == null) {
            throw new InvalidTransactionException("Transfer amount must not be null.");
        }
        amount.requirePositive("Transfer amount");

        if (timestamp == null) {
            throw new InvalidTransactionException("Transfer timestamp must not be null.");
        }

        this.transferId = transferId.trim();
        this.sourceAccountNumber = sourceAccountNumber;
        this.destinationAccountNumber = destinationAccountNumber;
        this.amount = amount;
        this.timestamp = timestamp;
        this.status = TransferStatus.PENDING;
        this.failureReason = null;
    }

    /**
     * Factory method creating a uniquely identified {@code FundTransfer} in {@code PENDING} status.
     */
    public static FundTransfer initiate(
            AccountNumber sourceAccountNumber,
            AccountNumber destinationAccountNumber,
            Money amount) {

        String uniqueId = String.format("TRF-%05d-%s",
                SEQUENCE.incrementAndGet(),
                UUID.randomUUID().toString().substring(0, 6).toUpperCase());

        return new FundTransfer(
                uniqueId,
                sourceAccountNumber,
                destinationAccountNumber,
                amount,
                LocalDateTime.now());
    }

    /**
     * Transitions the transfer from {@code PENDING} to {@code COMPLETED}.
     */
    public void markCompleted() {
        ensurePending("complete");
        this.status = TransferStatus.COMPLETED;
    }

    /**
     * Transitions the transfer from {@code PENDING} to {@code FAILED} and records the reason.
     */
    public void markFailed(String reason) {
        ensurePending("fail");
        this.status = TransferStatus.FAILED;
        this.failureReason = (reason == null || reason.isBlank()) ? "Unspecified failure" : reason.trim();
    }

    private void ensurePending(String action) {
        if (this.status != TransferStatus.PENDING) {
            throw new InvalidTransactionException(
                    String.format("Cannot %s transfer %s because it is already in terminal state %s.",
                            action, transferId, status));
        }
    }

    public String getTransferId() {
        return transferId;
    }

    public AccountNumber getSourceAccountNumber() {
        return sourceAccountNumber;
    }

    public AccountNumber getDestinationAccountNumber() {
        return destinationAccountNumber;
    }

    public Money getAmount() {
        return amount;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public TransferStatus getStatus() {
        return status;
    }

    public String getFailureReason() {
        return failureReason;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof FundTransfer other)) {
            return false;
        }
        return transferId.equals(other.transferId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(transferId);
    }

    @Override
    public String toString() {
        String reasonSuffix = failureReason != null ? " (" + failureReason + ")" : "";
        return String.format("[%s] %s | %s -> %s | Amount: %s | Status: %s%s",
                timestamp.format(DISPLAY_FORMAT),
                transferId,
                sourceAccountNumber,
                destinationAccountNumber,
                amount,
                status,
                reasonSuffix);
    }
}
