package com.pocketmind.presentation.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.pocketmind.R
import com.pocketmind.ui.components.PocketBrandMark
import com.pocketmind.ui.theme.PocketMindTheme
import com.pocketmind.ui.theme.PocketSpacing

/** Brand-only startup state shown while session and local data are resolved. */
@Composable
fun StartupScreen(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        PocketBrandMark(
            modifier = Modifier.size(PocketSpacing.startupBrandMark),
            contentDescription = stringResource(R.string.brand_mark_description),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun StartupScreenPreview() {
    PocketMindTheme {
        StartupScreen()
    }
}
