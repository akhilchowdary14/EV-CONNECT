package com.banking.exception;

/**
 * Base unchecked domain exception for all banking rule and invariant violations.
 */
public class BankingException extends RuntimeException {

    public BankingException(String message) {
        super(message);
    }

    public BankingException(String message, Throwable cause) {
        super(message, cause);
    }
}
