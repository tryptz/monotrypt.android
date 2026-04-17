package tf.monochrome.android.domain.usecase

import kotlinx.coroutines.flow.Flow
import tf.monochrome.android.data.local.repository.LocalMediaRepository
import tf.monochrome.android.data.local.scanner.ScanProgress
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScanLocalMediaUseCase @Inject constructor(
    private val localMediaRepository: LocalMediaRepository
) {
    fun fullScan(
        minDurationMs: Long = 30_000,
        excludedPaths: Set<String> = emptySet()
    ): Flow<ScanProgress> = localMediaRepository.fullScan(minDurationMs, excludedPaths)

    fun incrementalScan(
        minDurationMs: Long = 30_000
    ): Flow<ScanProgress> = localMediaRepository.incrementalScan(minDurationMs)
}
