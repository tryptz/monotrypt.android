package tf.monochrome.android.player

import androidx.annotation.OptIn
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi

/**
 * Wraps an [ExoPlayer] so the Media3-managed foreground notification and
 * lock-screen controls can show working next / previous buttons even though
 * the underlying player only ever holds one [androidx.media3.common.MediaItem]
 * at a time.
 *
 * The app resolves stream URLs one-track-at-a-time (TIDAL DASH MPDs have short
 * TTLs; batch-resolving a long queue up front wastes requests and ages
 * stream URLs before they're used). So ExoPlayer's own playlist is never the
 * source of truth for queue position — [QueueManager] is. Media3's notification
 * only surfaces SEEK_TO_NEXT / SEEK_TO_PREVIOUS when the wrapped player claims
 * the commands are available and has a next / previous media item, which is
 * why the default setup hides the buttons.
 *
 * This forwarder:
 *  - Always advertises SEEK_TO_NEXT / SEEK_TO_PREVIOUS as available commands
 *    when [QueueManager] has more tracks in that direction.
 *  - Re-routes `seekToNext*` / `seekToPrevious*` calls to [onNext] / [onPrev]
 *    (the service's skipToNext / skipToPrevious), which handle queue
 *    advancement plus stream-URL resolution.
 */
@OptIn(UnstableApi::class)
class QueueForwardingPlayer(
    delegate: Player,
    private val queueManager: QueueManager,
    private val onNext: () -> Unit,
    private val onPrev: () -> Unit,
) : ForwardingPlayer(delegate) {

    override fun getAvailableCommands(): Player.Commands {
        val base = super.getAvailableCommands()
        return Player.Commands.Builder()
            .addAll(base)
            .addAll(
                Player.COMMAND_SEEK_TO_NEXT,
                Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                Player.COMMAND_SEEK_TO_PREVIOUS,
                Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
            )
            .build()
    }

    override fun isCommandAvailable(command: Int): Boolean = when (command) {
        Player.COMMAND_SEEK_TO_NEXT,
        Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM -> queueManager.hasNext()
        Player.COMMAND_SEEK_TO_PREVIOUS,
        Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> queueManager.hasPrevious()
        else -> super.isCommandAvailable(command)
    }

    override fun hasNextMediaItem(): Boolean = queueManager.hasNext()
    override fun hasPreviousMediaItem(): Boolean = queueManager.hasPrevious()

    override fun seekToNextMediaItem() = onNext()
    override fun seekToPreviousMediaItem() = onPrev()

    override fun seekToNext() = onNext()
    override fun seekToPrevious() = onPrev()
}
