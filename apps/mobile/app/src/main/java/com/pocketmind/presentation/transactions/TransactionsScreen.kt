package com.pocketmind.presentation.transactions

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pocketmind.R
import com.pocketmind.shared.domain.model.CurrencyCode
import com.pocketmind.shared.domain.model.FinancialTransaction
import com.pocketmind.shared.domain.model.Money
import com.pocketmind.shared.domain.model.TransactionCategoryId
import com.pocketmind.shared.domain.model.TransactionSource
import com.pocketmind.shared.domain.model.TransactionType
import com.pocketmind.ui.components.PocketPrimaryButton
import com.pocketmind.ui.theme.PocketMindTheme
import com.pocketmind.ui.theme.PocketSpacing
import java.text.NumberFormat
import java.util.Locale

@Composable
fun TransactionsRoute(
    onCreate: () -> Unit,
    onEdit: (String) -> Unit,
    onManageAccounts: () -> Unit,
    onBack: () -> Unit,
    viewModel: TransactionsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    TransactionsScreen(state, onCreate, onEdit, onManageAccounts, onBack)
}

@Composable
fun TransactionsScreen(
    state: TransactionsUiState,
    onCreate: () -> Unit,
    onEdit: (String) -> Unit,
    onManageAccounts: () -> Unit,
    onBack: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val filtered = state.items.filter { item ->
        query.isBlank() || item.transaction.merchant.orEmpty().contains(query, true) ||
            item.transaction.note.orEmpty().contains(query, true) || item.accountName.contains(query, true)
    }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            FloatingActionButton(onClick = onCreate, containerColor = MaterialTheme.colorScheme.primary) {
                Icon(Icons.Rounded.Add, stringResource(R.string.transactions_add), tint = MaterialTheme.colorScheme.onPrimary)
            }
        },
    ) { paddingValues ->
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            TransactionsHeader(onBack)
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text(stringResource(R.string.transactions_search)) },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = PocketSpacing.xl),
            )
            when {
                state.isLoading -> LoadingState()
                state.items.isEmpty() -> EmptyTransactions(onCreate, onManageAccounts)
                else -> TransactionsList(filtered, onEdit)
            }
        }
    }
}

@Composable
private fun TransactionsHeader(onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primary).statusBarsPadding().padding(PocketSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.transactions_back), tint = MaterialTheme.colorScheme.onPrimary)
        }
        Spacer(Modifier.width(PocketSpacing.xs))
        Column {
            Text(stringResource(R.string.transactions_title), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimary)
            Text(stringResource(R.string.transactions_subtitle), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.82f))
        }
    }
}

@Composable
private fun LoadingState() = Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }

@Composable
private fun EmptyTransactions(onCreate: () -> Unit, onManageAccounts: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(PocketSpacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.AutoMirrored.Rounded.ReceiptLong, null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(PocketSpacing.md))
        Text(stringResource(R.string.transactions_empty_title), style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(PocketSpacing.xs))
        Text(stringResource(R.string.transactions_empty_description), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(PocketSpacing.lg))
        PocketPrimaryButton(stringResource(R.string.transactions_add), onCreate)
        Spacer(Modifier.height(PocketSpacing.sm))
        androidx.compose.material3.OutlinedButton(onClick = onManageAccounts, modifier = Modifier.fillMaxWidth().height(PocketSpacing.primaryButtonHeight)) {
            Text(stringResource(R.string.accounts_manage))
        }
    }
}

@Composable
private fun TransactionsList(items: List<TransactionListItem>, onEdit: (String) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(PocketSpacing.xl),
        verticalArrangement = Arrangement.spacedBy(PocketSpacing.sm),
    ) {
        if (items.isEmpty()) item { Text(stringResource(R.string.transactions_no_results), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(items, key = { it.transaction.id }) { item -> TransactionRow(item, onEdit) }
    }
}

@Composable
private fun TransactionRow(item: TransactionListItem, onEdit: (String) -> Unit) {
    val transaction = item.transaction
    val isIncome = transaction.type == TransactionType.INCOME
    Card(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).clickable { onEdit(transaction.id) },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(modifier = Modifier.padding(PocketSpacing.md), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (isIncome) Icons.AutoMirrored.Rounded.TrendingUp else Icons.AutoMirrored.Rounded.TrendingDown,
                contentDescription = stringResource(if (isIncome) R.string.transactions_income else R.string.transactions_expense),
                tint = if (isIncome) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error,
            )
            Spacer(Modifier.width(PocketSpacing.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(transaction.merchant.orEmpty().ifBlank { categoryLabel(transaction.categoryId) }, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(item.accountName.ifBlank { stringResource(R.string.transactions_unknown_account) }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(R.string.transactions_source_manual), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                text = amountText(transaction.amount, isIncome),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (isIncome) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error,
            )
            Icon(Icons.Rounded.Edit, stringResource(R.string.transactions_edit), modifier = Modifier.padding(start = PocketSpacing.sm).size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun categoryLabel(id: String?): String = stringResource(
    runCatching { id?.let(TransactionCategoryId::valueOf) }.getOrNull().toCategoryLabelRes(),
)

private fun TransactionCategoryId?.toCategoryLabelRes(): Int = when (this) {
    TransactionCategoryId.SALARY -> R.string.category_salary
    TransactionCategoryId.FREELANCE -> R.string.category_freelance
    TransactionCategoryId.TRANSFER -> R.string.category_transfer
    TransactionCategoryId.FOOD -> R.string.category_food
    TransactionCategoryId.TRANSPORT -> R.string.category_transport
    TransactionCategoryId.HOME -> R.string.category_home
    TransactionCategoryId.HEALTH -> R.string.category_health
    TransactionCategoryId.EDUCATION -> R.string.category_education
    TransactionCategoryId.ENTERTAINMENT -> R.string.category_entertainment
    TransactionCategoryId.SHOPPING -> R.string.category_shopping
    TransactionCategoryId.SERVICES -> R.string.category_services
    TransactionCategoryId.DEBT_PAYMENT -> R.string.category_debt_payment
    TransactionCategoryId.SAVINGS -> R.string.category_savings
    TransactionCategoryId.OTHER, null -> R.string.category_other
}

private fun amountText(amount: Money, income: Boolean): String {
    val currency = NumberFormat.getCurrencyInstance(Locale.Builder().setLanguage("es").setRegion("CO").build()).apply { maximumFractionDigits = 0 }.format(amount.minorUnits)
    return if (income) "+$currency" else "-$currency"
}

@Preview(showBackground = true)
@Composable
private fun TransactionsPreview() = PocketMindTheme {
    TransactionsScreen(
        TransactionsUiState(
            items = listOf(
                TransactionListItem(
                    FinancialTransaction("1", "account", TransactionType.EXPENSE, Money(24_000, CurrencyCode.COP), 1L, TransactionCategoryId.FOOD.name, "Cafetería", source = TransactionSource.MANUAL),
                    "Billetera",
                ),
            ),
            isLoading = false,
        ),
        onCreate = {}, onEdit = {}, onManageAccounts = {}, onBack = {},
    )
}
