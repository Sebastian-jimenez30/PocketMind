package com.pocketmind.shared.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/** Current deterministic rule set used when PocketMind creates new schedules. */
const val CURRENT_FINANCIAL_RULE_VERSION = 1

/** Meaning of a payment when its amount can be derived from current debt state. */
@Serializable
enum class DebtPaymentType {
    @SerialName("scheduled_installment")
    SCHEDULED_INSTALLMENT,

    @SerialName("full_balance")
    FULL_BALANCE,

    @SerialName("extra_principal")
    EXTRA_PRINCIPAL,

    @SerialName("custom")
    CUSTOM,
}

/** Inclusive installment range that overrides the product's standard annual rate. */
@Serializable
data class InstallmentRatePeriod(
    @SerialName("first_installment")
    val firstInstallment: Int,
    @SerialName("last_installment")
    val lastInstallment: Int,
    @SerialName("annual_interest_basis_points")
    val annualInterestBasisPoints: Int,
) {
    init {
        require(firstInstallment > 0)
        require(lastInstallment >= firstInstallment)
        require(annualInterestBasisPoints >= 0)
    }

    operator fun contains(installmentNumber: Int): Boolean =
        installmentNumber in firstInstallment..lastInstallment
}

/** Auditable result of the deterministic installment schedule calculation. */
data class InstallmentScheduleEntry(
    val installmentNumber: Int,
    val amount: Money,
    val principal: Money,
    val interest: Money,
    val annualInterestBasisPoints: Int,
    val calculationRuleVersion: Int,
)

sealed interface ProductReferenceResolution {
    data class Resolved(
        val product: FinancialAccount,
        val matchedByAlias: Boolean,
    ) : ProductReferenceResolution

    data class Ambiguous(
        val candidates: List<FinancialAccount>,
    ) : ProductReferenceResolution

    data object NotFound : ProductReferenceResolution
}

/**
 * Resolves an exact product name first and then confirmed aliases.
 *
 * Fuzzy or model-generated identifiers are intentionally excluded: ambiguous
 * references must be clarified with options belonging to the current user.
 */
fun resolveProductReference(
    reference: String,
    products: List<FinancialAccount>,
): ProductReferenceResolution {
    val normalized = reference.normalizedReference()
    if (normalized.isEmpty()) return ProductReferenceResolution.NotFound

    val exactMatches = products.filter { it.name.normalizedReference() == normalized }
    if (exactMatches.size == 1) {
        return ProductReferenceResolution.Resolved(exactMatches.single(), matchedByAlias = false)
    }
    if (exactMatches.size > 1) return ProductReferenceResolution.Ambiguous(exactMatches)

    val aliasMatches = products.filter { product ->
        product.aliases.any { it.normalizedReference() == normalized }
    }
    return when (aliasMatches.size) {
        0 -> ProductReferenceResolution.NotFound
        1 -> ProductReferenceResolution.Resolved(aliasMatches.single(), matchedByAlias = true)
        else -> ProductReferenceResolution.Ambiguous(aliasMatches)
    }
}

/** Stable JSON representation used by Room's account payload. */
object ProductAliasCodec {
    private val serializer = ListSerializer(String.serializer())

    fun encode(aliases: List<String>): String =
        Json.encodeToString(serializer, aliases.sortedBy { it.lowercase() })

    fun decode(value: String): List<String> =
        Json.decodeFromString(serializer, value)
}

/** Stable JSON representation used by Room's installment-purchase payload. */
object InstallmentRatePeriodCodec {
    private val serializer = ListSerializer(InstallmentRatePeriod.serializer())

    fun encode(periods: List<InstallmentRatePeriod>): String =
        Json.encodeToString(serializer, periods.sortedBy(InstallmentRatePeriod::firstInstallment))

    fun decode(value: String): List<InstallmentRatePeriod> =
        Json.decodeFromString(serializer, value)
}

private fun String.normalizedReference(): String =
    trim().lowercase().replace(Regex("\\s+"), " ")
