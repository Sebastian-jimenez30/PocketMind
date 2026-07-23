package com.pocketmind.presentation.home

import com.pocketmind.shared.domain.model.DashboardSummary
import com.pocketmind.shared.domain.repository.DashboardRepository
import com.pocketmind.shared.domain.usecase.ObserveDashboardSummaryUseCase
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeViewModelTest {
    @Test
    fun `starts in loading while dashboard source has no value`() {
        val repository = object : DashboardRepository {
            override fun observeSummary() = emptyFlow<DashboardSummary>()
        }

        val viewModel = HomeViewModel(ObserveDashboardSummaryUseCase(repository))

        assertEquals(HomeUiState.Loading, viewModel.uiState.value)
    }
}
