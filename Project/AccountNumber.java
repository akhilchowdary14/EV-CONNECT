package com.banking.model;

import com.banking.exception.InvalidTransactionException;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Value Object representing a unique, validated, immutable bank account identifier.
 *
 * <p>DDD Classification: <b>Value Object</b>.
 * Two {@code AccountNumber} instances with the same normalized string value are
 * interchangeable and equal. Once constructed, an account number can never change.</p>
 */
public final class AccountNumber implements Comparable<AccountNumber> {

    /**
     * Valid format: 4 to 20 characters, uppercase alphanumeric with optional internal hyphens
     * (e.g., {@code ACC-1001}, {@code SB202601}, {@code ACCT-0001-IN}).
     */
    private static final Pattern VALID_PATTERN =
            Pattern.compile("^[A-Z0-9](?:[A-Z0-9-]{2,18}[A-Z0-9])$");

    private final String value;

    /**
     * Constructs and validates an immutable {@code AccountNumber}.
     *
     * @param value raw account number string
     * @throws InvalidTransactionException if the value is null, blank, or fails format rules
     */
    public AccountNumber(String value) {
        if (value == null || value.isBlank()) {
            throw new InvalidTransactionException("Account number must not be null or blank.");
        }
        String normalized = value.trim().toUpperCase();
        if (!VALID_PATTERN.matcher(normalized).matches()) {
            throw new InvalidTransactionException(
                    "Invalid account number '" + value + "'. Must be 4-20 alphanumeric characters "
                            + "(optional internal hyphens, e.g., ACC-1001).");
        }
        this.value = normalized;
    }

    /**
     * Static factory method for creating an {@code AccountNumber}.
     */
    public static AccountNumber of(String value) {
        return new AccountNumber(value);
    }

    public String getValue() {
        return value;
    }

    @Override
    public int compareTo(AccountNumber other) {
        Objects.requireNonNull(other, "Cannot compare with null AccountNumber.");
        return this.value.compareTo(other.value);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AccountNumber other)) {
            return false;
        }
        return value.equals(other.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
