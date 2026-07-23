package com.pocketmind.di

import com.pocketmind.data.repository.PreviewDashboardRepository
import com.pocketmind.domain.repository.DashboardRepository
import dagger.Binds
import dagger.Module
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
        implementation: PreviewDashboardRepository,
    ): DashboardRepository
}
