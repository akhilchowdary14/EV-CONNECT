package com.banking.enums;

/**
 * Represents the execution status of a {@link com.banking.model.FundTransfer} entity.
 */
public enum TransferStatus {
    /** Transfer has been initiated and is currently in progress. */
    PENDING,

    /** Both debit and credit legs succeeded atomically. */
    COMPLETED,

    /** Transfer failed validation or execution and any partial changes were rolled back. */
    FAILED
}
