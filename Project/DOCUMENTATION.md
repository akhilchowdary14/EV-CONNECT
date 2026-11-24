# CASE STUDY 3: BANK ACCOUNT AND MONEY TRANSFERS — TECHNICAL DOCUMENTATION

**Course:** Advanced Object-Oriented Programming (AOOP)  
**Java Target:** Java 17 LTS  
**Design Paradigm:** Object-Oriented Design (OOD) & Domain-Driven Design (DDD)

---

## Architectural Overview

The application is structured into four cohesive layers with strict inward dependency flow:

```text
+-------------------------------------------------------------------+
| Presentation Layer: com.banking.Main (Interactive Console UI)     |
+-------------------------------------------------------------------+
                                  |
                                  v
+-------------------------------------------------------------------+
| Application Layer: com.banking.service.BankingService             |
| (Account registry, use-case coordination, ledger pagination)      |
+-------------------------------------------------------------------+
                                  |
                                  v
+-------------------------------------------------------------------+
| Domain Service Layer: com.banking.service.TransferService         |
| (Cross-aggregate ₹ transfer atomicity, lock ordering, rollback)   |
+-------------------------------------------------------------------+
                                  |
                                  v
+-------------------------------------------------------------------+
| Domain Model Layer: com.banking.model.*                           |
| - Entities:      BankAccount (Aggregate Root), LedgerEntry,       |
|                  FundTransfer                                     |
| - Value Objects: AccountNumber, Money, OverdraftLimit             |
| - Enums & Domain Exceptions: com.banking.enums.*, exception.*     |
+-------------------------------------------------------------------+
```

### Key Design Decisions
1. **Rich Self-Enforcing Domain Model (Not Anemic):** Business rules live inside `BankAccount`, `Money`, `OverdraftLimit`, and `LedgerEntry` rather than in UI/controller code, making invalid domain states unrepresentable.
2. **Memento Pattern for In-Memory Atomic Rollback:** `BankAccount` exposes an opaque `AccountSnapshot` Memento (with a `private` constructor and owner verification) so `TransferService` can roll back partial state changes if an error occurs mid-transfer—without ever exposing `setBalance()` or `setStatus()` mutators to external classes.
3. **Opening Deposit Recorded in Ledger:** When an account is opened with a positive initial deposit (e.g., `₹15,000.00`), an opening `DEPOSIT` `LedgerEntry` is recorded immediately so that replaying the ledger from `₹0.00` always equals `getBalance()`.

---

## Q1. Identification of Domain Invariants (6 Marks)

A **domain invariant** is a business rule that must remain true at the end of every valid operation across the lifetime of the domain model. Below are **ten enforced business invariants** (exceeding the minimum six required) implemented in this system.

---

### Invariant 1: Overdraft Boundary Rule
1. **Business Rule:** An account's balance must never fall below the negative boundary defined by its permitted overdraft limit (`balance >= -overdraftLimit`).
2. **Why the Rule Is Important:** Overdraft limits represent approved credit risk boundaries set by the bank. Enforcing this floor guarantees customers only borrow within authorized credit limits.
3. **Possible Consequences if Violated:** Unauthorized credit extension, unrecoverable financial losses/bad debt for the bank, regulatory non-compliance, and inaccurate liquidity reporting.
4. **How the Java Implementation Enforces It:**
   - `OverdraftLimit` validates during construction that the limit itself is non-negative (`!limitAmount.isNegative()`).
   - Before any debit in `BankAccount.withdraw(...)` or `BankAccount.validateCanTransferOut(...)`, `overdraftLimit.validateDebit(this.balance, amount, this.accountNumber)` calculates `projectedBalance = currentBalance.subtract(debitAmount)` and throws `InsufficientFundsException` if `projectedBalance < -limitAmount`.

---

### Invariant 2: Strictly Positive Transaction Amounts
1. **Business Rule:** All financial transaction amounts (deposits, withdrawals, and fund transfers) must be strictly greater than zero (`amount > 0`).
2. **Why the Rule Is Important:** A zero-amount transaction carries no economic meaning and pollutes audit logs, while a negative deposit would silently act as an unauthorized withdrawal (and a negative withdrawal would act as a fabricated deposit!).
3. **Possible Consequences if Violated:** Severe security exploit (withdrawing funds from frozen accounts via negative deposits or inflating balances via negative withdrawals) and corrupted ledger records.
4. **How the Java Implementation Enforces It:**
   - `Money.requirePositive(String context)` checks `amount.compareTo(BigDecimal.ZERO) > 0` and throws `InvalidTransactionException` if the value is `<= 0`.
   - Called at the entry point of `BankAccount.deposit(...)`, `BankAccount.withdraw(...)`, `TransferService.executeTransfer(...)`, `FundTransfer` constructor, and `LedgerEntry` constructor.

---

### Invariant 3: Frozen Account Transaction Restriction
1. **Business Rule:** A frozen (`AccountStatus.FROZEN`) account cannot perform withdrawals or participate in outgoing/incoming inter-account fund transfers.
2. **Why the Rule Is Important:** Accounts are frozen due to suspected fraud, court orders, KYC non-compliance, or regulatory holds to prevent unauthorized fund movement while under investigation.
3. **Possible Consequences if Violated:** Illicit money laundering, theft of disputed funds, legal liability for violating judicial/regulatory freeze orders.
4. **How the Java Implementation Enforces It:**
   - `BankAccount.ensureActiveForWithdrawal()`, `BankAccount.validateCanTransferOut(...)`, and `BankAccount.validateCanTransferIn(...)` inspect `this.status == AccountStatus.FROZEN` and immediately throw `FrozenAccountException` before any balance calculation occurs.

---

### Invariant 4: Closed Account Permanence and Transaction Prohibition
1. **Business Rule:** A closed (`AccountStatus.CLOSED`) account is in a permanent terminal state; it cannot perform deposits, withdrawals, transfers, freezes, or unfreezes, and an account can only transition to `CLOSED` once its balance is settled to `₹0.00`.
2. **Why the Rule Is Important:** Closing an account terminates the contractual relationship between customer and bank. Allowing transactions on closed accounts creates phantom liabilities, while closing an account with a non-zero balance would either trap customer funds forever or erase overdrawn debt.
3. **Possible Consequences if Violated:** Orphaned funds stranded in inaccessible accounts, uncollected overdraft debt, tax/audit reporting discrepancies.
4. **How the Java Implementation Enforces It:**
   - `BankAccount.close()` throws `InvalidTransactionException` if `!this.balance.isZero()`, and throws `ClosedAccountException` if already closed.
   - `BankAccount.ensureNotClosed(...)` guards `deposit`, `withdraw`, `freeze`, `unfreeze`, `validateCanTransferOut`, and `validateCanTransferIn`, throwing `ClosedAccountException`.

---

### Invariant 5: Atomic Money Transfers (Conservation of Money)
1. **Business Rule:** An inter-account fund transfer must either succeed completely (debiting the source and crediting the destination by the exact same amount) or fail completely with zero net change to either account (`sourceBefore + destBefore == sourceAfter + destAfter`).
2. **Why the Rule Is Important:** Money cannot vanish into thin air or be duplicated during movement between accounts.
3. **Possible Consequences if Violated:** Direct loss of customer money (source debited without destination credited), money creation out of nothing, customer disputes, and broken balance sheets.
4. **How the Java Implementation Enforces It:**
   - `TransferService.executeTransfer(...)` acquires locks on both accounts in canonical `AccountNumber` order, pre-validates both accounts, captures `BankAccount.AccountSnapshot` Mementos, executes debit and credit inside a `try` block, verifies `combinedBefore.equals(combinedAfter)`, and on any `RuntimeException` restores both accounts via `restoreSnapshot(...)` in the `catch` block.

---

### Invariant 6: Mandatory Immutable Audit Ledger Entries
1. **Business Rule:** Every completed balance-changing financial operation must atomically generate a corresponding immutable `LedgerEntry` (one entry for a deposit/withdrawal; two linked entries—`TRANSFER_DEBIT` and `TRANSFER_CREDIT`—sharing `relatedTransferId` for a transfer).
2. **Why the Rule Is Important:** Banking systems require complete traceability and non-repudiation for every rupee moved.
3. **Possible Consequences if Violated:** Unexplained balance changes ("ghost money"), inability to audit fraud or resolve customer disputes, failure of statutory financial audits.
4. **How the Java Implementation Enforces It:**
   - Every mutating method in `BankAccount` (`deposit`, `withdraw`, `recordTransferDebit`, `recordTransferCredit`, and positive opening deposit in constructor) constructs and appends a `final` `LedgerEntry` in the exact same `synchronized` block that updates `this.balance`.

---

### Invariant 7: Balance-to-Ledger Mathematical Consistency
1. **Business Rule:** At the end of every operation, `BankAccount.getBalance()` must equal the net sum of all credit entries (`DEPOSIT`, `TRANSFER_CREDIT`) minus all debit entries (`WITHDRAWAL`, `TRANSFER_DEBIT`) in `transactionHistory`.
2. **Why the Rule Is Important:** Ensures the cached account balance and historical ledger never drift out of sync.
3. **Possible Consequences if Violated:** Conflicting account statements where the closing balance disagrees with itemized transactions, eroding customer trust and regulatory compliance.
4. **How the Java Implementation Enforces It:**
   - `BankAccount.verifyLedgerConsistency()` invokes `reconstructBalanceFromLedger()` at the end of account construction, every deposit/withdrawal/transfer leg, and every snapshot rollback, throwing `IllegalStateException` if `balance` ever diverges from the ledger sum.

---

### Invariant 8: Stable and Validated Account Identity
1. **Business Rule:** Every bank account must possess a non-blank, format-validated, unique `AccountNumber` that never changes after creation.
2. **Why the Rule Is Important:** Account numbers serve as permanent primary identifiers across ledgers, transfer references, and external statements.
3. **Possible Consequences if Violated:** Broken foreign references in `LedgerEntry` and `FundTransfer`, misrouted transfers, duplicate accounts overwriting one another in registries.
4. **How the Java Implementation Enforces It:**
   - `AccountNumber` is a `final` class with a `private final String value` validated against `^[A-Z0-9](?:[A-Z0-9-]{2,18}[A-Z0-9])$`.
   - `BankAccount.accountNumber` is declared `private final`, with no setter, and `BankingService.createAccount(...)` rejects duplicate `AccountNumber` registrations.

---

### Invariant 9: Exact Decimal Arithmetic (`BigDecimal` Only)
1. **Business Rule:** Monetary values must use exact base-10 arithmetic (`BigDecimal`) paired with `Currency`, never binary floating-point primitives (`float` or `double`), and must respect currency decimal scale (2 decimal places for INR).
2. **Why the Rule Is Important:** Binary IEEE 754 floating-point cannot represent `0.1` or `0.01` exactly (`0.1 + 0.2 == 0.30000000000000004`), causing rounding drift across thousands of transactions.
3. **Possible Consequences if Violated:** Paise/cent discrepancies, failed equality checks on balances, cumulative accounting errors ("salami slicing" rounding bugs).
4. **How the Java Implementation Enforces It:**
   - `Money` stores `private final BigDecimal amount` and `private final Currency currency`, deliberately omits `float`/`double` constructors, rejects fractional precision beyond `currency.getDefaultFractionDigits()` (e.g., `₹100.125`), and enforces currency matching on `add`, `subtract`, and `compareTo`.

---

### Invariant 10: Strict Aggregate Encapsulation
1. **Business Rule:** External classes must never directly modify `balance`, `status`, or the internal `transactionHistory` list of a `BankAccount`.
2. **Why the Rule Is Important:** If external code could call `account.setBalance(...)` or `account.getTransactionHistory().clear()`, every invariant above could be bypassed trivially.
3. **Possible Consequences if Violated:** Arbitrary balance tampering, deletion of audit logs, invalid state transitions (e.g., reopening a `CLOSED` account without validation).
4. **How the Java Implementation Enforces It:**
   - All fields in `BankAccount` are `private` (and immutable references are `final`); no setter methods exist; `getTransactionHistory()` returns `Collections.unmodifiableList(new ArrayList<>(transactionHistory))`; and `AccountSnapshot` has a `private` constructor so external code cannot forge snapshots.

---

## Q2. Entities and Value Objects (6 Marks)

In Domain-Driven Design (DDD), domain objects are divided into two fundamental building blocks:
- **Entities:** Objects defined primarily by a **continuous thread of identity** across time, independent of attribute changes.
- **Value Objects:** Immutable objects that measure, quantify, or describe a domain characteristic and have **no conceptual identity**—two instances with identical attribute values are completely interchangeable.

### Classification Summary Table

| Domain Concept | DDD Classification | Identity Basis | Equality Basis | Lifecycle | Mutability | Java Class |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **Bank Account** | **Entity** *(Aggregate Root)* | Unique `AccountNumber` | Identity (`accountNumber`) | Long-lived (`ACTIVE` -> `FROZEN` -> `CLOSED`) | Controlled mutability (`balance`, `status`, `transactionHistory` via domain methods) | `BankAccount` |
| **Account Number** | **Value Object** | None (it *is* an identifier value) | Structural value (`value` string) | Bound to referencing objects; stateless | Strictly **Immutable** (`final` class & field) | `AccountNumber` |
| **Money** | **Value Object** | None | Structural value (`amount` via `compareTo == 0` + `currency`) | Created & discarded freely during arithmetic | Strictly **Immutable** (`final` class; arithmetic returns new instances) | `Money` |
| **Ledger Entry** | **Entity** *(Append-Only)* | Unique `transactionId` | Identity (`transactionId`) | Created upon transaction completion; retained permanently for audit | **Immutable after creation** (`final` class, all `private final` fields) | `LedgerEntry` |
| **Overdraft Limit** | **Value Object** | None | Structural value (`limitAmount`) | Configured at account creation; evaluated on debits | Strictly **Immutable** (`final` class & field) | `OverdraftLimit` |
| **Fund Transfer** | **Entity** | Unique `transferId` | Identity (`transferId`) | Initiated as `PENDING` -> finalized as `COMPLETED` or `FAILED` | Controlled lifecycle transition (`PENDING` -> terminal state) | `FundTransfer` |

---

### Detailed Justifications

#### 1. Bank Account — **Entity (Aggregate Root)**
- **Identity:** Uniquely identified by its `AccountNumber` (e.g., `ACC-1001`). Even if two customers both have `₹10,000.00` balance, `₹2,000.00` overdraft limit, and `ACTIVE` status, they remain two distinct accounts.
- **Equality:** `equals()` and `hashCode()` compare `accountNumber` only.
- **Lifecycle:** Has a rich, multi-stage business lifecycle: opened as `ACTIVE`, potentially suspended to `FROZEN` and restored to `ACTIVE`, and eventually settled and terminated as `CLOSED`.
- **Mutability:** Mutable internal state (`balance`, `status`, `transactionHistory`), mutated strictly through invariant-enforcing methods (`deposit`, `withdraw`, `freeze`, `unfreeze`, `close`).

#### 2. Account Number — **Value Object**
- **Identity:** Has no separate ID of its own—the formatted string value *itself* (e.g., `"ACC-1001"`) is the whole meaning of the object.
- **Equality:** Value-based (`AccountNumber.of("acc-1001").equals(AccountNumber.of("ACC-1001"))` is `true`).
- **Lifecycle:** Validated once at construction (`4–20` alphanumeric/hyphen characters); carries no state transitions.
- **Mutability:** Strictly immutable (`final` class, `private final String value`), guaranteeing stable identity for `BankAccount` (Invariant 8).

#### 3. Money — **Value Object**
- **Identity:** Banknotes in a digital account are fungible—one `₹5,000.00` value has no individual identity distinct from another `₹5,000.00` value.
- **Equality:** Currency-aware value equality: two `Money` instances are equal if and only if their `Currency` codes match and `amount.compareTo(other.amount) == 0`.
- **Lifecycle:** Ephemeral and side-effect-free; created on demand for deposits, balances, and arithmetic results.
- **Mutability:** Strictly immutable (`final` class, `private final BigDecimal amount`, `private final Currency currency`). Calling `a.add(b)` or `a.subtract(b)` returns a brand-new `Money` object, preventing aliasing bugs where modifying one variable accidentally alters an account's stored balance.

#### 4. Ledger Entry — **Entity**
- **Identity:** Possesses a unique `transactionId` (e.g., `TXN-00001-A3F91C`). Suppose a customer makes two `₹1,000.00` cash deposits at the exact same second—both must be recorded as **two distinct financial events**, not collapsed into one set element as a Value Object would be!
- **Equality:** Identity-based (`equals()` and `hashCode()` compare `transactionId`).
- **Lifecycle:** Born at the moment a financial transaction succeeds and preserved indefinitely in the audit log.
- **Mutability:** **Immutable after creation** (`final` class, all 8 fields `private final`). Note an important DDD insight: *mutability is not required to be an Entity*—historical event entities have unique identity yet are immutable to preserve audit integrity.

#### 5. Overdraft Limit — **Value Object**
- **Identity:** Represents a quantitative credit policy threshold (e.g., `₹2,000.00`), not an identifiable business actor or event.
- **Equality:** Value-based (`limitAmount.equals(other.limitAmount)`). Two accounts with a `₹2,000.00` overdraft limit have equal `OverdraftLimit` values.
- **Lifecycle:** Validated as non-negative upon creation and queried whenever a debit is evaluated.
- **Mutability:** Strictly immutable (`final` class wrapping immutable `Money`), ensuring an account's credit boundary cannot be silently mutated externally.

#### 6. Fund Transfer — **Entity**
- **Identity:** Uniquely identified by `transferId` (e.g., `TRF-00001-B8C21E`). Two ₹5,000 transfers from Account A to Account B on the same day are distinct business transactions that must be tracked, audited, and referenced independently.
- **Equality:** Identity-based (`equals()` and `hashCode()` compare `transferId`).
- **Lifecycle:** Stateful progression: created in `TransferStatus.PENDING`, then transitions either to `TransferStatus.COMPLETED` (linking `TRANSFER_DEBIT` and `TRANSFER_CREDIT` ledger entries via `transferId`) or to `TransferStatus.FAILED` (recording `failureReason` upon rollback).
- **Mutability:** Controlled state mutability—only `status` and `failureReason` can transition once out of `PENDING`; once in a terminal state (`COMPLETED` or `FAILED`), further transitions are rejected.

---

## Q3. Self-Enforcing Objects and Java Design (8 Marks)

A **self-enforcing domain object** follows the Object-Oriented **Tell, Don't Ask** principle: instead of external services asking `account.getBalance()` and computing `account.setBalance(balance - amount)`, callers tell `account.withdraw(amount)` and the `BankAccount` object itself validates preconditions, updates its internal state, records its ledger entry, and verifies post-conditions.

### 1. Classes and Methods Implementing Core Account Operations

All five core account lifecycle and financial operations are encapsulated inside `com.banking.model.BankAccount` (and orchestrated at the application boundary by `com.banking.service.BankingService`):

| Operation | Domain Method (`BankAccount`) | Application Facade (`BankingService`) | Primary Collaborators |
| :--- | :--- | :--- | :--- |
| **Deposit Money** | `deposit(Money amount)` / `deposit(Money amount, String description)` | `deposit(AccountNumber, Money)` | `Money`, `LedgerEntry`, `AccountStatus` |
| **Withdraw Money** | `withdraw(Money amount)` / `withdraw(Money amount, String description)` | `withdraw(AccountNumber, Money)` | `Money`, `OverdraftLimit`, `LedgerEntry`, `AccountStatus` |
| **Freeze Account** | `freeze()` | `freezeAccount(AccountNumber)` | `AccountStatus` |
| **Unfreeze Account** | `unfreeze()` | `unfreezeAccount(AccountNumber)` | `AccountStatus` |
| **Close Account** | `close()` | `closeAccount(AccountNumber)` | `AccountStatus`, `Money` |

#### Walkthrough of Each Method
1. **`deposit(Money amount)`**: Verifies the account is not `CLOSED`, validates that `amount` is non-null, matches the account's currency, and is strictly positive (`> 0`), computes `updatedBalance = balance.add(amount)`, creates an immutable `DEPOSIT` `LedgerEntry`, updates `balance`, appends the entry, and runs `verifyLedgerConsistency()`.
2. **`withdraw(Money amount)`**: Verifies the account is neither `CLOSED` nor `FROZEN`, validates that `amount` is strictly positive and currency-compatible, delegates overdraft checking to `overdraftLimit.validateDebit(...)`, computes `updatedBalance = balance.subtract(amount)`, creates an immutable `WITHDRAWAL` `LedgerEntry`, updates `balance`, appends the entry, and verifies ledger consistency.
3. **`freeze()`**: Rejects `CLOSED` accounts (`ClosedAccountException`) and already `FROZEN` accounts (`InvalidTransactionException`), transitioning `ACTIVE -> FROZEN`.
4. **`unfreeze()`**: Rejects `CLOSED` accounts (`ClosedAccountException`) and already `ACTIVE` accounts (`InvalidTransactionException`), transitioning `FROZEN -> ACTIVE`.
5. **`close()`**: Rejects already `CLOSED` accounts (`ClosedAccountException`) and enforces `balance.isZero()` (`InvalidTransactionException`) so neither customer funds are stranded nor overdraft debt is erased, transitioning `ACTIVE/FROZEN -> CLOSED`.

---

### 2. How the Design Enforces Each Protection Guarantee

#### A. Rejecting Invalid Transaction Amounts
- `Money` rejects `null` values, unparseable strings, currency mismatches, and sub-paise fractional scales (e.g., `100.125` INR) at construction time.
- `Money.requirePositive(String context)` rejects `0` and negative amounts before any balance arithmetic executes:

```java
// From com.banking.model.Money
public void requirePositive(String context) {
    if (!isPositive()) {
        throw new InvalidTransactionException(
                context + " must be strictly positive (greater than zero), but received: " + this);
    }
}
```

#### B. Blocking Withdrawals from Frozen and Closed Accounts
- Guard clauses execute at the very top of `withdraw(...)` before touching balances or ledgers, throwing specific domain exceptions (`ClosedAccountException` and `FrozenAccountException`):

```java
// From com.banking.model.BankAccount
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
```

#### C. Respecting Overdraft Limits
- `BankAccount` delegates boundary math to the `OverdraftLimit` Value Object (`getMinimumAllowedBalance()` = `-limitAmount`), ensuring `balance - debitAmount >= -limitAmount`:

```java
// From com.banking.model.OverdraftLimit
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

// From com.banking.model.BankAccount
public synchronized LedgerEntry withdraw(Money amount, String description) {
    ensureActiveForWithdrawal();
    validatePositiveTransactionAmount(amount, "Withdrawal amount");
    overdraftLimit.validateDebit(this.balance, amount, this.accountNumber);

    String desc = (description == null || description.isBlank()) ? "Cash/Direct withdrawal" : description.trim();
    Money updatedBalance = this.balance.subtract(amount);
    LedgerEntry entry = LedgerEntry.create(
            this.accountNumber, TransactionType.WITHDRAWAL, amount, desc, updatedBalance, null);

    this.balance = updatedBalance;
    this.transactionHistory.add(entry);
    verifyLedgerConsistency();
    return entry;
}
```

#### D. Preventing Direct Modification of Account Data by External Classes
1. **Private Fields & Zero Setters:** `accountNumber`, `accountHolderName`, `overdraftLimit`, `transactionHistory`, `balance`, and `status` are all `private`. There is no `setBalance(...)` or `setStatus(...)`.
2. **Immutable Return Types:** `getBalance()`, `getAccountNumber()`, and `getOverdraftLimit()` return immutable Value Objects (`Money`, `AccountNumber`, `OverdraftLimit`). Mutating a returned reference is impossible.
3. **Defensive Unmodifiable Collection Copy:** `getTransactionHistory()` returns an unmodifiable wrapper around a fresh copy of `transactionHistory`, so calling `.add()`, `.remove()`, or `.clear()` throws `UnsupportedOperationException`:

```java
// From com.banking.model.BankAccount
public synchronized List<LedgerEntry> getTransactionHistory() {
    return Collections.unmodifiableList(new ArrayList<>(transactionHistory));
}
```

---

## Q4. Money Transfer and Aggregate Consistency (6 Marks)

**Scenario:** **Account A transfers ₹5,000 to Account B.** Suppose the system deducts ₹5,000 from Account A first, and an error occurs before Account B is credited.

---

### 1. What Problems Can Arise from This Situation?
- **Permanent Loss of Customer Funds:** ₹5,000 disappears from Account A without ever arriving in Account B, reducing the total money in the banking system by ₹5,000.
- **Partial / Torn Transaction State:** Account A's statement shows a ₹5,000 `TRANSFER_DEBIT` entry referencing a transfer that Account B never received, creating an asymmetric, irreconcilable audit trail.
- **Concurrency Race Conditions & Deadlocks:** If another thread simultaneously reads Account A's prematurely reduced balance (dirty read) or initiates a reverse transfer from Account B to Account A without ordered locking, threads can either make wrong overdraft decisions or deadlock forever.
- **Duplicate Debits on Naive Retry:** If the user retries the failed ₹5,000 transfer without rollback or idempotency, Account A may be debited a second ₹5,000 (₹10,000 total!).

---

### 2. Which Business Invariants Are Violated?
1. **Atomic Fund Transfer / Conservation of Money Invariant (Invariant 5):** The sum of balances across Account A and Account B decreases by ₹5,000 (`(A_after + B_after) != (A_before + B_before)`), violating all-or-nothing execution.
2. **Symmetric Transfer Ledger Invariant (Invariant 6):** Every completed transfer requires paired `TRANSFER_DEBIT` and `TRANSFER_CREDIT` ledger entries sharing the same `relatedTransferId`. A partial failure leaves an orphaned `TRANSFER_DEBIT` with no matching `TRANSFER_CREDIT`.
3. **Destination Status / Domain Rule Invariants (Invariants 3 & 4):** If Account A was debited before checking that Account B was `CLOSED` or `FROZEN`, an invalid transfer mutated domain state despite failing domain preconditions.

---

### 3. How Would You Design the Transfer Operation to Prevent Money Loss?
We encapsulate cross-account coordination inside a dedicated Domain Service (`TransferService.executeTransfer(...)`) defining a single logical **Transaction Boundary** with four safeguards:
1. **Pre-Flight Validation Before Any State Change:** Validate non-null accounts, `source != destination`, `amount > 0` (₹5,000.00), matching currencies, `source.validateCanTransferOut(₹5,000)` (active + within overdraft), and `destination.validateCanTransferIn(₹5,000)` (active, neither frozen nor closed) *before* touching either balance.
2. **Canonical Lock Ordering:** Lock the two `BankAccount` aggregates in deterministic `AccountNumber` alphabetical order (`firstLock`, `secondLock`) using nested `synchronized` blocks so concurrent transfers (`A -> B` and `B -> A`) never deadlock or interleave.
3. **Memento State Snapshots Prior to Mutation:** Capture `sourceSnapshot` and `destinationSnapshot` before executing `source.recordTransferDebit(...)` and `destination.recordTransferCredit(...)`.
4. **Post-Condition Conservation Check:** Verify `(A_before + B_before).equals(A_after + B_after)` and `isBalanceConsistentWithLedger()` on both accounts before marking `FundTransfer` as `COMPLETED`.

---

### 4. How Would Your Design Handle Failures During the Transfer?

#### In-Memory Application-Level Rollback (Implemented Here)
- If validation fails *before* debiting Account A (e.g., Account B is `FROZEN` or `CLOSED`), an exception is thrown immediately and neither account is touched.
- If an unexpected runtime exception occurs *after* ₹5,000 is deducted from Account A and *before* Account B is credited, the `catch (RuntimeException ex)` block inside `TransferService` executes **application-level Memento rollback**:
  1. `source.restoreSnapshot(sourceSnapshot)` restores Account A's original balance (+₹5,000 back) and truncates the uncommitted `TRANSFER_DEBIT` ledger entry.
  2. `destination.restoreSnapshot(destinationSnapshot)` restores Account B's state if partially touched.
  3. `transferRecord.markFailed(ex.getMessage())` records the failed transfer in audit history.
  4. The exception propagates cleanly to the caller.

#### Distinction: Application-Level Rollback vs. True Database ACID Transactions
| Aspect | Application-Level Rollback (In-Memory Java) | True Database Transaction (ACID RDBMS) |
| :--- | :--- | :--- |
| **Atomicity Mechanism** | `try/catch` + GoF Memento snapshots in JVM heap | Database transaction log (`UNDO`/`REDO`) with `BEGIN` / `COMMIT` / `ROLLBACK` |
| **Isolation** | JVM `synchronized` locks ordered by `AccountNumber` | Row-level pessimistic locks (`SELECT ... FOR UPDATE`) or optimistic `@Version` locking |
| **Durability & Crash Safety** | **Volatile:** If power fails or `kill -9` terminates the JVM mid-transfer, all heap memory is lost | **Durable:** Write-Ahead Logging (WAL) guarantees uncommitted changes roll back automatically on DB restart |

---

### 5. How Would Your Approach Differ If the Two Accounts Belonged to Different Banking Systems?
When Account A lives in **Bank X** and Account B lives in **Bank Y** (separate microservices, separate databases, separate organizations—e.g., UPI, NEFT, RTGS), in-memory locks and single-database ACID transactions are **impossible**. Furthermore, **Two-Phase Commit (2PC / XA)** is generally avoided across independent banks because holding distributed locks over slow/unreliable inter-bank networks causes severe availability bottlenecks and blocking if a coordinator crashes.

Instead, an inter-bank transfer of ₹5,000 uses **eventual consistency with strong financial safeguards**:
1. **Saga Pattern (Orchestration or Choreography) with Compensating Transactions:**
   - **Step 1 (Local ACID Tx in Bank X):** Debit ₹5,000 from Account A (or move ₹5,000 into an internal *Outbound Clearing/Escrow Account*) and record a `TRANSFER_INITIATED` event in an **Outbox Table** within the same local DB transaction (**Transactional Outbox Pattern**).
   - **Step 2 (Inter-Bank Message):** Clearing network/message broker delivers the credit instruction for ₹5,000 to Bank Y.
   - **Step 3a (Success Path):** Bank Y credits ₹5,000 to Account B in a local ACID transaction and sends a `CREDIT_CONFIRMED` acknowledgment; Bank X marks the transfer `COMPLETED`.
   - **Step 3b (Failure & Compensation Path):** If Bank Y rejects the credit (e.g., Account B is closed/frozen) or times out permanently, Bank X executes an explicit **Compensating Transaction**—appending a `TRANSFER_REVERSAL` credit of ₹5,000 back to Account A rather than rolling back a locked DB row.
2. **Idempotency Keys:** Every inter-bank transfer request carries a globally unique `idempotencyKey` (`transferId`). If network timeouts cause Bank X to retry sending the ₹5,000 credit request to Bank Y, Bank Y checks its processed-keys table and returns the existing result without crediting Account B twice.
3. **Periodic Settlement & Reconciliation:** Banks reconcile inter-bank clearing ledgers (via central clearing houses like NPCI/RBI) at end-of-day to detect and resolve any stuck `IN_FLIGHT` transfers.

---

## Q5. Ledger Management and Design Evaluation (4 Marks)

### 1. What Information Should a Ledger Entry Contain?
As implemented in `com.banking.model.LedgerEntry`, every ledger record contains eight essential audit attributes:
1. **Unique Transaction ID (`transactionId`):** Globally unique identifier (e.g., `TXN-00003-9A4B1C`) distinguishing each historical event.
2. **Account Number (`accountNumber`):** Identifies which `BankAccount` owns this ledger entry.
3. **Transaction Type (`transactionType`):** Classifies credit vs. debit direction (`DEPOSIT`, `WITHDRAWAL`, `TRANSFER_DEBIT`, `TRANSFER_CREDIT`).
4. **Transaction Amount (`amount`):** Exact positive `Money` value (`BigDecimal` + `Currency`) transacted.
5. **Timestamp (`timestamp`):** Exact date and time (`LocalDateTime`) when the transaction occurred.
6. **Description (`description`):** Human-readable context or counterparty note (e.g., `"Transfer to account ACC-1002"`).
7. **Balance After Transaction (`balanceAfterTransaction`):** Running snapshot of the account's closing balance immediately after applying this entry, enabling instant statement rendering.
8. **Related Transfer ID (`relatedTransferId`):** Correlation key (`TRF-...`) linking the source account's `TRANSFER_DEBIT` entry with the destination account's `TRANSFER_CREDIT` entry during inter-account transfers (`null` for standalone deposits/withdrawals).

---

### 2. Should an Existing Ledger Entry Be Modified or Deleted After a Transaction Is Completed? Justify Your Answer.
**No—a completed ledger entry must NEVER be modified or deleted.**

**Justification:**
1. **Auditability & Non-Repudiation:** A bank ledger is a legal financial record. Allowing updates or deletions destroys historical truth and makes it impossible to detect internal fraud, unauthorized tampering, or software bugs.
2. **Regulatory & Accounting Standards:** Core accounting principles (Double-Entry Bookkeeping, RBI/SOX/PCI-DSS compliance) mandate **append-only** ledgers. If an erroneous ₹5,000 transaction occurred, it is corrected by appending a **new compensating/reversal ledger entry** referencing the original `transactionId`, preserving a transparent trail of both the mistake and its correction.
3. **Running Balance Integrity:** Every subsequent `LedgerEntry` stores `balanceAfterTransaction`. Mutating or deleting an older entry invalidates the running balance of every entry recorded after it.
4. **Java Enforcement:** `LedgerEntry` is declared `public final class`, every field is `private final` using immutable types (`String`, `AccountNumber`, `TransactionType`, `Money`, `LocalDateTime`), zero setters exist, and `BankAccount.getTransactionHistory()` returns an unmodifiable defensive copy.

---

### 3. How Would You Ensure That the Account Balance Remains Consistent with Transaction History?
1. **Single Atomic Mutation Boundary inside the Aggregate Root:** `BankAccount` never updates `this.balance` independently of `this.transactionHistory`. Every credit/debit method computes `updatedBalance`, builds the `LedgerEntry`, updates `this.balance`, and appends the entry inside the same `synchronized` method.
2. **Recording Opening Balance as an Initial Ledger Entry:** Even when an account is created with an opening deposit (e.g., `₹15,000.00`), `BankAccount`'s constructor records an `"Account opening deposit"` `LedgerEntry` so there is no unrecorded baseline gap.
3. **Continuous Post-Condition Invariant Verification:** `BankAccount.reconstructBalanceFromLedger()` replays all ledger entries (`+amount` for `DEPOSIT`/`TRANSFER_CREDIT`, `-amount` for `WITHDRAWAL`/`TRANSFER_DEBIT`) and `verifyLedgerConsistency()` asserts `this.balance.equals(reconstructBalanceFromLedger())` after every operation.
4. **Paired Rollback on Failure:** If a multi-account transfer fails mid-flight, `restoreSnapshot(...)` rolls back *both* `balance` and `transactionHistory` together so neither orphaned ledger entries nor unlogged balance shifts survive.

---

### 4. What Challenges Might Arise When the Transaction History Becomes Very Large?
For high-volume accounts (e.g., corporate merchants or payroll accounts accumulating millions of transactions over years), keeping all entries in an in-memory `List<LedgerEntry>` or unoptimized table introduces four major challenges—along with standard engineering solutions:

1. **Memory Exhaustion (`OutOfMemoryError`) & Large Payload Transfers:**
   - *Challenge:* Loading millions of `LedgerEntry` objects into heap memory or returning them all at once crashes the JVM or saturates network bandwidth.
   - *Solution — **Pagination**:* Fetch entries in bounded pages (demonstrated in `BankingService.getTransactionHistoryPage(accountNumber, pageNumber, pageSize)`). In production databases, prefer **keyset/cursor pagination** (`WHERE account_number = ? AND (timestamp, transaction_id) < (?, ?) ORDER BY timestamp DESC LIMIT 50`) over large `OFFSET` scans.
2. **Slow Query Latency on Historical Lookups:**
   - *Challenge:* Filtering transactions by account, date range, or `relatedTransferId` degrades to `O(N)` full scans as the ledger grows.
   - *Solution — **Indexing**:* Create composite B-Tree indexes on `(account_number, timestamp DESC, transaction_id)` for statement queries and an index on `(related_transfer_id)` for instant transfer correlation lookups.
3. **`O(N)` Cost of Full-Ledger Balance Reconstruction:**
   - *Challenge:* Replaying 5,000,000 historical entries on every transaction to verify `reconstructBalanceFromLedger()` becomes prohibitively slow.
   - *Solution — **Periodic Balance Snapshots / Checkpoints**:* Persist verified monthly/daily closing checkpoints (e.g., closing balance as of `2026-10-31` = `₹4,50,000.00`). Consistency checks then only sum entries *since the latest checkpoint* (`O(ΔN)`).
4. **Storage Bloat & Hot-Table Contention:**
   - *Challenge:* Years of historical rows slow down backups, index maintenance, and active writes.
   - *Solution — **Partitioning & Cold-Storage Archival**:* Partition the `ledger_entries` table by time range (monthly/quarterly partitions) and move partitions older than regulatory hot-access windows (e.g., > 1–2 years) into immutable, compressed cold storage while keeping cryptographic hash digests and closing checkpoints online.
