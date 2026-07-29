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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import com.pocketmind.shared.domain.model.FinancialTransaction
import java.text.SimpleDateFormat
import java.util.Date
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
import com.pocketmind.presentation.common.DynamicCategorySelector
import com.pocketmind.presentation.common.categoryLabel
import com.pocketmind.shared.domain.model.BudgetPeriodType
import com.pocketmind.shared.domain.model.BudgetProgress
import com.pocketmind.shared.domain.model.BudgetStatus
import com.pocketmind.shared.domain.model.CurrencyCode
import com.pocketmind.shared.domain.model.CustomCategory
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
        onUpdateBudget = viewModel::updateBudget,
        onDeleteBudget = viewModel::deleteBudget,
        onCreateCustomCategory = viewModel::createCustomCategory,
        onUpdateCustomCategory = viewModel::updateCustomCategory,
        onDeleteCustomCategory = viewModel::deleteCustomCategory,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetsScreen(
    state: BudgetsUiState,
    onBack: () -> Unit,
    onCreateBudget: (String, String, Long, CurrencyCode, BudgetPeriodType, Boolean) -> Unit,
    onUpdateBudget: (String, String, String, Long, CurrencyCode, BudgetPeriodType, Boolean) -> Unit = { _, _, _, _, _, _, _ -> },
    onDeleteBudget: (String) -> Unit,
    onCreateCustomCategory: (String, (String) -> Unit) -> Unit = { _, _ -> },
    onUpdateCustomCategory: (String, String) -> Unit = { _, _ -> },
    onDeleteCustomCategory: (String) -> Unit = {},
) {
    var isCreating by remember { mutableStateOf(false) }
    var budgetToDelete by remember { mutableStateOf<BudgetProgress?>(null) }
    var budgetToEdit by remember { mutableStateOf<BudgetProgress?>(null) }
    var selectedBudgetForHistory by remember { mutableStateOf<BudgetProgress?>(null) }


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
                            customCategories = state.customCategories,
                            onClick = { selectedBudgetForHistory = progress },
                            onEdit = { budgetToEdit = progress },
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
                customCategories = state.customCategories,
                onCreateCustomCategory = onCreateCustomCategory,
                onUpdateCustomCategory = onUpdateCustomCategory,
                onDeleteCustomCategory = onDeleteCustomCategory,
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

    budgetToEdit?.let { progress ->
        ModalBottomSheet(
            onDismissRequest = { budgetToEdit = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            EditBudgetSheet(
                progress = progress,
                onDismiss = { budgetToEdit = null },
                onSave = { id, name, category, amount, period ->
                    onUpdateBudget(id, name, category, amount, progress.budget.maxAmount.currency, period, progress.budget.isRecurring)
                    budgetToEdit = null
                },
                customCategories = state.customCategories,
                onCreateCustomCategory = onCreateCustomCategory,
                onUpdateCustomCategory = onUpdateCustomCategory,
                onDeleteCustomCategory = onDeleteCustomCategory,
            )
        }
    }

    selectedBudgetForHistory?.let { progress ->
        ModalBottomSheet(
            onDismissRequest = { selectedBudgetForHistory = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            BudgetHistorySheet(
                progress = progress,
                transactions = state.transactions,
                customCategories = state.customCategories,
                onDismiss = { selectedBudgetForHistory = null },
            )
        }
    }
}

@Composable
private fun BudgetCard(
    progress: BudgetProgress,
    customCategories: List<CustomCategory> = emptyList(),
    onClick: () -> Unit,
    onEdit: () -> Unit,
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
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
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
                        text = "${categoryLabel(budget.categoryId, customCategories)} • ${periodLabel(budget.periodType)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,

                    )
                }
                Row {
                    IconButton(onClick = onEdit) {
                        Icon(
                            imageVector = Icons.Rounded.Edit,
                            contentDescription = stringResource(R.string.assistant_edit),
                            tint = MaterialTheme.colorScheme.primary,
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
    onCreate: (String, String, Long, BudgetPeriodType) -> Unit,
    customCategories: List<CustomCategory> = emptyList(),
    onCreateCustomCategory: (String, (String) -> Unit) -> Unit = { _, _ -> },
    onUpdateCustomCategory: (String, String) -> Unit = { _, _ -> },
    onDeleteCustomCategory: (String) -> Unit = {},
) {
    var name by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    var selectedCategoryId by remember { mutableStateOf(TransactionCategoryId.FOOD.name) }
    var selectedPeriod by remember { mutableStateOf(BudgetPeriodType.MONTHLY) }

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
            DynamicCategorySelector(
                selectedCategoryId = selectedCategoryId,
                onSelectCategory = { selectedCategoryId = it },
                customCategories = customCategories,
                onCreateCustomCategory = onCreateCustomCategory,
                onUpdateCustomCategory = onUpdateCustomCategory,
                onDeleteCustomCategory = onDeleteCustomCategory,
            )
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
                onCreate(name.trim(), selectedCategoryId, amount, selectedPeriod)
            },
            enabled = isValid,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun EditBudgetSheet(
    progress: BudgetProgress,
    onDismiss: () -> Unit,
    onSave: (String, String, String, Long, BudgetPeriodType) -> Unit,
    customCategories: List<CustomCategory> = emptyList(),
    onCreateCustomCategory: (String, (String) -> Unit) -> Unit = { _, _ -> },
    onUpdateCustomCategory: (String, String) -> Unit = { _, _ -> },
    onDeleteCustomCategory: (String) -> Unit = {},
) {
    val budget = progress.budget
    var name by remember(budget.id) { mutableStateOf(budget.name) }
    var amountText by remember(budget.id) { mutableStateOf(budget.maxAmount.minorUnits.toString()) }
    var selectedCategoryId by remember(budget.id) { mutableStateOf(budget.categoryId) }
    var selectedPeriod by remember(budget.id) { mutableStateOf(budget.periodType) }

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
            text = stringResource(R.string.assistant_edit),
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
            DynamicCategorySelector(
                selectedCategoryId = selectedCategoryId,
                onSelectCategory = { selectedCategoryId = it },
                customCategories = customCategories,
                onCreateCustomCategory = onCreateCustomCategory,
                onUpdateCustomCategory = onUpdateCustomCategory,
                onDeleteCustomCategory = onDeleteCustomCategory,
            )
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
            text = stringResource(R.string.custom_category_update_save),
            onClick = {
                val amount = (amountText.toLongOrNull() ?: 0L)
                onSave(budget.id, name.trim(), selectedCategoryId, amount, selectedPeriod)
            },
            enabled = isValid,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun BudgetHistorySheet(
    progress: BudgetProgress,
    transactions: List<FinancialTransaction>,
    customCategories: List<CustomCategory> = emptyList(),
    onDismiss: () -> Unit,
) {
    val budget = progress.budget
    val budgetTransactions = remember(transactions, budget) {
        transactions
            .filter { it.categoryId == budget.categoryId }
            .sortedByDescending { it.occurredAtEpochMillis }
    }
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy", Locale("es", "CO")) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = PocketSpacing.xl, vertical = PocketSpacing.lg)
            .padding(bottom = PocketSpacing.xxl),
        verticalArrangement = Arrangement.spacedBy(PocketSpacing.lg),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(PocketSpacing.xs)) {
            Text(
                text = budget.name,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "${categoryLabel(budget.categoryId, customCategories)} • ${periodLabel(budget.periodType)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(PocketSpacing.md),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.budgets_spent_label),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = "${money(progress.spentAmount)} / ${money(budget.maxAmount)}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        Text(
            text = "Historial de movimientos",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )

        if (budgetTransactions.isEmpty()) {
            Text(
                text = "No hay movimientos registrados para este presupuesto aún.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = PocketSpacing.lg),
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp),
                verticalArrangement = Arrangement.spacedBy(PocketSpacing.sm),
            ) {
                items(budgetTransactions, key = { it.id }) { tx ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(PocketSpacing.md),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = (tx.merchant ?: tx.note ?: "").ifBlank { categoryLabel(tx.categoryId ?: "", customCategories) },
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = dateFormat.format(Date(tx.occurredAtEpochMillis)),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(
                                text = money(tx.amount),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
        }
    }
}



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
