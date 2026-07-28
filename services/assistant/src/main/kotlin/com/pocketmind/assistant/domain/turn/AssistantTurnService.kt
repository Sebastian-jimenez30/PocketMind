package com.pocketmind.assistant.domain.turn

import ai.koog.agents.core.tools.ToolRegistry
import com.pocketmind.assistant.agent.chat.AssistantBasicIntent
import com.pocketmind.assistant.agent.chat.AssistantDecisionAction
import com.pocketmind.assistant.agent.chat.AssistantInterpreterInput
import com.pocketmind.assistant.agent.chat.AssistantInterpreterMessage
import com.pocketmind.assistant.agent.chat.AssistantInterpreterProduct
import com.pocketmind.assistant.agent.chat.AssistantModelDecision
import com.pocketmind.assistant.agent.chat.AssistantTurnInterpreter
import com.pocketmind.assistant.agent.tools.AssistantReadToolRegistryFactory
import com.pocketmind.assistant.auth.AuthenticatedUser
import com.pocketmind.assistant.domain.finance.FinancialReadService
import com.pocketmind.assistant.domain.finance.FinancialReadServiceFactory
import com.pocketmind.assistant.domain.finance.ProductSummary
import com.pocketmind.assistant.domain.memory.AssistantCommandDraft
import com.pocketmind.assistant.domain.memory.AssistantMemoryRepository
import com.pocketmind.assistant.domain.memory.AssistantMessage
import com.pocketmind.assistant.domain.memory.InputModality
import com.pocketmind.assistant.domain.memory.MessageRole
import com.pocketmind.assistant.domain.memory.NewCommandDraft
import com.pocketmind.assistant.domain.memory.NewConversation
import com.pocketmind.assistant.domain.memory.NewMessage
import com.pocketmind.shared.assistant.AssistantChatMessage
import com.pocketmind.shared.assistant.AssistantDraftPreview
import com.pocketmind.shared.assistant.AssistantTurnRequest
import com.pocketmind.shared.assistant.AssistantTurnResponse
import com.pocketmind.shared.assistant.AssistantTurnStatus
import com.pocketmind.shared.domain.command.CURRENT_FINANCIAL_COMMAND_SCHEMA_VERSION
import com.pocketmind.shared.domain.command.FinancialCommand
import com.pocketmind.shared.domain.command.FinancialCommandCodec
import com.pocketmind.shared.domain.model.CurrencyCode
import com.pocketmind.shared.domain.model.FinancialAccountType
import com.pocketmind.shared.domain.model.Money
import com.pocketmind.shared.domain.model.TransactionCategoryId
import com.pocketmind.shared.domain.model.TransactionSource
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.nio.charset.StandardCharsets
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID

fun interface AssistantTurnHandler {
    suspend fun handle(
        session: AuthenticatedUser,
        request: AssistantTurnRequest,
    ): AssistantTurnResponse
}

/**
 * Coordinates a text turn without executing financial writes.
 *
 * The model extracts intent and may use read-only tools. This service remains
 * the authority for product resolution, validation and draft construction.
 */
class AssistantTurnService(
    private val memoryRepository: AssistantMemoryRepository,
    private val readServiceFactory: FinancialReadServiceFactory,
    private val toolRegistryFactory: AssistantReadToolRegistryFactory,
    private val interpreter: AssistantTurnInterpreter,
    private val promptVersion: String,
    private val toolSchemaVersion: Int,
    private val modelId: String,
    private val clock: Clock = Clock.systemUTC(),
) : AssistantTurnHandler {
    override suspend fun handle(
        session: AuthenticatedUser,
        request: AssistantTurnRequest,
    ): AssistantTurnResponse {
        val existingUserMessage = memoryRepository.findMessageByClientMessageId(
            session,
            request.clientMessageId,
        )
        existingUserMessage?.let { existing ->
            replayCompletedTurn(session, existing)?.let { return it }
        }

        val conversationId = existingUserMessage?.conversationId
            ?: request.conversationId
            ?: UUID.randomUUID().toString()
        val turnId = existingUserMessage?.turnId ?: UUID.randomUUID().toString()
        val userMessage = existingUserMessage ?: run {
            ensureConversation(session, conversationId, request)
            memoryRepository.appendMessage(
                session,
                NewMessage(
                    id = stableUuid("user:${request.clientMessageId}"),
                    conversationId = conversationId,
                    turnId = turnId,
                    clientMessageId = request.clientMessageId,
                    role = MessageRole.USER,
                    content = request.content.trim(),
                    inputModality = InputModality.TEXT,
                    promptVersion = promptVersion,
                    modelId = null,
                ),
            )
        }
        val history = memoryRepository.listMessages(session, conversationId, HISTORY_LIMIT)
        val readService = readServiceFactory.create(session)
        val products = readService.listProducts(includeArchived = false).products
        val tools = toolRegistryFactory.create(readService)
        val decision = interpreter.interpret(
            AssistantInterpreterInput(
                locale = request.locale,
                timeZoneId = request.timeZoneId,
                currentEpochMillis = clock.millis(),
                products = products.map {
                    AssistantInterpreterProduct(
                        id = it.id,
                        name = it.name,
                        type = it.type,
                        currency = it.currency,
                        aliases = it.aliases,
                    )
                },
                conversation = history.map {
                    AssistantInterpreterMessage(it.role.wireValue, it.content)
                },
            ),
            tools,
        )
        return finalizeTurn(
            session = session,
            conversationId = conversationId,
            turnId = turnId,
            userMessage = userMessage,
            decision = decision,
            readService = readService,
        )
    }

    private suspend fun ensureConversation(
        session: AuthenticatedUser,
        conversationId: String,
        request: AssistantTurnRequest,
    ) {
        val existing = memoryRepository.getConversation(session, conversationId)
        if (existing != null) return
        require(request.conversationId == null) {
            "The requested conversation does not exist."
        }
        memoryRepository.createConversation(
            session,
            NewConversation(
                id = conversationId,
                title = request.content.trim().take(TITLE_LIMIT),
                locale = request.locale,
                promptVersion = promptVersion,
                toolSchemaVersion = toolSchemaVersion,
            ),
        )
    }

    private suspend fun finalizeTurn(
        session: AuthenticatedUser,
        conversationId: String,
        turnId: String,
        userMessage: AssistantMessage,
        decision: AssistantModelDecision,
        readService: FinancialReadService,
    ): AssistantTurnResponse {
        val resolution = when (decision.action) {
            AssistantDecisionAction.RESPOND -> Resolution.Conversation(
                decision.reply.normalizedOptional()
                    ?: "Hola, ¿cómo puedo ayudarte con tus finanzas?",
            )
            AssistantDecisionAction.UNSUPPORTED -> Resolution.Unsupported(
                decision.reply.normalizedOptional(),
            )
            AssistantDecisionAction.CLARIFY -> Resolution.Clarification(
                decision.reply.normalizedOptional()
                    ?: clarificationFor(decision.missingFields),
            )
            AssistantDecisionAction.PROPOSE -> resolveProposal(decision, readService, turnId)
        }
        val draft = if (resolution is Resolution.Proposal) {
            memoryRepository.getDraftByIdempotencyKey(
                session,
                idempotencyKey(turnId),
            ) ?: memoryRepository.createDraft(
                session,
                NewCommandDraft(
                    id = stableUuid("draft:$turnId"),
                    conversationId = conversationId,
                    commandType = resolution.commandType,
                    commandPayload = resolution.commandPayload,
                    commandSchemaVersion = CURRENT_FINANCIAL_COMMAND_SCHEMA_VERSION,
                    idempotencyKey = idempotencyKey(turnId),
                    financialStateVersion = resolution.financialStateVersion,
                    expiresAt = clock.instant().plus(DRAFT_TTL),
                ),
            )
        } else {
            null
        }
        val reply = when (resolution) {
            is Resolution.Clarification -> resolution.message
            is Resolution.Conversation -> resolution.message
            is Resolution.Unsupported -> resolution.message
                ?: "No entendí qué quieres hacer. Puedo conversar contigo, consultar " +
                "tus finanzas y registrar ingresos, gastos o transferencias."
            is Resolution.Proposal -> proposalReply(resolution)
        }
        val assistantMessage = memoryRepository.appendMessage(
            session,
            NewMessage(
                id = stableUuid("assistant:$turnId"),
                conversationId = conversationId,
                turnId = turnId,
                clientMessageId = null,
                role = MessageRole.ASSISTANT,
                content = reply,
                inputModality = InputModality.TEXT,
                promptVersion = promptVersion,
                modelId = modelId,
            ),
        )
        return AssistantTurnResponse(
            conversationId = conversationId,
            turnId = turnId,
            status = when (resolution) {
                is Resolution.Clarification -> AssistantTurnStatus.CLARIFICATION
                is Resolution.Conversation -> AssistantTurnStatus.CONVERSATION
                is Resolution.Proposal -> AssistantTurnStatus.PROPOSAL
                is Resolution.Unsupported -> AssistantTurnStatus.UNSUPPORTED
            },
            reply = reply,
            userMessage = userMessage.toTransport(),
            assistantMessage = assistantMessage.toTransport(),
            draft = if (resolution is Resolution.Proposal && draft != null) {
                resolution.toPreview(draft)
            } else {
                null
            },
        )
    }

    private suspend fun resolveProposal(
        decision: AssistantModelDecision,
        readService: FinancialReadService,
        turnId: String,
    ): Resolution {
        val intent = decision.intent
            ?: return Resolution.Clarification("¿Qué movimiento quieres registrar?")
        val amount = decision.amountMinorUnits
            ?.takeIf { it > 0 }
            ?: return Resolution.Clarification("¿Cuál es el valor del movimiento?")
        val primaryReference = decision.primaryProductReference
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?: return Resolution.Clarification(
                if (intent == AssistantBasicIntent.TRANSFER) {
                    "¿Desde cuál producto quieres transferir?"
                } else {
                    "¿En cuál producto ocurrió el movimiento?"
                },
            )
        val primary = resolveLiquidProduct(primaryReference, readService)
            ?: return Resolution.Clarification(
                "No pude identificar con certeza el producto \"$primaryReference\". " +
                    "¿Puedes usar su nombre completo?",
            )
        val currency = resolveCurrency(decision.currency, primary)
            ?: return Resolution.Clarification(
                "La moneda indicada no coincide con ${primary.name}.",
            )
        val occurredAt = decision.occurredAtEpochMillis ?: clock.millis()
        if (occurredAt <= 0 || occurredAt > clock.millis() + MAX_FUTURE_SKEW.toMillis()) {
            return Resolution.Clarification("¿En qué fecha ocurrió el movimiento?")
        }
        val categoryId = decision.categoryId?.let { value ->
            TransactionCategoryId.entries.firstOrNull { it.name == value }
                ?: return Resolution.Clarification(
                    "No reconocí la categoría. ¿Cuál quieres usar?",
                )
        }?.name
        val destination = if (intent == AssistantBasicIntent.TRANSFER) {
            val reference = decision.destinationProductReference
                ?.trim()
                ?.takeIf(String::isNotEmpty)
                ?: return Resolution.Clarification(
                    "¿A cuál producto quieres enviar el dinero?",
                )
            resolveLiquidProduct(reference, readService)
                ?: return Resolution.Clarification(
                    "No pude identificar con certeza el destino \"$reference\". " +
                        "¿Puedes usar su nombre completo?",
                )
        } else {
            null
        }
        if (destination != null && destination.id == primary.id) {
            return Resolution.Clarification(
                "El origen y el destino deben ser productos diferentes.",
            )
        }
        if (destination != null && destination.currency != primary.currency) {
            return Resolution.Clarification(
                "Por ahora la transferencia debe usar productos de la misma moneda.",
            )
        }
        val money = Money(amount, currency)
        val command = when (intent) {
            AssistantBasicIntent.RECORD_INCOME -> FinancialCommand.RecordIncome(
                commandId = turnId,
                productId = primary.id,
                amount = money,
                occurredAtEpochMillis = occurredAt,
                source = TransactionSource.ASSISTANT_TEXT,
                categoryId = categoryId,
                merchant = decision.merchant.normalizedOptional(),
                note = decision.note.normalizedOptional(),
            )
            AssistantBasicIntent.RECORD_EXPENSE -> FinancialCommand.RecordExpense(
                commandId = turnId,
                productId = primary.id,
                amount = money,
                occurredAtEpochMillis = occurredAt,
                source = TransactionSource.ASSISTANT_TEXT,
                categoryId = categoryId,
                merchant = decision.merchant.normalizedOptional(),
                note = decision.note.normalizedOptional(),
            )
            AssistantBasicIntent.TRANSFER -> FinancialCommand.Transfer(
                commandId = turnId,
                sourceProductId = primary.id,
                destinationProductId = requireNotNull(destination).id,
                amount = money,
                occurredAtEpochMillis = occurredAt,
                source = TransactionSource.ASSISTANT_TEXT,
                categoryId = categoryId ?: TransactionCategoryId.TRANSFER.name,
                merchant = decision.merchant.normalizedOptional(),
                note = decision.note.normalizedOptional(),
            )
        }
        val payload = Json.parseToJsonElement(FinancialCommandCodec.encode(command)).jsonObject
        val metadata = readService.getOverview().metadata
        return Resolution.Proposal(
            commandType = intent.wireValue,
            commandPayload = payload,
            financialStateVersion = metadata.stateVersion,
            amountMinorUnits = amount,
            currency = currency.name,
            primary = primary,
            destination = destination,
            merchant = decision.merchant.normalizedOptional(),
            categoryId = categoryId,
            occurredAtEpochMillis = occurredAt,
        )
    }

    private suspend fun resolveLiquidProduct(
        reference: String,
        service: FinancialReadService,
    ): ProductSummary? {
        val product = service.getProduct(reference, LIQUID_PRODUCT_TYPES).product ?: return null
        if (product.isArchived) return null
        return product
    }

    private fun resolveCurrency(
        requested: String?,
        product: ProductSummary,
    ): CurrencyCode? {
        val productCurrency = CurrencyCode.entries.single { it.name == product.currency }
        if (requested.isNullOrBlank()) return productCurrency
        val requestedCurrency = CurrencyCode.entries
            .firstOrNull { it.name == requested.trim().uppercase() }
            ?: return null
        return requestedCurrency.takeIf { it == productCurrency }
    }

    private suspend fun replayCompletedTurn(
        session: AuthenticatedUser,
        userMessage: AssistantMessage,
    ): AssistantTurnResponse? {
        val messages = memoryRepository.listMessages(
            session,
            userMessage.conversationId,
            HISTORY_LIMIT,
        )
        val assistantMessage = messages.firstOrNull {
            it.turnId == userMessage.turnId && it.role == MessageRole.ASSISTANT
        } ?: return null
        val draft = memoryRepository.getDraftByIdempotencyKey(
            session,
            idempotencyKey(userMessage.turnId),
        )
        return AssistantTurnResponse(
            conversationId = userMessage.conversationId,
            turnId = userMessage.turnId,
            status = if (draft == null) {
                AssistantTurnStatus.CLARIFICATION
            } else {
                AssistantTurnStatus.PROPOSAL
            },
            reply = assistantMessage.content,
            userMessage = userMessage.toTransport(),
            assistantMessage = assistantMessage.toTransport(),
            draft = draft?.let { previewFromStoredDraft(session, it) },
        )
    }

    private suspend fun previewFromStoredDraft(
        session: AuthenticatedUser,
        draft: AssistantCommandDraft,
    ): AssistantDraftPreview? {
        val command = FinancialCommandCodec.decode(draft.commandPayload.toString()).getOrNull()
            ?: return null
        val service = readServiceFactory.create(session)
        val values = command.previewValues() ?: return null
        val primary = service.getProduct(values.primaryProductId).product ?: return null
        val destination = values.destinationProductId
            ?.let { service.getProduct(it).product }
        return AssistantDraftPreview(
            id = draft.id,
            version = draft.version,
            commandType = draft.commandType,
            amountMinorUnits = values.amount.minorUnits,
            currency = values.amount.currency.name,
            primaryProductId = primary.id,
            primaryProductName = primary.name,
            destinationProductId = destination?.id,
            destinationProductName = destination?.name,
            merchant = values.merchant,
            categoryId = values.categoryId,
            occurredAtEpochMillis = values.occurredAtEpochMillis,
            expiresAt = draft.expiresAt.toString(),
        )
    }

    private fun proposalReply(value: Resolution.Proposal): String = when (value.commandType) {
        AssistantBasicIntent.RECORD_INCOME.wireValue ->
            "Preparé un ingreso en ${value.primary.name}. Revísalo antes de guardarlo."
        AssistantBasicIntent.RECORD_EXPENSE.wireValue ->
            "Preparé un gasto en ${value.primary.name}. Revísalo antes de guardarlo."
        else ->
            "Preparé una transferencia de ${value.primary.name} a " +
                "${value.destination?.name}. Revísala antes de guardarla."
    }

    private fun clarificationFor(fields: List<String>): String {
        val normalized = fields.map(String::lowercase)
        return when {
            normalized.any { it.contains("amount") || it.contains("monto") } ->
                "¿Cuál es el valor del movimiento?"
            normalized.any { it.contains("destination") || it.contains("destino") } ->
                "¿A cuál producto quieres enviar el dinero?"
            normalized.any { it.contains("product") || it.contains("cuenta") } ->
                "¿En cuál producto ocurrió el movimiento?"
            else -> "Necesito un poco más de información. ¿Qué movimiento quieres registrar?"
        }
    }

    private fun Resolution.Proposal.toPreview(
        draft: AssistantCommandDraft,
    ): AssistantDraftPreview = AssistantDraftPreview(
        id = draft.id,
        version = draft.version,
        commandType = commandType,
        amountMinorUnits = amountMinorUnits,
        currency = currency,
        primaryProductId = primary.id,
        primaryProductName = primary.name,
        destinationProductId = destination?.id,
        destinationProductName = destination?.name,
        merchant = merchant,
        categoryId = categoryId,
        occurredAtEpochMillis = occurredAtEpochMillis,
        expiresAt = draft.expiresAt.toString(),
    )

    private sealed interface Resolution {
        data class Clarification(val message: String) : Resolution
        data class Conversation(val message: String) : Resolution
        data class Unsupported(val message: String?) : Resolution
        data class Proposal(
            val commandType: String,
            val commandPayload: JsonObject,
            val financialStateVersion: Long,
            val amountMinorUnits: Long,
            val currency: String,
            val primary: ProductSummary,
            val destination: ProductSummary?,
            val merchant: String?,
            val categoryId: String?,
            val occurredAtEpochMillis: Long,
        ) : Resolution
    }

    private data class CommandPreviewValues(
        val primaryProductId: String,
        val destinationProductId: String?,
        val amount: Money,
        val merchant: String?,
        val categoryId: String?,
        val occurredAtEpochMillis: Long,
    )

    private fun FinancialCommand.previewValues(): CommandPreviewValues? = when (this) {
        is FinancialCommand.RecordIncome -> CommandPreviewValues(
            productId, null, amount, merchant, categoryId, occurredAtEpochMillis,
        )
        is FinancialCommand.RecordExpense -> CommandPreviewValues(
            productId, null, amount, merchant, categoryId, occurredAtEpochMillis,
        )
        is FinancialCommand.Transfer -> CommandPreviewValues(
            sourceProductId,
            destinationProductId,
            amount,
            merchant,
            categoryId,
            occurredAtEpochMillis,
        )
        else -> null
    }

    private fun AssistantMessage.toTransport(): AssistantChatMessage =
        AssistantChatMessage(
            id = id,
            turnId = turnId,
            role = role.wireValue,
            content = content,
            createdAt = createdAt.toString(),
        )

    private fun stableUuid(seed: String): String =
        UUID.nameUUIDFromBytes(seed.toByteArray(StandardCharsets.UTF_8)).toString()

    private fun idempotencyKey(turnId: String): String = "assistant-turn:$turnId"

    private companion object {
        const val HISTORY_LIMIT = 80
        const val TITLE_LIMIT = 80
        val LIQUID_PRODUCT_TYPES = setOf(
            FinancialAccountType.CASH,
            FinancialAccountType.BANK_ACCOUNT,
        )
        val DRAFT_TTL: Duration = Duration.ofMinutes(15)
        val MAX_FUTURE_SKEW: Duration = Duration.ofMinutes(5)
    }
}

private val AssistantBasicIntent.wireValue: String
    get() = when (this) {
        AssistantBasicIntent.RECORD_INCOME -> "record_income"
        AssistantBasicIntent.RECORD_EXPENSE -> "record_expense"
        AssistantBasicIntent.TRANSFER -> "transfer"
    }

private fun String?.normalizedOptional(): String? =
    this?.trim()?.takeIf(String::isNotEmpty)
