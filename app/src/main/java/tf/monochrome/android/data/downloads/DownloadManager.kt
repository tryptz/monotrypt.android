package tf.monochrome.android.data.downloads

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import dagger.hilt.android.qualifiers.ApplicationContext
import tf.monochrome.android.domain.model.Track
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val workManager = WorkManager.getInstance(context)

    fun downloadTrack(track: Track) {
        val inputData = workDataOf(
            DownloadWorker.KEY_TRACK_ID to track.id,
            DownloadWorker.KEY_TRACK_TITLE to track.title,
            DownloadWorker.KEY_ARTIST_NAME to (track.artist?.name ?: "Unknown Artist"),
            DownloadWorker.KEY_ALBUM_TITLE to (track.album?.title),
            DownloadWorker.KEY_ALBUM_COVER to (track.album?.cover),
            DownloadWorker.KEY_DURATION to track.duration
        )

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val downloadRequest = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(inputData)
            .setConstraints(constraints)
            .build()

        workManager.enqueueUniqueWork(
            "download_${track.id}",
            ExistingWorkPolicy.KEEP,
            downloadRequest
        )
    }
}
