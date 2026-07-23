package com.pocketmind.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pocketmind.domain.model.DashboardSummary
import com.pocketmind.domain.usecase.ObserveDashboardSummaryUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class HomeViewModel @Inject constructor(
    observeDashboardSummary: ObserveDashboardSummaryUseCase,
) : ViewModel() {
    val uiState: StateFlow<HomeUiState> = observeDashboardSummary()
        .map<DashboardSummary, HomeUiState> { HomeUiState.Content(it) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
            initialValue = HomeUiState.Loading,
        )
}
