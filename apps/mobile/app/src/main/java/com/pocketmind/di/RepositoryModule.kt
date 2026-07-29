package com.pocketmind.di

import com.pocketmind.data.repository.RoomDashboardRepository
import com.pocketmind.data.repository.RoomFinancialAccountRepository
import com.pocketmind.data.repository.RoomFinancialSetupRepository
import com.pocketmind.data.repository.RoomManualFinanceRepository
import com.pocketmind.data.repository.RoomTransactionRepository
import com.pocketmind.data.time.JavaTimeCreditCardPaymentDateCalculator
import com.pocketmind.data.auth.AuthRepository
import com.pocketmind.data.auth.SupabaseAuthRepository
import com.pocketmind.data.profile.ProfileRepository
import com.pocketmind.data.profile.SupabaseProfileRepository
import com.pocketmind.data.sync.SessionBootstrapper
import com.pocketmind.data.sync.SyncCoordinator
import com.pocketmind.shared.domain.repository.DashboardRepository
import com.pocketmind.shared.domain.repository.FinancialAccountRepository
import com.pocketmind.shared.domain.repository.FinancialSetupRepository
import com.pocketmind.shared.domain.repository.ManualFinanceRepository
import com.pocketmind.shared.domain.repository.TransactionRepository
import com.pocketmind.shared.domain.usecase.ObserveDashboardSummaryUseCase
import com.pocketmind.shared.domain.usecase.GetFinancialAccountUseCase
import com.pocketmind.shared.domain.usecase.GetTransactionUseCase
import com.pocketmind.data.repository.RoomBudgetRepository
import com.pocketmind.data.repository.RoomCustomCategoryRepository
import com.pocketmind.shared.domain.repository.BudgetRepository
import com.pocketmind.shared.domain.repository.CustomCategoryRepository
import com.pocketmind.shared.domain.usecase.CreateBudgetUseCase
import com.pocketmind.shared.domain.usecase.DeleteBudgetUseCase
import com.pocketmind.shared.domain.usecase.DeleteCustomCategoryUseCase
import com.pocketmind.shared.domain.usecase.ObserveBudgetSummariesUseCase
import com.pocketmind.shared.domain.usecase.ObserveCustomCategoriesUseCase
import com.pocketmind.shared.domain.usecase.SaveCustomCategoryUseCase
import com.pocketmind.shared.domain.usecase.UpdateBudgetUseCase
import com.pocketmind.shared.domain.usecase.ObserveActiveFinancialAccountsUseCase
import com.pocketmind.shared.domain.usecase.CreditCardPaymentDateCalculator
import com.pocketmind.shared.domain.usecase.ExecuteFinancialCommandUseCase
import com.pocketmind.shared.domain.usecase.ObserveFinancialSetupCompletedUseCase
import com.pocketmind.shared.domain.usecase.SaveInitialFinancialSetupUseCase
import com.pocketmind.shared.domain.usecase.ManualFinanceUseCases
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
    abstract fun bindManualFinanceRepository(
        implementation: RoomManualFinanceRepository,
    ): ManualFinanceRepository

    @Binds
    @Singleton
    abstract fun bindBudgetRepository(
        implementation: RoomBudgetRepository,
    ): BudgetRepository

    @Binds
    @Singleton
    abstract fun bindCustomCategoryRepository(
        implementation: RoomCustomCategoryRepository,
    ): CustomCategoryRepository

    @Binds
    @Singleton
    abstract fun bindAuthRepository(implementation: SupabaseAuthRepository): AuthRepository

    @Binds
    @Singleton
    abstract fun bindProfileRepository(implementation: SupabaseProfileRepository): ProfileRepository

    @Binds
    @Singleton
    abstract fun bindSessionBootstrapper(implementation: SyncCoordinator): SessionBootstrapper

    @Binds
    @Singleton
    abstract fun bindCreditCardPaymentDateCalculator(
        implementation: JavaTimeCreditCardPaymentDateCalculator,
    ): CreditCardPaymentDateCalculator

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
        fun provideGetTransactionUseCase(
            repository: TransactionRepository,
        ): GetTransactionUseCase = GetTransactionUseCase(repository)

        @Provides
        fun provideManualFinanceUseCases(
            repository: ManualFinanceRepository,
        ): ManualFinanceUseCases = ManualFinanceUseCases(repository)

        @Provides
        @Singleton
        fun provideExecuteFinancialCommandUseCase(
            accountRepository: FinancialAccountRepository,
            transactionRepository: TransactionRepository,
            manualFinanceRepository: ManualFinanceRepository,
            cardPaymentDateCalculator: CreditCardPaymentDateCalculator,
        ): ExecuteFinancialCommandUseCase = ExecuteFinancialCommandUseCase(
            accountRepository = accountRepository,
            transactionRepository = transactionRepository,
            manualFinanceRepository = manualFinanceRepository,
            cardPaymentDateCalculator = cardPaymentDateCalculator,
        )

        @Provides
        fun provideObserveBudgetSummariesUseCase(
            budgetRepository: BudgetRepository,
            transactionRepository: TransactionRepository,
        ): ObserveBudgetSummariesUseCase = ObserveBudgetSummariesUseCase(budgetRepository, transactionRepository)

        @Provides
        fun provideCreateBudgetUseCase(
            budgetRepository: BudgetRepository,
        ): CreateBudgetUseCase = CreateBudgetUseCase(budgetRepository)

        @Provides
        fun provideUpdateBudgetUseCase(
            budgetRepository: BudgetRepository,
        ): UpdateBudgetUseCase = UpdateBudgetUseCase(budgetRepository)

        @Provides
        fun provideDeleteBudgetUseCase(
            budgetRepository: BudgetRepository,
        ): DeleteBudgetUseCase = DeleteBudgetUseCase(budgetRepository)

        @Provides
        fun provideObserveCustomCategoriesUseCase(
            repository: CustomCategoryRepository,
        ): ObserveCustomCategoriesUseCase = ObserveCustomCategoriesUseCase(repository)

        @Provides
        fun provideSaveCustomCategoryUseCase(
            repository: CustomCategoryRepository,
        ): SaveCustomCategoryUseCase = SaveCustomCategoryUseCase(repository)

        @Provides
        fun provideDeleteCustomCategoryUseCase(
            repository: CustomCategoryRepository,
        ): DeleteCustomCategoryUseCase = DeleteCustomCategoryUseCase(repository)
    }
}

