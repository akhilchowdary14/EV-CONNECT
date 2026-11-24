package com.banking.exception;

/**
 * Thrown when an operation such as a withdrawal or fund transfer is attempted
 * involving a frozen bank account.
 */
public class FrozenAccountException extends BankingException {

    public FrozenAccountException(String message) {
        super(message);
    }
}
