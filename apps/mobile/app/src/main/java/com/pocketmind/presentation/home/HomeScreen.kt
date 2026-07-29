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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.automirrored.rounded.TrendingDown
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddCircle
import androidx.compose.material.icons.rounded.Analytics
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
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
import com.pocketmind.ui.components.PocketPrimaryButton
import com.pocketmind.ui.components.PocketSectionCard
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
                onCreateExpense = {
                    showQuickActions = false
                    onCreateTransaction(TransactionType.EXPENSE)
                },
                onCreateIncome = {
                    showQuickActions = false
                    onCreateTransaction(TransactionType.INCOME)
                },
                onManageAccounts = {
                    showQuickActions = false
                    onManageAccounts()
                },
                onOpenBudgets = {
                    showQuickActions = false
                    onOpenBudgets()
                },
                onOpenAssistant = {
                    showQuickActions = false
                    onOpenAssistant()
                },
                onOpenTransactions = {
                    showQuickActions = false
                    onOpenTransactions()
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
            FinancialSummaryCard(summary = summary, amountsVisible = amountsVisible, onToggleVisibility = {
                amountsVisible = !amountsVisible
            })
        }

        item {
            ProductsSection(
                accounts = accounts,
                amountsVisible = amountsVisible,
                onManageAccounts = onManageAccounts,
                onOpenProduct = onOpenProduct,
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
private fun FinancialSummaryCard(
    summary: DashboardSummary,
    amountsVisible: Boolean,
    onToggleVisibility: () -> Unit,
) {
    val visibilityDescription = stringResource(
        if (amountsVisible) R.string.home_privacy_hide else R.string.home_privacy_show,
    )
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
    ) {
        Column(
            modifier = Modifier.padding(PocketSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(PocketSpacing.md),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.home_summary_title),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.82f),
                    )
                    Text(
                        text = stringResource(R.string.home_available_balance),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.74f),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = visibleAmount(summary.availableBalance.minorUnits, amountsVisible),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary,
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

            HorizontalDivider(
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.16f),
                thickness = 1.dp,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(PocketSpacing.md)) {
                SummaryMiniStat(
                    label = stringResource(R.string.home_income),
                    amount = visibleAmount(summary.monthlyIncome.minorUnits, amountsVisible),
                    icon = Icons.AutoMirrored.Rounded.TrendingUp,
                    modifier = Modifier.weight(1f),
                )
                SummaryMiniStat(
                    label = stringResource(R.string.home_expenses),
                    amount = visibleAmount(summary.monthlyExpense.minorUnits, amountsVisible),
                    icon = Icons.AutoMirrored.Rounded.TrendingDown,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun SummaryMiniStat(
    label: String,
    amount: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PocketSpacing.sm),
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.86f),
            modifier = Modifier.size(16.dp),
        )
        Column {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.76f),
            )
            Text(
                amount,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}

@Composable
private fun ProductsSection(
    accounts: List<AccountOverview>,
    amountsVisible: Boolean,
    onManageAccounts: () -> Unit,
    onOpenProduct: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(PocketSpacing.sm)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(R.string.home_section_products),
                style = MaterialTheme.typography.titleLarge,
            )
            TextButton(onClick = onManageAccounts) {
                Text(text = stringResource(R.string.accounts_manage))
            }
        }

        if (accounts.isEmpty()) {
            PocketSectionCard {
                Text(
                    text = stringResource(R.string.accounts_empty_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = stringResource(R.string.accounts_empty_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(PocketSpacing.xs))
                PocketPrimaryButton(
                    text = stringResource(R.string.accounts_add),
                    onClick = onManageAccounts,
                )
            }
        } else {
            val chunked = remember(accounts) { accounts.chunked(3) }
            val pagerState = rememberPagerState(pageCount = { chunked.size })

            HorizontalPager(
                state = pagerState,
                pageSpacing = PocketSpacing.md,
                modifier = Modifier.fillMaxWidth(),
            ) { pageIndex ->
                val pageAccounts = chunked[pageIndex]
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(PocketSpacing.sm),
                ) {
                    pageAccounts.forEach { overview ->
                        AccountCompactTile(
                            overview = overview,
                            amountsVisible = amountsVisible,
                            onOpenProduct = onOpenProduct,
                        )
                    }
                }
            }

            if (chunked.size > 1) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = PocketSpacing.xs),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    repeat(chunked.size) { index ->
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 3.dp)
                                .size(if (pagerState.currentPage == index) 7.dp else 5.dp)
                                .clip(CircleShape)
                                .background(
                                    if (pagerState.currentPage == index) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                                ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountCompactTile(
    overview: AccountOverview,
    amountsVisible: Boolean,
    onOpenProduct: (String) -> Unit,
) {
    val icon = when (overview.account.type) {
        FinancialAccountType.CREDIT_CARD -> Icons.Rounded.CreditCard
        FinancialAccountType.SAVINGS -> Icons.Rounded.Savings
        else -> Icons.Rounded.AccountBalanceWallet
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenProduct(overview.account.id) },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = PocketSpacing.md, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(PocketSpacing.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = overview.account.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(
                        if (overview.isLiability) R.string.home_account_debt else R.string.home_available_balance,
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = visibleAmount(overview.currentBalance.minorUnits, amountsVisible),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
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
    Column(verticalArrangement = Arrangement.spacedBy(PocketSpacing.xs)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(R.string.home_section_budgets),
                style = MaterialTheme.typography.titleLarge,
            )
            TextButton(onClick = onOpenBudgets) {
                Text(text = stringResource(R.string.home_see_all))
            }
        }

        if (budgets.isEmpty()) {
            Text(
                text = stringResource(R.string.home_budgets_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = PocketSpacing.xs),
            )
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
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
    Column(verticalArrangement = Arrangement.spacedBy(PocketSpacing.sm)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(R.string.home_recent_title),
                style = MaterialTheme.typography.titleLarge,
            )
            if (transactions.isNotEmpty()) {
                TextButton(onClick = onOpenTransactions) {
                    Text(text = stringResource(R.string.home_see_all))
                }
            }
        }

        if (transactions.isEmpty()) {
            PocketSectionCard {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Rounded.Payments,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                Text(stringResource(R.string.home_recent_empty_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.home_recent_empty_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenTransactions),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
            ) {
                Column(Modifier.padding(PocketSpacing.md)) {
                    transactions.forEachIndexed { index, transaction ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = PocketSpacing.sm),
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
                            )
                            Spacer(Modifier.width(PocketSpacing.sm))
                            Text(
                                transaction.merchant.orEmpty().ifBlank { transaction.note.orEmpty() },
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                visibleAmount(transaction.amount.minorUnits, amountsVisible),
                                style = MaterialTheme.typography.titleSmall,
                            )
                        }
                        if (index < transactions.lastIndex) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
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
    onCreateExpense: () -> Unit,
    onCreateIncome: () -> Unit,
    onManageAccounts: () -> Unit,
    onOpenBudgets: () -> Unit,
    onOpenAssistant: () -> Unit,
    onOpenTransactions: () -> Unit,
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
                icon = Icons.AutoMirrored.Rounded.TrendingDown,
                label = stringResource(R.string.home_fab_action_expense),
                onClick = onCreateExpense,
            )
            QuickActionSheetRow(
                icon = Icons.AutoMirrored.Rounded.TrendingUp,
                label = stringResource(R.string.home_fab_action_income),
                onClick = onCreateIncome,
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
                icon = Icons.Rounded.AutoAwesome,
                label = stringResource(R.string.home_fab_action_assistant),
                onClick = onOpenAssistant,
            )
            QuickActionSheetRow(
                icon = Icons.AutoMirrored.Rounded.ReceiptLong,
                label = stringResource(R.string.home_action_movements),
                onClick = onOpenTransactions,
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
