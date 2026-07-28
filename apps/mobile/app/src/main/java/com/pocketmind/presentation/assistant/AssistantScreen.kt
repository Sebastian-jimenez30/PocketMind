package com.pocketmind.presentation.assistant

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pocketmind.R
import com.pocketmind.shared.assistant.AssistantDraftPreview
import com.pocketmind.ui.components.pocketBringIntoViewOnFocus
import com.pocketmind.ui.theme.PocketSpacing
import java.text.NumberFormat
import java.util.Locale

@Composable
fun AssistantRoute(
    onBack: () -> Unit,
    viewModel: AssistantViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    AssistantScreen(
        state = state,
        onBack = onBack,
        onInputChanged = viewModel::onInputChanged,
        onSend = viewModel::send,
        onRetry = viewModel::retry,
        onDismissError = viewModel::dismissError,
    )
}

@Composable
private fun AssistantScreen(
    state: AssistantUiState,
    onBack: () -> Unit,
    onInputChanged: (String) -> Unit,
    onSend: () -> Unit,
    onRetry: () -> Unit,
    onDismissError: () -> Unit,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(state.messages.size, state.isSending, state.errorMessage) {
        val itemCount = state.messages.size +
            if (state.messages.isEmpty()) 1 else 0 +
            if (state.isSending) 1 else 0 +
            if (state.errorMessage != null) 1 else 0
        if (itemCount > 0) listState.animateScrollToItem(itemCount - 1)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .imePadding(),
    ) {
        AssistantHeader(onBack)
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(PocketSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(PocketSpacing.sm),
        ) {
            if (state.messages.isEmpty()) {
                item { AssistantWelcome() }
            }
            items(state.messages, key = AssistantUiMessage::id) { message ->
                MessageBubble(message)
            }
            if (state.isSending) {
                item {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(PocketSpacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.padding(PocketSpacing.xs))
                        Text(
                            text = stringResource(R.string.assistant_thinking),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            state.errorMessage?.let { message ->
                item {
                    ErrorCard(
                        message = message,
                        onRetry = onRetry,
                        onDismiss = onDismissError,
                    )
                }
            }
        }
        AssistantComposer(
            value = state.input,
            enabled = !state.isSending,
            onValueChange = onInputChanged,
            onSend = onSend,
        )
    }
}

@Composable
private fun AssistantHeader(onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primary)
            .statusBarsPadding()
            .padding(horizontal = PocketSpacing.sm, vertical = PocketSpacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = stringResource(R.string.assistant_back),
                tint = MaterialTheme.colorScheme.onPrimary,
            )
        }
        Text(
            text = stringResource(R.string.assistant_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onPrimary,
        )
    }
}

@Composable
private fun AssistantWelcome() {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(PocketSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(PocketSpacing.sm),
        ) {
            Icon(
                Icons.Rounded.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = stringResource(R.string.assistant_welcome_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.assistant_welcome_body),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MessageBubble(message: AssistantUiMessage) {
    val isUser = message.role == "user"
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(PocketSpacing.xs),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(if (isUser) 0.82f else 0.9f),
            shape = RoundedCornerShape(
                topStart = 20.dp,
                topEnd = 20.dp,
                bottomStart = if (isUser) 20.dp else 6.dp,
                bottomEnd = if (isUser) 6.dp else 20.dp,
            ),
            color = if (isUser) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ) {
            Text(
                text = message.content,
                modifier = Modifier.padding(PocketSpacing.md),
                color = if (isUser) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
        message.draft?.let { DraftReviewCard(it) }
    }
}

@Composable
private fun DraftReviewCard(draft: AssistantDraftPreview) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.secondary,
        ),
    ) {
        Column(
            modifier = Modifier.padding(PocketSpacing.md),
            verticalArrangement = Arrangement.spacedBy(PocketSpacing.xs),
        ) {
            Text(
                text = stringResource(R.string.assistant_review),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = draft.commandLabel(),
                style = MaterialTheme.typography.labelLarge,
            )
            Text(
                text = formatAmount(draft.amountMinorUnits, draft.currency),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = if (draft.destinationProductName == null) {
                    draft.primaryProductName
                } else {
                    "${draft.primaryProductName} → ${draft.destinationProductName}"
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            draft.merchant?.let {
                Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                text = stringResource(R.string.assistant_not_saved),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.tertiary,
            )
        }
    }
}

@Composable
private fun ErrorCard(
    message: String,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Column(Modifier.padding(PocketSpacing.md)) {
            Text(message, color = MaterialTheme.colorScheme.onErrorContainer)
            Row(modifier = Modifier.align(Alignment.End)) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.assistant_dismiss))
                }
                TextButton(onClick = onRetry) {
                    Text(stringResource(R.string.assistant_retry))
                }
            }
        }
    }
}

@Composable
private fun AssistantComposer(
    value: String,
    enabled: Boolean,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
) {
    Surface(
        tonalElevation = 4.dp,
        shadowElevation = 4.dp,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(PocketSpacing.sm),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(PocketSpacing.xs),
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .weight(1f)
                    .pocketBringIntoViewOnFocus(),
                enabled = enabled,
                placeholder = { Text(stringResource(R.string.assistant_input_hint)) },
                minLines = 1,
                maxLines = 4,
                shape = RoundedCornerShape(24.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSend() }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                ),
            )
            IconButton(
                onClick = onSend,
                enabled = enabled && value.isNotBlank(),
            ) {
                Icon(
                    Icons.AutoMirrored.Rounded.Send,
                    contentDescription = stringResource(R.string.assistant_send),
                    tint = if (enabled && value.isNotBlank()) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}

@Composable
private fun AssistantDraftPreview.commandLabel(): String = stringResource(
    when (commandType) {
        "record_income" -> R.string.assistant_income
        "record_expense" -> R.string.assistant_expense
        else -> R.string.assistant_transfer
    },
)

private fun formatAmount(amount: Long, currency: String): String =
    NumberFormat.getCurrencyInstance(
        Locale.Builder().setLanguage("es").setRegion("CO").build(),
    ).apply {
        this.currency = java.util.Currency.getInstance(currency)
        maximumFractionDigits = 0
    }.format(amount)
