package com.pocketmind.presentation.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.TrendingDown
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddCircle
import androidx.compose.material.icons.rounded.Analytics
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pocketmind.R
import com.pocketmind.presentation.common.categoryLabel
import com.pocketmind.shared.domain.model.BudgetProgress
import com.pocketmind.shared.domain.model.CurrencyCode
import com.pocketmind.shared.domain.model.CustomCategory
import com.pocketmind.shared.domain.model.DashboardSummary
import com.pocketmind.shared.domain.model.FinancialAccountType
import com.pocketmind.shared.domain.model.FinancialTransaction
import com.pocketmind.shared.domain.model.Money
import com.pocketmind.shared.domain.model.TransactionType
import com.pocketmind.ui.components.PocketBrandMark
import com.pocketmind.ui.components.PocketContentSheet
import com.pocketmind.ui.testing.PocketMindTestTags
import com.pocketmind.ui.theme.PocketMindTheme
import com.pocketmind.ui.theme.PocketSpacing
import java.text.NumberFormat
import java.util.Locale
import kotlinx.coroutines.launch

@Composable
fun HomeRoute(
    onOpenTransactions: () -> Unit,
    onCreateTransaction: (TransactionType) -> Unit,
    onManageAccounts: () -> Unit,
    onStartRecord: () -> Unit,
    onOpenProduct: (String) -> Unit,
    onOpenAssistant: () -> Unit,
    onOpenBudgets: () -> Unit,
    onManageSavings: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        viewModel.refreshProfile()
    }
    HomeScreen(
        uiState = uiState,
        onOpenTransactions = onOpenTransactions,
        onCreateTransaction = onCreateTransaction,
        onManageAccounts = onManageAccounts,
        onStartRecord = onStartRecord,
        onOpenProduct = onOpenProduct,
        onOpenAssistant = onOpenAssistant,
        onOpenBudgets = onOpenBudgets,
        onManageSavings = onManageSavings,
    )
}

@Composable
fun HomeScreen(
    uiState: HomeUiState,
    onOpenTransactions: () -> Unit,
    onCreateTransaction: (TransactionType) -> Unit,
    onManageAccounts: () -> Unit,
    onStartRecord: () -> Unit,
    onOpenProduct: (String) -> Unit,
    onOpenAssistant: () -> Unit,
    onOpenBudgets: () -> Unit = {},
    onManageSavings: () -> Unit = {},
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val comingSoonMessage = stringResource(R.string.home_coming_soon)
    val onComingSoon: () -> Unit = {
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(comingSoonMessage)
        }
    }

    var showQuickActions by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.primary,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (uiState is HomeUiState.Content) {
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    FloatingActionButton(
                        onClick = onOpenAssistant,
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                        shape = CircleShape,
                        elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = stringResource(R.string.home_fab_action_assistant),
                            modifier = Modifier.size(24.dp),
                        )
                    }
                    FloatingActionButton(
                        onClick = { showQuickActions = true },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        shape = CircleShape,
                        elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = stringResource(R.string.home_quick_actions_title),
                            modifier = Modifier.size(28.dp),
                        )
                    }
                }
            }
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            HomeHeader(
                displayName = (uiState as? HomeUiState.Content)?.displayName.orEmpty(),
                onNotificationsClick = onComingSoon,
            )
            PocketContentSheet(modifier = Modifier.weight(1f)) {
                when (uiState) {
                    HomeUiState.Loading -> LoadingContent()
                    is HomeUiState.Content -> {
                        DashboardContent(
                            summary = uiState.summary,
                            accounts = uiState.accounts,
                            budgets = uiState.budgets,
                            customCategories = uiState.customCategories,
                            recentTransactions = uiState.recentTransactions,
                            onCreateTransaction = onCreateTransaction,
                            onManageAccounts = onManageAccounts,
                            onOpenTransactions = onOpenTransactions,
                            onStartRecord = onStartRecord,
                            onOpenProduct = onOpenProduct,
                            onOpenAssistant = onOpenAssistant,
                            onOpenBudgets = onOpenBudgets,
                            onManageSavings = onManageSavings,
                        )
                    }
                }
            }
        }

        if (showQuickActions) {
            QuickActionsSheet(
                onDismiss = { showQuickActions = false },
                onStartRecord = {
                    showQuickActions = false
                    onStartRecord()
                },
                onManageAccounts = {
                    showQuickActions = false
                    onManageAccounts()
                },
                onOpenBudgets = {
                    showQuickActions = false
                    onOpenBudgets()
                },
                onManageSavings = {
                    showQuickActions = false
                    onManageSavings()
                },
            )
        }
    }
}

@Composable
private fun HomeHeader(displayName: String, onNotificationsClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = PocketSpacing.lg, vertical = PocketSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PocketBrandMark(
            modifier = Modifier.size(42.dp),
            contentDescription = stringResource(R.string.brand_mark_description),
        )
        Spacer(Modifier.width(PocketSpacing.md))
        Text(
            text = if (displayName.isBlank()) {
                stringResource(R.string.home_greeting)
            } else {
                stringResource(R.string.home_greeting_named, displayName)
            },
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onPrimary,
        )
        IconButton(onClick = onNotificationsClick) {
            Icon(
                imageVector = Icons.Rounded.Notifications,
                contentDescription = stringResource(R.string.home_notifications),
                tint = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}

@Composable
private fun LoadingContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun DashboardContent(
    summary: DashboardSummary,
    accounts: List<AccountOverview>,
    budgets: List<BudgetProgress>,
    customCategories: List<CustomCategory>,
    recentTransactions: List<FinancialTransaction>,
    onCreateTransaction: (TransactionType) -> Unit,
    onManageAccounts: () -> Unit,
    onOpenTransactions: () -> Unit,
    onStartRecord: () -> Unit,
    onOpenProduct: (String) -> Unit,
    onOpenAssistant: () -> Unit,
    onOpenBudgets: () -> Unit,
    onManageSavings: () -> Unit,
) {
    var amountsVisible by rememberSaveable { mutableStateOf(true) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag(PocketMindTestTags.HOME_CONTENT),
        contentPadding = PaddingValues(
            start = PocketSpacing.xl,
            top = PocketSpacing.lg,
            end = PocketSpacing.xl,
            bottom = 96.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(PocketSpacing.lg),
    ) {
        item {
            PanoramaAndProductsCard(
                summary = summary,
                accounts = accounts,
                amountsVisible = amountsVisible,
                onToggleVisibility = { amountsVisible = !amountsVisible },
                onOpenProduct = onOpenProduct,
                onManageAccounts = onManageAccounts,
            )
        }

        item {
            BudgetsSection(
                budgets = budgets,
                customCategories = customCategories,
                amountsVisible = amountsVisible,
                onOpenBudgets = onOpenBudgets,
            )
        }

        item {
            RecentMovementsCard(
                transactions = recentTransactions,
                amountsVisible = amountsVisible,
                onOpenTransactions = onOpenTransactions,
            )
        }
    }
}

@Composable
private fun PanoramaAndProductsCard(
    summary: DashboardSummary,
    accounts: List<AccountOverview>,
    amountsVisible: Boolean,
    onToggleVisibility: () -> Unit,
    onOpenProduct: (String) -> Unit,
    onManageAccounts: () -> Unit,
) {
    val visibilityDescription = stringResource(
        if (amountsVisible) R.string.home_privacy_hide else R.string.home_privacy_show,
    )
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Column(
            modifier = Modifier.padding(vertical = PocketSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(PocketSpacing.md),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = PocketSpacing.lg),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.home_summary_title),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
                        )
                        Text(
                            text = stringResource(R.string.home_available_balance),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.72f),
                        )
                    }
                    IconButton(onClick = onToggleVisibility) {
                        Icon(
                            imageVector = if (amountsVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                            contentDescription = visibilityDescription,
                            tint = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                }

                Text(
                    text = visibleAmount(summary.availableBalance.minorUnits, amountsVisible),
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.18f),
                thickness = 1.dp,
                modifier = Modifier.padding(horizontal = PocketSpacing.lg),
            )

            Text(
                text = stringResource(R.string.home_section_products),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
                modifier = Modifier.padding(horizontal = PocketSpacing.lg),
            )

            if (accounts.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = PocketSpacing.lg, vertical = PocketSpacing.sm),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = stringResource(R.string.accounts_empty_title),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.accounts_empty_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.76f),
                    )
                    Spacer(Modifier.height(PocketSpacing.sm))
                    TextButton(
                        onClick = onManageAccounts,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onPrimary),
                    ) {
                        Text(text = stringResource(R.string.accounts_add), fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = PocketSpacing.lg),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(accounts, key = { it.account.id }) { overview ->
                        ProductMiniCard(
                            overview = overview,
                            amountsVisible = amountsVisible,
                            onOpenProduct = onOpenProduct,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProductMiniCard(
    overview: AccountOverview,
    amountsVisible: Boolean,
    onOpenProduct: (String) -> Unit,
) {
    val icon = when (overview.account.type) {
        FinancialAccountType.CREDIT_CARD -> Icons.Rounded.CreditCard
        FinancialAccountType.SAVINGS -> Icons.Rounded.Savings
        FinancialAccountType.LOAN -> Icons.Rounded.Payments
        else -> Icons.Rounded.AccountBalanceWallet
    }
    Card(
        modifier = Modifier
            .width(115.dp)
            .clickable { onOpenProduct(overview.account.id) },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.12f),
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(20.dp),
                )
            }
            Text(
                text = overview.account.name,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
            Text(
                text = visibleAmount(overview.currentBalance.minorUnits, amountsVisible),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun BudgetsSection(
    budgets: List<BudgetProgress>,
    customCategories: List<CustomCategory>,
    amountsVisible: Boolean,
    onOpenBudgets: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onOpenBudgets),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(
            modifier = Modifier.padding(PocketSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.home_section_budgets),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (budgets.isEmpty()) {
                Text(
                    text = stringResource(R.string.home_budgets_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            } else {
                budgets.forEach { item ->
                    BudgetsSlimRow(
                        item = item,
                        customCategories = customCategories,
                        amountsVisible = amountsVisible,
                    )
                }
            }
        }
    }
}

@Composable
private fun BudgetsSlimRow(
    item: BudgetProgress,
    customCategories: List<CustomCategory>,
    amountsVisible: Boolean,
) {
    val categoryName = categoryLabel(item.budget.categoryId, customCategories)
    val maxUnits = item.budget.maxAmount.minorUnits
    val spentUnits = item.spentAmount.minorUnits
    val percentage = if (maxUnits > 0L) {
        ((spentUnits.toDouble() / maxUnits.toDouble()) * 100.0).toInt()
    } else {
        0
    }
    val progress = (percentage / 100f).coerceIn(0f, 1f)

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = categoryName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "${visibleAmount(spentUnits, amountsVisible)} / ${visibleAmount(maxUnits, amountsVisible)} ($percentage%)",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(CircleShape),
            color = if (percentage >= 100) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
    }
}

@Composable
private fun RecentMovementsCard(
    transactions: List<FinancialTransaction>,
    amountsVisible: Boolean,
    onOpenTransactions: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onOpenTransactions),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(
            modifier = Modifier.padding(PocketSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.home_recent_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (transactions.isEmpty()) {
                Text(
                    text = stringResource(R.string.home_recent_empty_title),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = stringResource(R.string.home_recent_empty_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                transactions.forEachIndexed { index, transaction ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            when (transaction.type) {
                                TransactionType.INCOME -> Icons.AutoMirrored.Rounded.TrendingUp
                                TransactionType.EXPENSE -> Icons.AutoMirrored.Rounded.TrendingDown
                                TransactionType.TRANSFER -> Icons.Rounded.AccountBalanceWallet
                            },
                            contentDescription = null,
                            tint = when (transaction.type) {
                                TransactionType.INCOME -> MaterialTheme.colorScheme.secondary
                                TransactionType.EXPENSE -> MaterialTheme.colorScheme.error
                                TransactionType.TRANSFER -> MaterialTheme.colorScheme.primary
                            },
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(PocketSpacing.sm))
                        Text(
                            text = transaction.merchant.orEmpty().ifBlank { transaction.note.orEmpty() },
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = visibleAmount(transaction.amount.minorUnits, amountsVisible),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    if (index < transactions.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(top = 4.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickActionsSheet(
    onDismiss: () -> Unit,
    onStartRecord: () -> Unit,
    onManageAccounts: () -> Unit,
    onOpenBudgets: () -> Unit,
    onManageSavings: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = PocketSpacing.xl, vertical = PocketSpacing.lg)
                .padding(bottom = PocketSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(PocketSpacing.sm),
        ) {
            Text(
                text = stringResource(R.string.home_quick_actions_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = PocketSpacing.xs),
            )

            QuickActionSheetRow(
                icon = Icons.Rounded.AddCircle,
                label = stringResource(R.string.home_fab_action_record),
                onClick = onStartRecord,
            )
            QuickActionSheetRow(
                icon = Icons.Rounded.AccountBalanceWallet,
                label = stringResource(R.string.home_action_products),
                onClick = onManageAccounts,
            )
            QuickActionSheetRow(
                icon = Icons.Rounded.Analytics,
                label = stringResource(R.string.home_fab_action_budgets),
                onClick = onOpenBudgets,
            )
            QuickActionSheetRow(
                icon = Icons.Rounded.Savings,
                label = stringResource(R.string.home_action_savings),
                onClick = onManageSavings,
            )
        }
    }
}

@Composable
private fun QuickActionSheetRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(14.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = PocketSpacing.md, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(PocketSpacing.md),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun visibleAmount(amount: Long, visible: Boolean): String = if (visible) {
    remember(amount) {
        NumberFormat.getCurrencyInstance(
            Locale.Builder().setLanguage("es").setRegion("CO").build(),
        ).apply {
            maximumFractionDigits = 0
        }.format(amount)
    }
} else {
    stringResource(R.string.home_hidden_amount)
}

@Preview(name = "Inicio", showBackground = true, widthDp = 360, heightDp = 800)
@Composable
private fun HomePreview() {
    PocketMindTheme {
        HomeScreen(
            uiState = HomeUiState.Content(
                DashboardSummary(
                    availableBalance = Money(1_850_000, CurrencyCode.COP),
                    monthlyIncome = Money(3_200_000, CurrencyCode.COP),
                    monthlyExpense = Money(1_350_000, CurrencyCode.COP),
                ),
            ),
            onOpenTransactions = {},
            onCreateTransaction = {},
            onManageAccounts = {},
            onStartRecord = {},
            onOpenProduct = {},
            onOpenAssistant = {},
            onOpenBudgets = {},
        )
    }
}
