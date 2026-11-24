package com.banking.model;

import com.banking.enums.TransactionType;
import com.banking.exception.InvalidTransactionException;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Immutable Entity representing an append-only financial transaction record in an account's ledger.
 *
 * <p>DDD Classification: <b>Entity</b> (with strict immutability).
 * Each ledger entry has a globally unique {@code transactionId}. Two identical ₹1,000 deposits
 * made by the same customer are distinct historical events distinguished by their identity,
 * yet once recorded, no attribute of a completed {@code LedgerEntry} can ever be modified.</p>
 */
public final class LedgerEntry {

    private static final AtomicLong SEQUENCE = new AtomicLong(0L);
    private static final DateTimeFormatter DISPLAY_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final String transactionId;
    private final AccountNumber accountNumber;
    private final TransactionType transactionType;
    private final Money amount;
    private final LocalDateTime timestamp;
    private final String description;
    private final Money balanceAfterTransaction;
    private final String relatedTransferId;

    /**
     * Constructs a validated, strictly immutable {@code LedgerEntry}.
     *
     * @param transactionId           unique identifier for this ledger entry
     * @param accountNumber           account to which this entry belongs
     * @param transactionType         classification of transaction
     * @param amount                  strictly positive transaction amount
     * @param timestamp               date and time when the transaction occurred
     * @param description             human-readable audit description
     * @param balanceAfterTransaction resulting account balance immediately after this transaction
     * @param relatedTransferId       associated {@link FundTransfer} ID for transfer entries, or {@code null}
     */
    public LedgerEntry(
            String transactionId,
            AccountNumber accountNumber,
            TransactionType transactionType,
            Money amount,
            LocalDateTime timestamp,
            String description,
            Money balanceAfterTransaction,
            String relatedTransferId) {

        if (transactionId == null || transactionId.isBlank()) {
            throw new InvalidTransactionException("Ledger entry transaction ID must not be null or blank.");
        }
        if (accountNumber == null) {
            throw new InvalidTransactionException("Ledger entry account number must not be null.");
        }
        if (transactionType == null) {
            throw new InvalidTransactionException("Ledger entry transaction type must not be null.");
        }
        if (amount == null) {
            throw new InvalidTransactionException("Ledger entry amount must not be null.");
        }
        amount.requirePositive("Ledger entry transaction amount");

        if (timestamp == null) {
            throw new InvalidTransactionException("Ledger entry timestamp must not be null.");
        }
        if (description == null || description.isBlank()) {
            throw new InvalidTransactionException("Ledger entry description must not be null or blank.");
        }
        if (balanceAfterTransaction == null) {
            throw new InvalidTransactionException("Ledger entry balanceAfterTransaction must not be null.");
        }
        amount.requireSameCurrency(balanceAfterTransaction);

        boolean isTransferEntry = transactionType == TransactionType.TRANSFER_DEBIT
                || transactionType == TransactionType.TRANSFER_CREDIT;
        if (isTransferEntry && (relatedTransferId == null || relatedTransferId.isBlank())) {
            throw new InvalidTransactionException(
                    "Transfer ledger entries must include a non-blank relatedTransferId.");
        }

        this.transactionId = transactionId.trim();
        this.accountNumber = accountNumber;
        this.transactionType = transactionType;
        this.amount = amount;
        this.timestamp = timestamp;
        this.description = description.trim();
        this.balanceAfterTransaction = balanceAfterTransaction;
        this.relatedTransferId = (relatedTransferId == null || relatedTransferId.isBlank())
                ? null
                : relatedTransferId.trim();
    }

    /**
     * Factory method that generates a unique transaction ID and current timestamp.
     */
    public static LedgerEntry create(
            AccountNumber accountNumber,
            TransactionType transactionType,
            Money amount,
            String description,
            Money balanceAfterTransaction,
            String relatedTransferId) {

        String uniqueId = String.format("TXN-%05d-%s",
                SEQUENCE.incrementAndGet(),
                UUID.randomUUID().toString().substring(0, 6).toUpperCase());

        return new LedgerEntry(
                uniqueId,
                accountNumber,
                transactionType,
                amount,
                LocalDateTime.now(),
                description,
                balanceAfterTransaction,
                relatedTransferId);
    }

    public String getTransactionId() {
        return transactionId;
    }

    public AccountNumber getAccountNumber() {
        return accountNumber;
    }

    public TransactionType getTransactionType() {
        return transactionType;
    }

    public Money getAmount() {
        return amount;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public String getDescription() {
        return description;
    }

    public Money getBalanceAfterTransaction() {
        return balanceAfterTransaction;
    }

    public String getRelatedTransferId() {
        return relatedTransferId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof LedgerEntry other)) {
            return false;
        }
        return transactionId.equals(other.transactionId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(transactionId);
    }

    @Override
    public String toString() {
        String transferRef = relatedTransferId != null ? " | Ref: " + relatedTransferId : "";
        return String.format("[%s] %s | %-15s | Amount: %12s | Balance: %12s | %s%s",
                timestamp.format(DISPLAY_FORMAT),
                transactionId,
                transactionType,
                amount,
                balanceAfterTransaction,
                description,
                transferRef);
    }
}
