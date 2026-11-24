package com.banking.service;

import com.banking.exception.InvalidTransactionException;
import com.banking.model.BankAccount;
import com.banking.model.FundTransfer;
import com.banking.model.Money;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Domain Service responsible for executing atomic money transfers across two distinct
 * {@link BankAccount} Aggregate Roots.
 *
 * <h3>Atomicity and Rollback Design</h3>
 * <ol>
 *     <li><b>Pre-Flight Validation:</b> Verifies non-null inputs, distinct accounts, strictly
 *     positive transfer amount, currency compatibility, active status of both accounts, and
 *     overdraft limit compliance before mutating any state.</li>
 *     <li><b>Canonical Lock Ordering:</b> Acquires monitor locks on the two accounts ordered by
 *     {@link com.banking.model.AccountNumber} to eliminate AB-BA deadlocks under concurrency.</li>
 *     <li><b>Memento Snapshot &amp; Rollback Boundary:</b> Captures opaque {@link BankAccount.AccountSnapshot}
 *     mementos of both accounts prior to mutation. If any runtime failure occurs after debiting
 *     the source account and before completing the credit leg, both accounts are restored to
 *     their exact pre-transfer balances, statuses, and ledger histories.</li>
 *     <li><b>Conservation of Money Post-Condition:</b> Verifies that combined money across both
 *     accounts is identical before and after the transfer and that both ledgers match their balances.</li>
 * </ol>
 */
public class TransferService {

    private final List<FundTransfer> transferHistory = Collections.synchronizedList(new ArrayList<>());

    /**
     * Executes an atomic fund transfer of {@code amount} from {@code source} to {@code destination}.
     *
     * @param source      account to be debited
     * @param destination account to be credited
     * @param amount      strictly positive monetary amount to transfer
     * @return completed {@link FundTransfer} entity containing the unique transfer ID and audit metadata
     */
    public FundTransfer transfer(BankAccount source, BankAccount destination, Money amount) {
        return executeTransfer(source, destination, amount, null);
    }

    /**
     * Executes an atomic fund transfer with an optional post-debit fault-injection hook.
     * Package-accessible / test-accessible to deterministically verify mid-transaction rollback
     * when a failure occurs after debiting the source account and before crediting the destination.
     *
     * @param source            account to be debited
     * @param destination       account to be credited
     * @param amount            strictly positive monetary amount to transfer
     * @param postDebitTestHook optional hook invoked immediately after source debit (may be {@code null})
     * @return completed {@link FundTransfer} entity
     */
    public FundTransfer executeTransfer(
            BankAccount source,
            BankAccount destination,
            Money amount,
            Runnable postDebitTestHook) {

        if (source == null) {
            throw new InvalidTransactionException("Source account must not be null.");
        }
        if (destination == null) {
            throw new InvalidTransactionException("Destination account must not be null.");
        }
        if (amount == null) {
            throw new InvalidTransactionException("Transfer amount must not be null.");
        }
        if (source.getAccountNumber().equals(destination.getAccountNumber())) {
            throw new InvalidTransactionException(
                    "Cannot transfer funds to the same account (" + source.getAccountNumber() + ").");
        }

        amount.requirePositive("Transfer amount");
        source.getBalance().requireSameCurrency(amount);
        destination.getBalance().requireSameCurrency(amount);

        FundTransfer transferRecord = FundTransfer.initiate(
                source.getAccountNumber(),
                destination.getAccountNumber(),
                amount);

        // Acquire locks in deterministic AccountNumber order to prevent deadlocks
        BankAccount firstLock = source.getAccountNumber().compareTo(destination.getAccountNumber()) < 0
                ? source
                : destination;
        BankAccount secondLock = (firstLock == source) ? destination : source;

        synchronized (firstLock) {
            synchronized (secondLock) {
                Money combinedBefore = source.getBalance().add(destination.getBalance());
                BankAccount.AccountSnapshot sourceSnapshot = source.createSnapshot();
                BankAccount.AccountSnapshot destinationSnapshot = destination.createSnapshot();

                try {
                    // Phase 1: Validate both sides prior to any state change
                    source.validateCanTransferOut(amount);
                    destination.validateCanTransferIn(amount);

                    // Phase 2: Debit source account
                    source.recordTransferDebit(
                            amount,
                            transferRecord.getTransferId(),
                            destination.getAccountNumber());

                    // Optional test hook to simulate mid-transfer crash after source debit
                    if (postDebitTestHook != null) {
                        postDebitTestHook.run();
                    }

                    // Phase 3: Credit destination account
                    destination.recordTransferCredit(
                            amount,
                            transferRecord.getTransferId(),
                            source.getAccountNumber());

                    // Phase 4: Verify conservation of money and ledger consistency invariants
                    Money combinedAfter = source.getBalance().add(destination.getBalance());
                    if (!combinedBefore.equals(combinedAfter)
                            || !source.isBalanceConsistentWithLedger()
                            || !destination.isBalanceConsistentWithLedger()) {
                        throw new IllegalStateException(
                                "Conservation-of-money or ledger invariant failed during transfer.");
                    }

                    transferRecord.markCompleted();
                    transferHistory.add(transferRecord);
                    return transferRecord;

                } catch (RuntimeException ex) {
                    // Application-level atomic rollback of both aggregates
                    source.restoreSnapshot(sourceSnapshot);
                    destination.restoreSnapshot(destinationSnapshot);
                    transferRecord.markFailed(ex.getMessage());
                    transferHistory.add(transferRecord);
                    throw ex;
                }
            }
        }
    }

    /**
     * Returns an unmodifiable snapshot of all attempted and completed {@link FundTransfer} records.
     */
    public List<FundTransfer> getTransferHistory() {
        synchronized (transferHistory) {
            return Collections.unmodifiableList(new ArrayList<>(transferHistory));
        }
    }
}
