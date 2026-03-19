package tf.monochrome.android.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import tf.monochrome.android.domain.model.Track
import java.io.File
import javax.inject.Inject

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    private val appCtx: android.app.Application
) : ViewModel() {

    private val _downloadedFiles = MutableStateFlow<List<File>>(emptyList())
    val downloadedFiles: StateFlow<List<File>> = _downloadedFiles.asStateFlow()

    init {
        loadDownloads()
    }

    fun loadDownloads() {
        viewModelScope.launch {
            val downloadsDir = File(appCtx.getExternalFilesDir(null), "downloads")
            if (downloadsDir.exists()) {
                _downloadedFiles.value = downloadsDir.listFiles()?.toList() ?: emptyList()
            } else {
                _downloadedFiles.value = emptyList()
            }
        }
    }

    fun deleteDownload(file: File) {
        viewModelScope.launch {
            if (file.exists() && file.delete()) {
                loadDownloads()
            }
        }
    }
}
