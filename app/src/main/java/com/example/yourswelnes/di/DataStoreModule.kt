package com.example.yourswelnes.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

// AuthPreferencesDataStore and LocationPreferencesDataStore are @Singleton @Inject constructor
// classes — Hilt provides them automatically without explicit @Provides bindings.
@Module
@InstallIn(SingletonComponent::class)
object DataStoreModule
