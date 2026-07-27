package com.pocketmind.presentation.common

import com.pocketmind.shared.domain.command.FinancialCommandError
import com.pocketmind.shared.domain.command.FinancialCommandResult

/** Spanish fallback copy for domain rejections until messages move to resources. */
internal fun FinancialCommandResult.Rejected.toUserMessage(): String = when {
    FinancialCommandError.PRODUCT_NOT_FOUND in errors ->
        "No encontramos el producto seleccionado."
    FinancialCommandError.PRODUCT_ALREADY_EXISTS in errors ->
        "Ya existe un producto con este identificador."
    FinancialCommandError.INVALID_PRODUCT_CONFIGURATION in errors ->
        "Revisa la configuración específica del producto."
    FinancialCommandError.UNSUPPORTED_PRODUCT_OPERATION in errors ->
        "El producto seleccionado no es compatible con esta operación."
    FinancialCommandError.INVALID_AMOUNT in errors ->
        "Agrega un valor mayor que cero."
    FinancialCommandError.INVALID_DATE in errors ->
        "Usa una fecha válida."
    FinancialCommandError.INVALID_INSTALLMENTS in errors ->
        "Usa entre 1 y 60 cuotas."
    FinancialCommandError.INVALID_PROMOTIONAL_RATE_PERIODS in errors ->
        "Revisa las cuotas y tasas del periodo promocional."
    FinancialCommandError.INVALID_MERCHANT in errors ->
        "Escribe el comercio o concepto."
    FinancialCommandError.CURRENCY_MISMATCH in errors ->
        "Los productos deben usar la misma moneda."
    FinancialCommandError.SAME_TRANSFER_PRODUCT in errors ->
        "Elige productos diferentes para el origen y el destino."
    FinancialCommandError.MISSING_TRANSFER_DESTINATION in errors ->
        "Elige el producto de destino."
    FinancialCommandError.MISSING_CARD_PROFILE in errors ||
        FinancialCommandError.MISSING_SAVINGS_PROFILE in errors ||
        FinancialCommandError.MISSING_LOAN_PROFILE in errors ->
        "Completa primero los datos de este producto desde Editar."
    FinancialCommandError.PURCHASE_EXCEEDS_AVAILABLE_CREDIT in errors ->
        "La compra supera el cupo disponible."
    FinancialCommandError.PAYMENT_EXCEEDS_CARD_DEBT in errors ->
        "El pago no puede superar la deuda actual."
    FinancialCommandError.MISSING_PAYMENT_AMOUNT in errors ->
        "Agrega el valor del abono."
    FinancialCommandError.PAYMENT_AMOUNT_MISMATCH in errors ->
        "El valor cambió. Revisa la cuota o el saldo actualizado."
    FinancialCommandError.WITHDRAWAL_EXCEEDS_SAVINGS in errors ->
        "El retiro no puede superar el ahorro disponible."
    FinancialCommandError.INVALID_SAVINGS_RATE in errors ->
        "Agrega una tasa válida."
    FinancialCommandError.PAYMENT_EXCEEDS_LOAN_DEBT in errors ->
        "El abono no puede superar la deuda actual."
    FinancialCommandError.INVALID_MONEY_FLOW_ENDPOINTS in errors ->
        "Revisa los productos de origen y destino."
    FinancialCommandError.UNSUPPORTED_RULE_VERSION in errors ->
        "Actualiza PocketMind para usar esta operación."
    FinancialCommandError.TRANSACTION_NOT_FOUND in errors ->
        "No encontramos el movimiento."
    FinancialCommandError.LINKED_TRANSACTION_REQUIRES_PRODUCT_ACTION in errors ->
        "Este movimiento debe modificarse desde el producto relacionado."
    else -> "No pudimos completar la operación. Revisa los datos e inténtalo de nuevo."
}
