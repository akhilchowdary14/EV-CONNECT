package com.banking.enums;

/**
 * Represents the lifecycle states of a {@link com.banking.model.BankAccount}.
 * <ul>
 *     <li>{@code ACTIVE}: Normal operating state; permits deposits, withdrawals, and transfers.</li>
 *     <li>{@code FROZEN}: Restricted state; blocks withdrawals and transfers while preserving balance.</li>
 *     <li>{@code CLOSED}: Terminal state; permanently closed and blocks all financial operations.</li>
 * </ul>
 */
public enum AccountStatus {
    ACTIVE,
    FROZEN,
    CLOSED
}
