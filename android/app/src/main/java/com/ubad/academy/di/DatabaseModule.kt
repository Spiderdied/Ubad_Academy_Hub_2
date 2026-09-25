package com.ubad.academy.di

import android.content.Context
import androidx.room.Room
import com.ubad.academy.data.local.db.UbadDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides @Singleton
    fun database(@ApplicationContext context: Context): UbadDatabase =
        Room.databaseBuilder(context, UbadDatabase::class.java, "ubad.db").build()
}
