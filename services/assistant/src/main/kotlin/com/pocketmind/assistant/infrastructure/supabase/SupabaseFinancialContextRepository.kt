package com.pocketmind.assistant.infrastructure.supabase

import com.pocketmind.assistant.auth.AuthenticatedUser
import com.pocketmind.assistant.config.AssistantConfig
import com.pocketmind.assistant.domain.finance.FinancialContextException
import com.pocketmind.assistant.domain.finance.FinancialContextProblem
import com.pocketmind.assistant.domain.finance.FinancialContextRemoteException
import com.pocketmind.assistant.domain.finance.FinancialContextRepository
import com.pocketmind.assistant.domain.finance.FinancialContextSnapshot
import com.pocketmind.shared.domain.model.CreditCardPayment
import com.pocketmind.shared.domain.model.CreditCardProfile
import com.pocketmind.shared.domain.model.CustomCategory
import com.pocketmind.shared.domain.model.CurrencyCode
import com.pocketmind.shared.domain.model.Debt
import com.pocketmind.shared.domain.model.DebtPaymentType
import com.pocketmind.shared.domain.model.FinancialAccount
import com.pocketmind.shared.domain.model.FinancialAccountType
import com.pocketmind.shared.domain.model.FinancialTransaction
import com.pocketmind.shared.domain.model.IncomeSource
import com.pocketmind.shared.domain.model.InstallmentPurchase
import com.pocketmind.shared.domain.model.InstallmentRatePeriodCodec
import com.pocketmind.shared.domain.model.LoanPayment
import com.pocketmind.shared.domain.model.LoanProfile
import com.pocketmind.shared.domain.model.Money
import com.pocketmind.shared.domain.model.ProductAliasCodec
import com.pocketmind.shared.domain.model.Recurrence
import com.pocketmind.shared.domain.model.RecurringObligation
import com.pocketmind.shared.domain.model.SavingsMovement
import com.pocketmind.shared.domain.model.SavingsMovementType
import com.pocketmind.shared.domain.model.SavingsPlan
import com.pocketmind.shared.domain.model.SavingsPlanType
import com.pocketmind.shared.domain.model.SavingsProductType
import com.pocketmind.shared.domain.model.SavingsProfile
import com.pocketmind.shared.domain.model.TransactionSource
import com.pocketmind.shared.domain.model.TransactionStatus
import com.pocketmind.shared.domain.model.TransactionType
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.http.isSuccess
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement

class SupabaseFinancialContextRepository(
    private val client: HttpClient,
    private val config: AssistantConfig,
    private val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    },
) : FinancialContextRepository {
    override suspend fun fetchSnapshot(
        session: AuthenticatedUser,
    ): FinancialContextSnapshot {
        val response = client.get("${config.supabaseUrl}/rest/v1/$TABLE") {
            header("apikey", config.supabasePublishableKey.reveal())
            bearerAuth(session.accessToken.reveal())
            parameter(
                "select",
                "user_id,entity_type,entity_id,schema_version,payload," +
                    "is_deleted,updated_at_epoch_millis",
            )
            parameter("user_id", "eq.${session.userId}")
            parameter("order", "entity_type.asc,entity_id.asc")
        }
        if (!response.status.isSuccess()) {
            throw FinancialContextRemoteException(response.status.value)
        }

        val records = runCatching { response.body<List<RemoteFinanceRecordDto>>() }
            .getOrElse {
                throw FinancialContextException(
                    FinancialContextProblem.INVALID_REMOTE_DATA,
                )
            }
        if (records.any { it.userId != session.userId }) {
            throw FinancialContextException(FinancialContextProblem.CROSS_USER_RECORD)
        }

        return FinancialSnapshotDecoder(json).decode(records)
    }

    private companion object {
        const val TABLE = "finance_sync_records"
    }
}

internal class FinancialSnapshotDecoder(
    private val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    },
) {
    fun decode(records: List<RemoteFinanceRecordDto>): FinancialContextSnapshot =
        runCatching {
            val knownRecords = records.filter {
                SyncEntityType.fromWireName(it.entityType) != null
            }
            if (knownRecords.any { it.schemaVersion > CURRENT_SYNC_SCHEMA_VERSION }) {
                throw FinancialContextException(
                    FinancialContextProblem.UNSUPPORTED_SCHEMA,
                )
            }
            val active = knownRecords.filterNot(RemoteFinanceRecordDto::isDeleted)

            FinancialContextSnapshot(
                stateVersion = FinancialSnapshotVersion.from(records),
                latestRemoteUpdateEpochMillis =
                    records.maxOfOrNull(RemoteFinanceRecordDto::updatedAtEpochMillis),
                supportedSchemaVersion = CURRENT_SYNC_SCHEMA_VERSION,
                remoteRecordCount = records.size,
                unknownEntityTypes = records
                    .map(RemoteFinanceRecordDto::entityType)
                    .filter { SyncEntityType.fromWireName(it) == null }
                    .toSortedSet(),
                accounts = active.decodeList(SyncEntityType.ACCOUNT, ::decodeAccount),
                transactions = active.decodeList(
                    SyncEntityType.TRANSACTION,
                    ::decodeTransaction,
                ),
                incomeSources = active.decodeList(
                    SyncEntityType.INCOME_SOURCE,
                    ::decodeIncomeSource,
                ),
                debts = active.decodeList(SyncEntityType.DEBT, ::decodeDebt),
                savingsPlans = active.decodeList(
                    SyncEntityType.SAVINGS_PLAN,
                    ::decodeSavingsPlan,
                ),
                recurringObligations = active.decodeList(
                    SyncEntityType.RECURRING_OBLIGATION,
                    ::decodeRecurringObligation,
                ),
                creditCardProfiles = active.decodeList(
                    SyncEntityType.CREDIT_CARD_PROFILE,
                    ::decodeCreditCardProfile,
                ),
                installmentPurchases = active.decodeList(
                    SyncEntityType.INSTALLMENT_PURCHASE,
                    ::decodeInstallmentPurchase,
                ),
                creditCardPayments = active.decodeList(
                    SyncEntityType.CREDIT_CARD_PAYMENT,
                    ::decodeCreditCardPayment,
                ),
                savingsProfiles = active.decodeList(
                    SyncEntityType.SAVINGS_PROFILE,
                    ::decodeSavingsProfile,
                ),
                savingsMovements = active.decodeList(
                    SyncEntityType.SAVINGS_MOVEMENT,
                    ::decodeSavingsMovement,
                ),
                loanProfiles = active.decodeList(
                    SyncEntityType.LOAN_PROFILE,
                    ::decodeLoanProfile,
                ),
                loanPayments = active.decodeList(
                    SyncEntityType.LOAN_PAYMENT,
                    ::decodeLoanPayment,
                ),
                customCategories = active.decodeList(
                    SyncEntityType.CUSTOM_CATEGORY,
                    ::decodeCustomCategory,
                ),
            )
        }.getOrElse { error ->
            if (error is FinancialContextException) throw error
            throw FinancialContextException(FinancialContextProblem.INVALID_REMOTE_DATA)
        }

    private fun decodeAccount(record: RemoteFinanceRecordDto): FinancialAccount {
        val payload = record.payload<AccountPayload>()
        record.requireEntityId(payload.id)
        val currency = payload.currency.asCurrency()
        return FinancialAccount(
            id = payload.id,
            name = payload.name,
            type = payload.type.asEnum(),
            currency = currency,
            openingBalance = Money(payload.openingBalanceMinorUnits, currency),
            aliases = ProductAliasCodec.decode(payload.aliasesJson),
            isArchived = payload.isArchived,
        )
    }

    private fun decodeTransaction(
        record: RemoteFinanceRecordDto,
    ): FinancialTransaction {
        val payload = record.payload<TransactionPayload>()
        record.requireEntityId(payload.id)
        return FinancialTransaction(
            id = payload.id,
            accountId = payload.accountId,
            type = payload.type.asEnum(),
            amount = Money(
                payload.amountMinorUnits,
                payload.currency.asCurrency(),
            ),
            occurredAtEpochMillis = payload.occurredAtEpochMillis,
            categoryId = payload.categoryId,
            merchant = payload.merchant,
            note = payload.note,
            source = payload.source.asEnum(),
            status = payload.status.asEnum(),
            relatedAccountId = payload.relatedAccountId,
            manualRevision = payload.manualRevision,
        )
    }

    private fun decodeCustomCategory(
        record: RemoteFinanceRecordDto,
    ): CustomCategory {
        val payload = record.payload<CustomCategoryPayload>()
        record.requireEntityId(payload.id)
        return CustomCategory(
            id = payload.id,
            name = payload.name,
            createdAtEpochMillis = payload.createdAtEpochMillis,
        )
    }

    private fun decodeIncomeSource(
        record: RemoteFinanceRecordDto,
    ): IncomeSource {
        val payload = record.payload<IncomeSourcePayload>()
        record.requireEntityId(payload.id)
        return IncomeSource(
            id = payload.id,
            name = payload.name,
            expectedAmount = Money(
                payload.expectedAmountMinorUnits,
                payload.currency.asCurrency(),
            ),
            recurrence = payload.recurrence.asEnum(),
            nextExpectedAtEpochMillis = payload.nextExpectedAtEpochMillis,
            isActive = payload.isActive,
        )
    }

    private fun decodeDebt(record: RemoteFinanceRecordDto): Debt {
        val payload = record.payload<DebtPayload>()
        record.requireEntityId(payload.id)
        val currency = payload.currency.asCurrency()
        return Debt(
            id = payload.id,
            name = payload.name,
            outstandingBalance = Money(
                payload.outstandingBalanceMinorUnits,
                currency,
            ),
            interestRateAnnualBasisPoints =
                payload.interestRateAnnualBasisPoints,
            installmentAmount = payload.installmentAmountMinorUnits?.let {
                Money(it, currency)
            },
            dueDayOfMonth = payload.dueDayOfMonth,
            nextDueAtEpochMillis = payload.nextDueAtEpochMillis,
            isActive = payload.isActive,
        )
    }

    private fun decodeSavingsPlan(record: RemoteFinanceRecordDto): SavingsPlan {
        val payload = record.payload<SavingsPlanPayload>()
        record.requireEntityId(payload.id)
        val currency = payload.currency.asCurrency()
        return SavingsPlan(
            id = payload.id,
            name = payload.name,
            type = payload.type.asEnum(),
            currentAmount = Money(payload.currentAmountMinorUnits, currency),
            targetAmount = payload.targetAmountMinorUnits?.let {
                Money(it, currency)
            },
            monthlyContribution = payload.monthlyContributionMinorUnits?.let {
                Money(it, currency)
            },
            annualYieldBasisPoints = payload.annualYieldBasisPoints,
            targetDateEpochMillis = payload.targetDateEpochMillis,
            isActive = payload.isActive,
        )
    }

    private fun decodeRecurringObligation(
        record: RemoteFinanceRecordDto,
    ): RecurringObligation {
        val payload = record.payload<RecurringObligationPayload>()
        record.requireEntityId(payload.id)
        return RecurringObligation(
            id = payload.id,
            name = payload.name,
            amount = Money(
                payload.amountMinorUnits,
                payload.currency.asCurrency(),
            ),
            recurrence = payload.recurrence.asEnum(),
            dueDayOfMonth = payload.dueDayOfMonth,
            isActive = payload.isActive,
        )
    }

    private fun decodeCreditCardProfile(
        record: RemoteFinanceRecordDto,
    ): CreditCardProfile {
        val payload = record.payload<CreditCardProfilePayload>()
        record.requireEntityId(payload.accountId)
        return CreditCardProfile(
            accountId = payload.accountId,
            creditLimit = Money(
                payload.creditLimitMinorUnits,
                payload.currency.asCurrency(),
            ),
            annualInterestBasisPoints = payload.annualInterestBasisPoints,
            statementClosingDay = payload.statementClosingDay,
            paymentDueDay = payload.paymentDueDay,
            openingDebtInstallmentCount =
                payload.openingDebtInstallmentCount,
            openingDebtFirstPaymentAtEpochMillis =
                payload.openingDebtFirstPaymentAtEpochMillis,
            scheduleRuleVersion = payload.scheduleRuleVersion,
        )
    }

    private fun decodeInstallmentPurchase(
        record: RemoteFinanceRecordDto,
    ): InstallmentPurchase {
        val payload = record.payload<InstallmentPurchasePayload>()
        record.requireEntityId(payload.id)
        return InstallmentPurchase(
            id = payload.id,
            accountId = payload.accountId,
            merchant = payload.merchant,
            principal = Money(
                payload.principalMinorUnits,
                payload.currency.asCurrency(),
            ),
            installmentCount = payload.installmentCount,
            annualInterestBasisPoints = payload.annualInterestBasisPoints,
            purchasedAtEpochMillis = payload.purchasedAtEpochMillis,
            firstPaymentAtEpochMillis = payload.firstPaymentAtEpochMillis,
            categoryId = payload.categoryId,
            note = payload.note,
            promotionalRatePeriods = InstallmentRatePeriodCodec.decode(
                payload.promotionalRatePeriodsJson,
            ),
            calculationRuleVersion = payload.calculationRuleVersion,
        )
    }

    private fun decodeCreditCardPayment(
        record: RemoteFinanceRecordDto,
    ): CreditCardPayment {
        val payload = record.payload<DebtPaymentPayload>()
        record.requireEntityId(payload.id)
        return CreditCardPayment(
            id = payload.id,
            accountId = payload.accountId,
            amount = Money(
                payload.amountMinorUnits,
                payload.currency.asCurrency(),
            ),
            paidAtEpochMillis = payload.paidAtEpochMillis,
            sourceAccountId = payload.sourceAccountId,
            note = payload.note,
            type = payload.paymentType.asEnum(),
            calculationRuleVersion = payload.calculationRuleVersion,
        )
    }

    private fun decodeSavingsProfile(
        record: RemoteFinanceRecordDto,
    ): SavingsProfile {
        val payload = record.payload<SavingsProfilePayload>()
        record.requireEntityId(payload.accountId)
        return SavingsProfile(
            accountId = payload.accountId,
            type = payload.type.asEnum(),
            annualYieldBasisPoints = payload.annualYieldBasisPoints,
            openedAtEpochMillis = payload.openedAtEpochMillis,
            maturityAtEpochMillis = payload.maturityAtEpochMillis,
            calculationRuleVersion = payload.calculationRuleVersion,
        )
    }

    private fun decodeSavingsMovement(
        record: RemoteFinanceRecordDto,
    ): SavingsMovement {
        val payload = record.payload<SavingsMovementPayload>()
        record.requireEntityId(payload.id)
        return SavingsMovement(
            id = payload.id,
            accountId = payload.accountId,
            type = payload.type.asEnum(),
            amount = Money(
                payload.amountMinorUnits,
                payload.currency.asCurrency(),
            ),
            annualYieldBasisPoints = payload.annualYieldBasisPoints,
            occurredAtEpochMillis = payload.occurredAtEpochMillis,
            note = payload.note,
            calculationRuleVersion = payload.calculationRuleVersion,
        )
    }

    private fun decodeLoanProfile(
        record: RemoteFinanceRecordDto,
    ): LoanProfile {
        val payload = record.payload<LoanProfilePayload>()
        record.requireEntityId(payload.accountId)
        return LoanProfile(
            accountId = payload.accountId,
            annualInterestBasisPoints = payload.annualInterestBasisPoints,
            monthlyPayment = Money(
                payload.monthlyPaymentMinorUnits,
                payload.currency.asCurrency(),
            ),
            paymentDueDay = payload.paymentDueDay,
            openedAtEpochMillis = payload.openedAtEpochMillis,
            scheduleRuleVersion = payload.scheduleRuleVersion,
        )
    }

    private fun decodeLoanPayment(record: RemoteFinanceRecordDto): LoanPayment {
        val payload = record.payload<DebtPaymentPayload>()
        record.requireEntityId(payload.id)
        return LoanPayment(
            id = payload.id,
            accountId = payload.accountId,
            amount = Money(
                payload.amountMinorUnits,
                payload.currency.asCurrency(),
            ),
            paidAtEpochMillis = payload.paidAtEpochMillis,
            sourceAccountId = payload.sourceAccountId,
            note = payload.note,
            type = payload.paymentType.asEnum(),
            calculationRuleVersion = payload.calculationRuleVersion,
        )
    }

    private inline fun <reified T> RemoteFinanceRecordDto.payload(): T =
        json.decodeFromJsonElement(
            payload ?: throw FinancialContextException(
                FinancialContextProblem.INVALID_REMOTE_DATA,
            ),
        )

    private fun <T> List<RemoteFinanceRecordDto>.decodeList(
        type: SyncEntityType,
        decoder: (RemoteFinanceRecordDto) -> T,
    ): List<T> = asSequence()
        .filter { it.entityType == type.wireName }
        .map(decoder)
        .toList()
}

@Serializable
internal data class RemoteFinanceRecordDto(
    @SerialName("user_id")
    val userId: String,
    @SerialName("entity_type")
    val entityType: String,
    @SerialName("entity_id")
    val entityId: String,
    @SerialName("schema_version")
    val schemaVersion: Int,
    val payload: JsonElement?,
    @SerialName("is_deleted")
    val isDeleted: Boolean,
    @SerialName("updated_at_epoch_millis")
    val updatedAtEpochMillis: Long,
)

private enum class SyncEntityType(val wireName: String) {
    FINANCIAL_SETUP("FINANCIAL_SETUP"),
    ACCOUNT("ACCOUNT"),
    INCOME_SOURCE("INCOME_SOURCE"),
    DEBT("DEBT"),
    SAVINGS_PLAN("SAVINGS_PLAN"),
    RECURRING_OBLIGATION("RECURRING_OBLIGATION"),
    CREDIT_CARD_PROFILE("CREDIT_CARD_PROFILE"),
    SAVINGS_PROFILE("SAVINGS_PROFILE"),
    LOAN_PROFILE("LOAN_PROFILE"),
    TRANSACTION("TRANSACTION"),
    INSTALLMENT_PURCHASE("INSTALLMENT_PURCHASE"),
    CREDIT_CARD_PAYMENT("CREDIT_CARD_PAYMENT"),
    SAVINGS_MOVEMENT("SAVINGS_MOVEMENT"),
    LOAN_PAYMENT("LOAN_PAYMENT"),
    CUSTOM_CATEGORY("CUSTOM_CATEGORY");

    companion object {
        fun fromWireName(value: String): SyncEntityType? =
            entries.firstOrNull { it.wireName == value }
    }
}

private object FinancialSnapshotVersion {
    fun from(records: List<RemoteFinanceRecordDto>): Long {
        if (records.isEmpty()) return 0
        val canonical = records
            .sortedWith(
                compareBy(
                    RemoteFinanceRecordDto::entityType,
                    RemoteFinanceRecordDto::entityId,
                ),
            )
            .joinToString(separator = "\n") { record ->
                listOf(
                    record.entityType,
                    record.entityId,
                    record.schemaVersion.toString(),
                    record.isDeleted.toString(),
                    record.updatedAtEpochMillis.toString(),
                ).joinToString(separator = "|")
            }
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(canonical.toByteArray(StandardCharsets.UTF_8))
        var version = 0L
        repeat(Long.SIZE_BYTES) { index ->
            version = (version shl 8) or (digest[index].toLong() and 0xff)
        }
        return version and Long.MAX_VALUE
    }
}

private fun RemoteFinanceRecordDto.requireEntityId(payloadId: String) {
    if (entityId != payloadId) {
        throw FinancialContextException(
            FinancialContextProblem.INVALID_REMOTE_DATA,
        )
    }
}

private inline fun <reified T : Enum<T>> String.asEnum(): T =
    enumValues<T>().firstOrNull { it.name == this }
        ?: throw FinancialContextException(
            FinancialContextProblem.INVALID_REMOTE_DATA,
        )

private fun String.asCurrency(): CurrencyCode = asEnum()

@Serializable
private data class AccountPayload(
    val id: String,
    val name: String,
    val type: String,
    val currency: String,
    val openingBalanceMinorUnits: Long,
    val isArchived: Boolean,
    val aliasesJson: String = "[]",
)

@Serializable
private data class TransactionPayload(
    val id: String,
    val accountId: String,
    val type: String,
    val amountMinorUnits: Long,
    val currency: String,
    val occurredAtEpochMillis: Long,
    val categoryId: String?,
    val merchant: String?,
    val note: String?,
    val source: String,
    val status: String,
    val relatedAccountId: String? = null,
    val manualRevision: Int = 0,
)

@Serializable
private data class CustomCategoryPayload(
    val id: String,
    val name: String,
    val createdAtEpochMillis: Long,
)

@Serializable
private data class IncomeSourcePayload(
    val id: String,
    val name: String,
    val expectedAmountMinorUnits: Long,
    val currency: String,
    val recurrence: String,
    val nextExpectedAtEpochMillis: Long?,
    val isActive: Boolean,
)

@Serializable
private data class DebtPayload(
    val id: String,
    val name: String,
    val outstandingBalanceMinorUnits: Long,
    val currency: String,
    val interestRateAnnualBasisPoints: Int?,
    val installmentAmountMinorUnits: Long?,
    val dueDayOfMonth: Int?,
    val nextDueAtEpochMillis: Long?,
    val isActive: Boolean,
)

@Serializable
private data class SavingsPlanPayload(
    val id: String,
    val name: String,
    val type: String,
    val currentAmountMinorUnits: Long,
    val targetAmountMinorUnits: Long?,
    val monthlyContributionMinorUnits: Long?,
    val currency: String,
    val annualYieldBasisPoints: Int?,
    val targetDateEpochMillis: Long?,
    val isActive: Boolean,
)

@Serializable
private data class RecurringObligationPayload(
    val id: String,
    val name: String,
    val amountMinorUnits: Long,
    val currency: String,
    val recurrence: String,
    val dueDayOfMonth: Int?,
    val isActive: Boolean,
)

@Serializable
private data class CreditCardProfilePayload(
    val accountId: String,
    val creditLimitMinorUnits: Long,
    val currency: String,
    val annualInterestBasisPoints: Int,
    val statementClosingDay: Int,
    val paymentDueDay: Int,
    val openingDebtInstallmentCount: Int,
    val openingDebtFirstPaymentAtEpochMillis: Long?,
    val scheduleRuleVersion: Int = 1,
)

@Serializable
private data class InstallmentPurchasePayload(
    val id: String,
    val accountId: String,
    val merchant: String,
    val principalMinorUnits: Long,
    val currency: String,
    val installmentCount: Int,
    val annualInterestBasisPoints: Int,
    val purchasedAtEpochMillis: Long,
    val firstPaymentAtEpochMillis: Long,
    val categoryId: String?,
    val note: String?,
    val promotionalRatePeriodsJson: String = "[]",
    val calculationRuleVersion: Int = 1,
)

@Serializable
private data class DebtPaymentPayload(
    val id: String,
    val accountId: String,
    val amountMinorUnits: Long,
    val currency: String,
    val paidAtEpochMillis: Long,
    val sourceAccountId: String?,
    val note: String?,
    val paymentType: String = "CUSTOM",
    val calculationRuleVersion: Int = 1,
)

@Serializable
private data class SavingsProfilePayload(
    val accountId: String,
    val type: String,
    val annualYieldBasisPoints: Int,
    val openedAtEpochMillis: Long,
    val maturityAtEpochMillis: Long?,
    val calculationRuleVersion: Int = 1,
)

@Serializable
private data class SavingsMovementPayload(
    val id: String,
    val accountId: String,
    val type: String,
    val amountMinorUnits: Long,
    val currency: String,
    val annualYieldBasisPoints: Int?,
    val occurredAtEpochMillis: Long,
    val note: String?,
    val calculationRuleVersion: Int = 1,
)

@Serializable
private data class LoanProfilePayload(
    val accountId: String,
    val annualInterestBasisPoints: Int,
    val monthlyPaymentMinorUnits: Long,
    val currency: String,
    val paymentDueDay: Int,
    val openedAtEpochMillis: Long,
    val scheduleRuleVersion: Int = 1,
)

private const val CURRENT_SYNC_SCHEMA_VERSION = 2
