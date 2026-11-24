package com.banking;

import com.banking.enums.TransactionType;
import com.banking.exception.InvalidTransactionException;
import com.banking.model.AccountNumber;
import com.banking.model.BankAccount;
import com.banking.model.LedgerEntry;
import com.banking.model.Money;
import com.banking.model.OverdraftLimit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JUnit 5 unit tests verifying {@link LedgerEntry} immutability, entity identity semantics,
 * validation invariants, and unmodifiable ledger history guarantees.
 */
class LedgerEntryTest {

    @Test
    @DisplayName("Immutability: LedgerEntry class is final, all fields are private final, and no setter methods exist")
    void shouldBeStrictlyImmutableAtClassStructureLevel() {
        assertTrue(Modifier.isFinal(LedgerEntry.class.getModifiers()),
                "LedgerEntry class must be declared final.");

        for (Field field : LedgerEntry.class.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            assertTrue(Modifier.isPrivate(field.getModifiers()),
                    "Field '" + field.getName() + "' must be private.");
            assertTrue(Modifier.isFinal(field.getModifiers()),
                    "Field '" + field.getName() + "' must be final.");
        }

        for (Method method : LedgerEntry.class.getDeclaredMethods()) {
            assertFalse(method.getName().startsWith("set"),
                    "LedgerEntry must not expose any setter method, found: " + method.getName());
        }
    }

    @Test
    @DisplayName("Immutability: BankAccount transaction history view cannot be modified or cleared externally")
    void shouldPreventExternalModificationOfAccountTransactionHistory() {
        BankAccount account = new BankAccount(
                AccountNumber.of("ACC-5001"),
                "Neha Kulkarni",
                Money.inr("5000.00"),
                OverdraftLimit.zeroInr());
        account.deposit(Money.inr("1500.00"));
        account.withdraw(Money.inr("500.00"));

        List<LedgerEntry> externalHistoryView = account.getTransactionHistory();
        assertEquals(3, externalHistoryView.size());

        LedgerEntry forgedEntry = LedgerEntry.create(
                AccountNumber.of("ACC-5001"),
                TransactionType.DEPOSIT,
                Money.inr("999999.00"),
                "Forged external entry",
                Money.inr("1005999.00"),
                null);

        assertThrows(UnsupportedOperationException.class, () -> externalHistoryView.add(forgedEntry));
        assertThrows(UnsupportedOperationException.class, () -> externalHistoryView.remove(0));
        assertThrows(UnsupportedOperationException.class, externalHistoryView::clear);

        // Verify original account ledger and balance remain completely intact
        assertEquals(3, account.getTransactionHistory().size());
        assertEquals(Money.inr("6000.00"), account.getBalance());
        assertTrue(account.isBalanceConsistentWithLedger());
    }

    @Test
    @DisplayName("Entity Identity: Two ledger entries with identical attributes but distinct IDs are unequal")
    void shouldDetermineEqualityByUniqueTransactionId() {
        LocalDateTime fixedTime = LocalDateTime.of(2026, 11, 24, 10, 30, 0);
        AccountNumber acc = AccountNumber.of("ACC-7001");

        LedgerEntry entry1 = new LedgerEntry(
                "TXN-00001",
                acc,
                TransactionType.DEPOSIT,
                Money.inr("1000.00"),
                fixedTime,
                "Cash Deposit",
                Money.inr("1000.00"),
                null);

        LedgerEntry entry2 = new LedgerEntry(
                "TXN-00002",
                acc,
                TransactionType.DEPOSIT,
                Money.inr("1000.00"),
                fixedTime,
                "Cash Deposit",
                Money.inr("2000.00"),
                null);

        LedgerEntry sameIdReference = new LedgerEntry(
                "TXN-00001",
                acc,
                TransactionType.DEPOSIT,
                Money.inr("1000.00"),
                fixedTime,
                "Cash Deposit",
                Money.inr("1000.00"),
                null);

        assertNotEquals(entry1, entry2);
        assertEquals(entry1, sameIdReference);
        assertEquals(entry1.hashCode(), sameIdReference.hashCode());
    }

    @Test
    @DisplayName("Validation: LedgerEntry rejects non-positive amounts and transfer entries missing relatedTransferId")
    void shouldValidateLedgerEntryInvariantsOnConstruction() {
        LocalDateTime now = LocalDateTime.now();
        AccountNumber acc = AccountNumber.of("ACC-8001");

        // Zero amount rejected
        assertThrows(InvalidTransactionException.class, () -> new LedgerEntry(
                "TXN-100", acc, TransactionType.DEPOSIT, Money.zeroInr(),
                now, "Zero deposit", Money.zeroInr(), null));

        // Negative amount rejected
        assertThrows(InvalidTransactionException.class, () -> new LedgerEntry(
                "TXN-101", acc, TransactionType.WITHDRAWAL, Money.inr("-50.00"),
                now, "Negative withdrawal", Money.zeroInr(), null));

        // Transfer entry without relatedTransferId rejected
        assertThrows(InvalidTransactionException.class, () -> new LedgerEntry(
                "TXN-102", acc, TransactionType.TRANSFER_DEBIT, Money.inr("500.00"),
                now, "Transfer missing ref", Money.inr("500.00"), null));
    }
}
