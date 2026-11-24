package com.banking.enums;

/**
 * Classifies financial transactions recorded in the immutable account ledger.
 */
public enum TransactionType {
    /** Direct deposit into an account (including initial opening deposit). */
    DEPOSIT(true),

    /** Direct withdrawal from an account. */
    WITHDRAWAL(false),

    /** Outgoing funds debited during an inter-account transfer. */
    TRANSFER_DEBIT(false),

    /** Incoming funds credited during an inter-account transfer. */
    TRANSFER_CREDIT(true);

    private final boolean credit;

    TransactionType(boolean credit) {
        this.credit = credit;
    }

    /**
     * Returns {@code true} if this transaction increases the account balance,
     * or {@code false} if it decreases the account balance.
     */
    public boolean isCredit() {
        return credit;
    }
}
