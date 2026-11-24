package com.banking.exception;

/**
 * Thrown when an account lookup fails because no registered account matches the given account number.
 */
public class AccountNotFoundException extends BankingException {

    public AccountNotFoundException(String message) {
        super(message);
    }
}
