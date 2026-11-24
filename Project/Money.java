package com.banking.model;

import com.banking.exception.InvalidTransactionException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Currency;
import java.util.Locale;
import java.util.Objects;

/**
 * Immutable Value Object representing a currency-aware monetary amount backed by {@link BigDecimal}.
 *
 * <p>DDD Classification: <b>Value Object</b>.
 * Money has no conceptual identity—two ₹5,000.00 instances are completely equal.
 * Binary floating-point types ({@code float} and {@code double}) are deliberately excluded
 * to prevent IEEE 754 precision loss in financial calculations.</p>
 */
public final class Money implements Comparable<Money> {

    /** Default currency used for Indian Rupee (₹) operations in the case study. */
    public static final Currency DEFAULT_CURRENCY = Currency.getInstance("INR");

    private final BigDecimal amount;
    private final Currency currency;

    /**
     * Constructs a validated, scale-normalized {@code Money} instance.
     *
     * @param amount   exact decimal value (may be positive, zero, or negative for overdraft balances)
     * @param currency ISO 4217 currency
     * @throws InvalidTransactionException if amount/currency is null or fractional precision exceeds currency rules
     */
    public Money(BigDecimal amount, Currency currency) {
        if (amount == null) {
            throw new InvalidTransactionException("Monetary amount must not be null.");
        }
        if (currency == null) {
            throw new InvalidTransactionException("Currency must not be null.");
        }

        int allowedFractionDigits = Math.max(0, currency.getDefaultFractionDigits());
        if (amount.stripTrailingZeros().scale() > allowedFractionDigits) {
            throw new InvalidTransactionException(
                    String.format("Monetary value '%s' exceeds maximum %d decimal places allowed for %s.",
                            amount.toPlainString(), allowedFractionDigits, currency.getCurrencyCode()));
        }

        this.amount = amount.setScale(allowedFractionDigits, RoundingMode.UNNECESSARY);
        this.currency = currency;
    }

    /** Creates a {@code Money} instance for a given {@link BigDecimal} and {@link Currency}. */
    public static Money of(BigDecimal amount, Currency currency) {
        return new Money(amount, currency);
    }

    /** Parses a decimal string into a {@code Money} instance for the specified {@link Currency}. */
    public static Money of(String amount, Currency currency) {
        if (amount == null || amount.isBlank()) {
            throw new InvalidTransactionException("Monetary amount string must not be null or blank.");
        }
        try {
            return new Money(new BigDecimal(amount.trim()), currency);
        } catch (NumberFormatException ex) {
            throw new InvalidTransactionException("Invalid monetary amount format: '" + amount + "'.", ex);
        }
    }

    /** Creates an Indian Rupee (INR) {@code Money} instance from a {@link BigDecimal}. */
    public static Money inr(BigDecimal amount) {
        return new Money(amount, DEFAULT_CURRENCY);
    }

    /** Creates an Indian Rupee (INR) {@code Money} instance from an exact string representation. */
    public static Money inr(String amount) {
        return of(amount, DEFAULT_CURRENCY);
    }

    /** Creates an Indian Rupee (INR) {@code Money} instance from whole integer rupees (exact long). */
    public static Money inr(long wholeRupees) {
        return new Money(BigDecimal.valueOf(wholeRupees), DEFAULT_CURRENCY);
    }

    /** Returns a zero-valued {@code Money} instance in the specified currency. */
    public static Money zero(Currency currency) {
        if (currency == null) {
            throw new InvalidTransactionException("Currency must not be null.");
        }
        return new Money(BigDecimal.ZERO, currency);
    }

    /** Returns ₹0.00 in INR. */
    public static Money zeroInr() {
        return zero(DEFAULT_CURRENCY);
    }

    /**
     * Returns a new {@code Money} representing {@code this + other}.
     *
     * @throws InvalidTransactionException if currencies differ
     */
    public Money add(Money other) {
        requireSameCurrency(other);
        return new Money(this.amount.add(other.amount), this.currency);
    }

    /**
     * Returns a new {@code Money} representing {@code this - other}.
     *
     * @throws InvalidTransactionException if currencies differ
     */
    public Money subtract(Money other) {
        requireSameCurrency(other);
        return new Money(this.amount.subtract(other.amount), this.currency);
    }

    /** Returns a new {@code Money} with the negated sign ({@code -this}). */
    public Money negate() {
        return new Money(this.amount.negate(), this.currency);
    }

    /** Returns a new {@code Money} with the absolute value ({@code |this|}). */
    public Money abs() {
        return new Money(this.amount.abs(), this.currency);
    }

    public boolean isPositive() {
        return amount.compareTo(BigDecimal.ZERO) > 0;
    }

    public boolean isNegative() {
        return amount.compareTo(BigDecimal.ZERO) < 0;
    }

    public boolean isZero() {
        return amount.compareTo(BigDecimal.ZERO) == 0;
    }

    public boolean isNonNegative() {
        return amount.compareTo(BigDecimal.ZERO) >= 0;
    }

    public boolean isGreaterThan(Money other) {
        requireSameCurrency(other);
        return this.amount.compareTo(other.amount) > 0;
    }

    public boolean isGreaterThanOrEqualTo(Money other) {
        requireSameCurrency(other);
        return this.amount.compareTo(other.amount) >= 0;
    }

    public boolean isLessThan(Money other) {
        requireSameCurrency(other);
        return this.amount.compareTo(other.amount) < 0;
    }

    /**
     * Enforces that this monetary value is strictly greater than zero.
     *
     * @param context descriptive name of the operation or field for error reporting
     * @throws InvalidTransactionException if amount is zero or negative
     */
    public void requirePositive(String context) {
        if (!isPositive()) {
            throw new InvalidTransactionException(
                    context + " must be strictly positive (greater than zero), but received: " + this);
        }
    }

    /**
     * Ensures {@code other} is non-null and denominated in the exact same currency.
     *
     * @throws InvalidTransactionException if {@code other} is null or has a different currency
     */
    public void requireSameCurrency(Money other) {
        if (other == null) {
            throw new InvalidTransactionException("Target monetary amount must not be null.");
        }
        if (!this.currency.equals(other.currency)) {
            throw new InvalidTransactionException(
                    String.format("Currency mismatch: cannot combine %s and %s.",
                            this.currency.getCurrencyCode(), other.currency.getCurrencyCode()));
        }
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public Currency getCurrency() {
        return currency;
    }

    @Override
    public int compareTo(Money other) {
        requireSameCurrency(other);
        return this.amount.compareTo(other.amount);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Money other)) {
            return false;
        }
        return this.currency.equals(other.currency)
                && this.amount.compareTo(other.amount) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(amount.stripTrailingZeros(), currency);
    }

    @Override
    public String toString() {
        int fractionDigits = Math.max(0, currency.getDefaultFractionDigits());
        StringBuilder pattern = new StringBuilder("#,##0");
        if (fractionDigits > 0) {
            pattern.append('.');
            pattern.append("0".repeat(fractionDigits));
        }
        DecimalFormat formatter = new DecimalFormat(
                pattern.toString(), DecimalFormatSymbols.getInstance(Locale.US));
        String formattedMagnitude = formatter.format(amount.abs());
        String sign = isNegative() ? "-" : "";
        if ("INR".equals(currency.getCurrencyCode())) {
            return sign + "₹" + formattedMagnitude;
        }
        return sign + currency.getCurrencyCode() + " " + formattedMagnitude;
    }
}
