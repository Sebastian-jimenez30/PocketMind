package com.pocketmind.presentation.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pocketmind.R
import com.pocketmind.ui.theme.PocketSpacing
import com.pocketmind.shared.domain.model.CustomCategory
import com.pocketmind.shared.domain.model.RecommendedCategories
import com.pocketmind.shared.domain.model.TransactionCategoryId


@Composable
fun categoryLabel(
    categoryId: String,
    customCategories: List<CustomCategory> = emptyList(),
): String {
    val custom = customCategories.find { it.id == categoryId }
    if (custom != null) {
        return custom.name
    }
    val enumCategory = runCatching { TransactionCategoryId.valueOf(categoryId) }.getOrNull()
    if (enumCategory != null) {
        return stringResource(
            when (enumCategory) {
                TransactionCategoryId.SALARY -> R.string.category_salary
                TransactionCategoryId.FREELANCE -> R.string.category_freelance
                TransactionCategoryId.TRANSFER -> R.string.category_transfer
                TransactionCategoryId.FOOD -> R.string.category_food
                TransactionCategoryId.TRANSPORT -> R.string.category_transport
                TransactionCategoryId.HOME -> R.string.category_home
                TransactionCategoryId.HEALTH -> R.string.category_health
                TransactionCategoryId.EDUCATION -> R.string.category_education
                TransactionCategoryId.ENTERTAINMENT -> R.string.category_entertainment
                TransactionCategoryId.SHOPPING -> R.string.category_shopping
                TransactionCategoryId.SERVICES -> R.string.category_services
                TransactionCategoryId.DEBT_PAYMENT -> R.string.category_debt_payment
                TransactionCategoryId.SAVINGS -> R.string.category_savings
                TransactionCategoryId.OTHER -> R.string.category_other
            }
        )
    }
    return categoryId
}

@Composable
fun DynamicCategorySelector(
    selectedCategoryId: String,
    onSelectCategory: (String) -> Unit,
    customCategories: List<CustomCategory> = emptyList(),
    allowedCoreCategories: List<TransactionCategoryId> = listOf(
        TransactionCategoryId.FOOD,
        TransactionCategoryId.TRANSPORT,
        TransactionCategoryId.SERVICES,
        TransactionCategoryId.HEALTH,
        TransactionCategoryId.ENTERTAINMENT,
    ),
    onCreateCustomCategory: (String, (String) -> Unit) -> Unit = { _, _ -> },
    onUpdateCustomCategory: (String, String) -> Unit = { _, _ -> },
    onDeleteCustomCategory: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var isCreateDialogOpen by remember { mutableStateOf(false) }
    var categoryToEdit by remember { mutableStateOf<CustomCategory?>(null) }
    var categoryToDelete by remember { mutableStateOf<CustomCategory?>(null) }

    val selectedCustomCategory = remember(selectedCategoryId, customCategories) {
        customCategories.find { it.id == selectedCategoryId }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(PocketSpacing.xs),
    ) {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(PocketSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items(customCategories) { customCategory ->
                FilterChip(
                    selected = selectedCategoryId == customCategory.id,
                    onClick = { onSelectCategory(customCategory.id) },
                    label = { Text(customCategory.name) },
                )
            }

            items(allowedCoreCategories) { coreCategory ->
                FilterChip(
                    selected = selectedCategoryId == coreCategory.name,
                    onClick = { onSelectCategory(coreCategory.name) },
                    label = { Text(categoryLabel(coreCategory.name, customCategories)) },
                )
            }

            item {
                FilterChip(
                    selected = false,
                    onClick = { isCreateDialogOpen = true },
                    label = {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Add,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Text(stringResource(R.string.category_other_create))
                        }
                    },
                )
            }
        }

        if (selectedCustomCategory != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(PocketSpacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = { categoryToEdit = selectedCustomCategory },
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Edit,
                        contentDescription = null,
                        modifier = Modifier
                            .size(16.dp)
                            .padding(end = 4.dp),
                    )
                    Text(stringResource(R.string.custom_category_edit_title))
                }

                TextButton(
                    onClick = { categoryToDelete = selectedCustomCategory },
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Delete,
                        contentDescription = null,
                        modifier = Modifier
                            .size(16.dp)
                            .padding(end = 4.dp),
                        tint = MaterialTheme.colorScheme.error,
                    )
                    Text(
                        text = stringResource(R.string.custom_category_delete_title),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }

    if (isCreateDialogOpen) {
        CreateCustomCategoryDialog(
            onDismiss = { isCreateDialogOpen = false },
            onCreate = { name ->
                onCreateCustomCategory(name) { newId ->
                    onSelectCategory(newId)
                }
                isCreateDialogOpen = false
            },
        )
    }

    categoryToEdit?.let { customCategory ->
        EditCustomCategoryDialog(
            category = customCategory,
            onDismiss = { categoryToEdit = null },
            onSave = { newName ->
                onUpdateCustomCategory(customCategory.id, newName)
                categoryToEdit = null
            },
        )
    }

    categoryToDelete?.let { customCategory ->
        DeleteCustomCategoryConfirmDialog(
            categoryName = customCategory.name,
            onDismiss = { categoryToDelete = null },
            onConfirm = {
                onDeleteCustomCategory(customCategory.id)
                categoryToDelete = null
            },
        )
    }
}

@Composable
fun CreateCustomCategoryDialog(
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit,
) {
    var nameText by remember { mutableStateOf("") }
    val recommended = remember { RecommendedCategories.COMMON_SUGGESTIONS }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.custom_category_new_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(PocketSpacing.md),
                modifier = Modifier.fillMaxWidth(),
            ) {
                OutlinedTextField(
                    value = nameText,
                    onValueChange = { nameText = it },
                    label = { Text(stringResource(R.string.custom_category_name_label)) },
                    placeholder = { Text(stringResource(R.string.custom_category_name_placeholder)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Column(verticalArrangement = Arrangement.spacedBy(PocketSpacing.xs)) {
                    Text(
                        text = stringResource(R.string.custom_category_recommended_title),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(PocketSpacing.xs),
                    ) {
                        items(recommended) { option ->
                            FilterChip(
                                selected = nameText.trim().equals(option, ignoreCase = true),
                                onClick = { nameText = option },
                                label = { Text(option) },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (nameText.isNotBlank()) {
                        onCreate(nameText.trim())
                    }
                },
                enabled = nameText.isNotBlank(),
            ) {
                Text(stringResource(R.string.custom_category_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.assistant_dismiss))
            }
        },
    )
}

@Composable
fun EditCustomCategoryDialog(
    category: CustomCategory,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var nameText by remember(category) { mutableStateOf(category.name) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.custom_category_edit_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(PocketSpacing.sm),
            ) {
                OutlinedTextField(
                    value = nameText,
                    onValueChange = { nameText = it },
                    label = { Text(stringResource(R.string.custom_category_name_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (nameText.isNotBlank()) {
                        onSave(nameText.trim())
                    }
                },
                enabled = nameText.isNotBlank() && nameText.trim() != category.name,
            ) {
                Text(stringResource(R.string.custom_category_update_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.assistant_dismiss))
            }
        },
    )
}

@Composable
fun DeleteCustomCategoryConfirmDialog(
    categoryName: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.custom_category_delete_title)) },
        text = {
            Text(
                text = "${stringResource(R.string.custom_category_delete_confirm)} \"$categoryName\"",
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = stringResource(R.string.budgets_delete_action),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.assistant_dismiss))
            }
        },
    )
}
