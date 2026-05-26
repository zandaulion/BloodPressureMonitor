package com.example.bpdigitizer.di

import android.content.Context
import androidx.room.Room
import com.example.bpdigitizer.data.local.BPDatabase
import com.example.bpdigitizer.data.local.BPReadingDao
import com.example.bpdigitizer.data.repository.BPRepositoryImpl
import com.example.bpdigitizer.domain.repository.BPRepository
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
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
    fun provideTextRecognizer(): TextRecognizer =
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun bindRepository(impl: BPRepositoryImpl): BPRepository
}
