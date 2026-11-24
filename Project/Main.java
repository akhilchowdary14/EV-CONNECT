package com.banking;

import com.banking.exception.BankingException;
import com.banking.model.AccountNumber;
import com.banking.model.BankAccount;
import com.banking.model.FundTransfer;
import com.banking.model.LedgerEntry;
import com.banking.model.Money;
import com.banking.model.OverdraftLimit;
import com.banking.service.BankingService;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Scanner;

/**
 * Interactive Console Presentation Layer for Case Study 3: Bank Account and Money Transfers.
 *
 * <p>Pre-loads realistic sample accounts and transactions—including the ₹5,000 transfer example—so
 * students and faculty can immediately inspect, test, and demonstrate every business rule.</p>
 */
public class Main {

    private final BankingService bankingService;
    private final Scanner scanner;

    public Main(BankingService bankingService, Scanner scanner) {
        this.bankingService = bankingService;
        this.scanner = scanner;
    }

    public static void main(String[] args) {
        // Ensure UTF-8 output so the Indian Rupee symbol (₹) renders cleanly on Windows terminals
        System.setOut(new PrintStream(new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8));

        BankingService bankingService = new BankingService();
        seedDemonstrationData(bankingService);

        try (Scanner scanner = new Scanner(System.in, StandardCharsets.UTF_8)) {
            Main app = new Main(bankingService, scanner);
            app.run();
        }
    }

    /**
     * Seeds sample accounts and initial transactions for immediate classroom demonstration.
     */
    public static void seedDemonstrationData(BankingService service) {
        service.createAccount(
                "ACC-1001",
                "Aarav Sharma",
                Money.inr("15000.00"),
                OverdraftLimit.inr("2000.00"));

        service.createAccount(
                "ACC-1002",
                "Priya Nair",
                Money.inr("8000.00"),
                OverdraftLimit.inr("1000.00"));

        service.createAccount(
                "ACC-1003",
                "Rohan Verma",
                Money.inr("0.00"),
                OverdraftLimit.inr("0.00"));

        // Perform sample transactions including the ₹5,000 transfer scenario
        service.deposit("ACC-1001", Money.inr("2500.00"), "Salary performance bonus");
        service.withdraw("ACC-1002", Money.inr("1500.00"), "ATM cash withdrawal");
        service.transfer("ACC-1001", "ACC-1002", Money.inr("5000.00"));
    }

    /**
     * Runs the interactive console menu loop until the user selects Exit (10) or input ends.
     */
    public void run() {
        printWelcomeBanner();

        boolean running = true;
        while (running) {
            printMenu();
            System.out.print("Enter your choice (1-10): ");

            if (!scanner.hasNextLine()) {
                System.out.println("\nInput stream closed. Exiting Bank Management System.");
                break;
            }

            String rawChoice = scanner.nextLine().trim();
            if (rawChoice.isEmpty()) {
                System.out.println("[ERROR] Please enter a valid menu option between 1 and 10.");
                continue;
            }

            int choice;
            try {
                choice = Integer.parseInt(rawChoice);
            } catch (NumberFormatException ex) {
                System.out.println("[ERROR] Invalid input '" + rawChoice + "'. Please enter a number from 1 to 10.");
                continue;
            }

            System.out.println();
            try {
                switch (choice) {
                    case 1 -> handleCreateAccount();
                    case 2 -> handleViewAccountDetails();
                    case 3 -> handleDeposit();
                    case 4 -> handleWithdraw();
                    case 5 -> handleTransfer();
                    case 6 -> handleFreezeAccount();
                    case 7 -> handleUnfreezeAccount();
                    case 8 -> handleCloseAccount();
                    case 9 -> handleViewTransactionHistory();
                    case 10 -> {
                        System.out.println("Thank you for using the Bank Management System. Goodbye!");
                        running = false;
                    }
                    default -> System.out.println("[ERROR] Option out of range. Please choose between 1 and 10.");
                }
            } catch (BankingException ex) {
                System.out.println("[TRANSACTION REJECTED] " + ex.getMessage());
            } catch (Exception ex) {
                System.out.println("[UNEXPECTED ERROR] Operation could not be completed: " + ex.getMessage());
            }
        }
    }

    private void printWelcomeBanner() {
        System.out.println("=========================================================================");
        System.out.println("      CASE STUDY 3: BANK ACCOUNT AND MONEY TRANSFERS (DDD / JAVA 17)     ");
        System.out.println("=========================================================================");
        System.out.println("Pre-loaded Demo Accounts:");
        printAccountsSummaryTable();
    }

    private void printMenu() {
        System.out.println();
        System.out.println("========== BANK MANAGEMENT SYSTEM ==========");
        System.out.println("1. Create Account");
        System.out.println("2. View Account Details");
        System.out.println("3. Deposit Money");
        System.out.println("4. Withdraw Money");
        System.out.println("5. Transfer Money");
        System.out.println("6. Freeze Account");
        System.out.println("7. Unfreeze Account");
        System.out.println("8. Close Account");
        System.out.println("9. View Transaction History");
        System.out.println("10. Exit");
        System.out.println("=============================================");
    }

    private void handleCreateAccount() {
        System.out.println("--- Create New Bank Account ---");
        String accNumStr = promptLine("Enter Account Number (4-20 alphanumeric/hyphen, e.g., ACC-1004): ");
        AccountNumber accountNumber = AccountNumber.of(accNumStr);

        String holderName = promptLine("Enter Account Holder Name: ");
        String initialDepositStr = promptLine("Enter Initial Opening Deposit in INR (e.g., 5000.00): ");
        Money initialDeposit = Money.inr(initialDepositStr);

        String overdraftStr = promptLine("Enter Permitted Overdraft Limit in INR (e.g., 1000.00 or 0): ");
        OverdraftLimit overdraftLimit = OverdraftLimit.inr(overdraftStr);

        BankAccount created = bankingService.createAccount(
                accountNumber, holderName, initialDeposit, overdraftLimit);

        System.out.println("[SUCCESS] Account created successfully!");
        printDetailedAccountCard(created);
    }

    private void handleViewAccountDetails() {
        System.out.println("--- View Account Details ---");
        String input = promptLine("Enter Account Number (or press ENTER / type ALL to list all accounts): ");
        if (input.isBlank() || "ALL".equalsIgnoreCase(input.trim())) {
            printAccountsSummaryTable();
            return;
        }

        BankAccount account = bankingService.findAccount(input);
        printDetailedAccountCard(account);
    }

    private void handleDeposit() {
        System.out.println("--- Deposit Money ---");
        String accNum = promptLine("Enter Account Number: ");
        String amountStr = promptLine("Enter Deposit Amount in INR: ");
        String description = promptLine("Enter Description (optional, press ENTER for default): ");

        Money amount = Money.inr(amountStr);
        LedgerEntry entry = bankingService.deposit(accNum, amount, description);
        BankAccount updated = bankingService.findAccount(accNum);

        System.out.println("[SUCCESS] Deposited " + amount + " into account " + updated.getAccountNumber() + ".");
        System.out.println("          Transaction ID : " + entry.getTransactionId());
        System.out.println("          Updated Balance: " + updated.getBalance());
    }

    private void handleWithdraw() {
        System.out.println("--- Withdraw Money ---");
        String accNum = promptLine("Enter Account Number: ");
        String amountStr = promptLine("Enter Withdrawal Amount in INR: ");
        String description = promptLine("Enter Description (optional, press ENTER for default): ");

        Money amount = Money.inr(amountStr);
        LedgerEntry entry = bankingService.withdraw(accNum, amount, description);
        BankAccount updated = bankingService.findAccount(accNum);

        System.out.println("[SUCCESS] Withdrew " + amount + " from account " + updated.getAccountNumber() + ".");
        System.out.println("          Transaction ID : " + entry.getTransactionId());
        System.out.println("          Updated Balance: " + updated.getBalance());
        System.out.println("          Available Funds: " + updated.getAvailableFunds());
    }

    private void handleTransfer() {
        System.out.println("--- Atomic Fund Transfer ---");
        String sourceAcc = promptLine("Enter Source Account Number: ");
        String destAcc = promptLine("Enter Destination Account Number: ");
        String amountStr = promptLine("Enter Transfer Amount in INR: ");

        Money amount = Money.inr(amountStr);
        FundTransfer receipt = bankingService.transfer(sourceAcc, destAcc, amount);

        BankAccount source = bankingService.findAccount(sourceAcc);
        BankAccount destination = bankingService.findAccount(destAcc);

        System.out.println("[SUCCESS] Atomic transfer completed!");
        System.out.println("          Transfer ID         : " + receipt.getTransferId());
        System.out.println("          Transfer Status     : " + receipt.getStatus());
        System.out.println("          Amount Transferred  : " + receipt.getAmount());
        System.out.println("          Source (" + source.getAccountNumber() + ") Balance     : " + source.getBalance());
        System.out.println("          Destination (" + destination.getAccountNumber() + ") Balance: " + destination.getBalance());
    }

    private void handleFreezeAccount() {
        System.out.println("--- Freeze Account ---");
        String accNum = promptLine("Enter Account Number to Freeze: ");
        bankingService.freezeAccount(accNum);
        BankAccount account = bankingService.findAccount(accNum);
        System.out.println("[SUCCESS] Account " + account.getAccountNumber()
                + " is now " + account.getStatus() + ". Withdrawals and transfers are blocked.");
    }

    private void handleUnfreezeAccount() {
        System.out.println("--- Unfreeze Account ---");
        String accNum = promptLine("Enter Account Number to Unfreeze: ");
        bankingService.unfreezeAccount(accNum);
        BankAccount account = bankingService.findAccount(accNum);
        System.out.println("[SUCCESS] Account " + account.getAccountNumber()
                + " has been restored to " + account.getStatus() + " status.");
    }

    private void handleCloseAccount() {
        System.out.println("--- Close Account ---");
        String accNum = promptLine("Enter Account Number to Close (balance must be ₹0.00): ");
        bankingService.closeAccount(accNum);
        BankAccount account = bankingService.findAccount(accNum);
        System.out.println("[SUCCESS] Account " + account.getAccountNumber()
                + " is now permanently " + account.getStatus() + ".");
    }

    private void handleViewTransactionHistory() {
        System.out.println("--- View Immutable Transaction Ledger ---");
        String accNum = promptLine("Enter Account Number: ");
        BankAccount account = bankingService.findAccount(accNum);
        List<LedgerEntry> history = account.getTransactionHistory();

        System.out.println("Account: " + account.getAccountNumber()
                + " (" + account.getAccountHolderName() + ") | Status: " + account.getStatus()
                + " | Current Balance: " + account.getBalance()
                + " | Ledger Consistent: " + (account.isBalanceConsistentWithLedger() ? "YES" : "NO"));
        System.out.println("------------------------------------------------------------------------------------------------------------");
        if (history.isEmpty()) {
            System.out.println("No transactions recorded for this account yet.");
        } else {
            for (LedgerEntry entry : history) {
                System.out.println(entry);
            }
        }
        System.out.println("------------------------------------------------------------------------------------------------------------");
        System.out.println("Total Ledger Entries: " + history.size()
                + " | Reconstructed Ledger Balance: " + account.reconstructBalanceFromLedger());
    }

    private void printAccountsSummaryTable() {
        List<BankAccount> allAccounts = bankingService.getAllAccounts();
        System.out.println("---------------------------------------------------------------------------------------");
        System.out.printf("%-12s | %-18s | %-8s | %14s | %14s | %5s%n",
                "ACCOUNT NO", "HOLDER NAME", "STATUS", "BALANCE", "OVERDRAFT", "TXNS");
        System.out.println("---------------------------------------------------------------------------------------");
        for (BankAccount acc : allAccounts) {
            System.out.printf("%-12s | %-18s | %-8s | %14s | %14s | %5d%n",
                    acc.getAccountNumber(),
                    truncate(acc.getAccountHolderName(), 18),
                    acc.getStatus(),
                    acc.getBalance(),
                    acc.getOverdraftLimit(),
                    acc.getTransactionHistory().size());
        }
        System.out.println("---------------------------------------------------------------------------------------");
    }

    private void printDetailedAccountCard(BankAccount account) {
        System.out.println("------------------------------------------------------------");
        System.out.println("Account Number       : " + account.getAccountNumber());
        System.out.println("Account Holder       : " + account.getAccountHolderName());
        System.out.println("Account Status       : " + account.getStatus());
        System.out.println("Current Balance      : " + account.getBalance());
        System.out.println("Overdraft Limit      : " + account.getOverdraftLimit()
                + " (Min Allowed: " + account.getOverdraftLimit().getMinimumAllowedBalance() + ")");
        System.out.println("Available Withdrawal : " + account.getAvailableFunds());
        System.out.println("Ledger Entries       : " + account.getTransactionHistory().size());
        System.out.println("Ledger Consistency   : "
                + (account.isBalanceConsistentWithLedger() ? "VERIFIED (Balance == Ledger Sum)" : "MISMATCH"));
        System.out.println("------------------------------------------------------------");
    }

    private String promptLine(String prompt) {
        System.out.print(prompt);
        if (!scanner.hasNextLine()) {
            return "";
        }
        return scanner.nextLine();
    }

    private static String truncate(String text, int maxLen) {
        if (text.length() <= maxLen) {
            return text;
        }
        return text.substring(0, maxLen - 3) + "...";
    }
}
