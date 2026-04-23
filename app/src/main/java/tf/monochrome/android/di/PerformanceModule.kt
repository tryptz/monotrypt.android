package tf.monochrome.android.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import tf.monochrome.android.MonochromeApp
import tf.monochrome.android.performance.PerformanceProfile
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object PerformanceModule {

    /**
     * Hand out the same [PerformanceProfile] instance that was picked in
     * [MonochromeApp]'s companion-object `init`. The Kotlin coroutine
     * scheduler has already been configured from it by the time Hilt graph
     * construction starts, so every injected consumer (FFT tap, visualizer,
     * image pipeline) sees a consistent tier.
     */
    @Provides
    @Singleton
    fun providePerformanceProfile(): PerformanceProfile = MonochromeApp.profile
}
