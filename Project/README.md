# Bank Account and Money Transfers System (Case Study 3)

## Project Overview

A modular, layered **Java 17** banking application built for **Case Study 3: Bank Account and Money Transfers** in **Advanced Object-Oriented Programming (AOOP)**. The project demonstrates rich **Object-Oriented Design (OOD)** and **Domain-Driven Design (DDD)** principles—including self-enforcing Entities, immutable Value Objects, exact `BigDecimal` monetary arithmetic, atomic inter-account transfers with Memento-based rollback, and an append-only immutable transaction ledger—without relying on heavy external frameworks or databases.

> For complete answers to Case Study Questions **Q1 through Q5** (domain invariants, Entity vs. Value Object classification, self-enforcing Java design, ₹5,000 atomic transfer & distributed Saga analysis, and scalable ledger evaluation), see **[DOCUMENTATION.md](./DOCUMENTATION.md)**.

---

## Objectives

- Create and manage bank accounts with validated, immutable `AccountNumber` identities.
- Perform deposits and withdrawals with strict positive-amount and `OverdraftLimit` enforcement.
- Execute **atomic money transfers** (`TransferService`) between accounts so partial debits never cause money loss.
- Support account lifecycle state transitions: `ACTIVE`, `FROZEN`, and permanently `CLOSED`.
- Record an immutable, append-only `LedgerEntry` history where `getBalance()` always matches the ledger sum.
- Enforce all 10 domain invariants inside rich domain objects rather than UI scripts.

---

## Technologies Used

| Component | Technology |
| :--- | :--- |
| **Language** | Java 17 LTS |
| **Build Tool** | Apache Maven 3.9+ |
| **Unit Testing** | JUnit 5 (`junit-jupiter` 5.10.2) |
| **Monetary Arithmetic** | `java.math.BigDecimal` + `java.util.Currency` (`INR` default) |
| **Interface** | Interactive Console Application (`java.util.Scanner`) |
| **Documentation** | GitHub-Flavored Markdown (`README.md`, `DOCUMENTATION.md`) |

---

## Key Features

- **Exact Monetary Precision (`Money`):** Currency-aware `BigDecimal` arithmetic; completely forbids `float` and `double`.
- **Self-Enforcing Aggregate Root (`BankAccount`):** Private fields, zero public setters, unmodifiable ledger copies, and continuous `verifyLedgerConsistency()` post-condition checks.
- **Overdraft Protection (`OverdraftLimit`):** Allows controlled negative balances down to `-overdraftLimit` and throws `InsufficientFundsException` beyond the boundary.
- **Atomic Transfers & Rollback (`TransferService`):** Combines pre-flight validation, canonical `AccountNumber` lock ordering (deadlock-free), GoF **Memento (`AccountSnapshot`)** rollback on mid-transfer exceptions, and conservation-of-money verification.
- **Pre-Seeded Demonstration Data:** Starts with three sample accounts (`ACC-1001`, `ACC-1002`, `ACC-1003`) and sample transactions—including the **₹5,000 transfer** scenario—ready for immediate viva demonstration.

---

## Project Structure

```text
aoop/  (Maven Project: bank-account-system)
│
├── src/
│   ├── main/
│   │   └── java/
│   │       └── com/banking/
│   │           ├── Main.java
│   │           ├── model/
│   │           │   ├── BankAccount.java
│   │           │   ├── AccountNumber.java
│   │           │   ├── Money.java
│   │           │   ├── LedgerEntry.java
│   │           │   ├── OverdraftLimit.java
│   │           │   └── FundTransfer.java
│   │           ├── service/
│   │           │   ├── BankingService.java
│   │           │   └── TransferService.java
│   │           ├── exception/
│   │           │   ├── BankingException.java
│   │           │   ├── AccountNotFoundException.java
│   │           │   ├── InsufficientFundsException.java
│   │           │   ├── FrozenAccountException.java
│   │           │   ├── ClosedAccountException.java
│   │           │   └── InvalidTransactionException.java
│   │           └── enums/
│   │               ├── AccountStatus.java
│   │               ├── TransactionType.java
│   │               └── TransferStatus.java
│   │
│   └── test/
│       └── java/
│           └── com/banking/
│               ├── BankAccountTest.java
│               ├── TransferServiceTest.java
│               └── LedgerEntryTest.java
│
├── pom.xml
├── README.md
└── DOCUMENTATION.md
```

---

## Prerequisites (Windows 11)

1. **Java Development Kit (JDK) 17 or later**
2. **Apache Maven 3.8+ / 3.9+**

### Installing JDK 17 and Maven on Windows 11 (if not yet in `PATH`)
Open **PowerShell** and run:

```powershell
winget install Microsoft.OpenJDK.17
winget install Apache.Maven
```

Restart PowerShell and verify:

```powershell
java -version
javac -version
mvn -version
```

---

## Installation, Compilation, and Execution (Windows 11)

Open **PowerShell** or **Windows Terminal** in the project directory:

```powershell
cd "c:\Users\Acer\OneDrive\Desktop\aoop"
```

### 1. Compile and Package the Application

```powershell
mvn clean package
```

This compiles all Java 17 sources, executes all 28 JUnit 5 tests, and builds `target\bank-account-system-1.0.0.jar`.

### 2. Run the Interactive Console Application

Enable UTF-8 console encoding so the Indian Rupee symbol (`₹`) renders cleanly on Windows 11, then run either via `java -jar` or Maven:

```powershell
chcp 65001
java "-Dfile.encoding=UTF-8" -jar target\bank-account-system-1.0.0.jar
```

*(Alternative via Maven Exec Plugin)*:

```powershell
chcp 65001
mvn -q exec:java
```

---

## How to Execute Unit Tests

Run the complete JUnit 5 test suite (`BankAccountTest`, `TransferServiceTest`, `LedgerEntryTest`):

```powershell
mvn test
```

### Expected Test Output

```text
-------------------------------------------------------
 T E S T S
-------------------------------------------------------
Running com.banking.BankAccountTest
Tests run: 14, Failures: 0, Errors: 0, Skipped: 0
Running com.banking.LedgerEntryTest
Tests run: 4, Failures: 0, Errors: 0, Skipped: 0
Running com.banking.TransferServiceTest
Tests run: 10, Failures: 0, Errors: 0, Skipped: 0

Results:

Tests run: 28, Failures: 0, Errors: 0, Skipped: 0

------------------------------------------------------------------------
BUILD SUCCESS
------------------------------------------------------------------------
```

---

## Sample Console Interaction & Expected Output

```text
=========================================================================
      CASE STUDY 3: BANK ACCOUNT AND MONEY TRANSFERS (DDD / JAVA 17)
=========================================================================
Pre-loaded Demo Accounts:
---------------------------------------------------------------------------------------
ACCOUNT NO   | HOLDER NAME        | STATUS   |        BALANCE |      OVERDRAFT |  TXNS
---------------------------------------------------------------------------------------
ACC-1001     | Aarav Sharma       | ACTIVE   |     ₹12,500.00 |      ₹2,000.00 |     3
ACC-1002     | Priya Nair         | ACTIVE   |     ₹11,500.00 |      ₹1,000.00 |     3
ACC-1003     | Rohan Verma        | ACTIVE   |          ₹0.00 |          ₹0.00 |     0
---------------------------------------------------------------------------------------

========== BANK MANAGEMENT SYSTEM ==========
1. Create Account
2. View Account Details
3. Deposit Money
4. Withdraw Money
5. Transfer Money
6. Freeze Account
7. Unfreeze Account
8. Close Account
9. View Transaction History
10. Exit
=============================================
Enter your choice (1-10): 5

--- Atomic Fund Transfer ---
Enter Source Account Number: ACC-1001
Enter Destination Account Number: ACC-1002
Enter Transfer Amount in INR: 2000.00
[SUCCESS] Atomic transfer completed!
          Transfer ID         : TRF-00002-E145EF
          Transfer Status     : COMPLETED
          Amount Transferred  : ₹2,000.00
          Source (ACC-1001) Balance     : ₹10,500.00
          Destination (ACC-1002) Balance: ₹13,500.00

Enter your choice (1-10): 6

--- Freeze Account ---
Enter Account Number to Freeze: ACC-1002
[SUCCESS] Account ACC-1002 is now FROZEN. Withdrawals and transfers are blocked.

Enter your choice (1-10): 4

--- Withdraw Money ---
Enter Account Number: ACC-1002
Enter Withdrawal Amount in INR: 500.00
Enter Description (optional, press ENTER for default): ATM cash
[TRANSACTION REJECTED] Cannot withdraw from frozen account ACC-1002.

Enter your choice (1-10): 9

--- View Immutable Transaction Ledger ---
Enter Account Number: ACC-1001
Account: ACC-1001 (Aarav Sharma) | Status: ACTIVE | Current Balance: ₹10,500.00 | Ledger Consistent: YES
------------------------------------------------------------------------------------------------------------
[2026-11-24 12:06:13] TXN-00001-A60329 | DEPOSIT         | Amount:   ₹15,000.00 | Balance:   ₹15,000.00 | Account opening deposit
[2026-11-24 12:06:13] TXN-00003-718445 | DEPOSIT         | Amount:    ₹2,500.00 | Balance:   ₹17,500.00 | Salary performance bonus
[2026-11-24 12:06:13] TXN-00005-917CB9 | TRANSFER_DEBIT  | Amount:    ₹5,000.00 | Balance:   ₹12,500.00 | Transfer to account ACC-1002 | Ref: TRF-00001-219D0A
[2026-11-24 12:06:13] TXN-00010-D5C8C3 | TRANSFER_DEBIT  | Amount:    ₹2,000.00 | Balance:   ₹10,500.00 | Transfer to account ACC-1004 | Ref: TRF-00002-E145EF
------------------------------------------------------------------------------------------------------------
Total Ledger Entries: 4 | Reconstructed Ledger Balance: ₹10,500.00

Enter your choice (1-10): 10

Thank you for using the Bank Management System. Goodbye!
```
