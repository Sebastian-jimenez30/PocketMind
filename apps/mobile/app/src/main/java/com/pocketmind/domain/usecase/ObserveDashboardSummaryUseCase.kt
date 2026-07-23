package com.pocketmind.domain.usecase

import com.pocketmind.domain.model.DashboardSummary
import com.pocketmind.domain.repository.DashboardRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

/** Exposes dashboard data without coupling the presentation layer to its source. */
class ObserveDashboardSummaryUseCase @Inject constructor(
    private val dashboardRepository: DashboardRepository,
) {
    operator fun invoke(): Flow<DashboardSummary> = dashboardRepository.observeSummary()
}
