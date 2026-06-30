package tf.monochrome.android.ui.navigation

import android.net.Uri
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object AppDeepLinkRouter {
    private val _uris = MutableSharedFlow<Uri>(
        replay = 1,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val uris: SharedFlow<Uri> = _uris.asSharedFlow()

    fun offer(uri: Uri) {
        _uris.tryEmit(uri)
    }
}
