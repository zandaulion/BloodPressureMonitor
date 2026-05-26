package com.example.bpdigitizer.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import com.example.bpdigitizer.data.local.BPDatabase
import com.example.bpdigitizer.data.local.BPReadingDao
import com.example.bpdigitizer.data.repository.ApiKeyRepositoryImpl
import com.example.bpdigitizer.data.repository.BPRepositoryImpl
import com.example.bpdigitizer.data.repository.ReminderRepositoryImpl
import com.example.bpdigitizer.data.repository.UserProfileRepositoryImpl
import com.example.bpdigitizer.domain.repository.ApiKeyRepository
import com.example.bpdigitizer.domain.repository.BPRepository
import com.example.bpdigitizer.domain.repository.ReminderRepository
import com.example.bpdigitizer.domain.repository.UserProfileRepository
import com.example.bpdigitizer.data.ocr.TesseractRecognizer
import kotlinx.serialization.json.Json
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): BPDatabase =
        Room.databaseBuilder(context, BPDatabase::class.java, "bp.db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideDao(db: BPDatabase): BPReadingDao = db.readingDao()

    @Provides
    @Singleton
    fun provideTesseractRecognizer(@ApplicationContext context: Context): TesseractRecognizer =
        TesseractRecognizer(context)

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        prettyPrint = true
        encodeDefaults = true
    }

    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            produceFile = { File(context.filesDir, "datastore/user_profile.preferences_pb") },
        )
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun bindBpRepository(impl: BPRepositoryImpl): BPRepository

    @Binds
    @Singleton
    abstract fun bindUserProfileRepository(impl: UserProfileRepositoryImpl): UserProfileRepository

    @Binds
    @Singleton
    abstract fun bindReminderRepository(impl: ReminderRepositoryImpl): ReminderRepository

    @Binds
    @Singleton
    abstract fun bindApiKeyRepository(impl: ApiKeyRepositoryImpl): ApiKeyRepository
}
