package com.pocketmind.assistant.domain.turn

import com.pocketmind.assistant.agent.chat.AssistantDecisionAction
import com.pocketmind.assistant.agent.chat.AssistantInterpreterInput
import com.pocketmind.assistant.agent.chat.AssistantInterpreterMessage
import com.pocketmind.assistant.agent.chat.AssistantInterpreterProduct
import com.pocketmind.assistant.agent.chat.AssistantModelDecision
import com.pocketmind.assistant.agent.chat.AssistantTurnInterpreter
import com.pocketmind.assistant.agent.chat.toModelDecision
import com.pocketmind.assistant.agent.tools.AssistantReadToolRegistryFactory
import com.pocketmind.assistant.auth.AuthenticatedUser
import com.pocketmind.assistant.domain.finance.FinancialReadService
import com.pocketmind.assistant.domain.finance.FinancialReadServiceFactory
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
import com.pocketmind.shared.domain.model.FinancialProductConfiguration
import com.pocketmind.shared.domain.model.Money
import com.pocketmind.shared.domain.model.TransactionCategoryId
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.nio.charset.StandardCharsets
import java.time.Clock
import java.time.Duration
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
    private val proposalResolver = AssistantProposalResolver(clock)

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
        val interpreterProducts = products.map {
            AssistantInterpreterProduct(
                id = it.id,
                name = it.name,
                type = it.type,
                currency = it.currency,
                aliases = it.aliases,
                currentBalanceMinorUnits = it.currentBalance?.minorUnits,
                currentDebtMinorUnits = it.currentDebt?.minorUnits,
                availableCreditMinorUnits = it.availableCredit?.minorUnits,
                nextPaymentMinorUnits = it.nextPayment?.minorUnits,
                annualRateBasisPoints = it.annualRateBasisPoints,
                statementClosingDay = it.statementClosingDay,
                paymentDueDay = it.paymentDueDay,
                maturityAtEpochMillis = it.maturityAtEpochMillis,
            )
        }
        val decision = interpreter.interpret(
            AssistantInterpreterInput(
                locale = request.locale,
                timeZoneId = request.timeZoneId,
                currentEpochMillis = clock.millis(),
                products = interpreterProducts,
                conversation = history.map {
                    AssistantInterpreterMessage(it.role.wireValue, it.content)
                },
            ),
            tools,
        ).withSafeProductReferences(
            products = interpreterProducts,
            latestUserMessage = request.content,
        )
        return finalizeTurn(
            session = session,
            conversationId = conversationId,
            turnId = turnId,
            userMessage = userMessage,
            decision = decision,
            readService = readService,
            interpreterProducts = interpreterProducts,
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
        interpreterProducts: List<AssistantInterpreterProduct>,
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
        val additionalResolutions = decision.additionalDecisions
            .take(MAX_ACTIONS_PER_TURN - 1)
            .mapIndexed { idx, additional ->
                resolveProposal(
                    additional
                        .toModelDecision()
                        .withSafeProductReferences(
                            products = interpreterProducts,
                            latestUserMessage = userMessage.content,
                        ),
                    readService,
                    "$turnId-$idx",
                )
            }
        val additionalDrafts = additionalResolutions.mapIndexedNotNull { idx, addRes ->
            when (addRes) {
                is Resolution.Proposal -> {
                    val key = idempotencyKey("$turnId-$idx")
                    val addDraft = memoryRepository.getDraftByIdempotencyKey(
                        session,
                        key,
                    ) ?: memoryRepository.createDraft(
                        session,
                        NewCommandDraft(
                            id = stableUuid("draft:$turnId-$idx"),
                            conversationId = conversationId,
                            commandType = addRes.commandType,
                            commandPayload = addRes.commandPayload,
                            commandSchemaVersion = CURRENT_FINANCIAL_COMMAND_SCHEMA_VERSION,
                            idempotencyKey = key,
                            financialStateVersion = addRes.financialStateVersion,
                            expiresAt = clock.instant().plus(DRAFT_TTL),
                        ),
                    )
                    addRes.toPreview(addDraft)
                }
                else -> null
            }
        }
        val baseReply = when (resolution) {
            is Resolution.Clarification -> resolution.message
            is Resolution.Conversation -> resolution.message
            is Resolution.Unsupported -> resolution.message
                ?: "No entendí qué quieres hacer. Puedo conversar contigo, consultar " +
                "tus finanzas y ayudarte con movimientos, productos, tarjetas, " +
                "ahorros o préstamos."
            is Resolution.Proposal -> proposalReply(resolution)
        }
        val unresolvedAdditional = additionalResolutions
            .filterIsInstance<Resolution.Clarification>()
            .map(Resolution.Clarification::message)
            .distinct()
        val movementCount = (if (resolution is Resolution.Proposal) 1 else 0) +
            additionalDrafts.size
        val reply = buildString {
            if (movementCount > 1) {
                append("Interpreté $movementCount movimientos y los registraré por separado.")
            } else {
                append(baseReply)
            }
            if (unresolvedAdditional.isNotEmpty()) {
                append(" ")
                append(unresolvedAdditional.joinToString(" "))
            }
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
            additionalDrafts = additionalDrafts,
        )
    }

    private suspend fun resolveProposal(
        decision: AssistantModelDecision,
        readService: FinancialReadService,
        turnId: String,
    ): Resolution = when (
        val result = proposalResolver.resolve(decision, readService, turnId)
    ) {
        is AssistantProposalResolution.Clarification ->
            Resolution.Clarification(result.message)
        is AssistantProposalResolution.Proposal -> Resolution.Proposal(
            commandId = result.command.commandId,
            commandType = result.command.commandType,
            commandPayload = Json.parseToJsonElement(
                FinancialCommandCodec.encode(result.command),
            ).jsonObject,
            financialStateVersion = result.financialStateVersion,
            amountMinorUnits = result.amount?.minorUnits,
            currency = result.amount?.currency?.name ?: result.primary.currency,
            primary = result.primary,
            destination = result.destination,
            merchant = result.merchant,
            categoryId = result.categoryId,
            occurredAtEpochMillis = result.occurredAtEpochMillis,
            productType = result.productType,
            installmentCount = result.installmentCount,
            annualRateBasisPoints = result.annualRateBasisPoints,
            paymentType = result.paymentType,
            movementType = result.movementType,
        )
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
        val additionalDrafts = (0 until MAX_ACTIONS_PER_TURN - 1)
            .mapNotNull { index ->
                memoryRepository.getDraftByIdempotencyKey(
                    session,
                    idempotencyKey("${userMessage.turnId}-$index"),
                )
            }
            .mapNotNull { stored -> previewFromStoredDraft(session, stored) }
        return AssistantTurnResponse(
            conversationId = userMessage.conversationId,
            turnId = userMessage.turnId,
            status = if (draft == null && additionalDrafts.isEmpty()) {
                AssistantTurnStatus.CLARIFICATION
            } else {
                AssistantTurnStatus.PROPOSAL
            },
            reply = assistantMessage.content,
            userMessage = userMessage.toTransport(),
            assistantMessage = assistantMessage.toTransport(),
            draft = draft?.let { previewFromStoredDraft(session, it) },
            additionalDrafts = additionalDrafts,
        )
    }

    private suspend fun previewFromStoredDraft(
        session: AuthenticatedUser,
        draft: AssistantCommandDraft,
    ): AssistantDraftPreview? {
        val command = FinancialCommandCodec.decode(draft.commandPayload.toString()).getOrNull()
            ?: return null
        val service = readServiceFactory.create(session)
        val values = command.previewValues(service) ?: return null
        val primary = service.getProduct(values.primaryProductId).product
        val destination = values.destinationProductId
            ?.let { service.getProduct(it).product }
        val primaryName = values.primaryProductName ?: primary?.name ?: return null
        return AssistantDraftPreview(
            id = draft.id,
            commandId = command.commandId,
            version = draft.version,
            commandType = draft.commandType,
            amountMinorUnits = values.amount?.minorUnits,
            currency = values.amount?.currency?.name ?: primary?.currency,
            primaryProductId = values.primaryProductId,
            primaryProductName = primaryName,
            destinationProductId = destination?.id,
            destinationProductName = destination?.name,
            merchant = values.merchant,
            categoryId = values.categoryId,
            occurredAtEpochMillis = values.occurredAtEpochMillis,
            productType = values.productType,
            installmentCount = values.installmentCount,
            annualRateBasisPoints = values.annualRateBasisPoints,
            paymentType = values.paymentType,
            movementType = values.movementType,
            expiresAt = draft.expiresAt.toString(),
        )
    }

    private fun proposalReply(value: Resolution.Proposal): String = when (value.commandType) {
        "record_income" -> "Registraré este ingreso en ${value.primary.name}:"
        "record_expense" -> "Registraré este gasto en ${value.primary.name}:"
        "transfer" -> "Registraré este movimiento de ${value.primary.name} a ${value.destination?.name}:"
        "record_card_purchase" -> "Registraré esta compra con ${value.primary.name}:"
        "record_card_payment" -> "Registraré este pago de ${value.primary.name}:"
        "record_savings_movement" -> "Registraré este movimiento de ${value.primary.name}:"
        "record_loan_payment" -> "Registraré este pago de ${value.primary.name}:"
        "create_product" -> "Preparé el nuevo producto ${value.primary.name}:"
        "update_product" -> "Preparé la actualización de ${value.primary.name}:"
        "archive_product" -> "Preparé el archivo de ${value.primary.name}:"
        "update_transaction" -> "Preparé la actualización del movimiento:"
        "delete_transaction" -> "Preparé la eliminación del movimiento:"
        else -> "Preparé esta acción:"
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
        commandId = commandId,
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
        productType = productType,
        installmentCount = installmentCount,
        annualRateBasisPoints = annualRateBasisPoints,
        paymentType = paymentType,
        movementType = movementType,
        expiresAt = draft.expiresAt.toString(),
    )

    private sealed interface Resolution {
        data class Clarification(val message: String) : Resolution
        data class Conversation(val message: String) : Resolution
        data class Unsupported(val message: String?) : Resolution
        data class Proposal(
            val commandId: String,
            val commandType: String,
            val commandPayload: JsonObject,
            val financialStateVersion: Long,
            val amountMinorUnits: Long?,
            val currency: String,
            val primary: ProposalProduct,
            val destination: ProposalProduct?,
            val merchant: String?,
            val categoryId: String?,
            val occurredAtEpochMillis: Long,
            val productType: String? = null,
            val installmentCount: Int? = null,
            val annualRateBasisPoints: Int? = null,
            val paymentType: String? = null,
            val movementType: String? = null,
        ) : Resolution
    }

    private data class CommandPreviewValues(
        val primaryProductId: String,
        val primaryProductName: String? = null,
        val destinationProductId: String?,
        val amount: Money?,
        val merchant: String?,
        val categoryId: String?,
        val occurredAtEpochMillis: Long,
        val productType: String? = null,
        val installmentCount: Int? = null,
        val annualRateBasisPoints: Int? = null,
        val paymentType: String? = null,
        val movementType: String? = null,
    )

    private suspend fun FinancialCommand.previewValues(
        service: FinancialReadService,
    ): CommandPreviewValues? = when (this) {
        is FinancialCommand.RecordIncome -> CommandPreviewValues(
            productId, null, null, amount, merchant, categoryId, occurredAtEpochMillis,
        )
        is FinancialCommand.RecordExpense -> CommandPreviewValues(
            productId, null, null, amount, merchant, categoryId, occurredAtEpochMillis,
        )
        is FinancialCommand.Transfer -> CommandPreviewValues(
            sourceProductId,
            null,
            destinationProductId,
            amount,
            merchant,
            categoryId,
            occurredAtEpochMillis,
        )
        is FinancialCommand.CreateProduct -> CommandPreviewValues(
            primaryProductId = account.id,
            primaryProductName = account.name,
            destinationProductId = null,
            amount = account.openingBalance,
            merchant = null,
            categoryId = null,
            occurredAtEpochMillis = configuration.openedAtOrNow(clock.millis()),
            productType = account.type.name,
            annualRateBasisPoints = configuration.annualRateBasisPoints(),
        )
        is FinancialCommand.UpdateProduct -> CommandPreviewValues(
            primaryProductId = account.id,
            primaryProductName = account.name,
            destinationProductId = null,
            amount = account.openingBalance,
            merchant = null,
            categoryId = null,
            occurredAtEpochMillis = clock.millis(),
            productType = account.type.name,
            annualRateBasisPoints = configuration.annualRateBasisPoints(),
        )
        is FinancialCommand.ArchiveProduct -> CommandPreviewValues(
            primaryProductId = productId,
            destinationProductId = null,
            amount = null,
            merchant = null,
            categoryId = null,
            occurredAtEpochMillis = clock.millis(),
        )
        is FinancialCommand.RecordCardPurchase -> CommandPreviewValues(
            primaryProductId = cardId,
            destinationProductId = null,
            amount = principal,
            merchant = merchant,
            categoryId = categoryId,
            occurredAtEpochMillis = purchasedAtEpochMillis,
            installmentCount = installmentCount,
        )
        is FinancialCommand.RecordCardPayment -> {
            val card = service.getProduct(cardId).product ?: return null
            val resolvedAmount = amount ?: Money(
                when (paymentType) {
                    com.pocketmind.shared.domain.model.DebtPaymentType.SCHEDULED_INSTALLMENT ->
                        card.nextPayment?.minorUnits ?: 0
                    com.pocketmind.shared.domain.model.DebtPaymentType.FULL_BALANCE ->
                        card.currentDebt?.minorUnits ?: 0
                    else -> 0
                },
                com.pocketmind.shared.domain.model.CurrencyCode.valueOf(card.currency),
            )
            CommandPreviewValues(
                primaryProductId = cardId,
                destinationProductId = sourceProductId,
                amount = resolvedAmount,
                merchant = null,
                categoryId = TransactionCategoryId.DEBT_PAYMENT.name,
                occurredAtEpochMillis = paidAtEpochMillis,
                paymentType = paymentType.name,
            )
        }
        is FinancialCommand.RecordSavingsMovement -> CommandPreviewValues(
            primaryProductId = savingsId,
            destinationProductId = destinationProductId ?: sourceProductId,
            amount = amount.takeUnless {
                movementType ==
                    com.pocketmind.shared.domain.model.SavingsMovementType.RATE_CHANGE
            },
            merchant = null,
            categoryId = TransactionCategoryId.SAVINGS.name,
            occurredAtEpochMillis = occurredAtEpochMillis,
            annualRateBasisPoints = annualYieldBasisPoints,
            movementType = movementType.name,
        )
        is FinancialCommand.RecordLoanPayment -> {
            val loan = service.getProduct(loanId).product ?: return null
            val resolvedAmount = amount ?: Money(
                when (paymentType) {
                    com.pocketmind.shared.domain.model.DebtPaymentType.SCHEDULED_INSTALLMENT ->
                        loan.nextPayment?.minorUnits ?: 0
                    com.pocketmind.shared.domain.model.DebtPaymentType.FULL_BALANCE ->
                        loan.currentDebt?.minorUnits ?: 0
                    else -> 0
                },
                com.pocketmind.shared.domain.model.CurrencyCode.valueOf(loan.currency),
            )
            CommandPreviewValues(
                primaryProductId = loanId,
                destinationProductId = sourceProductId,
                amount = resolvedAmount,
                merchant = null,
                categoryId = TransactionCategoryId.DEBT_PAYMENT.name,
                occurredAtEpochMillis = paidAtEpochMillis,
                paymentType = paymentType.name,
            )
        }
        is FinancialCommand.UpdateTransaction -> CommandPreviewValues(
            primaryProductId = productId,
            destinationProductId = relatedProductId,
            amount = amount,
            merchant = merchant,
            categoryId = categoryId,
            occurredAtEpochMillis = occurredAtEpochMillis,
        )
        is FinancialCommand.DeleteTransaction -> {
            val transaction = service.getTransactionById(transactionId) ?: return null
            CommandPreviewValues(
                primaryProductId = transaction.accountId,
                destinationProductId = transaction.relatedAccountId,
                amount = transaction.amount,
                merchant = transaction.merchant,
                categoryId = transaction.categoryId,
                occurredAtEpochMillis = transaction.occurredAtEpochMillis,
            )
        }
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
        const val MAX_ACTIONS_PER_TURN = 5
        val DRAFT_TTL: Duration = Duration.ofMinutes(15)
    }
}

private val FinancialCommand.commandType: String
    get() = when (this) {
        is FinancialCommand.RecordIncome -> "record_income"
        is FinancialCommand.RecordExpense -> "record_expense"
        is FinancialCommand.Transfer -> "transfer"
        is FinancialCommand.CreateProduct -> "create_product"
        is FinancialCommand.UpdateProduct -> "update_product"
        is FinancialCommand.ArchiveProduct -> "archive_product"
        is FinancialCommand.RecordCardPurchase -> "record_card_purchase"
        is FinancialCommand.RecordCardPayment -> "record_card_payment"
        is FinancialCommand.RecordSavingsMovement -> "record_savings_movement"
        is FinancialCommand.RecordLoanPayment -> "record_loan_payment"
        is FinancialCommand.UpdateTransaction -> "update_transaction"
        is FinancialCommand.DeleteTransaction -> "delete_transaction"
    }

private fun FinancialProductConfiguration.annualRateBasisPoints(): Int? = when (this) {
    FinancialProductConfiguration.Standard -> null
    is FinancialProductConfiguration.CreditCard -> profile.annualInterestBasisPoints
    is FinancialProductConfiguration.Savings -> profile.annualYieldBasisPoints.takeUnless {
        profile.type == com.pocketmind.shared.domain.model.SavingsProductType.SIMPLE
    }
    is FinancialProductConfiguration.Loan -> profile.annualInterestBasisPoints
}

private fun FinancialProductConfiguration.openedAtOrNow(now: Long): Long = when (this) {
    FinancialProductConfiguration.Standard -> now
    is FinancialProductConfiguration.CreditCard ->
        profile.openingDebtFirstPaymentAtEpochMillis ?: now
    is FinancialProductConfiguration.Savings -> profile.openedAtEpochMillis
    is FinancialProductConfiguration.Loan -> profile.openedAtEpochMillis
}

private fun String?.normalizedOptional(): String? =
    this?.trim()?.takeIf(String::isNotEmpty)
