package com.pocketmind.assistant.domain.finance

import com.pocketmind.shared.domain.model.CreditCardPayment
import com.pocketmind.shared.domain.model.CreditCardProfile
import com.pocketmind.shared.domain.model.Debt
import com.pocketmind.shared.domain.model.FinancialAccount
import com.pocketmind.shared.domain.model.FinancialTransaction
import com.pocketmind.shared.domain.model.IncomeSource
import com.pocketmind.shared.domain.model.InstallmentPurchase
import com.pocketmind.shared.domain.model.LoanPayment
import com.pocketmind.shared.domain.model.LoanProfile
import com.pocketmind.shared.domain.model.RecurringObligation
import com.pocketmind.shared.domain.model.SavingsMovement
import com.pocketmind.shared.domain.model.SavingsPlan
import com.pocketmind.shared.domain.model.SavingsProfile

/**
 * Consistent, user-scoped view reconstructed from the remote sync envelopes.
 *
 * [stateVersion] changes whenever any known or unknown remote envelope changes,
 * including tombstones. It is stored with command drafts so a later phase can
 * revalidate the financial state immediately before executing a command.
 */
data class FinancialContextSnapshot(
    val stateVersion: Long,
    val latestRemoteUpdateEpochMillis: Long?,
    val supportedSchemaVersion: Int,
    val remoteRecordCount: Int,
    val unknownEntityTypes: Set<String>,
    val accounts: List<FinancialAccount>,
    val transactions: List<FinancialTransaction>,
    val incomeSources: List<IncomeSource>,
    val debts: List<Debt>,
    val savingsPlans: List<SavingsPlan>,
    val recurringObligations: List<RecurringObligation>,
    val creditCardProfiles: List<CreditCardProfile>,
    val installmentPurchases: List<InstallmentPurchase>,
    val creditCardPayments: List<CreditCardPayment>,
    val savingsProfiles: List<SavingsProfile>,
    val savingsMovements: List<SavingsMovement>,
    val loanProfiles: List<LoanProfile>,
    val loanPayments: List<LoanPayment>,
)

enum class FinancialContextProblem {
    UNSUPPORTED_SCHEMA,
    INVALID_REMOTE_DATA,
    CROSS_USER_RECORD,
}

class FinancialContextException(
    val problem: FinancialContextProblem,
) : IllegalStateException(
    when (problem) {
        FinancialContextProblem.UNSUPPORTED_SCHEMA ->
            "Remote financial data uses an unsupported schema."

        FinancialContextProblem.INVALID_REMOTE_DATA ->
            "Remote financial data is inconsistent."

        FinancialContextProblem.CROSS_USER_RECORD ->
            "Remote financial data does not belong to the authenticated user."
    },
)

class FinancialContextRemoteException(
    val statusCode: Int,
) : IllegalStateException(
    "Remote financial context request failed with status $statusCode.",
)
