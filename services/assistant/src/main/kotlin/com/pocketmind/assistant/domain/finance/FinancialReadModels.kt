package com.pocketmind.assistant.domain.finance

import kotlinx.serialization.Serializable

@Serializable
data class FinancialReadMetadata(
    val stateVersion: Long,
    val latestRemoteUpdateEpochMillis: Long?,
    val observedAtEpochMillis: Long,
    val remoteRecordCount: Int,
    val source: String = "supabase_sync_snapshot",
    val mayLagUnsyncedDeviceChanges: Boolean = true,
    val mustRevalidateBeforeWrite: Boolean = true,
    val unknownEntityTypes: List<String> = emptyList(),
)

@Serializable
data class MoneyValue(
    val minorUnits: Long,
    val currency: String,
)

@Serializable
data class ProductSummary(
    val id: String,
    val name: String,
    val type: String,
    val currency: String,
    val aliases: List<String>,
    val isArchived: Boolean,
    val dataStatus: String,
    val currentBalance: MoneyValue? = null,
    val currentDebt: MoneyValue? = null,
    val availableCredit: MoneyValue? = null,
    val nextPayment: MoneyValue? = null,
    val annualRateBasisPoints: Int? = null,
    val statementClosingDay: Int? = null,
    val paymentDueDay: Int? = null,
    val maturityAtEpochMillis: Long? = null,
)

@Serializable
data class ProductListResult(
    val metadata: FinancialReadMetadata,
    val products: List<ProductSummary>,
)

@Serializable
data class ProductLookupResult(
    val status: String,
    val metadata: FinancialReadMetadata,
    val product: ProductSummary? = null,
    val candidates: List<ProductSummary> = emptyList(),
)

@Serializable
data class CurrencyOverview(
    val currency: String,
    val liquidBalanceMinorUnits: Long,
    val monthlyIncomeMinorUnits: Long,
    val monthlyExpenseMinorUnits: Long,
    val productSavingsBalanceMinorUnits: Long,
    val legacySavingsPlanBalanceMinorUnits: Long,
    val creditCardDebtMinorUnits: Long,
    val loanDebtMinorUnits: Long,
    val legacyDebtMinorUnits: Long,
)

@Serializable
data class FinancialOverviewResult(
    val metadata: FinancialReadMetadata,
    val monthStartEpochMillis: Long,
    val currencies: List<CurrencyOverview>,
    val activeProductCount: Int,
    val postedTransactionCount: Int,
    val activeIncomeSourceCount: Int,
    val activeRecurringObligationCount: Int,
)

@Serializable
data class TransactionSummary(
    val id: String,
    val productId: String,
    val relatedProductId: String?,
    val type: String,
    val amount: MoneyValue,
    val occurredAtEpochMillis: Long,
    val categoryId: String?,
    val merchant: String?,
    val note: String?,
    val source: String,
    val status: String,
    val manualRevision: Int,
)

@Serializable
data class TransactionListResult(
    val status: String,
    val metadata: FinancialReadMetadata,
    val resolvedProduct: ProductSummary? = null,
    val candidates: List<ProductSummary> = emptyList(),
    val transactions: List<TransactionSummary> = emptyList(),
    val totalMatches: Int = 0,
    val returnedCount: Int = 0,
    val truncated: Boolean = false,
)
