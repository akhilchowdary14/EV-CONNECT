package com.banking.exception;

/**
 * Thrown when a requested withdrawal or outgoing transfer would push an account's
 * balance below its permitted overdraft boundary.
 */
public class InsufficientFundsException extends BankingException {

    public InsufficientFundsException(String message) {
        super(message);
    }
}
