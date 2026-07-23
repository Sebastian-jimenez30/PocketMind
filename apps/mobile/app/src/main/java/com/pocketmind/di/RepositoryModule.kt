package com.pocketmind.di

import com.pocketmind.data.repository.RoomDashboardRepository
import com.pocketmind.data.repository.RoomTransactionRepository
import com.pocketmind.data.auth.AuthRepository
import com.pocketmind.data.auth.SupabaseAuthRepository
import com.pocketmind.shared.domain.repository.DashboardRepository
import com.pocketmind.shared.domain.repository.TransactionRepository
import com.pocketmind.shared.domain.usecase.ObserveDashboardSummaryUseCase
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Binds data-layer implementations to domain-layer contracts. */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun bindDashboardRepository(
        implementation: RoomDashboardRepository,
    ): DashboardRepository

    @Binds
    @Singleton
    abstract fun bindTransactionRepository(implementation: RoomTransactionRepository): TransactionRepository

    @Binds
    @Singleton
    abstract fun bindAuthRepository(implementation: SupabaseAuthRepository): AuthRepository

    companion object {
        @Provides
        fun provideObserveDashboardSummaryUseCase(
            dashboardRepository: DashboardRepository,
        ): ObserveDashboardSummaryUseCase = ObserveDashboardSummaryUseCase(dashboardRepository)
    }
}
