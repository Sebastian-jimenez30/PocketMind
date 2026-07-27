package com.pocketmind.presentation.accounts

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
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pocketmind.R
import com.pocketmind.shared.domain.model.CurrencyCode
import com.pocketmind.shared.domain.model.FinancialAccount
import com.pocketmind.shared.domain.model.FinancialAccountType
import com.pocketmind.shared.domain.model.Money
import com.pocketmind.ui.components.PocketPrimaryButton
import com.pocketmind.ui.theme.PocketMindTheme
import com.pocketmind.ui.theme.PocketSpacing
import java.text.NumberFormat
import java.util.Locale

@Composable
fun AccountsRoute(onCreate: () -> Unit, onOpen: (String) -> Unit, onBack: () -> Unit, viewModel: AccountsViewModel = hiltViewModel()) {
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    AccountsScreen(accounts, onCreate, onOpen, onBack)
}

@Composable
fun AccountsScreen(accounts: List<ProductListItem>, onCreate: () -> Unit, onOpen: (String) -> Unit, onBack: () -> Unit) {
    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = onCreate, containerColor = MaterialTheme.colorScheme.primary) {
                Icon(Icons.Rounded.Add, stringResource(R.string.accounts_add), tint = MaterialTheme.colorScheme.onPrimary)
            }
        },
    ) { paddingValues ->
        Column(Modifier.fillMaxSize().padding(paddingValues)) {
            AccountsHeader(onBack)
            if (accounts.isEmpty()) {
                EmptyAccounts(onCreate)
            } else {
                ProductsList(accounts, onOpen)
            }
        }
    }
}

@Composable
private fun ProductsList(accounts: List<ProductListItem>, onOpen: (String) -> Unit) {
    val sections = listOf(
        R.string.accounts_section_money to accounts.filter {
            it.account.type == FinancialAccountType.BANK_ACCOUNT || it.account.type == FinancialAccountType.CASH
        },
        R.string.accounts_section_cards to accounts.filter { it.account.type == FinancialAccountType.CREDIT_CARD },
        R.string.accounts_section_savings to accounts.filter { it.account.type == FinancialAccountType.SAVINGS },
        R.string.accounts_section_loans to accounts.filter { it.account.type == FinancialAccountType.LOAN },
    ).filter { it.second.isNotEmpty() }
    LazyColumn(
        contentPadding = PaddingValues(PocketSpacing.xl),
        verticalArrangement = Arrangement.spacedBy(PocketSpacing.sm),
    ) {
        sections.forEach { (title, products) ->
            item(key = "section-$title") {
                Text(
                    stringResource(title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(top = PocketSpacing.sm, bottom = PocketSpacing.xs),
                )
            }
            items(products, key = { it.account.id }) { account -> AccountRow(account, onOpen) }
        }
    }
}

@Composable
private fun AccountsHeader(onBack: () -> Unit) = Row(
    modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primary).statusBarsPadding()
        .padding(horizontal = PocketSpacing.sm, vertical = PocketSpacing.xxs),
    verticalAlignment = Alignment.CenterVertically,
) {
    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.accounts_back), tint = MaterialTheme.colorScheme.onPrimary) }
    Spacer(Modifier.width(PocketSpacing.xs))
    Text(stringResource(R.string.accounts_title), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimary)
}

@Composable
private fun EmptyAccounts(onCreate: () -> Unit) = Column(
    modifier = Modifier.fillMaxSize().padding(PocketSpacing.xl),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
) {
    Icon(Icons.Rounded.AccountBalanceWallet, null, Modifier.size(56.dp), MaterialTheme.colorScheme.primary)
    Spacer(Modifier.height(PocketSpacing.md))
    Text(stringResource(R.string.accounts_empty_title), style = MaterialTheme.typography.titleLarge)
    Spacer(Modifier.height(PocketSpacing.xs))
    Text(stringResource(R.string.accounts_empty_description), color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(PocketSpacing.lg))
    PocketPrimaryButton(stringResource(R.string.accounts_add), onCreate)
}

@Composable
private fun AccountRow(item: ProductListItem, onOpen: (String) -> Unit) = Card(
    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).clickable { onOpen(item.account.id) },
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
) {
    Row(Modifier.padding(PocketSpacing.md), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            when (item.account.type) {
                FinancialAccountType.CREDIT_CARD -> Icons.Rounded.CreditCard
                FinancialAccountType.SAVINGS -> Icons.Rounded.Savings
                FinancialAccountType.LOAN -> Icons.Rounded.Payments
                else -> Icons.Rounded.AccountBalanceWallet
            },
            null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(PocketSpacing.md))
        Column(Modifier.weight(1f)) {
            Text(
                item.account.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(stringResource(item.account.type.labelRes()), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            formatMoney(item.currentAmount),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
        Icon(Icons.Rounded.ChevronRight, stringResource(R.string.accounts_open), Modifier.padding(start = PocketSpacing.sm).size(20.dp), MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

internal fun FinancialAccountType.labelRes(): Int = when (this) {
    FinancialAccountType.CASH -> R.string.account_type_cash
    FinancialAccountType.BANK_ACCOUNT -> R.string.account_type_bank
    FinancialAccountType.SAVINGS -> R.string.account_type_savings
    FinancialAccountType.CREDIT_CARD -> R.string.account_type_card
    FinancialAccountType.LOAN -> R.string.account_type_loan
}

private fun formatMoney(money: Money): String = NumberFormat.getCurrencyInstance(
    Locale.Builder().setLanguage("es").setRegion("CO").build(),
).apply { maximumFractionDigits = 0 }.format(money.minorUnits)

@Preview(showBackground = true)
@Composable
private fun AccountsPreview() = PocketMindTheme {
    AccountsScreen(
        listOf(
            ProductListItem(
                FinancialAccount("1", "Cuenta principal", FinancialAccountType.BANK_ACCOUNT, CurrencyCode.COP, Money(500_000, CurrencyCode.COP)),
                Money(500_000, CurrencyCode.COP),
            ),
        ),
        {}, {}, {},
    )
}
