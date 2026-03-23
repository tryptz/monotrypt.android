package tf.monochrome.android.data.local.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import tf.monochrome.android.data.db.MusicDatabase
import tf.monochrome.android.data.local.db.LocalMediaDao

@Module
@InstallIn(SingletonComponent::class)
object LocalMediaModule {

    @Provides
    fun provideLocalMediaDao(db: MusicDatabase): LocalMediaDao = db.localMediaDao()
}
