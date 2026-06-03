package com.example.yourswelnes.di

import com.example.yourswelnes.feature.monitoring.data.repository.AppMonitoringRepository
import com.example.yourswelnes.feature.monitoring.data.repository.AppMonitoringRepositoryImpl
import com.example.yourswelnes.feature.auth.data.repository.AuthRepository
import com.example.yourswelnes.feature.auth.data.repository.AuthRepositoryImpl
import com.example.yourswelnes.feature.biometric.data.repository.BiometricRepository
import com.example.yourswelnes.feature.biometric.data.repository.BiometricRepositoryImpl
import com.example.yourswelnes.feature.dashboard.data.repository.DashboardRepository
import com.example.yourswelnes.feature.dashboard.data.repository.DashboardRepositoryImpl
import com.example.yourswelnes.feature.home.data.repository.ClubRepository
import com.example.yourswelnes.feature.home.data.repository.ClubRepositoryImpl
import com.example.yourswelnes.feature.home.data.repository.GroupDetailsRepository
import com.example.yourswelnes.feature.home.data.repository.GroupDetailsRepositoryImpl
import com.example.yourswelnes.feature.location.data.repository.LocationConfigRepository
import com.example.yourswelnes.feature.location.data.repository.LocationConfigRepositoryImpl
import com.example.yourswelnes.feature.location.data.repository.LocationRepository
import com.example.yourswelnes.feature.location.data.repository.LocationRepositoryImpl
import com.example.yourswelnes.feature.notifications.data.repository.NotificationRepository
import com.example.yourswelnes.feature.notifications.data.repository.NotificationRepositoryImpl
import com.example.yourswelnes.feature.requirements.data.repository.RequirementsRepository
import com.example.yourswelnes.feature.requirements.data.repository.RequirementsRepositoryImpl
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
