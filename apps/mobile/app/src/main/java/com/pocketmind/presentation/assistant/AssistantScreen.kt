package com.pocketmind.presentation.assistant

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pocketmind.R
import com.pocketmind.shared.assistant.AssistantDraftPreview
import com.pocketmind.shared.domain.command.FinancialCommand
import com.pocketmind.shared.domain.model.CustomCategory
import com.pocketmind.shared.domain.model.TransactionCategoryId
import com.pocketmind.presentation.common.categoryLabel
import com.pocketmind.ui.components.PocketContextTopBar
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
        onConfirmDraft = viewModel::confirmDraft,
        onEditDraft = viewModel::editDraft,
        onCancelDraft = viewModel::cancelDraft,
        onRetryDraft = viewModel::retryDraftCompletion,
        onUpdateDraftEditor = viewModel::updateDraftEditor,
        onSaveDraftEditor = viewModel::saveDraftEditor,
        onCloseDraftEditor = viewModel::closeDraftEditor,
    )
}

@Composable
private fun AssistantScreen(
    state: AssistantUiState,
    onBack: () -> Unit,
    onInputChanged: (String) -> Unit,
    onSend: () -> Unit,
    onRetry: (AssistantUiMessage) -> Unit,
    onConfirmDraft: (AssistantUiDraft) -> Unit,
    onEditDraft: (AssistantUiDraft) -> Unit,
    onCancelDraft: (AssistantUiDraft) -> Unit,
    onRetryDraft: (AssistantUiDraft) -> Unit,
    onUpdateDraftEditor: (
        (AssistantDraftEditorState) -> AssistantDraftEditorState,
    ) -> Unit,
    onSaveDraftEditor: () -> Unit,
    onCloseDraftEditor: () -> Unit,
) {
    val listState = rememberLazyListState()
    val imeBottom = WindowInsets.ime.getBottom(LocalDensity.current)
    LaunchedEffect(state.messages, state.isSending, imeBottom) {
        val itemCount = state.messages.size +
            if (state.messages.isEmpty()) 1 else 0 +
            if (state.isSending) 1 else 0
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
                MessageBubble(
                    message = message,
                    draftActionsEnabled = state.activeDraftId == null && !state.isSending,
                    onConfirmDraft = onConfirmDraft,
                    onEditDraft = onEditDraft,
                    onCancelDraft = onCancelDraft,
                    onRetryDraft = onRetryDraft,
                    onRetryMessage = { onRetry(message) },
                    editor = state.draftEditor?.takeIf {
                        it.draft.preview.id == message.draft?.preview?.id
                    },
                    onUpdateDraftEditor = onUpdateDraftEditor,
                    onSaveDraftEditor = onSaveDraftEditor,
                    onCloseDraftEditor = onCloseDraftEditor,
                )
            }
            if (state.isSending) {
                item {
                    Row(
                        modifier = Modifier.padding(start = PocketSpacing.xs),
                        horizontalArrangement = Arrangement.spacedBy(PocketSpacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                        )
                        Text(
                            text = stringResource(R.string.assistant_thinking),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        AssistantComposer(
            value = state.input,
            enabled = !state.isSending && state.activeDraftId == null,
            onValueChange = onInputChanged,
            onSend = onSend,
        )
    }
}

@Composable
private fun AssistantHeader(onBack: () -> Unit) {
    PocketContextTopBar(
        title = stringResource(R.string.assistant_title),
        onBack = onBack,
        backContentDescription = stringResource(R.string.assistant_back),
    )
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
private fun MessageBubble(
    message: AssistantUiMessage,
    draftActionsEnabled: Boolean,
    onConfirmDraft: (AssistantUiDraft) -> Unit,
    onEditDraft: (AssistantUiDraft) -> Unit,
    onCancelDraft: (AssistantUiDraft) -> Unit,
    onRetryDraft: (AssistantUiDraft) -> Unit,
    onRetryMessage: () -> Unit,
    editor: AssistantDraftEditorState?,
    onUpdateDraftEditor: (
        (AssistantDraftEditorState) -> AssistantDraftEditorState,
    ) -> Unit,
    onSaveDraftEditor: () -> Unit,
    onCloseDraftEditor: () -> Unit,
) {
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
        when (message.deliveryState) {
            AssistantMessageDeliveryState.SENDING -> Unit
            AssistantMessageDeliveryState.FAILED -> {
                Row(
                    modifier = Modifier.fillMaxWidth(0.9f),
                    horizontalArrangement = Arrangement.spacedBy(PocketSpacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Rounded.WarningAmber,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                    )
                    Text(
                        text = message.deliveryError
                            ?: stringResource(R.string.assistant_message_failed),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    IconButton(onClick = onRetryMessage) {
                        Icon(
                            Icons.Rounded.Refresh,
                            contentDescription = stringResource(R.string.assistant_retry),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
            AssistantMessageDeliveryState.SENT -> Unit
        }
        message.draft?.let { draft ->
            if (editor != null) {
                DraftInlineEditor(
                    editor = editor,
                    enabled = draftActionsEnabled,
                    onUpdate = onUpdateDraftEditor,
                    onSave = onSaveDraftEditor,
                    onClose = onCloseDraftEditor,
                )
            } else {
                DraftReviewCard(
                    draft = draft,
                    actionsEnabled = draftActionsEnabled,
                    onConfirm = { onConfirmDraft(draft) },
                    onEdit = { onEditDraft(draft) },
                    onCancel = { onCancelDraft(draft) },
                    onRetry = { onRetryDraft(draft) },
                )
            }
        }
    }
}

@Composable
private fun DraftInlineEditor(
    editor: AssistantDraftEditorState,
    enabled: Boolean,
    onUpdate: ((AssistantDraftEditorState) -> AssistantDraftEditorState) -> Unit,
    onSave: () -> Unit,
    onClose: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.secondary,
        ),
    ) {
        Column(
            modifier = Modifier.padding(PocketSpacing.md),
            verticalArrangement = Arrangement.spacedBy(PocketSpacing.sm),
        ) {
            Text(
                text = stringResource(R.string.assistant_edit_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            when {
                editor.command.supportsProductSelection() -> {
                    EditorProductSelector(
                        products = editor.products,
                        selectedId = editor.productId,
                        fallbackName = editor.draft.preview.primaryProductName,
                        label = stringResource(R.string.transaction_editor_account),
                        allowEmpty = false,
                        onSelect = { productId ->
                            onUpdate {
                                it.copy(
                                    productId = productId,
                                    relatedProductId = it.relatedProductId
                                        .takeUnless { relatedId -> relatedId == productId }
                                        .orEmpty(),
                                )
                            }
                        },
                    )
                }
                editor.command.supportsProductNameEditing() -> {
                    EditorField(
                        value = editor.productName,
                        label = stringResource(R.string.account_editor_name),
                        onValueChange = { value ->
                            onUpdate { it.copy(productName = value) }
                        },
                    )
                }
                else -> {
                    Text(
                        text = editor.draft.preview.primaryProductName,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
            if (editor.command.supportsRelatedProductSelection()) {
                EditorProductSelector(
                    products = editor.relatedProducts.filter { it.id != editor.productId },
                    selectedId = editor.relatedProductId,
                    fallbackName = "",
                    label = stringResource(editor.command.relatedProductLabel()),
                    allowEmpty = editor.command.relatedProductIsOptional(),
                    onSelect = { productId ->
                        onUpdate { it.copy(relatedProductId = productId) }
                    },
                )
            }
            if (editor.command.supportsAmountEditing()) {
                EditorField(
                    value = editor.amount,
                    label = stringResource(R.string.manual_action_amount),
                    keyboardType = KeyboardType.Number,
                    onValueChange = { value ->
                        onUpdate { it.copy(amount = value.filter(Char::isDigit)) }
                    },
                )
            }
            if (editor.command.supportsMerchantEditing()) {
                EditorField(
                    value = editor.merchant,
                    label = stringResource(R.string.manual_action_merchant),
                    onValueChange = { value ->
                        onUpdate { it.copy(merchant = value) }
                    },
                )
            }
            if (editor.command.supportsCategoryEditing()) {
                EditorCategorySelector(
                    selectedCategoryId = editor.categoryId,
                    customCategories = editor.customCategories,
                    coreCategories = editor.command.editableCategories(),
                    onSelect = { categoryId ->
                        onUpdate { it.copy(categoryId = categoryId) }
                    },
                )
            }
            if (editor.command.supportsNoteEditing()) {
                EditorField(
                    value = editor.note,
                    label = stringResource(R.string.transaction_editor_note),
                    onValueChange = { value ->
                        onUpdate { it.copy(note = value) }
                    },
                )
            }
            if (editor.command is FinancialCommand.RecordCardPurchase) {
                EditorField(
                    value = editor.installmentCount,
                    label = stringResource(R.string.manual_action_installments),
                    keyboardType = KeyboardType.Number,
                    onValueChange = { value ->
                        onUpdate {
                            it.copy(installmentCount = value.filter(Char::isDigit))
                        }
                    },
                )
            }
            if (editor.command.supportsRateEditing()) {
                EditorField(
                    value = editor.annualRatePercent,
                    label = stringResource(R.string.manual_action_rate_label),
                    keyboardType = KeyboardType.Decimal,
                    onValueChange = { value ->
                        onUpdate {
                            it.copy(
                                annualRatePercent = value.filter { character ->
                                    character.isDigit() ||
                                        character == ',' ||
                                        character == '.'
                                },
                            )
                        }
                    },
                )
            }
            editor.error?.let { error ->
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Button(
                onClick = onSave,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.assistant_save_changes))
            }
            TextButton(
                onClick = onClose,
                enabled = enabled,
                modifier = Modifier.align(Alignment.End),
            ) {
                Text(stringResource(R.string.assistant_close_editor))
            }
        }
    }
}

@Composable
private fun EditorCategorySelector(
    selectedCategoryId: String,
    customCategories: List<CustomCategory>,
    coreCategories: List<TransactionCategoryId>,
    onSelect: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(PocketSpacing.xs)) {
        Text(
            text = stringResource(R.string.transaction_editor_category),
            style = MaterialTheme.typography.labelLarge,
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(PocketSpacing.sm)) {
            items(customCategories, key = CustomCategory::id) { category ->
                FilterChip(
                    selected = selectedCategoryId == category.id,
                    onClick = { onSelect(category.id) },
                    label = { Text(category.name) },
                )
            }
            items(coreCategories, key = { it.name }) { category ->
                FilterChip(
                    selected = selectedCategoryId == category.name,
                    onClick = { onSelect(category.name) },
                    label = { Text(categoryLabel(category.name, customCategories)) },
                )
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun EditorProductSelector(
    products: List<com.pocketmind.shared.domain.model.FinancialAccount>,
    selectedId: String,
    fallbackName: String,
    label: String,
    allowEmpty: Boolean,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedName = products.firstOrNull { it.id == selectedId }?.name
        ?: fallbackName
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        OutlinedTextField(
            value = selectedName,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(
                    androidx.compose.material3.ExposedDropdownMenuAnchorType
                        .PrimaryNotEditable,
                    enabled = products.isNotEmpty(),
                ),
            shape = RoundedCornerShape(16.dp),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = RoundedCornerShape(16.dp),
        ) {
            if (allowEmpty) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.manual_action_no_source)) },
                    onClick = {
                        onSelect("")
                        expanded = false
                    },
                )
            }
            products.forEach { product ->
                DropdownMenuItem(
                    text = { Text(product.name) },
                    onClick = {
                        onSelect(product.id)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun EditorField(
    value: String,
    label: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier
            .fillMaxWidth()
            .pocketBringIntoViewOnFocus(),
        label = { Text(label) },
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
    )
}

@Composable
private fun DraftReviewCard(
    draft: AssistantUiDraft,
    actionsEnabled: Boolean,
    onConfirm: () -> Unit,
    onEdit: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
) {
    val preview = draft.preview
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
                text = stringResource(
                    if (draft.isReversibleMovement) {
                        R.string.assistant_movement
                    } else {
                        R.string.assistant_review
                    },
                ),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = preview.commandLabel(),
                style = MaterialTheme.typography.labelLarge,
            )
            preview.amountMinorUnits?.let { amount ->
                preview.currency?.let { currency ->
                    Text(
                        text = formatAmount(amount, currency),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            val destinationProductName = preview.destinationProductName
            Text(
                text = if (
                    preview.commandType == "transfer" &&
                    destinationProductName != null
                ) {
                    "${preview.primaryProductName} → $destinationProductName"
                } else {
                    preview.primaryProductName
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (
                preview.commandType != "transfer" &&
                destinationProductName != null
            ) {
                Text(
                    text = stringResource(
                        R.string.assistant_related_product,
                        destinationProductName,
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            preview.merchant?.let {
                Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            preview.productType?.let {
                Text(
                    text = stringResource(
                        R.string.assistant_product_type_detail,
                        stringResource(it.productTypeLabelResource()),
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            preview.installmentCount?.let {
                Text(
                    text = stringResource(R.string.assistant_installments_detail, it),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            preview.annualRateBasisPoints?.let {
                Text(
                    text = stringResource(
                        R.string.assistant_rate_detail,
                        formatBasisPoints(it),
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            preview.paymentType?.let {
                Text(
                    text = stringResource(
                        R.string.assistant_payment_type_detail,
                        stringResource(it.paymentTypeLabelResource()),
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            preview.movementType?.let {
                Text(
                    text = stringResource(
                        R.string.assistant_movement_type_detail,
                        stringResource(it.movementTypeLabelResource()),
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val statusText = when {
                draft.isReversibleMovement &&
                    draft.state == AssistantDraftUiState.PROCESSING -> null
                draft.isReversibleMovement &&
                    draft.state == AssistantDraftUiState.COMPLETED -> null
                draft.isReversibleMovement &&
                    draft.state == AssistantDraftUiState.PROPOSED &&
                    draft.message == null -> null
                else -> draft.message ?: stringResource(
                    when (draft.state) {
                        AssistantDraftUiState.PROPOSED,
                        AssistantDraftUiState.PROCESSING,
                        -> R.string.assistant_not_saved
                        AssistantDraftUiState.COMPLETED ->
                            R.string.assistant_draft_completed
                        AssistantDraftUiState.CANCELLED ->
                            R.string.assistant_draft_cancelled
                        AssistantDraftUiState.FAILED ->
                            R.string.assistant_draft_failed
                        AssistantDraftUiState.COMPLETION_PENDING ->
                            R.string.assistant_draft_completion_pending
                    },
                )
            }
            statusText?.let { value ->
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodySmall,
                    color = when (draft.state) {
                        AssistantDraftUiState.FAILED ->
                            MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            when (draft.state) {
                AssistantDraftUiState.PROPOSED -> {
                    if (!draft.isReversibleMovement) {
                        Button(
                            onClick = onConfirm,
                            enabled = actionsEnabled,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.assistant_confirm))
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (draft.isReversibleMovement) {
                            IconButton(
                                onClick = onConfirm,
                                enabled = actionsEnabled,
                            ) {
                                Icon(
                                    Icons.Rounded.Refresh,
                                    contentDescription = stringResource(
                                        R.string.assistant_retry,
                                    ),
                                )
                            }
                        }
                        TextButton(onClick = onCancel, enabled = actionsEnabled) {
                            Text(stringResource(R.string.assistant_cancel))
                        }
                        TextButton(onClick = onEdit, enabled = actionsEnabled) {
                            Text(stringResource(R.string.assistant_edit))
                        }
                    }
                }
                AssistantDraftUiState.COMPLETED -> {
                    if (draft.isReversibleMovement) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            TextButton(onClick = onCancel, enabled = actionsEnabled) {
                                Text(stringResource(R.string.assistant_cancel))
                            }
                            TextButton(onClick = onEdit, enabled = actionsEnabled) {
                                Text(stringResource(R.string.assistant_edit))
                            }
                        }
                    }
                }
                AssistantDraftUiState.PROCESSING -> Unit
                AssistantDraftUiState.COMPLETION_PENDING -> {
                    Button(
                        onClick = onRetry,
                        enabled = actionsEnabled,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.assistant_verify))
                    }
                }
                AssistantDraftUiState.FAILED -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(onClick = onCancel, enabled = actionsEnabled) {
                            Text(stringResource(R.string.assistant_cancel))
                        }
                        TextButton(onClick = onEdit, enabled = actionsEnabled) {
                            Text(stringResource(R.string.assistant_edit))
                        }
                    }
                }
                AssistantDraftUiState.CANCELLED -> Unit
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
    val inputDescription = stringResource(R.string.assistant_input_description)
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val fieldShape = MaterialTheme.shapes.medium
    val borderColor = if (isFocused) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outline
    }

    Surface(
        tonalElevation = 4.dp,
        shadowElevation = 4.dp,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = PocketSpacing.sm,
                    vertical = PocketSpacing.xs,
                ),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(PocketSpacing.xs),
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .weight(1f)
                    .pocketBringIntoViewOnFocus()
                    .semantics { contentDescription = inputDescription },
                enabled = enabled,
                minLines = 1,
                maxLines = 3,
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                interactionSource = interactionSource,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSend() }),
                decorationBox = { innerTextField ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = PocketSpacing.touchTarget)
                            .border(1.dp, borderColor, fieldShape)
                            .padding(
                                horizontal = PocketSpacing.md,
                                vertical = PocketSpacing.xs,
                            ),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        innerTextField()
                    }
                },
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
        "transfer" -> R.string.assistant_transfer
        "create_product" -> R.string.assistant_create_product
        "update_product" -> R.string.assistant_update_product
        "archive_product" -> R.string.assistant_archive_product
        "record_card_purchase" -> R.string.assistant_card_purchase
        "record_card_payment" -> R.string.assistant_card_payment
        "record_savings_movement" -> R.string.assistant_savings_movement
        "record_loan_payment" -> R.string.assistant_loan_payment
        "update_transaction" -> R.string.assistant_update_transaction
        "delete_transaction" -> R.string.assistant_delete_transaction
        else -> R.string.assistant_proposal
    },
)

private fun formatAmount(amount: Long, currency: String): String =
    NumberFormat.getCurrencyInstance(
        Locale.Builder().setLanguage("es").setRegion("CO").build(),
    ).apply {
        this.currency = java.util.Currency.getInstance(currency)
        maximumFractionDigits = 0
    }.format(amount)

private fun formatBasisPoints(value: Int): String =
    NumberFormat.getNumberInstance(
        Locale.Builder().setLanguage("es").setRegion("CO").build(),
    ).apply {
        minimumFractionDigits = 0
        maximumFractionDigits = 2
    }.format(value / 100.0)

private fun String.productTypeLabelResource(): Int = when (this) {
    "CASH" -> R.string.assistant_product_cash
    "BANK_ACCOUNT" -> R.string.assistant_product_bank
    "SAVINGS" -> R.string.assistant_product_savings
    "CREDIT_CARD" -> R.string.assistant_product_card
    "LOAN" -> R.string.assistant_product_loan
    else -> R.string.assistant_unknown_detail
}

private fun String.paymentTypeLabelResource(): Int = when (this) {
    "SCHEDULED_INSTALLMENT" -> R.string.assistant_payment_scheduled
    "FULL_BALANCE" -> R.string.assistant_payment_full
    "EXTRA_PRINCIPAL" -> R.string.assistant_payment_principal
    else -> R.string.assistant_payment_custom
}

private fun String.movementTypeLabelResource(): Int = when (this) {
    "DEPOSIT" -> R.string.assistant_savings_deposit
    "WITHDRAWAL" -> R.string.assistant_savings_withdrawal
    "RATE_CHANGE" -> R.string.assistant_savings_rate_change
    else -> R.string.assistant_unknown_detail
}

private fun FinancialCommand.supportsProductNameEditing(): Boolean =
    this is FinancialCommand.CreateProduct ||
        this is FinancialCommand.UpdateProduct

private fun FinancialCommand.supportsProductSelection(): Boolean = when (this) {
    is FinancialCommand.RecordIncome,
    is FinancialCommand.RecordExpense,
    is FinancialCommand.Transfer,
    is FinancialCommand.RecordCardPurchase,
    is FinancialCommand.RecordCardPayment,
    is FinancialCommand.RecordSavingsMovement,
    is FinancialCommand.RecordLoanPayment,
    is FinancialCommand.UpdateTransaction,
    -> true
    is FinancialCommand.CreateProduct,
    is FinancialCommand.UpdateProduct,
    is FinancialCommand.ArchiveProduct,
    is FinancialCommand.DeleteTransaction,
    -> false
}

private fun FinancialCommand.supportsAmountEditing(): Boolean = when (this) {
    is FinancialCommand.ArchiveProduct,
    is FinancialCommand.DeleteTransaction,
    -> false
    is FinancialCommand.RecordSavingsMovement ->
        movementType != com.pocketmind.shared.domain.model.SavingsMovementType.RATE_CHANGE
    else -> true
}

private fun FinancialCommand.supportsMerchantEditing(): Boolean =
    this is FinancialCommand.RecordIncome ||
        this is FinancialCommand.RecordExpense ||
        this is FinancialCommand.Transfer ||
        this is FinancialCommand.RecordCardPurchase ||
        this is FinancialCommand.UpdateTransaction

private fun FinancialCommand.supportsCategoryEditing(): Boolean =
    this is FinancialCommand.RecordIncome ||
        this is FinancialCommand.RecordExpense ||
        this is FinancialCommand.Transfer ||
        this is FinancialCommand.RecordCardPurchase ||
        this is FinancialCommand.UpdateTransaction

private fun FinancialCommand.editableCategories(): List<TransactionCategoryId> = when (this) {
    is FinancialCommand.RecordIncome ->
        listOf(
            TransactionCategoryId.SALARY,
            TransactionCategoryId.FREELANCE,
            TransactionCategoryId.TRANSFER,
            TransactionCategoryId.OTHER,
        )
    is FinancialCommand.Transfer ->
        listOf(TransactionCategoryId.TRANSFER, TransactionCategoryId.OTHER)
    else -> TransactionCategoryId.entries.filterNot {
        it == TransactionCategoryId.SALARY ||
            it == TransactionCategoryId.FREELANCE ||
            it == TransactionCategoryId.TRANSFER
    }
}

private fun FinancialCommand.supportsRelatedProductSelection(): Boolean =
    this is FinancialCommand.Transfer ||
        this is FinancialCommand.RecordCardPayment ||
        this is FinancialCommand.RecordLoanPayment ||
        (
            this is FinancialCommand.RecordSavingsMovement &&
                movementType !=
                com.pocketmind.shared.domain.model.SavingsMovementType.RATE_CHANGE
        )

private fun FinancialCommand.relatedProductIsOptional(): Boolean =
    this !is FinancialCommand.Transfer

private fun FinancialCommand.relatedProductLabel(): Int = when (this) {
    is FinancialCommand.Transfer -> R.string.manual_record_destination
    is FinancialCommand.RecordSavingsMovement ->
        if (
            movementType ==
            com.pocketmind.shared.domain.model.SavingsMovementType.WITHDRAWAL
        ) {
            R.string.manual_record_destination
        } else {
            R.string.manual_action_source
        }
    else -> R.string.manual_action_source
}

private fun FinancialCommand.supportsNoteEditing(): Boolean =
    this is FinancialCommand.RecordIncome ||
        this is FinancialCommand.RecordExpense ||
        this is FinancialCommand.Transfer ||
        this is FinancialCommand.RecordCardPurchase ||
        this is FinancialCommand.RecordCardPayment ||
        this is FinancialCommand.RecordSavingsMovement ||
        this is FinancialCommand.RecordLoanPayment ||
        this is FinancialCommand.UpdateTransaction

private fun FinancialCommand.supportsRateEditing(): Boolean = when (this) {
    is FinancialCommand.CreateProduct ->
        configuration.supportsRateEditing()
    is FinancialCommand.UpdateProduct ->
        configuration.supportsRateEditing()
    is FinancialCommand.RecordSavingsMovement ->
        movementType == com.pocketmind.shared.domain.model.SavingsMovementType.RATE_CHANGE
    else -> false
}

private fun com.pocketmind.shared.domain.model.FinancialProductConfiguration
    .supportsRateEditing(): Boolean = when (this) {
        com.pocketmind.shared.domain.model.FinancialProductConfiguration.Standard -> false
        is com.pocketmind.shared.domain.model.FinancialProductConfiguration.Savings ->
            profile.type != com.pocketmind.shared.domain.model.SavingsProductType.SIMPLE
        else -> true
    }
