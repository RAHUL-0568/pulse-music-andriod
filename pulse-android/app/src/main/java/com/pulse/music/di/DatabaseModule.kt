package com.pulse.music.di

import android.content.Context
import androidx.room.Room
import com.pulse.music.data.local.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "pulse_music_db"
        ).fallbackToDestructiveMigration()
         .build()
    }

    @Provides
    fun provideSongDao(database: AppDatabase): SongDao = database.songDao()

    @Provides
    fun providePlaylistDao(database: AppDatabase): PlaylistDao = database.playlistDao()



    @Provides
    fun provideLikedSongDao(database: AppDatabase): LikedSongDao = database.likedSongDao()

    @Provides
    fun provideFollowedArtistDao(database: AppDatabase): FollowedArtistDao = database.followedArtistDao()
}
