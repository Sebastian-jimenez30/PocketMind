package com.pocketmind.presentation.products

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.TrendingDown
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Percent
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pocketmind.R
import com.pocketmind.shared.domain.model.FinancialAccountType
import com.pocketmind.shared.domain.model.Money
import com.pocketmind.shared.domain.model.SavingsMovement
import com.pocketmind.shared.domain.model.SavingsMovementType
import com.pocketmind.ui.components.PocketPrimaryButton
import com.pocketmind.ui.theme.PocketSpacing
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ProductDetailRoute(
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    onAction: (String, ManualActionType) -> Unit,
    viewModel: ProductDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ProductDetailScreen(state, onBack, onEdit, onAction)
}

@Composable
private fun ProductDetailScreen(
    state: ProductDetailUiState,
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    onAction: (String, ManualActionType) -> Unit,
) {
    val account = state.account
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            ProductHeader(account?.name.orEmpty(), onBack) {
                account?.let { onEdit(it.id) }
            }
            when {
                state.isLoading -> androidx.compose.foundation.layout.Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }
                account == null -> Text(
                    stringResource(R.string.transactions_unknown_account),
                    modifier = Modifier.padding(PocketSpacing.xl),
                )
                account.type == FinancialAccountType.CREDIT_CARD -> CardProductContent(state, onAction)
                account.type == FinancialAccountType.SAVINGS -> SavingsProductContent(state, onAction)
                else -> StandardProductContent(state)
            }
        }
    }
}

@Composable
private fun ProductHeader(title: String, onBack: () -> Unit, onEdit: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .background(MaterialTheme.colorScheme.primary)
            .statusBarsPadding()
            .padding(horizontal = PocketSpacing.md, vertical = PocketSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.accounts_back), tint = MaterialTheme.colorScheme.onPrimary)
        }
        Text(
            title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        IconButton(onClick = onEdit) {
            Icon(Icons.Rounded.Edit, stringResource(R.string.product_detail_edit), tint = MaterialTheme.colorScheme.onPrimary)
        }
    }
}

@Composable
private fun CardProductContent(
    state: ProductDetailUiState,
    onAction: (String, ManualActionType) -> Unit,
) {
    val account = state.account ?: return
    val overview = state.cardOverview
    LazyColumn(
        contentPadding = PaddingValues(PocketSpacing.xl),
        verticalArrangement = Arrangement.spacedBy(PocketSpacing.md),
    ) {
        item {
            ProductSummaryCard(
                icon = { Icon(Icons.Rounded.CreditCard, null, tint = MaterialTheme.colorScheme.primary) },
                title = stringResource(R.string.product_detail_debt),
                primaryValue = money(overview?.currentDebt ?: account.openingBalance),
                supporting = overview?.let {
                    stringResource(
                        R.string.product_detail_card_dates,
                        it.profile.statementClosingDay,
                        it.profile.paymentDueDay,
                        (it.profile.annualInterestBasisPoints / 100.0).toString(),
                    )
                }.orEmpty(),
            ) {
                SummaryStat(stringResource(R.string.product_detail_available), money(overview?.availableCredit))
                SummaryStat(stringResource(R.string.product_detail_next_payment), money(overview?.nextPayment))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(PocketSpacing.sm)) {
                PocketPrimaryButton(
                    text = stringResource(R.string.product_detail_purchase),
                    onClick = { onAction(account.id, ManualActionType.CARD_PURCHASE) },
                    modifier = Modifier.weight(1f),
                )
                OutlinedButton(
                    onClick = { onAction(account.id, ManualActionType.CARD_PAYMENT) },
                    modifier = Modifier.weight(1f).height(PocketSpacing.primaryButtonHeight),
                ) { Text(stringResource(R.string.product_detail_payment)) }
            }
        }
        item { SectionTitle(stringResource(R.string.product_detail_purchases)) }
        if (state.purchases.isEmpty()) {
            item { EmptyMessage(stringResource(R.string.product_detail_no_purchases)) }
        } else {
            items(state.purchases, key = { it.id }) { purchase ->
                val paid = overview?.paidInstallments?.get(purchase.id) ?: 0
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.padding(PocketSpacing.md), verticalArrangement = Arrangement.spacedBy(PocketSpacing.xs)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(purchase.merchant, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                            Text(money(purchase.principal), fontWeight = FontWeight.SemiBold)
                        }
                        Text(
                            stringResource(R.string.product_detail_installment, paid, purchase.installmentCount),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            stringResource(R.string.product_detail_installment_value, money(purchase.installmentAmount)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            if (paid >= purchase.installmentCount) {
                                stringResource(R.string.product_detail_completed)
                            } else {
                                stringResource(
                                    R.string.product_detail_next_due,
                                    nextInstallmentDate(purchase.firstPaymentAtEpochMillis, paid),
                                )
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        if (state.payments.isNotEmpty()) {
            item { SectionTitle(stringResource(R.string.product_detail_payments)) }
            items(state.payments, key = { it.id }) { payment ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Row(Modifier.padding(PocketSpacing.md), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.AutoMirrored.Rounded.TrendingUp, null, tint = MaterialTheme.colorScheme.secondary)
                        Spacer(Modifier.width(PocketSpacing.sm))
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.manual_action_payment), style = MaterialTheme.typography.titleMedium)
                            Text(date(payment.paidAtEpochMillis), style = MaterialTheme.typography.bodySmall)
                        }
                        Text(money(payment.amount), fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun SavingsProductContent(
    state: ProductDetailUiState,
    onAction: (String, ManualActionType) -> Unit,
) {
    val account = state.account ?: return
    val projection = state.savingsProjection
    LazyColumn(
        contentPadding = PaddingValues(PocketSpacing.xl),
        verticalArrangement = Arrangement.spacedBy(PocketSpacing.md),
    ) {
        item {
            ProductSummaryCard(
                icon = { Icon(Icons.Rounded.Savings, null, tint = MaterialTheme.colorScheme.secondary) },
                title = stringResource(R.string.product_detail_saved),
                primaryValue = money(projection?.currentBalance ?: account.openingBalance),
                supporting = stringResource(
                    R.string.product_detail_rate,
                    ((projection?.annualYieldBasisPoints ?: 0) / 100.0).toString(),
                ),
            ) {
                SummaryStat(stringResource(R.string.product_detail_contributed), money(projection?.contributedBalance))
                SummaryStat(stringResource(R.string.product_detail_yield), money(projection?.estimatedYield))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(PocketSpacing.sm)) {
                PocketPrimaryButton(
                    stringResource(R.string.product_detail_deposit),
                    { onAction(account.id, ManualActionType.SAVINGS_DEPOSIT) },
                    modifier = Modifier.weight(1f),
                )
                OutlinedButton(
                    onClick = { onAction(account.id, ManualActionType.SAVINGS_WITHDRAWAL) },
                    modifier = Modifier.weight(1f).height(PocketSpacing.primaryButtonHeight),
                ) { Text(stringResource(R.string.product_detail_withdraw)) }
            }
        }
        item {
            OutlinedButton(
                onClick = { onAction(account.id, ManualActionType.SAVINGS_RATE) },
                modifier = Modifier.fillMaxWidth().height(PocketSpacing.primaryButtonHeight),
            ) {
                Icon(Icons.Rounded.Percent, null)
                Spacer(Modifier.width(PocketSpacing.xs))
                Text(stringResource(R.string.product_detail_change_rate))
            }
        }
        item { SectionTitle(stringResource(R.string.product_detail_history)) }
        if (state.savingsMovements.isEmpty()) {
            item { EmptyMessage(stringResource(R.string.product_detail_no_history)) }
        } else {
            items(state.savingsMovements, key = { it.id }) { movement -> SavingsMovementRow(movement) }
        }
    }
}

@Composable
private fun StandardProductContent(state: ProductDetailUiState) {
    val account = state.account ?: return
    val incomes = state.transactions.filter { it.type == com.pocketmind.shared.domain.model.TransactionType.INCOME }.sumOf { it.amount.minorUnits }
    val expenses = state.transactions.filter { it.type == com.pocketmind.shared.domain.model.TransactionType.EXPENSE }.sumOf { it.amount.minorUnits }
    val outgoing = state.transactions.filter {
        it.type == com.pocketmind.shared.domain.model.TransactionType.TRANSFER && it.accountId == account.id
    }.sumOf { it.amount.minorUnits }
    val incoming = state.transactions.filter {
        it.type == com.pocketmind.shared.domain.model.TransactionType.TRANSFER && it.relatedAccountId == account.id
    }.sumOf { it.amount.minorUnits }
    val balance = Money(account.openingBalance.minorUnits + incomes - expenses - outgoing + incoming, account.currency)
    LazyColumn(
        contentPadding = PaddingValues(PocketSpacing.xl),
        verticalArrangement = Arrangement.spacedBy(PocketSpacing.md),
    ) {
        item {
            ProductSummaryCard(
                icon = { Icon(Icons.Rounded.Savings, null, tint = MaterialTheme.colorScheme.primary) },
                title = stringResource(R.string.product_detail_balance),
                primaryValue = money(balance),
                supporting = "",
            ) {
                SummaryStat(stringResource(R.string.transactions_income), money(Money(incomes, account.currency)))
                SummaryStat(stringResource(R.string.transactions_expense), money(Money(expenses, account.currency)))
            }
        }
        item { SectionTitle(stringResource(R.string.product_detail_history)) }
        if (state.transactions.isEmpty()) item { EmptyMessage(stringResource(R.string.product_detail_no_history)) }
        items(state.transactions, key = { it.id }) { transaction ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Row(Modifier.padding(PocketSpacing.md), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        when (transaction.type) {
                            com.pocketmind.shared.domain.model.TransactionType.INCOME -> Icons.AutoMirrored.Rounded.TrendingUp
                            com.pocketmind.shared.domain.model.TransactionType.EXPENSE -> Icons.AutoMirrored.Rounded.TrendingDown
                            com.pocketmind.shared.domain.model.TransactionType.TRANSFER -> Icons.Rounded.CreditCard
                        },
                        null,
                        tint = when (transaction.type) {
                            com.pocketmind.shared.domain.model.TransactionType.INCOME -> MaterialTheme.colorScheme.secondary
                            com.pocketmind.shared.domain.model.TransactionType.EXPENSE -> MaterialTheme.colorScheme.error
                            com.pocketmind.shared.domain.model.TransactionType.TRANSFER -> MaterialTheme.colorScheme.primary
                        },
                    )
                    Spacer(Modifier.width(PocketSpacing.sm))
                    Column(Modifier.weight(1f)) {
                        Text(transaction.merchant.orEmpty().ifBlank { transaction.note.orEmpty() }, maxLines = 1)
                        Text(date(transaction.occurredAtEpochMillis), style = MaterialTheme.typography.bodySmall)
                    }
                    Text(money(transaction.amount), fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun ProductSummaryCard(
    icon: @Composable () -> Unit,
    title: String,
    primaryValue: String,
    supporting: String,
    stats: @Composable RowScope.() -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(Modifier.padding(PocketSpacing.lg), verticalArrangement = Arrangement.spacedBy(PocketSpacing.sm)) {
            icon()
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(primaryValue, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.SemiBold)
            if (supporting.isNotBlank()) Text(supporting, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(PocketSpacing.sm)) { stats() }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.SummaryStat(label: String, value: String) {
    Column(
        Modifier.weight(1f)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.56f), RoundedCornerShape(16.dp))
            .padding(PocketSpacing.sm),
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun SavingsMovementRow(movement: SavingsMovement) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(Modifier.padding(PocketSpacing.md), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                when (movement.type) {
                    SavingsMovementType.DEPOSIT -> Icons.AutoMirrored.Rounded.TrendingUp
                    SavingsMovementType.WITHDRAWAL -> Icons.AutoMirrored.Rounded.TrendingDown
                    SavingsMovementType.RATE_CHANGE -> Icons.Rounded.Percent
                },
                null,
                Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(PocketSpacing.sm))
            Column(Modifier.weight(1f)) {
                Text(
                    when (movement.type) {
                        SavingsMovementType.DEPOSIT -> stringResource(R.string.manual_action_deposit)
                        SavingsMovementType.WITHDRAWAL -> stringResource(R.string.manual_action_withdrawal)
                        SavingsMovementType.RATE_CHANGE -> stringResource(R.string.manual_action_rate)
                    },
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(date(movement.occurredAtEpochMillis), style = MaterialTheme.typography.bodySmall)
            }
            Text(
                if (movement.type == SavingsMovementType.RATE_CHANGE) {
                    "${(movement.annualYieldBasisPoints ?: 0) / 100.0}%"
                } else {
                    money(movement.amount)
                },
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) = Text(text, style = MaterialTheme.typography.titleLarge)

@Composable
private fun EmptyMessage(text: String) = Card(
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
) {
    Text(text, Modifier.padding(PocketSpacing.lg), color = MaterialTheme.colorScheme.onSurfaceVariant)
}

private fun money(value: Money?): String = NumberFormat.getCurrencyInstance(
    Locale.Builder().setLanguage("es").setRegion("CO").build(),
).apply { maximumFractionDigits = 0 }.format(value?.minorUnits ?: 0)

private fun date(epochMillis: Long): String =
    SimpleDateFormat(
        "dd MMM yyyy",
        Locale.Builder().setLanguage("es").setRegion("CO").build(),
    ).format(Date(epochMillis))

private fun nextInstallmentDate(firstPaymentEpochMillis: Long, paidInstallments: Int): String {
    val next = java.time.Instant.ofEpochMilli(firstPaymentEpochMillis)
        .atZone(java.time.ZoneId.systemDefault())
        .toLocalDate()
        .plusMonths(paidInstallments.toLong())
    return "%02d/%02d/%04d".format(next.dayOfMonth, next.monthValue, next.year)
}
