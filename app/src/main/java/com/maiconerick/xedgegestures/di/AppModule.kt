package com.maiconerick.xedgegestures.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    // SettingsRepository, ActionExecutor, EdgeOverlayEngine are @Inject-annotated,
    // so Hilt binds them without provides methods here. This module is a hook for
    // any future providers (e.g. Retrofit client if we add remote settings sync).
}
