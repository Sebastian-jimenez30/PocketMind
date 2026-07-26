package com.pocketmind.presentation.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.automirrored.rounded.TrendingDown
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.AddCircle
import androidx.compose.material.icons.rounded.Analytics
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pocketmind.R
import com.pocketmind.shared.domain.model.CurrencyCode
import com.pocketmind.shared.domain.model.DashboardSummary
import com.pocketmind.shared.domain.model.Money
import com.pocketmind.shared.domain.model.TransactionType
import com.pocketmind.shared.domain.model.FinancialTransaction
import com.pocketmind.ui.components.PocketBottomNavigation
import com.pocketmind.ui.components.PocketBrandMark
import com.pocketmind.ui.components.PocketContentSheet
import com.pocketmind.ui.components.PocketNavigationItem
import com.pocketmind.ui.components.PocketPrimaryButton
import com.pocketmind.ui.components.PocketSectionCard
import com.pocketmind.ui.theme.PocketMindTheme
import com.pocketmind.ui.theme.PocketSpacing
import java.text.NumberFormat
import java.util.Locale
import kotlinx.coroutines.launch

@Composable
fun HomeRoute(
    onOpenProfile: () -> Unit,
    onOpenTransactions: () -> Unit,
    onCreateTransaction: (TransactionType) -> Unit,
    onManageAccounts: () -> Unit,
    onOpenAnalysis: () -> Unit,
    onOpenProduct: (String) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    HomeScreen(
        uiState = uiState,
        onOpenProfile = onOpenProfile,
        onOpenTransactions = onOpenTransactions,
        onCreateTransaction = onCreateTransaction,
        onManageAccounts = onManageAccounts,
        onOpenAnalysis = onOpenAnalysis,
        onOpenProduct = onOpenProduct,
    )
}

@Composable
fun HomeScreen(
    uiState: HomeUiState,
    onOpenProfile: () -> Unit,
    onOpenTransactions: () -> Unit,
    onCreateTransaction: (TransactionType) -> Unit,
    onManageAccounts: () -> Unit,
    onOpenAnalysis: () -> Unit,
    onOpenProduct: (String) -> Unit,
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

    Scaffold(
        containerColor = MaterialTheme.colorScheme.primary,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            PocketBottomNavigation(
                items = listOf(
                    PocketNavigationItem(stringResource(R.string.nav_home), Icons.Rounded.Home, true, {}),
                    PocketNavigationItem(
                        stringResource(R.string.nav_transactions),
                        Icons.AutoMirrored.Rounded.ReceiptLong,
                        false,
                        onOpenTransactions,
                    ),
                    PocketNavigationItem(stringResource(R.string.nav_analysis), Icons.Rounded.Analytics, false, onOpenAnalysis),
                    PocketNavigationItem(stringResource(R.string.nav_profile), Icons.Rounded.Person, false, onOpenProfile),
                ),
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            HomeHeader(onNotificationsClick = onComingSoon)
            PocketContentSheet(modifier = Modifier.weight(1f)) {
                when (uiState) {
                    HomeUiState.Loading -> LoadingContent()
                    is HomeUiState.Content -> DashboardContent(
                        summary = uiState.summary,
                        accounts = uiState.accounts,
                        recentTransactions = uiState.recentTransactions,
                        onCreateTransaction = onCreateTransaction,
                        onManageAccounts = onManageAccounts,
                        onOpenTransactions = onOpenTransactions,
                        onOpenProduct = onOpenProduct,
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeHeader(onNotificationsClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = PocketSpacing.xl, vertical = PocketSpacing.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PocketBrandMark(
            modifier = Modifier.size(52.dp),
            contentDescription = stringResource(R.string.brand_mark_description),
        )
        Spacer(Modifier.width(PocketSpacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.home_greeting),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onPrimary,
            )
            Text(
                text = stringResource(R.string.home_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.82f),
            )
        }
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
    recentTransactions: List<FinancialTransaction>,
    onCreateTransaction: (TransactionType) -> Unit,
    onManageAccounts: () -> Unit,
    onOpenTransactions: () -> Unit,
    onOpenProduct: (String) -> Unit,
) {
    val quickActions = listOf(
        QuickAction(R.string.home_action_expense, Icons.AutoMirrored.Rounded.TrendingDown) {
            onCreateTransaction(TransactionType.EXPENSE)
        },
        QuickAction(R.string.home_action_income, Icons.AutoMirrored.Rounded.TrendingUp) {
            onCreateTransaction(TransactionType.INCOME)
        },
        QuickAction(R.string.home_action_cards, Icons.Rounded.CreditCard, onManageAccounts),
        QuickAction(R.string.home_action_savings, Icons.Rounded.Savings, onManageAccounts),
        QuickAction(R.string.home_action_accounts, Icons.Rounded.AccountBalanceWallet, onManageAccounts),
        QuickAction(R.string.home_action_movements, Icons.AutoMirrored.Rounded.ReceiptLong, onOpenTransactions),
    )
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = PocketSpacing.xl,
            top = PocketSpacing.xl,
            end = PocketSpacing.xl,
            bottom = PocketSpacing.xxl,
        ),
        verticalArrangement = Arrangement.spacedBy(PocketSpacing.xl),
    ) {
        item { AccountWalletCarousel(summary, accounts, onOpenProduct) }
        item { CaptureCard(onClick = { onCreateTransaction(TransactionType.EXPENSE) }) }
        item {
            Text(
                text = stringResource(R.string.home_quick_actions_title),
                style = MaterialTheme.typography.titleLarge,
            )
        }
        items(quickActions.chunked(2)) { rowActions ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(PocketSpacing.md),
            ) {
                rowActions.forEach { action ->
                    QuickActionTile(
                        action = action,
                        onClick = action.onClick,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (rowActions.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        item { RecentMovementsCard(recentTransactions, onOpenTransactions) }
        item { InsightCard() }
    }
}

@Composable
private fun FinancialSummaryCard(summary: DashboardSummary) {
    var amountsVisible by rememberSaveable { mutableStateOf(true) }
    val visibilityDescription = stringResource(
        if (amountsVisible) R.string.home_privacy_hide else R.string.home_privacy_show,
    )
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Column(
            modifier = Modifier.padding(PocketSpacing.xl),
            verticalArrangement = Arrangement.spacedBy(PocketSpacing.lg),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.home_summary_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.82f),
                    )
                    Spacer(Modifier.height(PocketSpacing.xs))
                    Text(
                        text = stringResource(R.string.home_available_balance),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.76f),
                    )
                    Text(
                        text = visibleAmount(summary.availableBalance.minorUnits, amountsVisible),
                        style = MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }
                IconButton(onClick = { amountsVisible = !amountsVisible }) {
                    Icon(
                        imageVector = if (amountsVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                        contentDescription = visibilityDescription,
                        tint = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(PocketSpacing.md)) {
                SummaryStat(
                    label = stringResource(R.string.home_income),
                    amount = visibleAmount(summary.monthlyIncome.minorUnits, amountsVisible),
                    icon = Icons.AutoMirrored.Rounded.TrendingUp,
                    modifier = Modifier.weight(1f),
                )
                SummaryStat(
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
private fun AccountWalletCarousel(
    summary: DashboardSummary,
    accounts: List<AccountOverview>,
    onOpenProduct: (String) -> Unit,
) {
    val pageCount = accounts.size + 1
    val pagerState = rememberPagerState(pageCount = { pageCount })
    Column(verticalArrangement = Arrangement.spacedBy(PocketSpacing.sm)) {
        HorizontalPager(
            state = pagerState,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(end = PocketSpacing.xl),
            pageSpacing = PocketSpacing.md,
            modifier = Modifier.fillMaxWidth(),
        ) { page ->
            if (page == 0) {
                FinancialSummaryCard(summary)
            } else {
                AccountSummaryCard(accounts[page - 1], onOpenProduct)
            }
        }
        if (pageCount > 1) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                repeat(pageCount) { index ->
                    Box(
                        modifier = Modifier
                            .padding(horizontal = PocketSpacing.xxs)
                            .size(if (pagerState.currentPage == index) 8.dp else 6.dp)
                            .clip(CircleShape)
                            .background(
                                if (pagerState.currentPage == index) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
                            ),
                    )
                }
            }
        }
    }
}

@Composable
private fun AccountSummaryCard(overview: AccountOverview, onOpenProduct: (String) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onOpenProduct(overview.account.id) },
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(
            modifier = Modifier.padding(PocketSpacing.xl),
            verticalArrangement = Arrangement.spacedBy(PocketSpacing.md),
        ) {
            Text(stringResource(R.string.home_account_label), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
            Text(overview.account.name, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSecondaryContainer)
            Text(
                stringResource(if (overview.isLiability) R.string.home_account_debt else R.string.home_available_balance),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.78f),
            )
            Text(visibleAmount(overview.currentBalance.minorUnits, true), style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
            Row(horizontalArrangement = Arrangement.spacedBy(PocketSpacing.md)) {
                AccountStat(stringResource(R.string.home_income), visibleAmount(overview.income.minorUnits, true), Modifier.weight(1f))
                AccountStat(stringResource(R.string.home_expenses), visibleAmount(overview.expense.minorUnits, true), Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun AccountStat(label: String, amount: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.10f),
    ) {
        Column(modifier = Modifier.padding(PocketSpacing.md)) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.78f))
            Text(amount, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
        }
    }
}

@Composable
private fun SummaryStat(
    label: String,
    amount: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.12f),
    ) {
        Column(modifier = Modifier.padding(PocketSpacing.md)) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.height(PocketSpacing.xs))
            Text(
                label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f),
            )
            Text(amount, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimary)
        }
    }
}

@Composable
private fun CaptureCard(onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Column(
            modifier = Modifier.padding(PocketSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(PocketSpacing.sm),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.secondary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.AddCircle, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondary)
                }
                Spacer(Modifier.width(PocketSpacing.md))
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.home_capture_title), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.home_capture_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            PocketPrimaryButton(
                text = stringResource(R.string.home_capture_action),
                onClick = onClick,
            )
        }
    }
}

private data class QuickAction(
    val labelRes: Int,
    val icon: ImageVector,
    val onClick: () -> Unit,
)

@Composable
private fun QuickActionTile(
    action: QuickAction,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(action.labelRes)
    Surface(
        modifier = modifier
            .height(128.dp)
            .clip(RoundedCornerShape(18.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier.padding(PocketSpacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(action.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.height(PocketSpacing.xs))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.Center,
                maxLines = 2,
            )
        }
    }
}

@Composable
private fun RecentMovementsCard(
    transactions: List<FinancialTransaction>,
    onOpenTransactions: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(PocketSpacing.sm)) {
        Text(stringResource(R.string.home_recent_title), style = MaterialTheme.typography.titleLarge)
        if (transactions.isEmpty()) {
            PocketSectionCard {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Payments, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
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
                modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenTransactions),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(Modifier.padding(PocketSpacing.md)) {
                    transactions.forEachIndexed { index, transaction ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = PocketSpacing.sm),
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
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            )
                            Text(
                                visibleAmount(transaction.amount.minorUnits, true),
                                style = MaterialTheme.typography.titleSmall,
                            )
                        }
                        if (index < transactions.lastIndex) {
                            androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InsightCard() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(PocketSpacing.lg),
            horizontalArrangement = Arrangement.spacedBy(PocketSpacing.md),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column {
                Text(stringResource(R.string.home_insight_title), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(PocketSpacing.xs))
                Text(
                    stringResource(R.string.home_insight_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
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
            onOpenProfile = {},
            onOpenTransactions = {},
            onCreateTransaction = {},
            onManageAccounts = {},
            onOpenAnalysis = {},
            onOpenProduct = {},
        )
    }
}
