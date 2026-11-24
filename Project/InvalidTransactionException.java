package com.banking.exception;

/**
 * Thrown when a transaction violates domain validation rules (for example: zero or
 * negative transaction amounts, currency mismatch, self-transfer, invalid state transition,
 * or malformed monetary/account values).
 */
public class InvalidTransactionException extends BankingException {

    public InvalidTransactionException(String message) {
        super(message);
    }

    public InvalidTransactionException(String message, Throwable cause) {
        super(message, cause);
    }
}
