package com.banking;

import com.banking.enums.TransactionType;
import com.banking.enums.TransferStatus;
import com.banking.exception.ClosedAccountException;
import com.banking.exception.FrozenAccountException;
import com.banking.exception.InsufficientFundsException;
import com.banking.exception.InvalidTransactionException;
import com.banking.model.AccountNumber;
import com.banking.model.BankAccount;
import com.banking.model.FundTransfer;
import com.banking.model.LedgerEntry;
import com.banking.model.Money;
import com.banking.model.OverdraftLimit;
import com.banking.service.TransferService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JUnit 5 unit tests verifying atomic money transfers, validation boundaries,
 * and Memento-based rollback guarantees in {@link TransferService}.
 */
class TransferServiceTest {

    private TransferService transferService;
    private BankAccount accountA;
    private BankAccount accountB;

    @BeforeEach
    void setUp() {
        transferService = new TransferService();
        accountA = new BankAccount(
                AccountNumber.of("ACC-A100"),
                "Account Holder A",
                Money.inr("10000.00"),
                OverdraftLimit.inr("1000.00"));

        accountB = new BankAccount(
                AccountNumber.of("ACC-B200"),
                "Account Holder B",
                Money.inr("3000.00"),
                OverdraftLimit.zeroInr());
    }

    // ==================== POSITIVE TRANSFER TESTS ====================

    @Test
    @DisplayName("Positive: Successful ₹5,000 transfer debits Account A, credits Account B, and links both ledger entries")
    void shouldTransferFiveThousandRupeesAtomically() {
        Money combinedBefore = accountA.getBalance().add(accountB.getBalance());

        FundTransfer receipt = transferService.transfer(accountA, accountB, Money.inr("5000.00"));

        assertEquals(TransferStatus.COMPLETED, receipt.getStatus());
        assertEquals(Money.inr("5000.00"), accountA.getBalance());
        assertEquals(Money.inr("8000.00"), accountB.getBalance());
        assertEquals(combinedBefore, accountA.getBalance().add(accountB.getBalance()));

        // Verify matching TRANSFER_DEBIT and TRANSFER_CREDIT ledger entries
        List<LedgerEntry> historyA = accountA.getTransactionHistory();
        List<LedgerEntry> historyB = accountB.getTransactionHistory();
        LedgerEntry debitEntry = historyA.get(historyA.size() - 1);
        LedgerEntry creditEntry = historyB.get(historyB.size() - 1);

        assertEquals(TransactionType.TRANSFER_DEBIT, debitEntry.getTransactionType());
        assertEquals(TransactionType.TRANSFER_CREDIT, creditEntry.getTransactionType());
        assertEquals(receipt.getTransferId(), debitEntry.getRelatedTransferId());
        assertEquals(receipt.getTransferId(), creditEntry.getRelatedTransferId());
        assertTrue(accountA.isBalanceConsistentWithLedger());
        assertTrue(accountB.isBalanceConsistentWithLedger());
    }

    @Test
    @DisplayName("Positive: Transfer utilizing permitted overdraft on source account succeeds")
    void shouldAllowTransferUsingSourceOverdraftFacility() {
        // Account A has 10,000 + 1,000 overdraft = 11,000 max transferable
        FundTransfer receipt = transferService.transfer(accountA, accountB, Money.inr("10500.00"));

        assertEquals(TransferStatus.COMPLETED, receipt.getStatus());
        assertEquals(Money.inr("-500.00"), accountA.getBalance());
        assertEquals(Money.inr("13500.00"), accountB.getBalance());
        assertTrue(accountA.isBalanceConsistentWithLedger());
        assertTrue(accountB.isBalanceConsistentWithLedger());
    }

    // ==================== NEGATIVE & ATOMIC ROLLBACK TESTS ====================

    @Test
    @DisplayName("Negative: Transfer to a closed account fails and preserves source balance")
    void shouldRejectTransferToClosedAccount() {
        accountB.withdraw(Money.inr("3000.00"));
        accountB.close();

        assertThrows(ClosedAccountException.class,
                () -> transferService.transfer(accountA, accountB, Money.inr("5000.00")));

        assertEquals(Money.inr("10000.00"), accountA.getBalance());
        assertEquals(Money.zeroInr(), accountB.getBalance());
        assertEquals(1, accountA.getTransactionHistory().size());
        assertTrue(accountA.isBalanceConsistentWithLedger());
    }

    @Test
    @DisplayName("Negative: Transfer from a closed account fails and preserves destination balance")
    void shouldRejectTransferFromClosedAccount() {
        accountA.withdraw(Money.inr("10000.00"));
        accountA.close();

        assertThrows(ClosedAccountException.class,
                () -> transferService.transfer(accountA, accountB, Money.inr("500.00")));

        assertEquals(Money.zeroInr(), accountA.getBalance());
        assertEquals(Money.inr("3000.00"), accountB.getBalance());
        assertEquals(1, accountB.getTransactionHistory().size());
    }

    @Test
    @DisplayName("Negative: Transfer to a frozen account fails and preserves source balance")
    void shouldRejectTransferToFrozenAccount() {
        accountB.freeze();

        assertThrows(FrozenAccountException.class,
                () -> transferService.transfer(accountA, accountB, Money.inr("5000.00")));

        assertEquals(Money.inr("10000.00"), accountA.getBalance());
        assertEquals(Money.inr("3000.00"), accountB.getBalance());
        assertEquals(1, accountA.getTransactionHistory().size());
        assertEquals(1, accountB.getTransactionHistory().size());
    }

    @Test
    @DisplayName("Negative: Transfer from a frozen account fails and preserves destination balance")
    void shouldRejectTransferFromFrozenAccount() {
        accountA.freeze();

        assertThrows(FrozenAccountException.class,
                () -> transferService.transfer(accountA, accountB, Money.inr("5000.00")));

        assertEquals(Money.inr("10000.00"), accountA.getBalance());
        assertEquals(Money.inr("3000.00"), accountB.getBalance());
        assertEquals(1, accountA.getTransactionHistory().size());
        assertEquals(1, accountB.getTransactionHistory().size());
    }

    @Test
    @DisplayName("Negative: Transfer between the same account is rejected")
    void shouldRejectTransferBetweenSameAccount() {
        assertThrows(InvalidTransactionException.class,
                () -> transferService.transfer(accountA, accountA, Money.inr("5000.00")));

        assertEquals(Money.inr("10000.00"), accountA.getBalance());
        assertEquals(1, accountA.getTransactionHistory().size());
    }

    @Test
    @DisplayName("Negative: Zero or negative transfer amount is rejected")
    void shouldRejectZeroOrNegativeTransferAmount() {
        assertThrows(InvalidTransactionException.class,
                () -> transferService.transfer(accountA, accountB, Money.zeroInr()));
        assertThrows(InvalidTransactionException.class,
                () -> transferService.transfer(accountA, accountB, Money.inr("-5000.00")));

        assertEquals(Money.inr("10000.00"), accountA.getBalance());
        assertEquals(Money.inr("3000.00"), accountB.getBalance());
    }

    @Test
    @DisplayName("Negative: Transfer exceeding source overdraft limit fails without partial balance change")
    void shouldRejectTransferExceedingOverdraftLimit() {
        assertThrows(InsufficientFundsException.class,
                () -> transferService.transfer(accountA, accountB, Money.inr("11000.01")));

        assertEquals(Money.inr("10000.00"), accountA.getBalance());
        assertEquals(Money.inr("3000.00"), accountB.getBalance());
        assertTrue(accountA.isBalanceConsistentWithLedger());
        assertTrue(accountB.isBalanceConsistentWithLedger());
    }

    @Test
    @DisplayName("Atomicity Rollback: Mid-transfer failure AFTER debiting ₹5,000 from Account A rolls back balance and ledger")
    void shouldRollbackSourceDebitIfFailureOccursBeforeDestinationCredit() {
        Money initialA = accountA.getBalance();
        Money initialB = accountB.getBalance();
        int initialLedgerSizeA = accountA.getTransactionHistory().size();
        int initialLedgerSizeB = accountB.getTransactionHistory().size();

        // Simulate runtime failure occurring right AFTER ₹5,000 is deducted from Account A
        RuntimeException simulatedCrash = assertThrows(IllegalStateException.class, () ->
                transferService.executeTransfer(
                        accountA,
                        accountB,
                        Money.inr("5000.00"),
                        () -> {
                            // At this exact instant inside the transaction, A has already been debited to ₹5,000
                            assertEquals(Money.inr("5000.00"), accountA.getBalance());
                            throw new IllegalStateException("Simulated credit subsystem failure mid-transfer");
                        }));

        assertTrue(simulatedCrash.getMessage().contains("Simulated credit subsystem failure"));

        // Verify Memento rollback completely restored Account A's ₹10,000 balance and removed partial debit entry
        assertEquals(initialA, accountA.getBalance());
        assertEquals(initialB, accountB.getBalance());
        assertEquals(initialLedgerSizeA, accountA.getTransactionHistory().size());
        assertEquals(initialLedgerSizeB, accountB.getTransactionHistory().size());
        assertTrue(accountA.isBalanceConsistentWithLedger());
        assertTrue(accountB.isBalanceConsistentWithLedger());

        // Verify failed transfer record was logged with FAILED status
        List<FundTransfer> transferHistory = transferService.getTransferHistory();
        assertEquals(1, transferHistory.size());
        assertEquals(TransferStatus.FAILED, transferHistory.get(0).getStatus());
    }
}
