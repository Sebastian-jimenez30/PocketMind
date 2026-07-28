package com.pocketmind.presentation.analysis

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Analytics
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pocketmind.R
import com.pocketmind.shared.domain.model.Money
import com.pocketmind.shared.domain.model.TransactionCategoryId
import com.pocketmind.ui.theme.PocketSpacing
import com.pocketmind.ui.components.PocketContextTopBar
import java.text.NumberFormat
import java.util.Locale

@Composable
fun AnalysisRoute(onBack: () -> Unit, viewModel: AnalysisViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    AnalysisScreen(state, onBack)
}

@Composable
private fun AnalysisScreen(state: AnalysisUiState, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        PocketContextTopBar(
            title = stringResource(R.string.analysis_title),
            onBack = onBack,
            backContentDescription = stringResource(R.string.accounts_back),
        )
        if (state.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(PocketSpacing.xl),
                verticalArrangement = Arrangement.spacedBy(PocketSpacing.md),
            ) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        shape = RoundedCornerShape(24.dp),
                    ) {
                        Column(Modifier.padding(PocketSpacing.lg), verticalArrangement = Arrangement.spacedBy(PocketSpacing.md)) {
                            Icon(Icons.Rounded.Analytics, null, tint = MaterialTheme.colorScheme.primary)
                            Text(stringResource(R.string.analysis_month), style = MaterialTheme.typography.titleLarge)
                            Row(horizontalArrangement = Arrangement.spacedBy(PocketSpacing.sm)) {
                                AnalysisStat(stringResource(R.string.transactions_income), money(state.income), Modifier.weight(1f))
                                AnalysisStat(stringResource(R.string.transactions_expense), money(state.expense), Modifier.weight(1f))
                            }
                            val balance = state.income.minorUnits - state.expense.minorUnits
                            Text(
                                stringResource(R.string.analysis_balance, money(Money(balance, state.income.currency))),
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                    }
                }
                item { Text(stringResource(R.string.analysis_categories), style = MaterialTheme.typography.titleLarge) }
                if (state.categories.isEmpty()) {
                    item {
                        Text(
                            stringResource(R.string.analysis_empty),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    val max = state.categories.maxOf { it.amount.minorUnits }.coerceAtLeast(1)
                    items(state.categories, key = { it.category.name }) { item ->
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                            Column(Modifier.padding(PocketSpacing.md), verticalArrangement = Arrangement.spacedBy(PocketSpacing.xs)) {
                                Row {
                                    Text(categoryLabel(item.category), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                                    Text(money(item.amount), fontWeight = FontWeight.SemiBold)
                                }
                                LinearProgressIndicator(
                                    progress = { item.amount.minorUnits.toFloat() / max.toFloat() },
                                    modifier = Modifier.fillMaxWidth().height(PocketSpacing.xs),
                                    color = MaterialTheme.colorScheme.primary,
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.AnalysisStat(label: String, value: String, modifier: Modifier) {
    Column(modifier.background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f), RoundedCornerShape(16.dp)).padding(PocketSpacing.sm)) {
        Text(label, style = MaterialTheme.typography.bodySmall)
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun categoryLabel(category: TransactionCategoryId): String = stringResource(
    when (category) {
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
        TransactionCategoryId.OTHER -> R.string.category_other
    },
)

private fun money(value: Money): String = NumberFormat.getCurrencyInstance(
    Locale.Builder().setLanguage("es").setRegion("CO").build(),
).apply { maximumFractionDigits = 0 }.format(value.minorUnits)
