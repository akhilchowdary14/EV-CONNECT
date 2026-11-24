package com.banking;

import com.banking.enums.AccountStatus;
import com.banking.enums.TransactionType;
import com.banking.exception.AccountNotFoundException;
import com.banking.exception.ClosedAccountException;
import com.banking.exception.FrozenAccountException;
import com.banking.exception.InsufficientFundsException;
import com.banking.exception.InvalidTransactionException;
import com.banking.model.AccountNumber;
import com.banking.model.BankAccount;
import com.banking.model.LedgerEntry;
import com.banking.model.Money;
import com.banking.model.OverdraftLimit;
import com.banking.service.BankingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Currency;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Comprehensive JUnit 5 unit tests for {@link BankAccount}, {@link BankingService},
 * and associated Value Objects ({@link Money}, {@link AccountNumber}, {@link OverdraftLimit}).
 */
class BankAccountTest {

    private BankingService bankingService;
    private BankAccount standardAccount;

    @BeforeEach
    void setUp() {
        bankingService = new BankingService();
        standardAccount = bankingService.createAccount(
                AccountNumber.of("ACC-1001"),
                "Aarav Sharma",
                Money.inr("10000.00"),
                OverdraftLimit.inr("2000.00"));
    }

    // ==================== POSITIVE TEST CASES ====================

    @Test
    @DisplayName("Positive: Successful account creation initializes status, balance, and opening ledger entry")
    void shouldCreateAccountSuccessfully() {
        assertEquals(AccountNumber.of("ACC-1001"), standardAccount.getAccountNumber());
        assertEquals("Aarav Sharma", standardAccount.getAccountHolderName());
        assertEquals(AccountStatus.ACTIVE, standardAccount.getStatus());
        assertEquals(Money.inr("10000.00"), standardAccount.getBalance());
        assertEquals(OverdraftLimit.inr("2000.00"), standardAccount.getOverdraftLimit());
        assertEquals(1, standardAccount.getTransactionHistory().size());
        assertTrue(standardAccount.isBalanceConsistentWithLedger());

        // Zero-balance account starts with empty ledger and consistent zero balance
        BankAccount zeroAccount = bankingService.createAccount(
                "ACC-1002", "Priya Nair", Money.zeroInr(), OverdraftLimit.zeroInr());
        assertEquals(Money.zeroInr(), zeroAccount.getBalance());
        assertTrue(zeroAccount.getTransactionHistory().isEmpty());
        assertTrue(zeroAccount.isBalanceConsistentWithLedger());
    }

    @Test
    @DisplayName("Positive: Successful deposit updates balance and appends immutable DEPOSIT ledger entry")
    void shouldDepositMoneySuccessfully() {
        LedgerEntry entry = standardAccount.deposit(Money.inr("2500.50"), "Cheque clearance");

        assertEquals(Money.inr("12500.50"), standardAccount.getBalance());
        assertEquals(TransactionType.DEPOSIT, entry.getTransactionType());
        assertEquals(Money.inr("2500.50"), entry.getAmount());
        assertEquals(Money.inr("12500.50"), entry.getBalanceAfterTransaction());
        assertNull(entry.getRelatedTransferId());
        assertEquals(2, standardAccount.getTransactionHistory().size());
        assertTrue(standardAccount.isBalanceConsistentWithLedger());
    }

    @Test
    @DisplayName("Positive: Successful withdrawal within positive balance and up to exact overdraft boundary")
    void shouldWithdrawMoneyWithinBalanceAndOverdraftLimit() {
        // Withdraw within positive balance: 10,000 -> 4,000
        LedgerEntry first = standardAccount.withdraw(Money.inr("6000.00"));
        assertEquals(Money.inr("4000.00"), standardAccount.getBalance());
        assertEquals(TransactionType.WITHDRAWAL, first.getTransactionType());

        // Withdraw into overdraft up to exact -2,000 boundary: 4,000 - 6,000 = -2,000
        LedgerEntry second = standardAccount.withdraw(Money.inr("6000.00"));
        assertEquals(Money.inr("-2000.00"), standardAccount.getBalance());
        assertEquals(Money.inr("-2000.00"), second.getBalanceAfterTransaction());
        assertEquals(Money.zeroInr(), standardAccount.getAvailableFunds());
        assertTrue(standardAccount.isBalanceConsistentWithLedger());
    }

    @Test
    @DisplayName("Positive: Successful freeze and unfreeze transitions account status and restores withdrawal capability")
    void shouldFreezeAndUnfreezeAccountSuccessfully() {
        standardAccount.freeze();
        assertEquals(AccountStatus.FROZEN, standardAccount.getStatus());

        // Direct deposit into frozen account remains allowed (e.g. settling debt)
        standardAccount.deposit(Money.inr("500.00"));
        assertEquals(Money.inr("10500.00"), standardAccount.getBalance());

        // Unfreeze restores full withdrawal access
        standardAccount.unfreeze();
        assertEquals(AccountStatus.ACTIVE, standardAccount.getStatus());

        standardAccount.withdraw(Money.inr("500.00"));
        assertEquals(Money.inr("10000.00"), standardAccount.getBalance());
        assertTrue(standardAccount.isBalanceConsistentWithLedger());
    }

    @Test
    @DisplayName("Positive: Successful closure of a settled zero-balance account")
    void shouldCloseZeroBalanceAccountSuccessfully() {
        standardAccount.withdraw(Money.inr("10000.00"));
        assertEquals(Money.zeroInr(), standardAccount.getBalance());

        standardAccount.close();
        assertEquals(AccountStatus.CLOSED, standardAccount.getStatus());
        assertTrue(standardAccount.isBalanceConsistentWithLedger());
    }

    // ==================== NEGATIVE TEST CASES ====================

    @Test
    @DisplayName("Negative: Zero deposit is rejected without changing balance or ledger")
    void shouldRejectZeroDeposit() {
        int ledgerSizeBefore = standardAccount.getTransactionHistory().size();

        assertThrows(InvalidTransactionException.class,
                () -> standardAccount.deposit(Money.zeroInr()));

        assertEquals(Money.inr("10000.00"), standardAccount.getBalance());
        assertEquals(ledgerSizeBefore, standardAccount.getTransactionHistory().size());
    }

    @Test
    @DisplayName("Negative: Negative deposit is rejected without changing balance or ledger")
    void shouldRejectNegativeDeposit() {
        int ledgerSizeBefore = standardAccount.getTransactionHistory().size();

        assertThrows(InvalidTransactionException.class,
                () -> standardAccount.deposit(Money.inr("-500.00")));

        assertEquals(Money.inr("10000.00"), standardAccount.getBalance());
        assertEquals(ledgerSizeBefore, standardAccount.getTransactionHistory().size());
    }

    @Test
    @DisplayName("Negative: Zero withdrawal is rejected without changing balance or ledger")
    void shouldRejectZeroWithdrawal() {
        int ledgerSizeBefore = standardAccount.getTransactionHistory().size();

        assertThrows(InvalidTransactionException.class,
                () -> standardAccount.withdraw(Money.zeroInr()));

        assertEquals(Money.inr("10000.00"), standardAccount.getBalance());
        assertEquals(ledgerSizeBefore, standardAccount.getTransactionHistory().size());
    }

    @Test
    @DisplayName("Negative: Negative withdrawal is rejected without changing balance or ledger")
    void shouldRejectNegativeWithdrawal() {
        int ledgerSizeBefore = standardAccount.getTransactionHistory().size();

        assertThrows(InvalidTransactionException.class,
                () -> standardAccount.withdraw(Money.inr("-100.00")));

        assertEquals(Money.inr("10000.00"), standardAccount.getBalance());
        assertEquals(ledgerSizeBefore, standardAccount.getTransactionHistory().size());
    }

    @Test
    @DisplayName("Negative: Withdrawal beyond permitted overdraft limit throws InsufficientFundsException")
    void shouldRejectWithdrawalBeyondOverdraftLimit() {
        // Balance = 10,000, Overdraft Limit = 2,000 -> Max withdrawal = 12,000.00
        int ledgerSizeBefore = standardAccount.getTransactionHistory().size();

        assertThrows(InsufficientFundsException.class,
                () -> standardAccount.withdraw(Money.inr("12000.01")));

        assertEquals(Money.inr("10000.00"), standardAccount.getBalance());
        assertEquals(ledgerSizeBefore, standardAccount.getTransactionHistory().size());
        assertTrue(standardAccount.isBalanceConsistentWithLedger());
    }

    @Test
    @DisplayName("Negative: Withdrawal from frozen account throws FrozenAccountException")
    void shouldRejectWithdrawalFromFrozenAccount() {
        standardAccount.freeze();
        int ledgerSizeBefore = standardAccount.getTransactionHistory().size();

        assertThrows(FrozenAccountException.class,
                () -> standardAccount.withdraw(Money.inr("1000.00")));

        assertEquals(Money.inr("10000.00"), standardAccount.getBalance());
        assertEquals(ledgerSizeBefore, standardAccount.getTransactionHistory().size());
    }

    @Test
    @DisplayName("Negative: Closed account blocks withdrawals, deposits, freeze, and unfreeze")
    void shouldRejectAllTransactionsOnClosedAccount() {
        standardAccount.withdraw(Money.inr("10000.00"));
        standardAccount.close();
        int ledgerSizeAfterClose = standardAccount.getTransactionHistory().size();

        assertThrows(ClosedAccountException.class,
                () -> standardAccount.withdraw(Money.inr("100.00")));
        assertThrows(ClosedAccountException.class,
                () -> standardAccount.deposit(Money.inr("100.00")));
        assertThrows(ClosedAccountException.class,
                standardAccount::freeze);
        assertThrows(ClosedAccountException.class,
                standardAccount::unfreeze);
        assertThrows(ClosedAccountException.class,
                standardAccount::close);

        assertEquals(Money.zeroInr(), standardAccount.getBalance());
        assertEquals(ledgerSizeAfterClose, standardAccount.getTransactionHistory().size());
    }

    @Test
    @DisplayName("Negative: Cannot close account with positive or negative (overdrawn) balance")
    void shouldRejectClosingNonZeroOrOverdrawnAccount() {
        // Positive balance cannot be closed (would trap customer funds)
        assertThrows(InvalidTransactionException.class, standardAccount::close);

        // Overdrawn balance cannot be closed (would erase customer debt)
        standardAccount.withdraw(Money.inr("11000.00")); // Balance = -1,000
        assertThrows(InvalidTransactionException.class, standardAccount::close);
        assertEquals(AccountStatus.ACTIVE, standardAccount.getStatus());
    }

    @Test
    @DisplayName("Value Objects & Service: Validates Money currency/precision, AccountNumber format, and duplicate accounts")
    void shouldValidateValueObjectsAndBankingServiceLookups() {
        // Money currency-aware equality and scale validation
        assertEquals(Money.inr("500"), Money.inr("500.00"));
        assertNotEquals(Money.inr("500.00"), Money.of("500.00", Currency.getInstance("USD")));
        assertThrows(InvalidTransactionException.class, () -> Money.inr("100.125")); // 3 decimals invalid for INR
        assertThrows(InvalidTransactionException.class,
                () -> Money.inr("100.00").add(Money.of("50.00", Currency.getInstance("USD"))));

        // OverdraftLimit non-negative rule
        assertThrows(InvalidTransactionException.class, () -> OverdraftLimit.inr("-1.00"));

        // AccountNumber validation & normalization
        assertEquals(AccountNumber.of("acc-1001"), AccountNumber.of("ACC-1001"));
        assertThrows(InvalidTransactionException.class, () -> AccountNumber.of("AB")); // too short
        assertThrows(InvalidTransactionException.class, () -> AccountNumber.of("ACC@1001")); // invalid symbol

        // Duplicate account & missing account checks
        assertThrows(InvalidTransactionException.class,
                () -> bankingService.createAccount("ACC-1001", "Duplicate", Money.zeroInr(), OverdraftLimit.zeroInr()));
        assertThrows(AccountNotFoundException.class,
                () -> bankingService.findAccount("ACC-9999"));

        // Pagination check
        standardAccount.deposit(Money.inr("100.00"));
        standardAccount.deposit(Money.inr("200.00"));
        List<LedgerEntry> page1 = bankingService.getTransactionHistoryPage(AccountNumber.of("ACC-1001"), 1, 2);
        List<LedgerEntry> page2 = bankingService.getTransactionHistoryPage(AccountNumber.of("ACC-1001"), 2, 2);
        assertEquals(2, page1.size());
        assertEquals(1, page2.size());
    }
}
