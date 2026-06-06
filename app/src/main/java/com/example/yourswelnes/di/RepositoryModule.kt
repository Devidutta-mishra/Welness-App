package com.example.yourswelnes.di

import com.example.yourswelnes.feature.monitoring.data.AppMonitoringRepository
import com.example.yourswelnes.feature.monitoring.data.AppMonitoringRepositoryImpl
import com.example.yourswelnes.feature.auth.data.AuthRepository
import com.example.yourswelnes.feature.auth.data.AuthRepositoryImpl
import com.example.yourswelnes.feature.biometric.data.BiometricRepository
import com.example.yourswelnes.feature.biometric.data.BiometricRepositoryImpl
import com.example.yourswelnes.feature.dashboard.data.DashboardRepository
import com.example.yourswelnes.feature.dashboard.data.DashboardRepositoryImpl
import com.example.yourswelnes.feature.home.data.ClubRepository
import com.example.yourswelnes.feature.home.data.ClubRepositoryImpl
import com.example.yourswelnes.feature.home.data.GroupDetailsRepository
import com.example.yourswelnes.feature.home.data.GroupDetailsRepositoryImpl
import com.example.yourswelnes.feature.location.data.LocationConfigRepository
import com.example.yourswelnes.feature.location.data.LocationConfigRepositoryImpl
import com.example.yourswelnes.feature.location.data.LocationRepository
import com.example.yourswelnes.feature.location.data.LocationRepositoryImpl
import com.example.yourswelnes.feature.notifications.data.NotificationRepository
import com.example.yourswelnes.feature.notifications.data.NotificationRepositoryImpl
import com.example.yourswelnes.feature.onboarding.data.RequirementsRepository
import com.example.yourswelnes.feature.onboarding.data.RequirementsRepositoryImpl
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

    @Binds
    @Singleton
    abstract fun bindLocationConfigRepository(impl: LocationConfigRepositoryImpl): LocationConfigRepository

    @Binds
    @Singleton
    abstract fun bindLocationRepository(impl: LocationRepositoryImpl): LocationRepository

    @Binds
    @Singleton
    abstract fun bindNotificationRepository(impl: NotificationRepositoryImpl): NotificationRepository

    @Binds
    @Singleton
    abstract fun bindGroupDetailsRepository(impl: GroupDetailsRepositoryImpl): GroupDetailsRepository

    @Binds
    @Singleton
    abstract fun bindAppMonitoringRepository(impl: AppMonitoringRepositoryImpl): AppMonitoringRepository

    @Binds
    @Singleton
    abstract fun bindBiometricRepository(impl: BiometricRepositoryImpl): BiometricRepository

    @Binds
    @Singleton
    abstract fun bindRequirementsRepository(impl: RequirementsRepositoryImpl): RequirementsRepository
}
