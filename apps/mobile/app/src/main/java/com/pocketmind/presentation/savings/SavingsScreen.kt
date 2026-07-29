package com.pocketmind.presentation.savings

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pocketmind.R
import com.pocketmind.presentation.accounts.ProductListItem
import com.pocketmind.shared.domain.model.CurrencyCode
import com.pocketmind.shared.domain.model.Money
import com.pocketmind.ui.components.PocketContextTopBar
import com.pocketmind.ui.components.PocketPrimaryButton
import com.pocketmind.ui.theme.PocketSpacing
import java.text.NumberFormat
import java.util.Locale

@Composable
fun SavingsRoute(
    onCreateSavings: () -> Unit,
    onOpenSavings: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: SavingsViewModel = hiltViewModel(),
) {
    val savingsAccounts by viewModel.savingsAccounts.collectAsStateWithLifecycle()
    SavingsScreen(
        accounts = savingsAccounts,
        onCreateSavings = onCreateSavings,
        onOpenSavings = onOpenSavings,
        onBack = onBack,
    )
}

@Composable
fun SavingsScreen(
    accounts: List<ProductListItem>,
    onCreateSavings: () -> Unit,
    onOpenSavings: (String) -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            FloatingActionButton(
                onClick = onCreateSavings,
                containerColor = MaterialTheme.colorScheme.primary,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = stringResource(R.string.savings_create),
                    tint = MaterialTheme.colorScheme.onPrimary,
                )
            }
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            PocketContextTopBar(
                title = stringResource(R.string.savings_title),
                onBack = onBack,
                backContentDescription = stringResource(R.string.profile_back),
            )
            if (accounts.isEmpty()) {
                EmptySavings(onCreateSavings)
            } else {
                SavingsContent(accounts, onOpenSavings)
            }
        }
    }
}

@Composable
private fun EmptySavings(onCreateSavings: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(PocketSpacing.xl),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Rounded.Savings,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(PocketSpacing.md))
            Text(
                text = stringResource(R.string.savings_empty_title),
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(modifier = Modifier.height(PocketSpacing.xs))
            Text(
                text = stringResource(R.string.savings_empty_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(PocketSpacing.lg))
            PocketPrimaryButton(
                text = stringResource(R.string.savings_create),
                onClick = onCreateSavings,
            )
        }
    }
}

@Composable
private fun SavingsContent(
    accounts: List<ProductListItem>,
    onOpenSavings: (String) -> Unit,
) {
    val totalMinorUnits = accounts.sumOf { it.currentAmount.minorUnits }
    val totalMoney = Money(totalMinorUnits, accounts.firstOrNull()?.currentAmount?.currency ?: CurrencyCode.COP)

    LazyColumn(
        contentPadding = PaddingValues(PocketSpacing.xl),
        verticalArrangement = Arrangement.spacedBy(PocketSpacing.sm),
    ) {
        item(key = "total-card") {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = PocketSpacing.sm),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                ),
                shape = RoundedCornerShape(20.dp),
            ) {
                Column(
                    modifier = Modifier.padding(PocketSpacing.lg),
                ) {
                    Text(
                        text = stringResource(R.string.savings_total_label),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = formatSavingsMoney(totalMoney),
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        }

        items(accounts, key = { it.account.id }) { item ->
            SavingsAccountRow(item, onOpenSavings)
        }
    }
}

@Composable
private fun SavingsAccountRow(
    item: ProductListItem,
    onOpen: (String) -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable { onOpen(item.account.id) },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Row(
            modifier = Modifier.padding(PocketSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.Savings,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.width(PocketSpacing.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.account.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(R.string.savings_title),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = formatSavingsMoney(item.currentAmount),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                modifier = Modifier
                    .padding(start = PocketSpacing.sm)
                    .size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun formatSavingsMoney(money: Money): String {
    val format = NumberFormat.getCurrencyInstance(Locale("es", "CO"))
    format.maximumFractionDigits = 0
    return format.format(money.minorUnits)
}
