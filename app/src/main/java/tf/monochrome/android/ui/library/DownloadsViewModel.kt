package tf.monochrome.android.ui.library

import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import tf.monochrome.android.data.db.dao.DownloadDao
import tf.monochrome.android.data.db.entity.DownloadedTrackEntity
import java.io.File
import javax.inject.Inject

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    private val appCtx: android.app.Application,
    private val downloadDao: DownloadDao
) : ViewModel() {

    val downloadedTracks: StateFlow<List<DownloadedTrackEntity>> =
        downloadDao.getDownloadedTracks()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun deleteDownload(track: DownloadedTrackEntity) {
        viewModelScope.launch {
            if (track.filePath.startsWith("content://")) {
                try {
                    val uri = track.filePath.toUri()
                    val docFile = DocumentFile.fromSingleUri(appCtx, uri)
                    docFile?.delete()
                } catch (e: Exception) {
                    // Ignore exceptions during content deletion
                }
            } else {
                val file = File(track.filePath)
                if (file.exists()) file.delete()
            }
            downloadDao.deleteDownloadedTrack(track.id)
        }
    }
}
