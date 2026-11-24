package com.banking.model;

import com.banking.enums.AccountStatus;
import com.banking.enums.TransactionType;
import com.banking.exception.ClosedAccountException;
import com.banking.exception.FrozenAccountException;
import com.banking.exception.InvalidTransactionException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Currency;
import java.util.List;
import java.util.Objects;

/**
 * Aggregate Root Entity representing a customer's bank account.
 *
 * <p>DDD Classification: <b>Entity (Aggregate Root)</b>.
 * Identified across its entire lifecycle by an immutable {@link AccountNumber}.
 * Self-enforces all domain invariants:
 * <ul>
 *     <li>Balance never drops below {@link OverdraftLimit#getMinimumAllowedBalance()}.</li>
 *     <li>Deposits and withdrawals require strictly positive {@link Money} amounts.</li>
 *     <li>Frozen accounts forbid withdrawals and transfers.</li>
 *     <li>Closed accounts forbid all financial operations and state reversals.</li>
 *     <li>Every balance change atomically records an immutable {@link LedgerEntry}.</li>
 *     <li>Current balance is always mathematically equal to the sum of ledger entries.</li>
 *     <li>External callers cannot directly mutate balance, status, or ledger entries.</li>
 * </ul>
 */
public class BankAccount {

    private final AccountNumber accountNumber;
    private final String accountHolderName;
    private final OverdraftLimit overdraftLimit;
    private final List<LedgerEntry> transactionHistory;

    private Money balance;
    private AccountStatus status;

    /**
     * Creates a new {@code ACTIVE} bank account. If {@code initialDeposit} is positive,
     * an opening {@link TransactionType#DEPOSIT} ledger entry is automatically recorded
     * so that the account balance is consistent with its transaction history from creation.
     *
     * @param accountNumber     unique validated account number
     * @param accountHolderName customer's full name
     * @param initialDeposit    non-negative initial opening balance
     * @param overdraftLimit    permitted non-negative overdraft limit in the same currency
     */
    public BankAccount(
            AccountNumber accountNumber,
            String accountHolderName,
            Money initialDeposit,
            OverdraftLimit overdraftLimit) {

        if (accountNumber == null) {
            throw new InvalidTransactionException("Account number must not be null.");
        }
        if (accountHolderName == null || accountHolderName.isBlank()) {
            throw new InvalidTransactionException("Account holder name must not be null or blank.");
        }
        if (initialDeposit == null) {
            throw new InvalidTransactionException("Initial deposit amount must not be null.");
        }
        if (initialDeposit.isNegative()) {
            throw new InvalidTransactionException(
                    "Initial deposit cannot be negative, but received: " + initialDeposit);
        }
        if (overdraftLimit == null) {
            throw new InvalidTransactionException("Overdraft limit must not be null.");
        }
        initialDeposit.requireSameCurrency(overdraftLimit.getLimitAmount());

        this.accountNumber = accountNumber;
        this.accountHolderName = accountHolderName.trim();
        this.overdraftLimit = overdraftLimit;
        this.status = AccountStatus.ACTIVE;
        this.transactionHistory = new ArrayList<>();
        this.balance = Money.zero(initialDeposit.getCurrency());

        if (initialDeposit.isPositive()) {
            this.balance = initialDeposit;
            this.transactionHistory.add(LedgerEntry.create(
                    this.accountNumber,
                    TransactionType.DEPOSIT,
                    initialDeposit,
                    "Account opening deposit",
                    this.balance,
                    null));
        }

        verifyLedgerConsistency();
    }

    /**
     * Convenience constructor when an explicit holder name is not supplied.
     */
    public BankAccount(
            AccountNumber accountNumber,
            Money initialDeposit,
            OverdraftLimit overdraftLimit) {
        this(accountNumber, "Account Holder (" + (accountNumber != null ? accountNumber : "UNKNOWN") + ")",
                initialDeposit, overdraftLimit);
    }

    /**
     * Deposits a strictly positive monetary amount into this account and records a ledger entry.
     *
     * @param amount strictly positive deposit amount
     * @return the newly appended immutable {@link LedgerEntry}
     */
    public synchronized LedgerEntry deposit(Money amount) {
        return deposit(amount, "Cash/Direct deposit");
    }

    /**
     * Deposits a strictly positive monetary amount with a custom audit description.
     */
    public synchronized LedgerEntry deposit(Money amount, String description) {
        ensureNotClosed("deposit into");
        validatePositiveTransactionAmount(amount, "Deposit amount");
        String desc = (description == null || description.isBlank()) ? "Cash/Direct deposit" : description.trim();

        Money updatedBalance = this.balance.add(amount);
        LedgerEntry entry = LedgerEntry.create(
                this.accountNumber,
                TransactionType.DEPOSIT,
                amount,
                desc,
                updatedBalance,
                null);

        this.balance = updatedBalance;
        this.transactionHistory.add(entry);
        verifyLedgerConsistency();
        return entry;
    }

    /**
     * Withdraws a strictly positive monetary amount from this account, enforcing status
     * and overdraft invariants, and appends an immutable ledger entry.
     *
     * @param amount strictly positive withdrawal amount
     * @return the newly appended immutable {@link LedgerEntry}
     */
    public synchronized LedgerEntry withdraw(Money amount) {
        return withdraw(amount, "Cash/Direct withdrawal");
    }

    /**
     * Withdraws a strictly positive monetary amount with a custom audit description.
     */
    public synchronized LedgerEntry withdraw(Money amount, String description) {
        ensureActiveForWithdrawal();
        validatePositiveTransactionAmount(amount, "Withdrawal amount");
        overdraftLimit.validateDebit(this.balance, amount, this.accountNumber);

        String desc = (description == null || description.isBlank()) ? "Cash/Direct withdrawal" : description.trim();
        Money updatedBalance = this.balance.subtract(amount);
        LedgerEntry entry = LedgerEntry.create(
                this.accountNumber,
                TransactionType.WITHDRAWAL,
                amount,
                desc,
                updatedBalance,
                null);

        this.balance = updatedBalance;
        this.transactionHistory.add(entry);
        verifyLedgerConsistency();
        return entry;
    }

    /**
     * Freezes an active account, preventing withdrawals and transfers until unfrozen.
     */
    public synchronized void freeze() {
        ensureNotClosed("freeze");
        if (this.status == AccountStatus.FROZEN) {
            throw new InvalidTransactionException(
                    "Account " + accountNumber + " is already frozen.");
        }
        this.status = AccountStatus.FROZEN;
    }

    /**
     * Unfreezes a frozen account, restoring it to {@link AccountStatus#ACTIVE} status.
     */
    public synchronized void unfreeze() {
        ensureNotClosed("unfreeze");
        if (this.status == AccountStatus.ACTIVE) {
            throw new InvalidTransactionException(
                    "Account " + accountNumber + " is already active (not frozen).");
        }
        this.status = AccountStatus.ACTIVE;
    }

    /**
     * Permanently closes this account. An account can only be closed when its balance is
     * settled to zero so customer funds are neither trapped nor overdrawn debts written off.
     */
    public synchronized void close() {
        if (this.status == AccountStatus.CLOSED) {
            throw new ClosedAccountException(
                    "Account " + accountNumber + " is already closed.");
        }
        if (!this.balance.isZero()) {
            throw new InvalidTransactionException(
                    String.format("Cannot close account %s with non-zero balance of %s. "
                                    + "Please settle or withdraw funds to %s before closing.",
                            accountNumber, balance, Money.zero(balance.getCurrency())));
        }
        this.status = AccountStatus.CLOSED;
    }

    /**
     * Validates that this account can act as the source of an outgoing fund transfer.
     */
    public synchronized void validateCanTransferOut(Money amount) {
        ensureNotClosed("transfer funds from");
        if (this.status == AccountStatus.FROZEN) {
            throw new FrozenAccountException(
                    "Cannot transfer funds from frozen source account " + accountNumber + ".");
        }
        validatePositiveTransactionAmount(amount, "Transfer amount");
        overdraftLimit.validateDebit(this.balance, amount, this.accountNumber);
    }

    /**
     * Validates that this account can act as the destination of an incoming fund transfer.
     */
    public synchronized void validateCanTransferIn(Money amount) {
        ensureNotClosed("transfer funds to");
        if (this.status == AccountStatus.FROZEN) {
            throw new FrozenAccountException(
                    "Cannot transfer funds to frozen destination account " + accountNumber + ".");
        }
        validatePositiveTransactionAmount(amount, "Transfer amount");
    }

    /**
     * Records the debit leg of an inter-account transfer and creates a {@code TRANSFER_DEBIT} ledger entry.
     */
    public synchronized LedgerEntry recordTransferDebit(
            Money amount,
            String transferId,
            AccountNumber destinationAccount) {

        validateCanTransferOut(amount);
        if (destinationAccount == null) {
            throw new InvalidTransactionException("Destination account number must not be null.");
        }
        Money updatedBalance = this.balance.subtract(amount);
        LedgerEntry entry = LedgerEntry.create(
                this.accountNumber,
                TransactionType.TRANSFER_DEBIT,
                amount,
                "Transfer to account " + destinationAccount,
                updatedBalance,
                transferId);

        this.balance = updatedBalance;
        this.transactionHistory.add(entry);
        verifyLedgerConsistency();
        return entry;
    }

    /**
     * Records the credit leg of an inter-account transfer and creates a {@code TRANSFER_CREDIT} ledger entry.
     */
    public synchronized LedgerEntry recordTransferCredit(
            Money amount,
            String transferId,
            AccountNumber sourceAccount) {

        validateCanTransferIn(amount);
        if (sourceAccount == null) {
            throw new InvalidTransactionException("Source account number must not be null.");
        }
        Money updatedBalance = this.balance.add(amount);
        LedgerEntry entry = LedgerEntry.create(
                this.accountNumber,
                TransactionType.TRANSFER_CREDIT,
                amount,
                "Transfer from account " + sourceAccount,
                updatedBalance,
                transferId);

        this.balance = updatedBalance;
        this.transactionHistory.add(entry);
        verifyLedgerConsistency();
        return entry;
    }

    /**
     * Captures an opaque Memento snapshot of this account's state for atomic rollback by
     * {@link com.banking.service.TransferService} without exposing internal mutators.
     */
    public synchronized AccountSnapshot createSnapshot() {
        return new AccountSnapshot(this, this.balance, this.status, this.transactionHistory.size());
    }

    /**
     * Restores this account to a previously captured Memento snapshot during transaction rollback.
     * Only accepts snapshots originally generated by this exact {@code BankAccount} instance.
     */
    public synchronized void restoreSnapshot(AccountSnapshot snapshot) {
        if (snapshot == null || snapshot.owner != this) {
            throw new InvalidTransactionException("Invalid account snapshot for rollback.");
        }
        this.balance = snapshot.savedBalance;
        this.status = snapshot.savedStatus;
        while (this.transactionHistory.size() > snapshot.savedLedgerSize) {
            this.transactionHistory.remove(this.transactionHistory.size() - 1);
        }
        verifyLedgerConsistency();
    }

    /**
     * Reconstructs the account balance from scratch by replaying every immutable {@link LedgerEntry}.
     */
    public synchronized Money reconstructBalanceFromLedger() {
        Money computed = Money.zero(this.balance.getCurrency());
        for (LedgerEntry entry : this.transactionHistory) {
            if (entry.getTransactionType().isCredit()) {
                computed = computed.add(entry.getAmount());
            } else {
                computed = computed.subtract(entry.getAmount());
            }
        }
        return computed;
    }

    /**
     * Returns {@code true} if the current stored balance matches the balance reconstructed
     * from the complete ledger history.
     */
    public synchronized boolean isBalanceConsistentWithLedger() {
        return this.balance.equals(reconstructBalanceFromLedger());
    }

    public synchronized Money getBalance() {
        return balance;
    }

    public AccountNumber getAccountNumber() {
        return accountNumber;
    }

    public String getAccountHolderName() {
        return accountHolderName;
    }

    public OverdraftLimit getOverdraftLimit() {
        return overdraftLimit;
    }

    public synchronized AccountStatus getStatus() {
        return status;
    }

    public Currency getCurrency() {
        return overdraftLimit.getCurrency();
    }

    /**
     * Returns maximum currently withdrawable funds ({@code balance + overdraftLimit}) if active,
     * or zero otherwise.
     */
    public synchronized Money getAvailableFunds() {
        if (status != AccountStatus.ACTIVE) {
            return Money.zero(getCurrency());
        }
        Money rawAvailable = balance.add(overdraftLimit.getLimitAmount());
        return rawAvailable.isNegative() ? Money.zero(getCurrency()) : rawAvailable;
    }

    /**
     * Returns an unmodifiable defensive copy of the account's immutable ledger entries.
     * External callers cannot add, remove, or alter entries in the returned list.
     */
    public synchronized List<LedgerEntry> getTransactionHistory() {
        return Collections.unmodifiableList(new ArrayList<>(transactionHistory));
    }

    private void ensureNotClosed(String operationDescription) {
        if (this.status == AccountStatus.CLOSED) {
            throw new ClosedAccountException(
                    "Cannot " + operationDescription + " closed account " + accountNumber + ".");
        }
    }

    private void ensureActiveForWithdrawal() {
        ensureNotClosed("withdraw from");
        if (this.status == AccountStatus.FROZEN) {
            throw new FrozenAccountException(
                    "Cannot withdraw from frozen account " + accountNumber + ".");
        }
    }

    private void validatePositiveTransactionAmount(Money amount, String label) {
        if (amount == null) {
            throw new InvalidTransactionException(label + " must not be null.");
        }
        this.balance.requireSameCurrency(amount);
        amount.requirePositive(label);
    }

    private void verifyLedgerConsistency() {
        Money reconstructed = reconstructBalanceFromLedger();
        if (!this.balance.equals(reconstructed)) {
            throw new IllegalStateException(
                    String.format("Ledger consistency invariant violated on account %s: "
                                    + "balance=%s, ledgerSum=%s",
                            accountNumber, balance, reconstructed));
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof BankAccount other)) {
            return false;
        }
        return accountNumber.equals(other.accountNumber);
    }

    @Override
    public int hashCode() {
        return Objects.hash(accountNumber);
    }

    @Override
    public synchronized String toString() {
        return String.format(
                "Account[%s | Holder: %s | Status: %s | Balance: %s | Overdraft Limit: %s | Available: %s | Txns: %d]",
                accountNumber,
                accountHolderName,
                status,
                balance,
                overdraftLimit,
                getAvailableFunds(),
                transactionHistory.size());
    }

    /**
     * Opaque Memento object encapsulating a point-in-time state snapshot of a {@link BankAccount}.
     * Private fields and private constructor prevent external fabrication or tampering.
     */
    public static final class AccountSnapshot {
        private final BankAccount owner;
        private final Money savedBalance;
        private final AccountStatus savedStatus;
        private final int savedLedgerSize;

        private AccountSnapshot(
                BankAccount owner,
                Money savedBalance,
                AccountStatus savedStatus,
                int savedLedgerSize) {
            this.owner = owner;
            this.savedBalance = savedBalance;
            this.savedStatus = savedStatus;
            this.savedLedgerSize = savedLedgerSize;
        }
    }
}
