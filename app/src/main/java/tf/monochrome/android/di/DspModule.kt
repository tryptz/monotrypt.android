package tf.monochrome.android.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import tf.monochrome.android.audio.dsp.DspEngineManager
import tf.monochrome.android.audio.dsp.MixBusProcessor
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DspModule {

    @Provides
    @Singleton
    fun provideMixBusProcessor(): MixBusProcessor = MixBusProcessor()

    @Provides
    @Singleton
    fun provideDspEngineManager(processor: MixBusProcessor): DspEngineManager =
        DspEngineManager(processor)
}
