package tf.monochrome.android.data.collections.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import tf.monochrome.android.data.collections.db.CollectionDao
import tf.monochrome.android.data.db.MusicDatabase

@Module
@InstallIn(SingletonComponent::class)
object CollectionModule {

    @Provides
    fun provideCollectionDao(db: MusicDatabase): CollectionDao = db.collectionDao()
}
