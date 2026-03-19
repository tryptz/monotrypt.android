package tf.monochrome.android.auto

import android.media.browse.MediaBrowser
import android.os.Bundle
import android.service.media.MediaBrowserService

class MonochromeMediaBrowserService : MediaBrowserService() {

    override fun onCreate() {
        super.onCreate()
    }

    override fun onGetRoot(
        clientPackageName: String,
        clientUid: Int,
        rootHints: Bundle?
    ): BrowserRoot {
        return BrowserRoot("monochrome_root", null)
    }

    override fun onLoadChildren(
        parentId: String,
        result: Result<MutableList<MediaBrowser.MediaItem>>
    ) {
        result.sendResult(mutableListOf())
    }
}
