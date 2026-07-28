package com.pocketmind.assistant.domain.turn

import com.pocketmind.assistant.agent.chat.AssistantFinancialIntent
import com.pocketmind.assistant.agent.chat.AssistantModelDecision
import com.pocketmind.assistant.domain.finance.FinancialReadService
import com.pocketmind.assistant.domain.finance.ProductDetailsResolution
import com.pocketmind.assistant.domain.finance.ProductSummary
import com.pocketmind.shared.domain.command.FinancialCommand
import com.pocketmind.shared.domain.model.CreditCardProfile
import com.pocketmind.shared.domain.model.CurrencyCode
import com.pocketmind.shared.domain.model.DebtPaymentType
import com.pocketmind.shared.domain.model.FinancialAccount
import com.pocketmind.shared.domain.model.FinancialAccountType
import com.pocketmind.shared.domain.model.FinancialProductConfiguration
import com.pocketmind.shared.domain.model.FinancialTransaction
import com.pocketmind.shared.domain.model.InstallmentRatePeriod
import com.pocketmind.shared.domain.model.LoanProfile
import com.pocketmind.shared.domain.model.Money
import com.pocketmind.shared.domain.model.SavingsMovementType
import com.pocketmind.shared.domain.model.SavingsProductType
import com.pocketmind.shared.domain.model.SavingsProfile
import com.pocketmind.shared.domain.model.TransactionCategoryId
import com.pocketmind.shared.domain.model.TransactionSource
import com.pocketmind.shared.domain.model.TransactionType
import java.nio.charset.StandardCharsets
import java.time.Clock
import java.time.Duration
import java.util.UUID

internal class AssistantProposalResolver(
    private val clock: Clock = Clock.systemUTC(),
) {
    suspend fun resolve(
        decision: AssistantModelDecision,
        readService: FinancialReadService,
        commandId: String,
    ): AssistantProposalResolution {
        val intent = decision.intent
            ?: return AssistantProposalResolution.Clarification(
                "¿Qué acción financiera quieres realizar?",
            )
        return try {
            when (intent) {
                AssistantFinancialIntent.RECORD_INCOME,
                AssistantFinancialIntent.RECORD_EXPENSE,
                AssistantFinancialIntent.TRANSFER,
                -> resolveStandardMovement(intent, decision, readService, commandId)
                AssistantFinancialIntent.CREATE_PRODUCT ->
                    resolveCreateProduct(decision, readService, commandId)
                AssistantFinancialIntent.UPDATE_PRODUCT ->
                    resolveUpdateProduct(decision, readService, commandId)
                AssistantFinancialIntent.ARCHIVE_PRODUCT ->
                    resolveArchiveProduct(decision, readService, commandId)
                AssistantFinancialIntent.RECORD_CARD_PURCHASE ->
                    resolveCardPurchase(decision, readService, commandId)
                AssistantFinancialIntent.RECORD_CARD_PAYMENT ->
                    resolveCardPayment(decision, readService, commandId)
                AssistantFinancialIntent.RECORD_SAVINGS_MOVEMENT ->
                    resolveSavingsMovement(decision, readService, commandId)
                AssistantFinancialIntent.RECORD_LOAN_PAYMENT ->
                    resolveLoanPayment(decision, readService, commandId)
                AssistantFinancialIntent.UPDATE_TRANSACTION ->
                    resolveUpdateTransaction(decision, readService, commandId)
                AssistantFinancialIntent.DELETE_TRANSACTION ->
                    resolveDeleteTransaction(decision, readService, commandId)
            }
        } catch (clarification: ProposalClarificationException) {
            AssistantProposalResolution.Clarification(clarification.publicMessage)
        }
    }

    private suspend fun resolveStandardMovement(
        intent: AssistantFinancialIntent,
        decision: AssistantModelDecision,
        service: FinancialReadService,
        commandId: String,
    ): AssistantProposalResolution {
        val amount = decision.positiveAmount()
            ?: return clarification("¿Cuál es el valor del movimiento?")
        val primary = service.requireProduct(
            decision.primaryProductReference,
            LIQUID_PRODUCT_TYPES,
            "¿En cuál producto ocurrió el movimiento?",
        )
        val money = decision.moneyFor(amount, primary.account.currency)
            ?: return clarification("La moneda indicada no coincide con ${primary.account.name}.")
        val occurredAt = decision.validDateOrNow()
            ?: return clarification("¿En qué fecha ocurrió el movimiento?")
        val category = decision.validCategory()
            ?: if (decision.categoryId == null) null else {
                return clarification("No reconocí la categoría. ¿Cuál quieres usar?")
            }
        val destination = if (intent == AssistantFinancialIntent.TRANSFER) {
            service.requireProduct(
                decision.destinationProductReference,
                LIQUID_PRODUCT_TYPES,
                "¿A cuál producto quieres enviar el dinero?",
            )
        } else {
            null
        }
        if (destination?.account?.id == primary.account.id) {
            return clarification("El origen y el destino deben ser productos diferentes.")
        }
        if (destination != null && destination.account.currency != primary.account.currency) {
            return clarification("La transferencia debe usar productos de la misma moneda.")
        }
        val command = when (intent) {
            AssistantFinancialIntent.RECORD_INCOME -> FinancialCommand.RecordIncome(
                commandId,
                primary.account.id,
                money,
                occurredAt,
                TransactionSource.ASSISTANT_TEXT,
                category,
                decision.merchant.normalized(),
                decision.note.normalized(),
            )
            AssistantFinancialIntent.RECORD_EXPENSE -> FinancialCommand.RecordExpense(
                commandId,
                primary.account.id,
                money,
                occurredAt,
                TransactionSource.ASSISTANT_TEXT,
                category,
                decision.merchant.normalized(),
                decision.note.normalized(),
            )
            AssistantFinancialIntent.TRANSFER -> FinancialCommand.Transfer(
                commandId,
                primary.account.id,
                requireNotNull(destination).account.id,
                money,
                occurredAt,
                TransactionSource.ASSISTANT_TEXT,
                category ?: TransactionCategoryId.TRANSFER.name,
                decision.merchant.normalized(),
                decision.note.normalized(),
            )
            else -> error("Unsupported standard movement intent.")
        }
        return service.proposal(
            command = command,
            primary = primary.toPreviewProduct(),
            destination = destination?.toPreviewProduct(),
            amount = money,
            occurredAt = occurredAt,
            merchant = decision.merchant.normalized(),
            categoryId = category,
        )
    }

    private suspend fun resolveCreateProduct(
        decision: AssistantModelDecision,
        service: FinancialReadService,
        commandId: String,
    ): AssistantProposalResolution {
        val name = decision.productName.normalized()
            ?: return clarification("¿Qué nombre quieres darle al producto?")
        val nameAlreadyExists = service.listProducts(includeArchived = true).products.any {
            it.name.equals(name, ignoreCase = true)
        }
        if (nameAlreadyExists) {
            return clarification(
                "Ya existe un producto llamado \"$name\". Usa otro nombre o edita el existente.",
            )
        }
        val type = decision.productType.toProductType()
            ?: return clarification(
                "¿Es efectivo, cuenta bancaria, ahorro, tarjeta de crédito o préstamo?",
            )
        val currency = decision.currency.toCurrencyOrDefault()
            ?: return clarification("¿Qué moneda usa el producto?")
        val openingBalance = decision.amountMinorUnits ?: 0
        if (openingBalance < 0) return clarification("El saldo inicial no puede ser negativo.")
        val productId = stableUuid("assistant-product:$commandId")
        val account = runCatching {
            FinancialAccount(
                id = productId,
                name = name,
                type = type,
                currency = currency,
                openingBalance = Money(openingBalance, currency),
                aliases = decision.aliases.normalizedAliases(),
            )
        }.getOrElse {
            return clarification("Revisa el nombre y los alias del producto.")
        }
        val configuration = decision.createConfiguration(account)
            ?: return configurationClarification(type, decision)
        return service.proposal(
            command = FinancialCommand.CreateProduct(commandId, account, configuration),
            primary = account.toPreviewProduct(),
            amount = account.openingBalance,
            occurredAt = decision.openedAtEpochMillis ?: clock.millis(),
            productType = type.name,
            annualRateBasisPoints = configuration.annualRateBasisPoints(),
        )
    }

    private suspend fun resolveUpdateProduct(
        decision: AssistantModelDecision,
        service: FinancialReadService,
        commandId: String,
    ): AssistantProposalResolution {
        val existing = service.requireProduct(
            decision.primaryProductReference,
            FinancialAccountType.entries.toSet(),
            "¿Cuál producto quieres editar?",
        )
        val requestedType = decision.productType.toProductType()
        if (decision.productType != null && requestedType == null) {
            return clarification("No reconocí el tipo de producto indicado.")
        }
        if (requestedType != null && requestedType != existing.account.type) {
            return clarification("El tipo de un producto existente no se puede cambiar.")
        }
        val requestedCurrency = decision.currency?.toCurrencyOrNull()
        if (decision.currency != null && requestedCurrency == null) {
            return clarification("No reconocí la moneda indicada.")
        }
        val currency = requestedCurrency ?: existing.account.currency
        if (currency != existing.account.currency) {
            return clarification("La moneda de un producto existente no se puede cambiar.")
        }
        if (decision.amountMinorUnits != null && decision.amountMinorUnits < 0) {
            return clarification("El saldo inicial no puede ser negativo.")
        }
        val openingBalance = decision.amountMinorUnits
            ?.takeIf { it >= 0 }
            ?: existing.account.openingBalance.minorUnits
        val account = runCatching {
            existing.account.copy(
                name = decision.productName.normalized() ?: existing.account.name,
                openingBalance = Money(openingBalance, currency),
                aliases = decision.aliases
                    .takeIf { it.isNotEmpty() }
                    ?.normalizedAliases()
                    ?: existing.account.aliases,
            )
        }.getOrElse {
            return clarification("Revisa el nombre, saldo o alias que quieres actualizar.")
        }
        val configuration = decision.mergeConfiguration(existing.configuration, account)
            ?: return configurationClarification(account.type, decision)
        return service.proposal(
            command = FinancialCommand.UpdateProduct(commandId, account, configuration),
            primary = account.toPreviewProduct(),
            amount = account.openingBalance,
            occurredAt = clock.millis(),
            productType = account.type.name,
            annualRateBasisPoints = configuration.annualRateBasisPoints(),
        )
    }

    private suspend fun resolveArchiveProduct(
        decision: AssistantModelDecision,
        service: FinancialReadService,
        commandId: String,
    ): AssistantProposalResolution {
        val product = service.requireProduct(
            decision.primaryProductReference,
            FinancialAccountType.entries.toSet(),
            "¿Cuál producto quieres archivar?",
        )
        return service.proposal(
            command = FinancialCommand.ArchiveProduct(commandId, product.account.id),
            primary = product.toPreviewProduct(),
            amount = null,
            occurredAt = clock.millis(),
            productType = product.account.type.name,
        )
    }

    private suspend fun resolveCardPurchase(
        decision: AssistantModelDecision,
        service: FinancialReadService,
        commandId: String,
    ): AssistantProposalResolution {
        val card = service.requireProduct(
            decision.primaryProductReference,
            setOf(FinancialAccountType.CREDIT_CARD),
            "¿Con cuál tarjeta hiciste la compra?",
        )
        val amount = decision.positiveAmount()
            ?: return clarification("¿Cuál fue el valor de la compra?")
        val money = decision.moneyFor(amount, card.account.currency)
            ?: return clarification("La moneda indicada no coincide con ${card.account.name}.")
        val merchant = decision.merchant.normalized()
            ?: return clarification("¿Qué compraste o en qué comercio fue?")
        val installments = decision.installmentCount
            ?.takeIf { it in 1..60 }
            ?: return clarification("¿A cuántas cuotas hiciste la compra?")
        val periods = runCatching {
            decision.promotionalRatePeriods.map {
                InstallmentRatePeriod(
                    firstInstallment = it.firstInstallment,
                    lastInstallment = it.lastInstallment,
                    annualInterestBasisPoints = it.annualInterestBasisPoints,
                )
            }
        }.getOrElse {
            return clarification("Revisa las cuotas promocionales sin interés.")
        }
        val coveredInstallments = periods.flatMap {
            it.firstInstallment..it.lastInstallment
        }
        if (
            periods.any { it.lastInstallment > installments } ||
            coveredInstallments.distinct().size != coveredInstallments.size
        ) {
            return clarification("Las cuotas promocionales deben estar dentro del plazo y no superponerse.")
        }
        val occurredAt = decision.validDateOrNow()
            ?: return clarification("¿En qué fecha hiciste la compra?")
        val category = decision.validCategory()
            ?: if (decision.categoryId == null) null else {
                return clarification("No reconocí la categoría de la compra.")
            }
        val command = FinancialCommand.RecordCardPurchase(
            commandId = commandId,
            cardId = card.account.id,
            merchant = merchant,
            principal = money,
            installmentCount = installments,
            purchasedAtEpochMillis = occurredAt,
            source = TransactionSource.ASSISTANT_TEXT,
            categoryId = category,
            note = decision.note.normalized(),
            promotionalRatePeriods = periods,
        )
        return service.proposal(
            command = command,
            primary = card.toPreviewProduct(),
            amount = money,
            occurredAt = occurredAt,
            merchant = merchant,
            categoryId = category,
            installmentCount = installments,
            annualRateBasisPoints = (card.configuration as FinancialProductConfiguration.CreditCard)
                .profile.annualInterestBasisPoints,
        )
    }

    private suspend fun resolveCardPayment(
        decision: AssistantModelDecision,
        service: FinancialReadService,
        commandId: String,
    ): AssistantProposalResolution {
        val card = service.requireProduct(
            decision.primaryProductReference,
            setOf(FinancialAccountType.CREDIT_CARD),
            "¿Cuál tarjeta quieres pagar?",
        )
        val requestedPaymentType = decision.paymentType.toPaymentType()
        if (decision.paymentType != null && requestedPaymentType == null) {
            return clarification("No reconocí el tipo de pago de la tarjeta.")
        }
        val paymentType = requestedPaymentType ?: DebtPaymentType.CUSTOM
        val amount = decision.amountMinorUnits?.takeIf { it > 0 }
        if (
            amount == null &&
            paymentType in setOf(DebtPaymentType.EXTRA_PRINCIPAL, DebtPaymentType.CUSTOM)
        ) {
            return clarification("¿Cuánto quieres abonar a la tarjeta?")
        }
        val money = amount?.let {
            decision.moneyFor(it, card.account.currency)
                ?: return clarification("La moneda indicada no coincide con ${card.account.name}.")
        }
        val derivedAmount = when (paymentType) {
            DebtPaymentType.SCHEDULED_INSTALLMENT ->
                card.summary.nextPayment?.minorUnits
            DebtPaymentType.FULL_BALANCE ->
                card.summary.currentDebt?.minorUnits
            else -> null
        }
        if (money == null && (derivedAmount == null || derivedAmount <= 0)) {
            return clarification("${card.account.name} no tiene un valor pendiente para ese pago.")
        }
        val source = service.resolveOptionalFundingProduct(decision.sourceProductReference)
        val occurredAt = decision.validDateOrNow()
            ?: return clarification("¿En qué fecha hiciste el pago?")
        val command = FinancialCommand.RecordCardPayment(
            commandId = commandId,
            cardId = card.account.id,
            amount = money,
            paymentType = paymentType,
            paidAtEpochMillis = occurredAt,
            source = TransactionSource.ASSISTANT_TEXT,
            sourceProductId = source?.account?.id,
            note = decision.note.normalized(),
        )
        return service.proposal(
            command = command,
            primary = card.toPreviewProduct(),
            destination = source?.toPreviewProduct(),
            amount = money ?: Money(requireNotNull(derivedAmount), card.account.currency),
            occurredAt = occurredAt,
            paymentType = paymentType.name,
        )
    }

    private suspend fun resolveSavingsMovement(
        decision: AssistantModelDecision,
        service: FinancialReadService,
        commandId: String,
    ): AssistantProposalResolution {
        val savings = service.requireProduct(
            decision.primaryProductReference,
            setOf(FinancialAccountType.SAVINGS),
            "¿En cuál ahorro quieres hacer el movimiento?",
        )
        val movementType = decision.savingsMovementType.toSavingsMovementType()
            ?: return clarification("¿Quieres aportar, retirar o cambiar la tasa del ahorro?")
        val occurredAt = decision.validDateOrNow()
            ?: return clarification("¿En qué fecha ocurrió el movimiento?")
        val money = if (movementType == SavingsMovementType.RATE_CHANGE) {
            Money(0, savings.account.currency)
        } else {
            val amount = decision.positiveAmount()
                ?: return clarification(
                    if (movementType == SavingsMovementType.DEPOSIT) {
                        "¿Cuánto quieres agregar al ahorro?"
                    } else {
                        "¿Cuánto retiraste del ahorro?"
                    },
                )
            decision.moneyFor(amount, savings.account.currency)
                ?: return clarification("La moneda indicada no coincide con ${savings.account.name}.")
        }
        val annualRate = if (movementType == SavingsMovementType.RATE_CHANGE) {
            decision.annualRateBasisPoints
                ?.takeIf { it >= 0 }
                ?: return clarification("¿Cuál es la nueva tasa efectiva anual?")
        } else {
            null
        }
        val relatedReference = when (movementType) {
            SavingsMovementType.DEPOSIT -> decision.sourceProductReference
            SavingsMovementType.WITHDRAWAL ->
                decision.destinationProductReference ?: decision.sourceProductReference
            SavingsMovementType.RATE_CHANGE -> null
        }
        val related = service.resolveOptionalFundingProduct(relatedReference)
        if (related?.account?.id == savings.account.id) {
            return clarification("El origen o destino debe ser diferente al ahorro principal.")
        }
        val command = FinancialCommand.RecordSavingsMovement(
            commandId = commandId,
            savingsId = savings.account.id,
            movementType = movementType,
            amount = money,
            occurredAtEpochMillis = occurredAt,
            source = TransactionSource.ASSISTANT_TEXT,
            sourceProductId = related?.account?.id
                ?.takeIf { movementType == SavingsMovementType.DEPOSIT },
            destinationProductId = related?.account?.id
                ?.takeIf { movementType == SavingsMovementType.WITHDRAWAL },
            annualYieldBasisPoints = annualRate,
            note = decision.note.normalized(),
        )
        return service.proposal(
            command = command,
            primary = savings.toPreviewProduct(),
            destination = related?.toPreviewProduct(),
            amount = money.takeUnless { movementType == SavingsMovementType.RATE_CHANGE },
            occurredAt = occurredAt,
            annualRateBasisPoints = annualRate,
            movementType = movementType.name,
        )
    }

    private suspend fun resolveLoanPayment(
        decision: AssistantModelDecision,
        service: FinancialReadService,
        commandId: String,
    ): AssistantProposalResolution {
        val loan = service.requireProduct(
            decision.primaryProductReference,
            setOf(FinancialAccountType.LOAN),
            "¿Cuál préstamo quieres pagar?",
        )
        val requestedPaymentType = decision.paymentType.toPaymentType()
        if (decision.paymentType != null && requestedPaymentType == null) {
            return clarification("No reconocí el tipo de pago del préstamo.")
        }
        val paymentType = requestedPaymentType ?: DebtPaymentType.CUSTOM
        val amount = decision.amountMinorUnits?.takeIf { it > 0 }
        if (
            amount == null &&
            paymentType in setOf(DebtPaymentType.EXTRA_PRINCIPAL, DebtPaymentType.CUSTOM)
        ) {
            return clarification("¿Cuánto quieres abonar al préstamo?")
        }
        val money = amount?.let {
            decision.moneyFor(it, loan.account.currency)
                ?: return clarification("La moneda indicada no coincide con ${loan.account.name}.")
        }
        val derivedAmount = when (paymentType) {
            DebtPaymentType.SCHEDULED_INSTALLMENT ->
                loan.summary.nextPayment?.minorUnits
            DebtPaymentType.FULL_BALANCE ->
                loan.summary.currentDebt?.minorUnits
            else -> null
        }
        if (money == null && (derivedAmount == null || derivedAmount <= 0)) {
            return clarification("${loan.account.name} no tiene un valor pendiente para ese pago.")
        }
        val source = service.resolveOptionalFundingProduct(decision.sourceProductReference)
        val occurredAt = decision.validDateOrNow()
            ?: return clarification("¿En qué fecha hiciste el pago?")
        val command = FinancialCommand.RecordLoanPayment(
            commandId = commandId,
            loanId = loan.account.id,
            amount = money,
            paymentType = paymentType,
            paidAtEpochMillis = occurredAt,
            source = TransactionSource.ASSISTANT_TEXT,
            sourceProductId = source?.account?.id,
            note = decision.note.normalized(),
        )
        return service.proposal(
            command = command,
            primary = loan.toPreviewProduct(),
            destination = source?.toPreviewProduct(),
            amount = money ?: Money(requireNotNull(derivedAmount), loan.account.currency),
            occurredAt = occurredAt,
            paymentType = paymentType.name,
        )
    }

    private suspend fun resolveUpdateTransaction(
        decision: AssistantModelDecision,
        service: FinancialReadService,
        commandId: String,
    ): AssistantProposalResolution {
        val existing = decision.transactionId.normalized()
            ?.let { service.getTransactionById(it) }
            ?: return clarification(
                "No pude identificar el movimiento que quieres editar. " +
                    "Puedes indicar su identificador desde el historial.",
            )
        if (existing.isLinkedProductTransaction()) {
            return clarification(
                "Ese movimiento pertenece a una tarjeta, ahorro o préstamo. " +
                    "Corrígelo desde la operación del producto.",
            )
        }
        val product = service.requireProduct(
            decision.primaryProductReference ?: existing.accountId,
            LIQUID_PRODUCT_TYPES,
            "¿En cuál producto debe quedar el movimiento?",
        )
        val requestedType = decision.transactionType.toTransactionType()
        if (decision.transactionType != null && requestedType == null) {
            return clarification("No reconocí el tipo de movimiento indicado.")
        }
        val type = requestedType ?: existing.type
        val amount = decision.amountMinorUnits ?: existing.amount.minorUnits
        if (amount <= 0) return clarification("El valor del movimiento debe ser mayor que cero.")
        val money = decision.moneyFor(amount, product.account.currency)
            ?: return clarification("La moneda indicada no coincide con ${product.account.name}.")
        val related = if (type == TransactionType.TRANSFER) {
            val reference = when {
                decision.clearRelatedProduct -> null
                decision.destinationProductReference != null ->
                    decision.destinationProductReference
                else -> existing.relatedAccountId
            } ?: return clarification("¿A cuál producto corresponde la transferencia?")
            service.requireProduct(
                reference,
                LIQUID_PRODUCT_TYPES,
                "¿A cuál producto corresponde la transferencia?",
            )
        } else {
            null
        }
        if (related?.account?.id == product.account.id) {
            return clarification("El origen y el destino deben ser productos diferentes.")
        }
        if (related != null && related.account.currency != product.account.currency) {
            return clarification("La transferencia debe usar productos de la misma moneda.")
        }
        val category = when {
            decision.clearCategory -> null
            decision.categoryId != null -> decision.validCategory()
                ?: return clarification("No reconocí la categoría nueva.")
            else -> existing.categoryId
        }
        val occurredAt = decision.occurredAtEpochMillis
            ?.takeIf { it > 0 && it <= clock.millis() + MAX_FUTURE_SKEW.toMillis() }
            ?: if (decision.occurredAtEpochMillis == null) {
                existing.occurredAtEpochMillis
            } else {
                return clarification("La fecha indicada no es válida.")
            }
        val command = FinancialCommand.UpdateTransaction(
            commandId = commandId,
            transactionId = existing.id,
            productId = product.account.id,
            type = type,
            amount = money,
            occurredAtEpochMillis = occurredAt,
            source = TransactionSource.ASSISTANT_TEXT,
            categoryId = category,
            merchant = when {
                decision.clearMerchant -> null
                decision.merchant != null -> decision.merchant.normalized()
                else -> existing.merchant
            },
            note = when {
                decision.clearNote -> null
                decision.note != null -> decision.note.normalized()
                else -> existing.note
            },
            relatedProductId = related?.account?.id,
        )
        return service.proposal(
            command = command,
            primary = product.toPreviewProduct(),
            destination = related?.toPreviewProduct(),
            amount = money,
            occurredAt = command.occurredAtEpochMillis,
            merchant = command.merchant,
            categoryId = command.categoryId,
        )
    }

    private suspend fun resolveDeleteTransaction(
        decision: AssistantModelDecision,
        service: FinancialReadService,
        commandId: String,
    ): AssistantProposalResolution {
        val existing = decision.transactionId.normalized()
            ?.let { service.getTransactionById(it) }
            ?: return clarification(
                "No pude identificar el movimiento que quieres eliminar. " +
                    "Puedes indicar su identificador desde el historial.",
            )
        if (existing.isLinkedProductTransaction()) {
            return clarification(
                "Ese movimiento pertenece a una tarjeta, ahorro o préstamo y no puede eliminarse por separado.",
            )
        }
        val product = service.requireProduct(
            existing.accountId,
            FinancialAccountType.entries.toSet(),
            "No pude encontrar el producto del movimiento.",
        )
        return service.proposal(
            command = FinancialCommand.DeleteTransaction(commandId, existing.id),
            primary = product.toPreviewProduct(),
            amount = existing.amount,
            occurredAt = existing.occurredAtEpochMillis,
            merchant = existing.merchant,
            categoryId = existing.categoryId,
        )
    }

    private suspend fun FinancialReadService.proposal(
        command: FinancialCommand,
        primary: ProposalProduct,
        amount: Money?,
        occurredAt: Long,
        destination: ProposalProduct? = null,
        merchant: String? = null,
        categoryId: String? = null,
        productType: String? = null,
        installmentCount: Int? = null,
        annualRateBasisPoints: Int? = null,
        paymentType: String? = null,
        movementType: String? = null,
    ): AssistantProposalResolution.Proposal {
        val stateVersion = getOverview().metadata.stateVersion
        return AssistantProposalResolution.Proposal(
            command = command,
            financialStateVersion = stateVersion,
            primary = primary,
            destination = destination,
            amount = amount,
            merchant = merchant,
            categoryId = categoryId,
            occurredAtEpochMillis = occurredAt,
            productType = productType,
            installmentCount = installmentCount,
            annualRateBasisPoints = annualRateBasisPoints,
            paymentType = paymentType,
            movementType = movementType,
        )
    }

    private suspend fun FinancialReadService.requireProduct(
        reference: String?,
        allowedTypes: Set<FinancialAccountType>,
        missingMessage: String,
    ): ProductRequirement.Ready {
        val normalized = reference.normalized()
        if (normalized == null) {
            throw ProposalClarificationException(missingMessage)
        }
        return when (val resolution = resolveProductDetails(normalized, allowedTypes)) {
            is ProductDetailsResolution.Resolved -> {
                if (resolution.account.isArchived) {
                    throw ProposalClarificationException(
                        "\"${resolution.account.name}\" está archivado. Actívalo desde Productos.",
                    )
                }
                ProductRequirement.Ready(
                    account = resolution.account,
                    configuration = resolution.configuration,
                    summary = getProduct(resolution.account.id).product
                        ?: ProductSummary(
                            id = resolution.account.id,
                            name = resolution.account.name,
                            type = resolution.account.type.name,
                            currency = resolution.account.currency.name,
                            aliases = resolution.account.aliases,
                            isArchived = resolution.account.isArchived,
                            dataStatus = "complete",
                        ),
                )
            }
            is ProductDetailsResolution.Ambiguous -> throw ProposalClarificationException(
                "Encontré varios productos para \"$normalized\": " +
                    resolution.candidates.joinToString { it.name } +
                    ". ¿Cuál quieres usar?",
            )
            ProductDetailsResolution.NotFound -> throw ProposalClarificationException(
                "No pude identificar el producto \"$normalized\". ¿Puedes usar su nombre completo?",
            )
            ProductDetailsResolution.InvalidConfiguration -> throw ProposalClarificationException(
                "El producto \"$normalized\" tiene datos incompletos. Revísalo antes de continuar.",
            )
        }
    }

    private suspend fun FinancialReadService.resolveOptionalFundingProduct(
        reference: String?,
    ): ProductRequirement.Ready? {
        if (reference.normalized() == null) return null
        return requireProduct(
            reference,
            FUNDING_PRODUCT_TYPES,
            "¿Desde cuál producto saldrá el dinero?",
        )
    }

    private fun AssistantModelDecision.createConfiguration(
        account: FinancialAccount,
    ): FinancialProductConfiguration? = runCatching {
        when (account.type) {
            FinancialAccountType.CASH,
            FinancialAccountType.BANK_ACCOUNT,
            -> FinancialProductConfiguration.Standard
            FinancialAccountType.CREDIT_CARD -> FinancialProductConfiguration.CreditCard(
                CreditCardProfile(
                    accountId = account.id,
                    creditLimit = Money(requireNotNull(creditLimitMinorUnits), account.currency),
                    annualInterestBasisPoints = requireNotNull(annualRateBasisPoints),
                    statementClosingDay = requireNotNull(statementClosingDay),
                    paymentDueDay = requireNotNull(paymentDueDay),
                    openingDebtInstallmentCount = openingDebtInstallmentCount ?: 1,
                    openingDebtFirstPaymentAtEpochMillis = firstPaymentAtEpochMillis,
                ),
            )
            FinancialAccountType.SAVINGS -> {
                val savingsType = requireNotNull(resolvedSavingsProductType())
                FinancialProductConfiguration.Savings(
                    SavingsProfile(
                        accountId = account.id,
                        type = savingsType,
                        annualYieldBasisPoints = if (savingsType == SavingsProductType.SIMPLE) {
                            0
                        } else {
                            requireNotNull(annualRateBasisPoints)
                        },
                        openedAtEpochMillis = openedAtEpochMillis ?: clock.millis(),
                        maturityAtEpochMillis = if (
                            savingsType == SavingsProductType.TERM_DEPOSIT
                        ) {
                            requireNotNull(maturityAtEpochMillis)
                        } else {
                            maturityAtEpochMillis
                        },
                    ),
                )
            }
            FinancialAccountType.LOAN -> FinancialProductConfiguration.Loan(
                LoanProfile(
                    accountId = account.id,
                    annualInterestBasisPoints = requireNotNull(annualRateBasisPoints),
                    monthlyPayment = Money(
                        requireNotNull(monthlyPaymentMinorUnits),
                        account.currency,
                    ),
                    paymentDueDay = requireNotNull(paymentDueDay),
                    openedAtEpochMillis = openedAtEpochMillis ?: clock.millis(),
                ),
            )
        }
    }.getOrNull()

    private fun AssistantModelDecision.mergeConfiguration(
        existing: FinancialProductConfiguration,
        account: FinancialAccount,
    ): FinancialProductConfiguration? = runCatching {
        when (existing) {
            FinancialProductConfiguration.Standard -> existing
            is FinancialProductConfiguration.CreditCard -> {
                val profile = existing.profile
                FinancialProductConfiguration.CreditCard(
                    profile.copy(
                        accountId = account.id,
                        creditLimit = Money(
                            creditLimitMinorUnits ?: profile.creditLimit.minorUnits,
                            account.currency,
                        ),
                        annualInterestBasisPoints =
                            annualRateBasisPoints ?: profile.annualInterestBasisPoints,
                        statementClosingDay =
                            statementClosingDay ?: profile.statementClosingDay,
                        paymentDueDay = paymentDueDay ?: profile.paymentDueDay,
                        openingDebtInstallmentCount =
                            openingDebtInstallmentCount ?: profile.openingDebtInstallmentCount,
                        openingDebtFirstPaymentAtEpochMillis =
                            firstPaymentAtEpochMillis
                                ?: profile.openingDebtFirstPaymentAtEpochMillis,
                    ),
                )
            }
            is FinancialProductConfiguration.Savings -> {
                val profile = existing.profile
                val type = resolvedSavingsProductType() ?: profile.type
                FinancialProductConfiguration.Savings(
                    profile.copy(
                        accountId = account.id,
                        type = type,
                        annualYieldBasisPoints = if (type == SavingsProductType.SIMPLE) {
                            0
                        } else {
                            annualRateBasisPoints ?: profile.annualYieldBasisPoints
                        },
                        openedAtEpochMillis = openedAtEpochMillis
                            ?: profile.openedAtEpochMillis,
                        maturityAtEpochMillis = if (type == SavingsProductType.SIMPLE) {
                            null
                        } else {
                            maturityAtEpochMillis ?: profile.maturityAtEpochMillis
                        },
                    ),
                )
            }
            is FinancialProductConfiguration.Loan -> {
                val profile = existing.profile
                FinancialProductConfiguration.Loan(
                    profile.copy(
                        accountId = account.id,
                        annualInterestBasisPoints =
                            annualRateBasisPoints ?: profile.annualInterestBasisPoints,
                        monthlyPayment = Money(
                            monthlyPaymentMinorUnits ?: profile.monthlyPayment.minorUnits,
                            account.currency,
                        ),
                        paymentDueDay = paymentDueDay ?: profile.paymentDueDay,
                        openedAtEpochMillis = openedAtEpochMillis ?: profile.openedAtEpochMillis,
                    ),
                )
            }
        }
    }.getOrNull()

    private fun configurationClarification(
        type: FinancialAccountType,
        decision: AssistantModelDecision,
    ): AssistantProposalResolution.Clarification = clarification(
        when (type) {
            FinancialAccountType.CASH,
            FinancialAccountType.BANK_ACCOUNT,
            -> "Revisa los datos del producto."
            FinancialAccountType.CREDIT_CARD -> when {
                decision.creditLimitMinorUnits == null -> "¿Cuál es el cupo de la tarjeta?"
                decision.annualRateBasisPoints == null -> "¿Cuál es la tasa efectiva anual?"
                decision.statementClosingDay == null -> "¿Qué día es el corte de la tarjeta?"
                decision.paymentDueDay == null -> "¿Qué día vence el pago?"
                else -> "Revisa la configuración de la tarjeta."
            }
            FinancialAccountType.SAVINGS -> when {
                decision.resolvedSavingsProductType() == null ->
                    "¿Es un ahorro simple, una cajita o un CDT?"
                decision.resolvedSavingsProductType() != SavingsProductType.SIMPLE &&
                    decision.annualRateBasisPoints == null ->
                    "¿Cuál es el rendimiento efectivo anual?"
                decision.resolvedSavingsProductType() == SavingsProductType.TERM_DEPOSIT &&
                    decision.maturityAtEpochMillis == null ->
                    "¿En qué fecha vence el CDT?"
                else -> "Revisa la configuración del ahorro."
            }
            FinancialAccountType.LOAN -> when {
                decision.annualRateBasisPoints == null -> "¿Cuál es la tasa efectiva anual?"
                decision.monthlyPaymentMinorUnits == null -> "¿Cuál es el valor de la cuota?"
                decision.paymentDueDay == null -> "¿Qué día debes pagar el préstamo?"
                else -> "Revisa la configuración del préstamo."
            }
        },
    )

    private fun AssistantModelDecision.positiveAmount(): Long? =
        amountMinorUnits?.takeIf { it > 0 }

    private fun AssistantModelDecision.moneyFor(
        amount: Long,
        productCurrency: CurrencyCode,
    ): Money? {
        val requested = currency?.toCurrencyOrNull() ?: productCurrency
        return Money(amount, requested).takeIf { requested == productCurrency }
    }

    private fun AssistantModelDecision.validDateOrNow(): Long? {
        val value = occurredAtEpochMillis ?: clock.millis()
        return value.takeIf {
            it > 0 && it <= clock.millis() + MAX_FUTURE_SKEW.toMillis()
        }
    }

    private fun AssistantModelDecision.validCategory(): String? =
        categoryId?.trim()?.uppercase()?.let { value ->
            TransactionCategoryId.entries.firstOrNull { it.name == value }?.name
        }

    private fun ProductRequirement.Ready.toPreviewProduct(): ProposalProduct =
        account.toPreviewProduct()

    private fun FinancialAccount.toPreviewProduct(): ProposalProduct = ProposalProduct(
        id = id,
        name = name,
        type = type.name,
        currency = currency.name,
    )

    private fun FinancialProductConfiguration.annualRateBasisPoints(): Int? = when (this) {
        FinancialProductConfiguration.Standard -> null
        is FinancialProductConfiguration.CreditCard -> profile.annualInterestBasisPoints
        is FinancialProductConfiguration.Savings -> profile.annualYieldBasisPoints.takeUnless {
            profile.type == SavingsProductType.SIMPLE
        }
        is FinancialProductConfiguration.Loan -> profile.annualInterestBasisPoints
    }

    private fun String?.toProductType(): FinancialAccountType? = when (
        this.normalizedKey()
    ) {
        "CASH", "EFECTIVO" -> FinancialAccountType.CASH
        "BANK_ACCOUNT", "CUENTA", "CUENTA_BANCARIA" -> FinancialAccountType.BANK_ACCOUNT
        "SAVINGS", "AHORRO", "CAJITA", "CDT" -> FinancialAccountType.SAVINGS
        "CREDIT_CARD", "TARJETA", "TARJETA_CREDITO" -> FinancialAccountType.CREDIT_CARD
        "LOAN", "PRESTAMO", "CREDITO" -> FinancialAccountType.LOAN
        else -> null
    }

    private fun String?.toSavingsProductType(): SavingsProductType? = when (
        this.normalizedKey()
    ) {
        "SIMPLE", "AHORRO_SIMPLE" -> SavingsProductType.SIMPLE
        "POCKET", "CAJITA", "BOLSILLO" -> SavingsProductType.POCKET
        "TERM_DEPOSIT", "CDT", "DEPOSITO_TERMINO" -> SavingsProductType.TERM_DEPOSIT
        else -> null
    }

    private fun AssistantModelDecision.resolvedSavingsProductType(): SavingsProductType? =
        savingsProductType.toSavingsProductType() ?: productType.toSavingsProductType()

    private fun String?.toSavingsMovementType(): SavingsMovementType? = when (
        this.normalizedKey()
    ) {
        "DEPOSIT", "DEPOSITO", "APORTE", "AGREGAR" -> SavingsMovementType.DEPOSIT
        "WITHDRAWAL", "RETIRO", "SACAR" -> SavingsMovementType.WITHDRAWAL
        "RATE_CHANGE", "CAMBIO_TASA", "TASA" -> SavingsMovementType.RATE_CHANGE
        else -> null
    }

    private fun String?.toPaymentType(): DebtPaymentType? = when (this.normalizedKey()) {
        "SCHEDULED_INSTALLMENT", "CUOTA", "CUOTA_PROGRAMADA" ->
            DebtPaymentType.SCHEDULED_INSTALLMENT
        "FULL_BALANCE", "SALDAR", "PAGO_TOTAL" -> DebtPaymentType.FULL_BALANCE
        "EXTRA_PRINCIPAL", "ABONO_CAPITAL", "ABONO" -> DebtPaymentType.EXTRA_PRINCIPAL
        "CUSTOM", "PERSONALIZADO" -> DebtPaymentType.CUSTOM
        else -> null
    }

    private fun String?.toTransactionType(): TransactionType? = when (this.normalizedKey()) {
        "INCOME", "INGRESO" -> TransactionType.INCOME
        "EXPENSE", "GASTO" -> TransactionType.EXPENSE
        "TRANSFER", "TRANSFERENCIA" -> TransactionType.TRANSFER
        else -> null
    }

    private fun String?.toCurrencyOrDefault(): CurrencyCode? =
        if (isNullOrBlank()) CurrencyCode.COP else toCurrencyOrNull()

    private fun String.toCurrencyOrNull(): CurrencyCode? =
        CurrencyCode.entries.firstOrNull { it.name == trim().uppercase() }

    private fun String?.normalizedKey(): String? = normalized()
        ?.uppercase()
        ?.replace("Á", "A")
        ?.replace("É", "E")
        ?.replace("Í", "I")
        ?.replace("Ó", "O")
        ?.replace("Ú", "U")
        ?.replace(Regex("[^A-Z0-9]+"), "_")
        ?.trim('_')

    private fun List<String>.normalizedAliases(): List<String> =
        mapNotNull { it.normalized() }.distinctBy { it.lowercase() }

    private fun String?.normalized(): String? =
        this?.trim()?.takeIf(String::isNotEmpty)

    private fun FinancialTransaction.isLinkedProductTransaction(): Boolean =
        id.startsWith("purchase-") ||
            id.startsWith("card-payment-") ||
            id.startsWith("savings-") ||
            id.startsWith("loan-payment-")

    private fun stableUuid(seed: String): String =
        UUID.nameUUIDFromBytes(seed.toByteArray(StandardCharsets.UTF_8)).toString()

    private fun clarification(message: String) =
        AssistantProposalResolution.Clarification(message)

    private sealed interface ProductRequirement {
        data class Ready(
            val account: FinancialAccount,
            val configuration: FinancialProductConfiguration,
            val summary: ProductSummary,
        ) : ProductRequirement

    }

    private companion object {
        val LIQUID_PRODUCT_TYPES = setOf(
            FinancialAccountType.CASH,
            FinancialAccountType.BANK_ACCOUNT,
        )
        val FUNDING_PRODUCT_TYPES = LIQUID_PRODUCT_TYPES + FinancialAccountType.SAVINGS
        val MAX_FUTURE_SKEW: Duration = Duration.ofMinutes(5)
    }
}

private class ProposalClarificationException(
    val publicMessage: String,
) : IllegalStateException(publicMessage)

internal sealed interface AssistantProposalResolution {
    data class Clarification(val message: String) : AssistantProposalResolution

    data class Proposal(
        val command: FinancialCommand,
        val financialStateVersion: Long,
        val primary: ProposalProduct,
        val destination: ProposalProduct? = null,
        val amount: Money? = null,
        val merchant: String? = null,
        val categoryId: String? = null,
        val occurredAtEpochMillis: Long,
        val productType: String? = null,
        val installmentCount: Int? = null,
        val annualRateBasisPoints: Int? = null,
        val paymentType: String? = null,
        val movementType: String? = null,
    ) : AssistantProposalResolution
}

internal data class ProposalProduct(
    val id: String,
    val name: String,
    val type: String,
    val currency: String,
)
