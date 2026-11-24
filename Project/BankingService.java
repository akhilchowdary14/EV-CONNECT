package com.banking.service;

import com.banking.exception.AccountNotFoundException;
import com.banking.exception.InvalidTransactionException;
import com.banking.model.AccountNumber;
import com.banking.model.BankAccount;
import com.banking.model.FundTransfer;
import com.banking.model.LedgerEntry;
import com.banking.model.Money;
import com.banking.model.OverdraftLimit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Application Service coordinating account lifecycle management, single-account operations,
 * inter-account transfers, and ledger queries.
 */
public class BankingService {

    private final Map<AccountNumber, BankAccount> accounts = new LinkedHashMap<>();
    private final TransferService transferService;

    public BankingService() {
        this(new TransferService());
    }

    public BankingService(TransferService transferService) {
        if (transferService == null) {
            throw new InvalidTransactionException("TransferService must not be null.");
        }
        this.transferService = transferService;
    }

    /**
     * Creates and registers a new {@link BankAccount} with holder name, initial deposit, and overdraft limit.
     */
    public synchronized BankAccount createAccount(
            AccountNumber accountNumber,
            String accountHolderName,
            Money initialDeposit,
            OverdraftLimit overdraftLimit) {

        if (accountNumber == null) {
            throw new InvalidTransactionException("Account number must not be null.");
        }
        if (accounts.containsKey(accountNumber)) {
            throw new InvalidTransactionException(
                    "An account with account number " + accountNumber + " already exists.");
        }

        BankAccount account = new BankAccount(accountNumber, accountHolderName, initialDeposit, overdraftLimit);
        accounts.put(accountNumber, account);
        return account;
    }

    /**
     * Convenience overload accepting raw string account number.
     */
    public synchronized BankAccount createAccount(
            String rawAccountNumber,
            String accountHolderName,
            Money initialDeposit,
            OverdraftLimit overdraftLimit) {
        return createAccount(AccountNumber.of(rawAccountNumber), accountHolderName, initialDeposit, overdraftLimit);
    }

    /**
     * Convenience overload when holder name is omitted.
     */
    public synchronized BankAccount createAccount(
            AccountNumber accountNumber,
            Money initialDeposit,
            OverdraftLimit overdraftLimit) {

        if (accountNumber == null) {
            throw new InvalidTransactionException("Account number must not be null.");
        }
        if (accounts.containsKey(accountNumber)) {
            throw new InvalidTransactionException(
                    "An account with account number " + accountNumber + " already exists.");
        }

        BankAccount account = new BankAccount(accountNumber, initialDeposit, overdraftLimit);
        accounts.put(accountNumber, account);
        return account;
    }

    /**
     * Looks up a registered account by {@link AccountNumber}.
     *
     * @throws AccountNotFoundException if no matching account exists
     */
    public synchronized BankAccount findAccount(AccountNumber accountNumber) {
        if (accountNumber == null) {
            throw new InvalidTransactionException("Account number must not be null.");
        }
        BankAccount account = accounts.get(accountNumber);
        if (account == null) {
            throw new AccountNotFoundException("No bank account found with account number: " + accountNumber);
        }
        return account;
    }

    /**
     * Looks up a registered account by raw string account number.
     */
    public synchronized BankAccount findAccount(String rawAccountNumber) {
        return findAccount(AccountNumber.of(rawAccountNumber));
    }

    /**
     * Deposits a positive monetary amount into the specified account.
     */
    public LedgerEntry deposit(AccountNumber accountNumber, Money amount) {
        return findAccount(accountNumber).deposit(amount);
    }

    /**
     * Deposits a positive monetary amount with a custom description into the specified account.
     */
    public LedgerEntry deposit(String rawAccountNumber, Money amount, String description) {
        return findAccount(rawAccountNumber).deposit(amount, description);
    }

    /**
     * Withdraws a positive monetary amount from the specified account.
     */
    public LedgerEntry withdraw(AccountNumber accountNumber, Money amount) {
        return findAccount(accountNumber).withdraw(amount);
    }

    /**
     * Withdraws a positive monetary amount with a custom description from the specified account.
     */
    public LedgerEntry withdraw(String rawAccountNumber, Money amount, String description) {
        return findAccount(rawAccountNumber).withdraw(amount, description);
    }

    /**
     * Transfers money atomically from source account to destination account.
     */
    public FundTransfer transfer(
            AccountNumber sourceAccountNumber,
            AccountNumber destinationAccountNumber,
            Money amount) {

        BankAccount source = findAccount(sourceAccountNumber);
        BankAccount destination = findAccount(destinationAccountNumber);
        return transferService.transfer(source, destination, amount);
    }

    /**
     * Transfers money atomically using raw account number strings.
     */
    public FundTransfer transfer(
            String sourceAccountNumber,
            String destinationAccountNumber,
            Money amount) {
        return transfer(
                AccountNumber.of(sourceAccountNumber),
                AccountNumber.of(destinationAccountNumber),
                amount);
    }

    /**
     * Freezes an active bank account.
     */
    public void freezeAccount(AccountNumber accountNumber) {
        findAccount(accountNumber).freeze();
    }

    public void freezeAccount(String rawAccountNumber) {
        freezeAccount(AccountNumber.of(rawAccountNumber));
    }

    /**
     * Unfreezes a frozen bank account.
     */
    public void unfreezeAccount(AccountNumber accountNumber) {
        findAccount(accountNumber).unfreeze();
    }

    public void unfreezeAccount(String rawAccountNumber) {
        unfreezeAccount(AccountNumber.of(rawAccountNumber));
    }

    /**
     * Permanently closes an account whose balance has been settled to zero.
     */
    public void closeAccount(AccountNumber accountNumber) {
        findAccount(accountNumber).close();
    }

    public void closeAccount(String rawAccountNumber) {
        closeAccount(AccountNumber.of(rawAccountNumber));
    }

    /**
     * Retrieves the complete unmodifiable transaction ledger for an account.
     */
    public List<LedgerEntry> getTransactionHistory(AccountNumber accountNumber) {
        return findAccount(accountNumber).getTransactionHistory();
    }

    public List<LedgerEntry> getTransactionHistory(String rawAccountNumber) {
        return getTransactionHistory(AccountNumber.of(rawAccountNumber));
    }

    /**
     * Retrieves a 1-based page slice of ledger entries to demonstrate scalable ledger pagination.
     */
    public List<LedgerEntry> getTransactionHistoryPage(
            AccountNumber accountNumber,
            int pageNumber,
            int pageSize) {

        if (pageNumber < 1 || pageSize < 1) {
            throw new InvalidTransactionException("Page number and page size must both be >= 1.");
        }
        List<LedgerEntry> allEntries = findAccount(accountNumber).getTransactionHistory();
        int fromIndex = (pageNumber - 1) * pageSize;
        if (fromIndex >= allEntries.size()) {
            return Collections.emptyList();
        }
        int toIndex = Math.min(fromIndex + pageSize, allEntries.size());
        return Collections.unmodifiableList(new ArrayList<>(allEntries.subList(fromIndex, toIndex)));
    }

    /**
     * Returns an unmodifiable snapshot of all registered accounts.
     */
    public synchronized List<BankAccount> getAllAccounts() {
        return Collections.unmodifiableList(new ArrayList<>(accounts.values()));
    }

    public TransferService getTransferService() {
        return transferService;
    }

    public List<FundTransfer> getTransferHistory() {
        return transferService.getTransferHistory();
    }
}
