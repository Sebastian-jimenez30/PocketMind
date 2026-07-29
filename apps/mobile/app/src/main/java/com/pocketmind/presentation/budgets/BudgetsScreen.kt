package com.pocketmind.presentation.budgets

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pocketmind.R
import com.pocketmind.shared.domain.model.BudgetPeriodType
import com.pocketmind.shared.domain.model.BudgetProgress
import com.pocketmind.shared.domain.model.BudgetStatus
import com.pocketmind.shared.domain.model.CurrencyCode
import com.pocketmind.shared.domain.model.Money
import com.pocketmind.shared.domain.model.TransactionCategoryId
import com.pocketmind.ui.components.PocketContextTopBar
import com.pocketmind.ui.components.PocketPrimaryButton
import com.pocketmind.ui.theme.PocketSpacing
import java.text.NumberFormat
import java.util.Locale

@Composable
fun BudgetsRoute(
    onBack: () -> Unit,
    viewModel: BudgetsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    BudgetsScreen(
        state = state,
        onBack = onBack,
        onCreateBudget = viewModel::createBudget,
        onDeleteBudget = viewModel::deleteBudget,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetsScreen(
    state: BudgetsUiState,
    onBack: () -> Unit,
    onCreateBudget: (String, TransactionCategoryId, Long, CurrencyCode, BudgetPeriodType, Boolean) -> Unit,
    onDeleteBudget: (String) -> Unit,
) {
    var isCreating by remember { mutableStateOf(false) }
    var budgetToDelete by remember { mutableStateOf<BudgetProgress?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            PocketContextTopBar(
                title = stringResource(R.string.budgets_title),
                backContentDescription = stringResource(R.string.nav_back),
                onBack = onBack,
            )
            if (state.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            } else if (state.progressList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(PocketSpacing.xl),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(PocketSpacing.md),
                    ) {
                        Text(
                            text = stringResource(R.string.budgets_empty_title),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = stringResource(R.string.budgets_empty_description),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(PocketSpacing.md))
                        PocketPrimaryButton(
                            text = stringResource(R.string.budgets_create_button),
                            onClick = { isCreating = true },
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(PocketSpacing.xl),
                    verticalArrangement = Arrangement.spacedBy(PocketSpacing.lg),
                ) {
                    items(state.progressList, key = { it.budget.id }) { progress ->
                        BudgetCard(
                            progress = progress,
                            onDelete = { budgetToDelete = progress },
                        )
                    }
                }
            }
        }

        if (state.progressList.isNotEmpty()) {
            FloatingActionButton(
                onClick = { isCreating = true },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(PocketSpacing.xl),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.budgets_create_title))
            }
        }
    }

    if (isCreating) {
        ModalBottomSheet(
            onDismissRequest = { isCreating = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            CreateBudgetSheet(
                onDismiss = { isCreating = false },
                onCreate = { name, category, amount, period ->
                    onCreateBudget(name, category, amount, CurrencyCode.COP, period, true)
                    isCreating = false
                },
            )
        }
    }

    budgetToDelete?.let { progress ->
        AlertDialog(
            onDismissRequest = { budgetToDelete = null },
            title = { Text(stringResource(R.string.budgets_delete_confirm)) },
            text = { Text(progress.budget.name) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteBudget(progress.budget.id)
                        budgetToDelete = null
                    },
                ) {
                    Text(stringResource(R.string.budgets_delete_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { budgetToDelete = null }) {
                    Text(stringResource(R.string.assistant_dismiss))
                }
            },
        )
    }
}

@Composable
private fun BudgetCard(
    progress: BudgetProgress,
    onDelete: () -> Unit,
) {
    val budget = progress.budget
    val (statusLabelRes, statusColor) = when (progress.budget.status) {
        BudgetStatus.ACTIVE -> R.string.budgets_status_on_track to MaterialTheme.colorScheme.primary
        BudgetStatus.NEAR_LIMIT -> R.string.budgets_status_warning to MaterialTheme.colorScheme.tertiary
        BudgetStatus.EXCEEDED -> R.string.budgets_status_exceeded to MaterialTheme.colorScheme.error
        BudgetStatus.FINISHED -> R.string.budgets_status_finished to MaterialTheme.colorScheme.onSurfaceVariant
        BudgetStatus.PAUSED -> R.string.budgets_status_paused to MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier.padding(PocketSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(PocketSpacing.md),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = budget.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "${categoryLabel(budget.categoryId)} • ${periodLabel(budget.periodType)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Rounded.Delete,
                        contentDescription = stringResource(R.string.budgets_delete_action),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "${stringResource(R.string.budgets_spent_label)}: ${money(progress.spentAmount)}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = "${stringResource(R.string.budgets_available_label)}: ${money(progress.availableAmount)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (progress.budget.status == BudgetStatus.EXCEEDED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium,
                )
            }

            val progressRatio = (progress.percentage / 100.0).toFloat().coerceIn(0f, 1f)
            LinearProgressIndicator(
                progress = { progressRatio },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
                color = statusColor,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.budgets_execution_label, progress.percentage.toInt()),
                    style = MaterialTheme.typography.labelMedium,
                    color = statusColor,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(statusLabelRes),
                    style = MaterialTheme.typography.labelMedium,
                    color = statusColor,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun CreateBudgetSheet(
    onDismiss: () -> Unit,
    onCreate: (String, TransactionCategoryId, Long, BudgetPeriodType) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(TransactionCategoryId.FOOD) }
    var selectedPeriod by remember { mutableStateOf(BudgetPeriodType.MONTHLY) }

    val categories = remember {
        TransactionCategoryId.entries
    }
    val periods = remember {
        listOf(BudgetPeriodType.MONTHLY, BudgetPeriodType.WEEKLY, BudgetPeriodType.BIWEEKLY)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = PocketSpacing.xl, vertical = PocketSpacing.lg)
            .padding(bottom = PocketSpacing.xxl),
        verticalArrangement = Arrangement.spacedBy(PocketSpacing.lg),
    ) {
        Text(
            text = stringResource(R.string.budgets_create_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text(stringResource(R.string.budgets_name_label)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )

        OutlinedTextField(
            value = amountText,
            onValueChange = { amountText = it.filter { ch -> ch.isDigit() } },
            label = { Text(stringResource(R.string.budgets_amount_label)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )

        Column(verticalArrangement = Arrangement.spacedBy(PocketSpacing.xs)) {
            Text(
                text = stringResource(R.string.budgets_category_label),
                style = MaterialTheme.typography.labelLarge,
            )
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(PocketSpacing.sm),
            ) {
                items(categories) { category ->
                    FilterChip(
                        selected = selectedCategory == category,
                        onClick = { selectedCategory = category },
                        label = { Text(categoryLabel(category)) },
                    )
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(PocketSpacing.xs)) {
            Text(
                text = stringResource(R.string.budgets_period_label),
                style = MaterialTheme.typography.labelLarge,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(PocketSpacing.sm)) {
                periods.forEach { period ->
                    FilterChip(
                        selected = selectedPeriod == period,
                        onClick = { selectedPeriod = period },
                        label = { Text(periodLabel(period)) },
                    )
                }
            }
        }

        val isValid = name.isNotBlank() && (amountText.toLongOrNull() ?: 0L) > 0L
        PocketPrimaryButton(
            text = stringResource(R.string.budgets_create_button),
            onClick = {
                val amount = (amountText.toLongOrNull() ?: 0L)
                onCreate(name.trim(), selectedCategory, amount, selectedPeriod)
            },
            enabled = isValid,
            modifier = Modifier.fillMaxWidth(),
        )
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

@Composable
private fun periodLabel(period: BudgetPeriodType): String = stringResource(
    when (period) {
        BudgetPeriodType.MONTHLY -> R.string.budgets_period_monthly
        BudgetPeriodType.WEEKLY -> R.string.budgets_period_weekly
        BudgetPeriodType.BIWEEKLY -> R.string.budgets_period_biweekly
        BudgetPeriodType.CUSTOM -> R.string.budgets_period_monthly
    },
)

private fun money(value: Money): String = NumberFormat.getCurrencyInstance(
    Locale.Builder().setLanguage("es").setRegion("CO").build(),
).apply {
    maximumFractionDigits = 0
    minimumFractionDigits = 0
}.format(value.minorUnits)
