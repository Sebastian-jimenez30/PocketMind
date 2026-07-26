package com.pocketmind.di

import com.pocketmind.data.repository.RoomDashboardRepository
import com.pocketmind.data.repository.RoomFinancialAccountRepository
import com.pocketmind.data.repository.RoomFinancialSetupRepository
import com.pocketmind.data.repository.RoomTransactionRepository
import com.pocketmind.data.auth.AuthRepository
import com.pocketmind.data.auth.SupabaseAuthRepository
import com.pocketmind.data.profile.ProfileRepository
import com.pocketmind.data.profile.SupabaseProfileRepository
import com.pocketmind.shared.domain.repository.DashboardRepository
import com.pocketmind.shared.domain.repository.FinancialAccountRepository
import com.pocketmind.shared.domain.repository.FinancialSetupRepository
import com.pocketmind.shared.domain.repository.TransactionRepository
import com.pocketmind.shared.domain.usecase.ObserveDashboardSummaryUseCase
import com.pocketmind.shared.domain.usecase.GetFinancialAccountUseCase
import com.pocketmind.shared.domain.usecase.GetTransactionUseCase
import com.pocketmind.shared.domain.usecase.ObserveActiveFinancialAccountsUseCase
import com.pocketmind.shared.domain.usecase.CreateTransactionUseCase
import com.pocketmind.shared.domain.usecase.ObserveFinancialSetupCompletedUseCase
import com.pocketmind.shared.domain.usecase.SaveInitialFinancialSetupUseCase
import com.pocketmind.shared.domain.usecase.SaveFinancialAccountUseCase
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
    abstract fun bindFinancialAccountRepository(
        implementation: RoomFinancialAccountRepository,
    ): FinancialAccountRepository

    @Binds
    @Singleton
    abstract fun bindFinancialSetupRepository(
        implementation: RoomFinancialSetupRepository,
    ): FinancialSetupRepository

    @Binds
    @Singleton
    abstract fun bindAuthRepository(implementation: SupabaseAuthRepository): AuthRepository

    @Binds
    @Singleton
    abstract fun bindProfileRepository(implementation: SupabaseProfileRepository): ProfileRepository

    companion object {
        @Provides
        fun provideObserveDashboardSummaryUseCase(
            dashboardRepository: DashboardRepository,
        ): ObserveDashboardSummaryUseCase = ObserveDashboardSummaryUseCase(dashboardRepository)

        @Provides
        fun provideObserveFinancialSetupCompletedUseCase(
            repository: FinancialSetupRepository,
        ): ObserveFinancialSetupCompletedUseCase = ObserveFinancialSetupCompletedUseCase(repository)

        @Provides
        fun provideSaveInitialFinancialSetupUseCase(
            repository: FinancialSetupRepository,
        ): SaveInitialFinancialSetupUseCase = SaveInitialFinancialSetupUseCase(repository)

        @Provides
        fun provideObserveActiveFinancialAccountsUseCase(
            repository: FinancialAccountRepository,
        ): ObserveActiveFinancialAccountsUseCase = ObserveActiveFinancialAccountsUseCase(repository)

        @Provides
        fun provideGetFinancialAccountUseCase(
            repository: FinancialAccountRepository,
        ): GetFinancialAccountUseCase = GetFinancialAccountUseCase(repository)

        @Provides
        fun provideSaveFinancialAccountUseCase(
            repository: FinancialAccountRepository,
        ): SaveFinancialAccountUseCase = SaveFinancialAccountUseCase(repository)

        @Provides
        fun provideGetTransactionUseCase(
            repository: TransactionRepository,
        ): GetTransactionUseCase = GetTransactionUseCase(repository)

        @Provides
        fun provideCreateTransactionUseCase(
            repository: TransactionRepository,
        ): CreateTransactionUseCase = CreateTransactionUseCase(repository)
    }
}
