package com.pocketmind.presentation.home

import com.pocketmind.domain.model.DashboardSummary

sealed interface HomeUiState {
    data object Loading : HomeUiState
    data class Content(val summary: DashboardSummary) : HomeUiState
}
