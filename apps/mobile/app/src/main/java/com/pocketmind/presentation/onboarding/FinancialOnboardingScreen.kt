package com.pocketmind.presentation.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pocketmind.R
import com.pocketmind.shared.domain.model.FinancialAccountType
import com.pocketmind.shared.domain.model.Recurrence
import com.pocketmind.shared.domain.model.SavingsPlanType
import com.pocketmind.ui.components.PocketBrandMark
import com.pocketmind.ui.components.PocketContentSheet
import com.pocketmind.ui.components.PocketChoiceChip
import com.pocketmind.ui.components.PocketMessage
import com.pocketmind.ui.components.PocketPrimaryButton
import com.pocketmind.ui.components.pocketBringIntoViewOnFocus
import com.pocketmind.ui.theme.PocketSpacing

@Composable
fun FinancialOnboardingRoute(
    viewModel: FinancialOnboardingViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    FinancialOnboardingScreen(
        state = state,
        onPrevious = viewModel::previous,
        onNext = viewModel::next,
        onSave = viewModel::save,
        onUpdate = viewModel::update,
    )
}

@Composable
fun FinancialOnboardingScreen(
    state: FinancialOnboardingUiState,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSave: () -> Unit,
    onUpdate: ((FinancialOnboardingUiState) -> FinancialOnboardingUiState) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primary),
    ) {
        OnboardingHeader(step = state.step)
        PocketContentSheet(modifier = Modifier.weight(1f)) {
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(PocketSpacing.xl),
                verticalArrangement = Arrangement.spacedBy(PocketSpacing.lg),
            ) {
                OnboardingStep(state, onUpdate)
                state.error?.let { PocketMessage(it, isError = true) }
                Spacer(Modifier.weight(1f, fill = false))
                OnboardingActions(
                    step = state.step,
                    isSaving = state.isSaving,
                    onPrevious = onPrevious,
                    onNext = onNext,
                    onSave = onSave,
                )
            }
        }
    }
}

@Composable
private fun OnboardingHeader(step: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().statusBarsPadding()
            .padding(horizontal = PocketSpacing.lg, vertical = PocketSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PocketSpacing.md),
    ) {
        PocketBrandMark(
            modifier = Modifier.size(42.dp),
            contentDescription = stringResource(R.string.brand_mark_description),
        )
        Column {
            Text(stringResource(R.string.onboarding_header_title), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimary)
            Text(
                stringResource(R.string.onboarding_progress, step + 1, 6),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.82f),
            )
        }
    }
}

@Composable
private fun OnboardingStep(
    state: FinancialOnboardingUiState,
    onUpdate: ((FinancialOnboardingUiState) -> FinancialOnboardingUiState) -> Unit,
) {
    when (state.step) {
        0 -> IntroStep()
        1 -> AccountStep(state, onUpdate)
        2 -> IncomeStep(state, onUpdate)
        3 -> DebtStep(state, onUpdate)
        4 -> SavingsStep(state, onUpdate)
        else -> ObligationStep(state, onUpdate)
    }
}

@Composable
private fun IntroStep() {
    StepTitle(R.string.onboarding_intro_title, R.string.onboarding_intro_description)
    Text(stringResource(R.string.onboarding_intro_detail), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun AccountStep(state: FinancialOnboardingUiState, update: ((FinancialOnboardingUiState) -> FinancialOnboardingUiState) -> Unit) {
    StepTitle(R.string.onboarding_account_title, R.string.onboarding_account_description)
    TextField(state.accountName, { value -> update { it.copy(accountName = value) } }, R.string.onboarding_account_name, R.string.onboarding_account_name_example, KeyboardType.Text)
    MoneyField(state.accountBalance, { value -> update { it.copy(accountBalance = value) } }, R.string.onboarding_account_balance, R.string.onboarding_account_balance_example)
    AccountTypeSelector(state.accountType) { type -> update { it.copy(accountType = type) } }
    OptionalHint()
}

@Composable
private fun IncomeStep(state: FinancialOnboardingUiState, update: ((FinancialOnboardingUiState) -> FinancialOnboardingUiState) -> Unit) {
    StepTitle(R.string.onboarding_income_title, R.string.onboarding_income_description)
    TextField(state.incomeName, { value -> update { it.copy(incomeName = value) } }, R.string.onboarding_income_name, R.string.onboarding_income_name_example, KeyboardType.Text)
    MoneyField(state.incomeAmount, { value -> update { it.copy(incomeAmount = value) } }, R.string.onboarding_income_amount, R.string.onboarding_income_amount_example)
    RecurrenceSelector(state.incomeRecurrence) { recurrence -> update { it.copy(incomeRecurrence = recurrence) } }
    OptionalHint()
}

@Composable
private fun DebtStep(state: FinancialOnboardingUiState, update: ((FinancialOnboardingUiState) -> FinancialOnboardingUiState) -> Unit) {
    StepTitle(R.string.onboarding_debt_title, R.string.onboarding_debt_description)
    TextField(state.debtName, { value -> update { it.copy(debtName = value) } }, R.string.onboarding_debt_name, R.string.onboarding_debt_name_example, KeyboardType.Text)
    MoneyField(state.debtBalance, { value -> update { it.copy(debtBalance = value) } }, R.string.onboarding_debt_balance, R.string.onboarding_debt_balance_example)
    MoneyField(state.debtInstallment, { value -> update { it.copy(debtInstallment = value) } }, R.string.onboarding_debt_installment, R.string.onboarding_debt_installment_example)
    TextField(state.debtInterestRate, { value -> update { it.copy(debtInterestRate = value) } }, R.string.onboarding_debt_interest_rate, R.string.onboarding_debt_interest_rate_example, KeyboardType.Decimal)
    TextField(state.debtDueDay, { value -> update { it.copy(debtDueDay = value) } }, R.string.onboarding_due_day, R.string.onboarding_due_day_example, KeyboardType.Number)
    OptionalHint()
}

@Composable
private fun SavingsStep(state: FinancialOnboardingUiState, update: ((FinancialOnboardingUiState) -> FinancialOnboardingUiState) -> Unit) {
    StepTitle(R.string.onboarding_savings_title, R.string.onboarding_savings_description)
    TextField(state.savingsName, { value -> update { it.copy(savingsName = value) } }, R.string.onboarding_savings_name, R.string.onboarding_savings_name_example, KeyboardType.Text)
    MoneyField(state.savingsAmount, { value -> update { it.copy(savingsAmount = value) } }, R.string.onboarding_savings_amount, R.string.onboarding_savings_amount_example)
    MoneyField(state.savingsMonthlyContribution, { value -> update { it.copy(savingsMonthlyContribution = value) } }, R.string.onboarding_savings_contribution, R.string.onboarding_savings_contribution_example)
    MoneyField(state.savingsTargetAmount, { value -> update { it.copy(savingsTargetAmount = value) } }, R.string.onboarding_savings_target, R.string.onboarding_savings_target_example)
    TextField(state.savingsAnnualYield, { value -> update { it.copy(savingsAnnualYield = value) } }, R.string.onboarding_savings_yield, R.string.onboarding_savings_yield_example, KeyboardType.Decimal)
    SavingsTypeSelector(state.savingsType) { type -> update { it.copy(savingsType = type) } }
    OptionalHint()
}

@Composable
private fun ObligationStep(state: FinancialOnboardingUiState, update: ((FinancialOnboardingUiState) -> FinancialOnboardingUiState) -> Unit) {
    StepTitle(R.string.onboarding_obligation_title, R.string.onboarding_obligation_description)
    TextField(state.obligationName, { value -> update { it.copy(obligationName = value) } }, R.string.onboarding_obligation_name, R.string.onboarding_obligation_name_example, KeyboardType.Text)
    MoneyField(state.obligationAmount, { value -> update { it.copy(obligationAmount = value) } }, R.string.onboarding_obligation_amount, R.string.onboarding_obligation_amount_example)
    TextField(state.obligationDueDay, { value -> update { it.copy(obligationDueDay = value) } }, R.string.onboarding_due_day, R.string.onboarding_due_day_example, KeyboardType.Number)
    OptionalHint()
}

@Composable
private fun StepTitle(title: Int, description: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(PocketSpacing.xs)) {
        Text(stringResource(title), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(description), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun TextField(value: String, onChange: (String) -> Unit, label: Int, placeholder: Int, keyboardType: KeyboardType) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(stringResource(label)) },
        placeholder = { Text(stringResource(placeholder)) },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().pocketBringIntoViewOnFocus(),
    )
}

@Composable
private fun MoneyField(value: String, onChange: (String) -> Unit, label: Int, placeholder: Int) = TextField(value, onChange, label, placeholder, KeyboardType.Number)

@Composable
private fun RecurrenceSelector(selected: Recurrence, onSelect: (Recurrence) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(PocketSpacing.sm)) {
        Text(stringResource(R.string.onboarding_income_frequency), style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(PocketSpacing.sm), verticalArrangement = Arrangement.spacedBy(PocketSpacing.sm)) {
            listOf(Recurrence.MONTHLY, Recurrence.BIWEEKLY, Recurrence.VARIABLE).forEach { recurrence ->
                PocketChoiceChip(stringResource(recurrence.labelRes()), recurrence == selected, onClick = { onSelect(recurrence) })
            }
        }
    }
}

private fun Recurrence.labelRes(): Int = when (this) {
    Recurrence.MONTHLY -> R.string.onboarding_recurrence_monthly
    Recurrence.BIWEEKLY -> R.string.onboarding_recurrence_biweekly
    Recurrence.VARIABLE -> R.string.onboarding_recurrence_variable
    Recurrence.WEEKLY -> R.string.onboarding_recurrence_weekly
}

@Composable
private fun AccountTypeSelector(selected: FinancialAccountType, onSelect: (FinancialAccountType) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(PocketSpacing.sm)) {
        Text(stringResource(R.string.onboarding_account_type), style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(PocketSpacing.sm), verticalArrangement = Arrangement.spacedBy(PocketSpacing.sm)) {
            listOf(FinancialAccountType.BANK_ACCOUNT, FinancialAccountType.CASH, FinancialAccountType.SAVINGS).forEach { type ->
                PocketChoiceChip(stringResource(type.labelRes()), type == selected, onClick = { onSelect(type) })
            }
        }
    }
}

private fun FinancialAccountType.labelRes(): Int = when (this) {
    FinancialAccountType.BANK_ACCOUNT -> R.string.onboarding_account_type_bank
    FinancialAccountType.CASH -> R.string.onboarding_account_type_cash
    FinancialAccountType.SAVINGS -> R.string.onboarding_account_type_savings
    FinancialAccountType.CREDIT_CARD -> R.string.onboarding_account_type_card
    FinancialAccountType.LOAN -> R.string.onboarding_account_type_loan
}

@Composable
private fun SavingsTypeSelector(selected: SavingsPlanType, onSelect: (SavingsPlanType) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(PocketSpacing.sm)) {
        Text(stringResource(R.string.onboarding_savings_type), style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(PocketSpacing.sm), verticalArrangement = Arrangement.spacedBy(PocketSpacing.sm)) {
            listOf(
            SavingsPlanType.FLEXIBLE,
            SavingsPlanType.GOAL,
            SavingsPlanType.TERM_DEPOSIT,
            SavingsPlanType.POCKET,
            ).forEach { type ->
                PocketChoiceChip(stringResource(type.labelRes()), type == selected, onClick = { onSelect(type) })
            }
        }
    }
}

private fun SavingsPlanType.labelRes(): Int = when (this) {
    SavingsPlanType.FLEXIBLE -> R.string.onboarding_savings_type_flexible
    SavingsPlanType.GOAL -> R.string.onboarding_savings_type_goal
    SavingsPlanType.TERM_DEPOSIT -> R.string.onboarding_savings_type_cdt
    SavingsPlanType.POCKET -> R.string.onboarding_savings_type_pocket
}

@Composable
private fun OptionalHint() {
    PocketMessage(stringResource(R.string.onboarding_optional_hint), isError = false)
}

@Composable
private fun OnboardingActions(step: Int, isSaving: Boolean, onPrevious: () -> Unit, onNext: () -> Unit, onSave: () -> Unit) {
    HorizontalDivider()
    Row(horizontalArrangement = Arrangement.spacedBy(PocketSpacing.md), modifier = Modifier.fillMaxWidth()) {
        if (step > 0) {
            OutlinedButton(onClick = onPrevious, enabled = !isSaving, modifier = Modifier.weight(1f).height(PocketSpacing.primaryButtonHeight)) {
                Text(stringResource(R.string.onboarding_back))
            }
        }
        PocketPrimaryButton(
            text = stringResource(if (step == 5) R.string.onboarding_finish else R.string.onboarding_continue),
            onClick = if (step == 5) onSave else onNext,
            loading = isSaving,
            modifier = Modifier.weight(1f),
        )
    }
}
