package com.example.yourswelnes.di

import com.example.yourswelnes.feature.auth.data.repository.AuthRepository
import com.example.yourswelnes.feature.auth.data.repository.AuthRepositoryImpl
import com.example.yourswelnes.feature.dashboard.data.repository.DashboardRepository
import com.example.yourswelnes.feature.dashboard.data.repository.DashboardRepositoryImpl
import com.example.yourswelnes.feature.home.data.repository.ClubRepository
import com.example.yourswelnes.feature.home.data.repository.ClubRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindAuthRepository(impl: AuthRepositoryImpl): AuthRepository

    @Binds
    @Singleton
    abstract fun bindClubRepository(impl: ClubRepositoryImpl): ClubRepository

    @Binds
    @Singleton
    abstract fun bindDashboardRepository(impl: DashboardRepositoryImpl): DashboardRepository
}
