package com.pocketmind.presentation.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.compose.ui.res.stringResource
import com.pocketmind.R
import java.text.NumberFormat
import java.util.Locale

@Composable
fun HomeRoute(viewModel: HomeViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    HomeScreen(uiState = uiState)
}

@Composable
fun HomeScreen(uiState: HomeUiState) {
    Scaffold { paddingValues ->
        when (uiState) {
            HomeUiState.Loading -> LoadingContent(paddingValues)
            is HomeUiState.Content -> DashboardContent(paddingValues, uiState)
        }
    }
}

@Composable
private fun LoadingContent(paddingValues: PaddingValues) {
    Column(
        modifier = Modifier.fillMaxSize().padding(paddingValues),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun DashboardContent(paddingValues: PaddingValues, state: HomeUiState.Content) {
    val summary = state.summary
    Column(
        modifier = Modifier.fillMaxSize().padding(paddingValues).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.home_welcome), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(R.string.home_financial_overview), style = MaterialTheme.typography.bodyLarge)
        BalanceCard(stringResource(R.string.home_available_balance), summary.availableBalance.minorUnits, Modifier.fillMaxWidth())
        BalanceCard(stringResource(R.string.home_income), summary.monthlyIncome.minorUnits, Modifier.fillMaxWidth())
        BalanceCard(stringResource(R.string.home_expenses), summary.monthlyExpense.minorUnits, Modifier.fillMaxWidth())
        Text(
            text = stringResource(R.string.home_preview_notice),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun BalanceCard(title: String, amount: Long, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = copCurrencyFormat.format(amount),
                style = MaterialTheme.typography.headlineMedium,
            )
        }
    }
}

private val copCurrencyFormat: NumberFormat = NumberFormat.getCurrencyInstance(Locale("es", "CO"))
