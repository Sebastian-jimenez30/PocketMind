package com.pocketmind.assistant.domain.turn

import com.pocketmind.assistant.agent.chat.AssistantFinancialIntent
import com.pocketmind.assistant.agent.chat.AssistantInterpreterProduct
import com.pocketmind.assistant.agent.chat.AssistantModelDecision
import java.text.Normalizer

/**
 * Keeps model-facing product identifiers out of user-facing conversation and
 * repairs stale references with the product explicitly named in the latest
 * user message.
 */
internal fun AssistantModelDecision.withSafeProductReferences(
    products: List<AssistantInterpreterProduct>,
    latestUserMessage: String,
): AssistantModelDecision = copy(
    reply = reply?.toPublicProductText(products),
    primaryProductReference = safeReference(
        reference = primaryProductReference,
        products = products,
        allowedTypes = intent.primaryProductTypes(),
        latestUserMessage = latestUserMessage,
        inferWhenMissing = true,
    ),
    destinationProductReference = safeReference(
        reference = destinationProductReference,
        products = products,
        allowedTypes = intent.destinationProductTypes(),
        latestUserMessage = latestUserMessage,
        inferWhenMissing = false,
    ),
    sourceProductReference = safeReference(
        reference = sourceProductReference,
        products = products,
        allowedTypes = FUNDING_PRODUCT_TYPES,
        latestUserMessage = latestUserMessage,
        inferWhenMissing = false,
    ),
)

private fun safeReference(
    reference: String?,
    products: List<AssistantInterpreterProduct>,
    allowedTypes: Set<String>?,
    latestUserMessage: String,
    inferWhenMissing: Boolean,
): String? {
    val compatibleProducts = products.filter { product ->
        allowedTypes == null || product.type in allowedTypes
    }
    val mentionedProduct = if (reference != null || inferWhenMissing) {
        compatibleProducts
            .filter { it.isMentionedIn(latestUserMessage) }
            .singleOrNull()
    } else {
        null
    }
    if (mentionedProduct != null) return mentionedProduct.name

    val referencedProduct = reference?.let { value ->
        compatibleProducts.firstOrNull { it.matchesReference(value) }
    }
    if (referencedProduct != null) return referencedProduct.name

    return reference
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?.takeUnless { it.isOpaqueProductIdentifier() }
}

private fun AssistantInterpreterProduct.matchesReference(reference: String): Boolean {
    if (id.equals(reference.trim(), ignoreCase = true)) return true
    val normalizedReference = reference.normalizedForMatch()
    val visibleReferences = sequenceOf(name) + aliases.asSequence()
    return visibleReferences.any { it.normalizedForMatch() == normalizedReference }
}

private fun AssistantInterpreterProduct.isMentionedIn(message: String): Boolean {
    val normalizedMessage = message.normalizedForMatch()
    val messageTokens = normalizedMessage.tokens()
    val visibleReferences = sequenceOf(name) + aliases.asSequence()
    return visibleReferences.any { reference ->
        val normalizedReference = reference.normalizedForMatch()
        val referenceTokens = normalizedReference.tokens()
        normalizedReference.length >= MIN_ALIAS_LENGTH &&
            (
                normalizedMessage.contains(normalizedReference) ||
                    referenceTokens.size >= 2 && referenceTokens.all { it in messageTokens } ||
                    referenceTokens.size == 1 && referenceTokens.single() in messageTokens
                )
    }
}

private fun String.toPublicProductText(
    products: List<AssistantInterpreterProduct>,
): String {
    val namedReferences = products.fold(this) { message, product ->
        message.replace(product.id, product.name, ignoreCase = true)
    }
    return PRODUCT_UUID.replace(namedReferences, "el producto indicado")
}

internal fun String.isOpaqueProductIdentifier(): Boolean =
    PRODUCT_UUID.matches(trim())

private fun String.normalizedForMatch(): String =
    Normalizer.normalize(this, Normalizer.Form.NFD)
        .replace(DIACRITICS, "")
        .lowercase()
        .replace(NON_ALPHANUMERIC, " ")
        .trim()
        .replace(REPEATED_SPACES, " ")

private fun String.tokens(): Set<String> =
    split(' ').filterTo(linkedSetOf()) { it.length >= MIN_TOKEN_LENGTH }

private fun AssistantFinancialIntent?.primaryProductTypes(): Set<String>? = when (this) {
    AssistantFinancialIntent.RECORD_INCOME,
    AssistantFinancialIntent.RECORD_EXPENSE,
    AssistantFinancialIntent.TRANSFER,
    AssistantFinancialIntent.UPDATE_TRANSACTION,
    -> LIQUID_PRODUCT_TYPES
    AssistantFinancialIntent.RECORD_CARD_PURCHASE,
    AssistantFinancialIntent.RECORD_CARD_PAYMENT,
    -> setOf("CREDIT_CARD")
    AssistantFinancialIntent.RECORD_SAVINGS_MOVEMENT -> setOf("SAVINGS")
    AssistantFinancialIntent.RECORD_LOAN_PAYMENT -> setOf("LOAN")
    AssistantFinancialIntent.CREATE_PRODUCT,
    AssistantFinancialIntent.UPDATE_PRODUCT,
    AssistantFinancialIntent.ARCHIVE_PRODUCT,
    AssistantFinancialIntent.DELETE_TRANSACTION,
    null,
    -> null
}

private fun AssistantFinancialIntent?.destinationProductTypes(): Set<String>? = when (this) {
    AssistantFinancialIntent.TRANSFER,
    AssistantFinancialIntent.UPDATE_TRANSACTION,
    -> LIQUID_PRODUCT_TYPES
    AssistantFinancialIntent.RECORD_SAVINGS_MOVEMENT -> FUNDING_PRODUCT_TYPES
    else -> null
}

private val LIQUID_PRODUCT_TYPES = setOf("CASH", "BANK_ACCOUNT")
private val FUNDING_PRODUCT_TYPES = LIQUID_PRODUCT_TYPES + "SAVINGS"
private val PRODUCT_UUID = Regex(
    pattern = """(?i)\b[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}\b""",
)
private val DIACRITICS = Regex("\\p{M}+")
private val NON_ALPHANUMERIC = Regex("[^a-z0-9]+")
private val REPEATED_SPACES = Regex("\\s+")
private const val MIN_ALIAS_LENGTH = 2
private const val MIN_TOKEN_LENGTH = 2
