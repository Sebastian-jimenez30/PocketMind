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
 * Resolves a product using progressively broader deterministic matches.
 *
 * Exact names and confirmed aliases always take priority. A natural reference
 * such as "cuenta de Bancolombia" may then resolve "Ahorros Bancolombia" only
 * when a single product contains all meaningful reference words. Ambiguous
 * references are never guessed.
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
    if (aliasMatches.size == 1) {
        return ProductReferenceResolution.Resolved(
            aliasMatches.single(),
            matchedByAlias = true,
        )
    }
    if (aliasMatches.size > 1) {
        return ProductReferenceResolution.Ambiguous(aliasMatches)
    }

    val referenceWords = normalized.meaningfulReferenceWords()
    if (referenceWords.isEmpty()) return ProductReferenceResolution.NotFound
    val partialMatches = products.filter { product ->
        product.referenceCandidates().any { candidate ->
            val candidateWords = candidate.meaningfulReferenceWords()
            referenceWords.all(candidateWords::contains)
        }
    }
    return when (partialMatches.size) {
        0 -> ProductReferenceResolution.NotFound
        1 -> ProductReferenceResolution.Resolved(
            partialMatches.single(),
            matchedByAlias = true,
        )
        else -> ProductReferenceResolution.Ambiguous(partialMatches)
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

private fun FinancialAccount.referenceCandidates(): List<String> =
    (listOf(name) + aliases).map(String::normalizedReference)

private fun String.meaningfulReferenceWords(): Set<String> =
    split(' ')
        .asSequence()
        .filter(String::isNotBlank)
        .filterNot(GENERIC_PRODUCT_WORDS::contains)
        .toSet()

private fun String.normalizedReference(): String =
    trim()
        .lowercase()
        .replace('á', 'a')
        .replace('é', 'e')
        .replace('í', 'i')
        .replace('ó', 'o')
        .replace('ú', 'u')
        .replace('ü', 'u')
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()

private val GENERIC_PRODUCT_WORDS = setOf(
    "ahorro",
    "ahorros",
    "banco",
    "bancaria",
    "bancario",
    "cuenta",
    "de",
    "del",
    "desde",
    "el",
    "la",
    "las",
    "los",
    "mi",
    "mis",
    "producto",
)
