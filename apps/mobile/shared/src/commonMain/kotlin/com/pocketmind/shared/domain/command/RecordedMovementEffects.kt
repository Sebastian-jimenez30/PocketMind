package com.pocketmind.shared.domain.command

/**
 * Deterministic persistence identifiers produced by reversible movement
 * commands. Keeping this mapping beside the command contract makes execution,
 * editing and cancellation use the same identity rules on every platform.
 */
data class RecordedMovementEffects(
    val commandId: String,
    val ledgerTransactionIds: Set<String>,
    val savingsMovementIds: Set<String>,
)

fun recordedMovementEffects(commandId: String): RecordedMovementEffects {
    val normalized = commandId.trim()
    require(normalized.isNotEmpty()) { "A command id is required." }
    return RecordedMovementEffects(
        commandId = normalized,
        ledgerTransactionIds = setOf(
            normalized,
            "purchase-$normalized",
            "card-payment-$normalized",
            "savings-$normalized",
            "loan-payment-$normalized",
        ),
        savingsMovementIds = setOf(
            normalized,
            "source-savings-$normalized",
            "related-savings-$normalized",
        ),
    )
}

/**
 * Resolves the originating command from any ledger row emitted by a movement.
 * History can therefore reopen the same assistant draft without knowing which
 * specialized persistence table produced the visible transaction.
 */
fun recordedMovementCommandId(transactionId: String): String {
    require(transactionId.isNotBlank()) { "A transaction id is required." }
    val prefixes = listOf(
        "source-savings-",
        "related-savings-",
        "card-payment-",
        "loan-payment-",
        "purchase-",
        "savings-",
    )
    return prefixes.firstOrNull(transactionId::startsWith)
        ?.let(transactionId::removePrefix)
        ?: transactionId
}
