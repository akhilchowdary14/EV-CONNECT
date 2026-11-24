package com.banking.model;

import com.banking.exception.InsufficientFundsException;
import com.banking.exception.InvalidTransactionException;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.Objects;

/**
 * Immutable Value Object representing the maximum permitted overdraft (negative balance floor)
 * for a {@link BankAccount}.
 *
 * <p>DDD Classification: <b>Value Object</b>.
 * An {@code OverdraftLimit} of ₹2,000.00 means the account balance may drop down to -₹2,000.00,
 * but never below -₹2,000.00.</p>
 */
public final class OverdraftLimit {

    private final Money limitAmount;

    /**
     * Constructs an immutable, non-negative {@code OverdraftLimit}.
     *
     * @param limitAmount maximum permitted overdraft magnitude (must be &gt;= 0)
     * @throws InvalidTransactionException if {@code limitAmount} is null or negative
     */
    public OverdraftLimit(Money limitAmount) {
        if (limitAmount == null) {
            throw new InvalidTransactionException("Overdraft limit must not be null.");
        }
        if (limitAmount.isNegative()) {
            throw new InvalidTransactionException(
                    "Overdraft limit must be non-negative, but received: " + limitAmount);
        }
        this.limitAmount = limitAmount;
    }

    public static OverdraftLimit of(Money limitAmount) {
        return new OverdraftLimit(limitAmount);
    }

    public static OverdraftLimit inr(BigDecimal amount) {
        return new OverdraftLimit(Money.inr(amount));
    }

    public static OverdraftLimit inr(String amount) {
        return new OverdraftLimit(Money.inr(amount));
    }

    public static OverdraftLimit inr(long wholeRupees) {
        return new OverdraftLimit(Money.inr(wholeRupees));
    }

    public static OverdraftLimit zero(Currency currency) {
        return new OverdraftLimit(Money.zero(currency));
    }

    public static OverdraftLimit zeroInr() {
        return new OverdraftLimit(Money.zeroInr());
    }

    public Money getLimitAmount() {
        return limitAmount;
    }

    public Currency getCurrency() {
        return limitAmount.getCurrency();
    }

    /**
     * Returns the lowest permitted balance for an account with this overdraft limit
     * (for example, {@code -₹2,000.00} for a {@code ₹2,000.00} overdraft limit).
     */
    public Money getMinimumAllowedBalance() {
        return limitAmount.negate();
    }

    /**
     * Checks whether {@code projectedBalance} stays at or above {@link #getMinimumAllowedBalance()}.
     */
    public boolean allowsBalance(Money projectedBalance) {
        if (projectedBalance == null) {
            throw new InvalidTransactionException("Projected balance must not be null.");
        }
        limitAmount.requireSameCurrency(projectedBalance);
        return projectedBalance.isGreaterThanOrEqualTo(getMinimumAllowedBalance());
    }

    /**
     * Enforces the overdraft boundary for a debit operation.
     *
     * @param currentBalance current account balance prior to debit
     * @param debitAmount    positive amount being debited
     * @param accountNumber  account identifier for error reporting
     * @throws InsufficientFundsException if {@code currentBalance - debitAmount < -limitAmount}
     */
    public void validateDebit(Money currentBalance, Money debitAmount, AccountNumber accountNumber) {
        Money projectedBalance = currentBalance.subtract(debitAmount);
        if (!allowsBalance(projectedBalance)) {
            Money maxAvailable = currentBalance.add(limitAmount);
            throw new InsufficientFundsException(
                    String.format("Insufficient funds in account %s: requested %s, current balance %s, "
                                    + "overdraft limit %s (maximum available withdrawal: %s).",
                            accountNumber, debitAmount, currentBalance, limitAmount, maxAvailable));
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof OverdraftLimit other)) {
            return false;
        }
        return limitAmount.equals(other.limitAmount);
    }

    @Override
    public int hashCode() {
        return Objects.hash(limitAmount);
    }

    @Override
    public String toString() {
        return limitAmount.toString();
    }
}
