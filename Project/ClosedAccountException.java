package com.banking.exception;

/**
 * Thrown when any financial or state-transition operation is attempted on a
 * permanently closed bank account.
 */
public class ClosedAccountException extends BankingException {

    public ClosedAccountException(String message) {
        super(message);
    }
}
