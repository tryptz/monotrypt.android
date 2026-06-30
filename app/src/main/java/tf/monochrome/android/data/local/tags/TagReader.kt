package tf.monochrome.android.data.local.tags

import android.content.Context
import android.graphics.BitmapFactory
import android.annotation.SuppressLint
import android.media.MediaMetadataRetriever
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import tf.monochrome.android.domain.model.AudioCodec
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.charset.Charset
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

data class AudioTags(
    // Core identity
    val title: String? = null,
    val artist: String? = null,
    val albumArtist: String? = null,
    val album: String? = null,
    val genre: String? = null,
    val year: Int? = null,
    val trackNumber: Int? = null,
    val trackTotal: Int? = null,
    val discNumber: Int? = null,
    val discTotal: Int? = null,

    // Extended metadata
    val composer: String? = null,
    val comment: String? = null,
    val lyrics: String? = null,
    val isrc: String? = null,
    val musicBrainzTrack: String? = null,
    val musicBrainzAlbum: String? = null,

    // Audio properties
    val codec: AudioCodec = AudioCodec.UNKNOWN,
    val sampleRate: Int = 44100,
    val bitDepth: Int? = null,
    val bitRate: Int = 0,
    val channels: Int = 2,
    val durationSeconds: Int = 0,

    // Replay Gain (parsed from custom tags when available)
    val replayGainTrack: Float? = null,
    val replayGainAlbum: Float? = null,
    val r128TrackGain: Int? = null,
    val r128AlbumGain: Int? = null,

    // Artwork
    val hasEmbeddedArt: Boolean = false,
    val artworkCacheKey: String? = null,

    // File info
    val filePath: String = "",
    val fileSizeBytes: Long = 0,
    val lastModified: Long = 0
)

@Singleton
class TagReader @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val artworkCacheDir: File by lazy {
        File(context.cacheDir, "artwork").also { it.mkdirs() }
    }

    suspend fun readTags(
        filePath: String,
        folderArtCache: MutableMap<String, String?>? = null
    ): AudioTags {
        val file = File(filePath)
        if (!file.exists()) return AudioTags(filePath = filePath)

        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(filePath)
            extractTags(retriever, file, folderArtCache)
        } catch (e: Exception) {
            AudioTags(
                filePath = filePath,
                fileSizeBytes = file.length(),
                lastModified = file.lastModified(),
                codec = detectCodecFromExtension(filePath)
            )
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }
    }

    suspend fun readTagsFromUri(uri: Uri): AudioTags {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            extractTagsFromRetriever(retriever, uri.toString(), 0, 0)
        } catch (e: Exception) {
            AudioTags()
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }
    }

    private fun extractTags(
        retriever: MediaMetadataRetriever,
        file: File,
        folderArtCache: MutableMap<String, String?>? = null
    ): AudioTags {
        return extractTagsFromRetriever(
            retriever,
            file.absolutePath,
            file.length(),
            file.lastModified(),
            folderArtCache
        )
    }

    @SuppressLint("InlinedApi") // METADATA_KEY_SAMPLERATE/BITS_PER_SAMPLE: returns null on <31
    private fun extractTagsFromRetriever(
        retriever: MediaMetadataRetriever,
        filePath: String,
        fileSize: Long,
        lastModified: Long,
        folderArtCache: MutableMap<String, String?>? = null
    ): AudioTags {
        val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
        val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
        val albumArtist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)
        val album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
        val genre = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE)
        val composer = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_COMPOSER)
        val yearStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR)
        val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0
        val bitRateStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)
        val mimeType = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)
        val numChannels = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_NUM_TRACKS)
        val sampleRateStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)
        val bitsPerSample = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITS_PER_SAMPLE)

        // Parse track number (handles "3/12" format)
        val trackInfo = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)
        val (trackNumber, trackTotal) = parseTrackNumber(trackInfo)
        val discInfo = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DISC_NUMBER)
        val (discNumber, discTotal) = parseTrackNumber(discInfo)

        // Determine codec
        val codec = detectCodec(mimeType, filePath)

        // Extract and cache artwork. For MP4/M4A this reads the `covr` atom;
        // for FLAC/Vorbis/Opus this reads the METADATA_BLOCK_PICTURE/coverart;
        // for ID3v2 this reads APIC. If nothing is embedded, fall back to a
        // sidecar image in the track's folder (cover.jpg, folder.jpg, etc.).
        val artworkBytes = retriever.embeddedPicture
        val hasArt = artworkBytes != null
        val artworkCacheKey = when {
            artworkBytes != null -> cacheArtwork(artworkBytes, filePath)
            // Per-track sidecar: an image next to the audio file with the
            // same stem (e.g. "song.flac" + "song.jpg"). yt-dlp, Bandcamp,
            // and rip workflows all produce this convention. Checked before
            // folder-level cover.* / albumart.* fallback.
            else -> findPerTrackSidecar(filePath)
                ?: findFolderCoverArt(filePath, folderArtCache)
        }
        val hasArtOrSidecar = artworkCacheKey != null

        // Many sideloaded files (yt-dlp, loose rips) carry no ARTIST tag and
        // stuff the whole "Artist - Title" string into the title. When the
        // artist tag is genuinely absent, recover it from that convention so
        // the library doesn't show a wall of "Unknown Artist".
        val tagArtist = artist?.takeIf { it.isNotBlank() }
        var finalArtist = tagArtist
        var finalTitle = title?.takeIf { it.isNotBlank() }
        if (tagArtist == null) {
            val (derivedArtist, derivedTitle) = deriveArtistFromTitle(title, filePath)
            if (derivedArtist != null) {
                finalArtist = derivedArtist
                finalTitle = derivedTitle
            }
        }
        val extended = readExtendedTags(filePath)

        return AudioTags(
            title = finalTitle,
            artist = finalArtist,
            albumArtist = albumArtist,
            album = album,
            genre = genre,
            year = parseYear(yearStr),
            trackNumber = trackNumber,
            trackTotal = trackTotal,
            discNumber = discNumber,
            discTotal = discTotal,
            composer = composer,
            comment = extended.comment,
            lyrics = extended.lyrics,
            isrc = extended.isrc,
            musicBrainzTrack = extended.musicBrainzTrack,
            musicBrainzAlbum = extended.musicBrainzAlbum,
            codec = codec,
            sampleRate = sampleRateStr?.toIntOrNull() ?: 44100,
            bitDepth = bitsPerSample?.toIntOrNull(),
            bitRate = (bitRateStr?.toLongOrNull() ?: 0L).let { (it / 1000).toInt() },
            channels = numChannels?.toIntOrNull() ?: 2,
            durationSeconds = (durationMs / 1000).toInt(),
            replayGainTrack = extended.replayGainTrack,
            replayGainAlbum = extended.replayGainAlbum,
            r128TrackGain = extended.r128TrackGain,
            r128AlbumGain = extended.r128AlbumGain,
            hasEmbeddedArt = hasArtOrSidecar,
            artworkCacheKey = artworkCacheKey,
            filePath = filePath,
            fileSizeBytes = fileSize,
            lastModified = lastModified
        )
    }

    private data class ExtendedAudioTags(
        val comment: String? = null,
        val lyrics: String? = null,
        val isrc: String? = null,
        val musicBrainzTrack: String? = null,
        val musicBrainzAlbum: String? = null,
        val replayGainTrack: Float? = null,
        val replayGainAlbum: Float? = null,
        val r128TrackGain: Int? = null,
        val r128AlbumGain: Int? = null,
    )

    private fun readExtendedTags(filePath: String): ExtendedAudioTags {
        val file = File(filePath)
        if (!file.isFile) return ExtendedAudioTags()

        val values = when (file.extension.lowercase()) {
            "flac" -> readFlacVorbisComments(file)
            "mp3" -> readId3v2Tags(file).ifEmpty { readVisibleKeyValueTags(file) }
            "ogg", "oga", "opus" -> readVisibleKeyValueTags(file)
            else -> emptyMap()
        }
        if (values.isEmpty()) return ExtendedAudioTags()

        return ExtendedAudioTags(
            comment = values.firstTag("COMMENT", "DESCRIPTION"),
            lyrics = values.firstTag("LYRICS", "UNSYNCEDLYRICS", "USLT"),
            isrc = values.firstTag("ISRC", "TSRC"),
            musicBrainzTrack = values.firstTag(
                "MUSICBRAINZ_TRACKID",
                "MUSICBRAINZ_TRACK_ID",
                "MUSICBRAINZ_RELEASETRACKID",
                "MUSICBRAINZ_RELEASE_TRACK_ID",
                "MUSICBRAINZ TRACK ID"
            ),
            musicBrainzAlbum = values.firstTag(
                "MUSICBRAINZ_ALBUMID",
                "MUSICBRAINZ_ALBUM_ID",
                "MUSICBRAINZ RELEASE ID",
                "MUSICBRAINZ_RELEASEID"
            ),
            replayGainTrack = values.firstTag("REPLAYGAIN_TRACK_GAIN")
                ?.parseDbFloat(),
            replayGainAlbum = values.firstTag("REPLAYGAIN_ALBUM_GAIN")
                ?.parseDbFloat(),
            r128TrackGain = values.firstTag("R128_TRACK_GAIN")?.parseIntTag(),
            r128AlbumGain = values.firstTag("R128_ALBUM_GAIN")?.parseIntTag(),
        )
    }

    private fun readFlacVorbisComments(file: File): Map<String, String> = runCatching<Map<String, String>> {
        file.inputStream().use { input ->
            val marker = ByteArray(4)
            if (input.read(marker) != 4 || marker.decodeToString() != "fLaC") {
                return@use emptyMap<String, String>()
            }

            val header = ByteArray(4)
            while (input.read(header) == 4) {
                val isLast = header[0].toInt() and 0x80 != 0
                val type = header[0].toInt() and 0x7F
                val length = ((header[1].toInt() and 0xFF) shl 16) or
                    ((header[2].toInt() and 0xFF) shl 8) or
                    (header[3].toInt() and 0xFF)
                if (length <= 0) {
                    if (isLast) break
                    continue
                }

                if (type == 4) {
                    val block = input.readUpTo(length)
                    return@use parseVorbisCommentBlock(block)
                }

                input.skip(length.toLong())
                if (isLast) break
            }
            emptyMap<String, String>()
        }
    }.getOrDefault(emptyMap())

    private fun parseVorbisCommentBlock(block: ByteArray): Map<String, String> {
        var offset = 0
        fun readLeInt(): Int? {
            if (offset + 4 > block.size) return null
            val value = (block[offset].toInt() and 0xFF) or
                ((block[offset + 1].toInt() and 0xFF) shl 8) or
                ((block[offset + 2].toInt() and 0xFF) shl 16) or
                ((block[offset + 3].toInt() and 0xFF) shl 24)
            offset += 4
            return value
        }

        val vendorLength = readLeInt() ?: return emptyMap()
        if (vendorLength < 0 || offset + vendorLength > block.size) return emptyMap()
        offset += vendorLength

        val count = readLeInt() ?: return emptyMap()
        val values = linkedMapOf<String, String>()
        repeat(count.coerceAtLeast(0)) {
            val length = readLeInt() ?: return@repeat
            if (length < 0 || offset + length > block.size) return@repeat
            val raw = block.copyOfRange(offset, offset + length).toString(Charsets.UTF_8)
            offset += length
            val separator = raw.indexOf('=')
            if (separator > 0) {
                values.putIfAbsent(
                    normalizeTagKey(raw.substring(0, separator)),
                    raw.substring(separator + 1).trim()
                )
            }
        }
        return values
    }

    private fun readId3v2Tags(file: File): Map<String, String> = runCatching<Map<String, String>> {
        file.inputStream().use { input ->
            val header = input.readUpTo(10)
            if (header.size < 10 || header[0] != 'I'.code.toByte() ||
                header[1] != 'D'.code.toByte() || header[2] != '3'.code.toByte()
            ) {
                return@use emptyMap<String, String>()
            }

            val majorVersion = header[3].toInt() and 0xFF
            if (majorVersion !in 3..4) return@use emptyMap<String, String>()
            val tagSize = syncSafeInt(header, 6).coerceAtMost(1_048_576)
            val data = input.readUpTo(tagSize)
            parseId3Frames(data, majorVersion)
        }
    }.getOrDefault(emptyMap())

    private fun parseId3Frames(data: ByteArray, majorVersion: Int): Map<String, String> {
        val values = linkedMapOf<String, String>()
        var offset = 0
        while (offset + 10 <= data.size) {
            val frameId = data.copyOfRange(offset, offset + 4).toString(Charsets.ISO_8859_1)
            if (frameId.any { it.code == 0 }) break
            val frameSize = if (majorVersion == 4) syncSafeInt(data, offset + 4)
                else bigEndianInt(data, offset + 4)
            if (frameSize <= 0 || offset + 10 + frameSize > data.size) break

            val payload = data.copyOfRange(offset + 10, offset + 10 + frameSize)
            when {
                frameId == "TXXX" -> parseId3UserText(payload)?.let { (name, value) ->
                    values.putIfAbsent(normalizeTagKey(name), value)
                }
                frameId == "COMM" -> parseId3Comment(payload)?.let {
                    values.putIfAbsent(normalizeTagKey("COMMENT"), it)
                }
                frameId == "USLT" -> parseId3Comment(payload)?.let {
                    values.putIfAbsent(normalizeTagKey("USLT"), it)
                }
                frameId == "TSRC" -> decodeId3Text(payload)?.let {
                    values.putIfAbsent(normalizeTagKey("TSRC"), it)
                }
                frameId.startsWith("T") -> decodeId3Text(payload)?.let {
                    values.putIfAbsent(normalizeTagKey(frameId), it)
                }
            }
            offset += 10 + frameSize
        }
        return values
    }

    private fun readVisibleKeyValueTags(file: File): Map<String, String> = runCatching {
        val bytes = file.inputStream().use { it.readUpTo(1_048_576) }
        val text = bytes.toString(Charsets.ISO_8859_1)
        val keys = listOf(
            "REPLAYGAIN_TRACK_GAIN", "REPLAYGAIN_ALBUM_GAIN",
            "R128_TRACK_GAIN", "R128_ALBUM_GAIN",
            "ISRC", "MUSICBRAINZ_TRACKID", "MUSICBRAINZ_ALBUMID",
            "MUSICBRAINZ_RELEASETRACKID", "MUSICBRAINZ_RELEASEID",
            "LYRICS", "UNSYNCEDLYRICS", "COMMENT", "DESCRIPTION"
        )
        val values = linkedMapOf<String, String>()
        keys.forEach { key ->
            val pattern = Regex("${Regex.escape(key)}=([^\\u0000\\r\\n]+)", RegexOption.IGNORE_CASE)
            pattern.find(text)?.groupValues?.getOrNull(1)?.trim()?.let { value ->
                values.putIfAbsent(normalizeTagKey(key), value)
            }
        }
        values
    }.getOrDefault(emptyMap())

    private fun parseId3UserText(payload: ByteArray): Pair<String, String>? {
        val decoded = decodeId3Text(payload) ?: return null
        val parts = decoded.split('\u0000').map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.size < 2) return null
        return parts.first() to parts.drop(1).joinToString(" ").trim()
    }

    private fun parseId3Comment(payload: ByteArray): String? {
        if (payload.size < 5) return null
        val encoding = payload[0]
        val body = payload.copyOfRange(4, payload.size)
        val decoded = decodeEncodedText(encoding, body) ?: return null
        return decoded.split('\u0000').lastOrNull { it.isNotBlank() }?.trim()
    }

    private fun decodeId3Text(payload: ByteArray): String? {
        if (payload.isEmpty()) return null
        return decodeEncodedText(payload[0], payload.copyOfRange(1, payload.size))
            ?.trim('\u0000', ' ', '\r', '\n')
            ?.takeIf { it.isNotBlank() }
    }

    private fun decodeEncodedText(encoding: Byte, data: ByteArray): String? {
        val charset = when (encoding.toInt()) {
            0 -> Charsets.ISO_8859_1
            1 -> Charsets.UTF_16
            2 -> Charset.forName("UTF-16BE")
            3 -> Charsets.UTF_8
            else -> Charsets.UTF_8
        }
        return runCatching { data.toString(charset) }.getOrNull()
    }

    private fun InputStream.readUpTo(length: Int): ByteArray {
        if (length <= 0) return ByteArray(0)
        val output = ByteArray(length)
        var offset = 0
        while (offset < length) {
            val read = read(output, offset, length - offset)
            if (read < 0) break
            offset += read
        }
        return if (offset == length) output else output.copyOf(offset)
    }

    private fun syncSafeInt(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0x7F) shl 21) or
            ((bytes[offset + 1].toInt() and 0x7F) shl 14) or
            ((bytes[offset + 2].toInt() and 0x7F) shl 7) or
            (bytes[offset + 3].toInt() and 0x7F)

    private fun bigEndianInt(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0xFF) shl 24) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
            (bytes[offset + 3].toInt() and 0xFF)

    private fun Map<String, String>.firstTag(vararg names: String): String? {
        for (name in names) {
            val value = this[normalizeTagKey(name)]?.takeIf { it.isNotBlank() }
            if (value != null) return value
        }
        return null
    }

    private fun normalizeTagKey(raw: String): String =
        raw.uppercase().filter { it.isLetterOrDigit() }

    private fun String.parseDbFloat(): Float? =
        Regex("""[-+]?\d+(?:\.\d+)?""").find(this)?.value?.toFloatOrNull()

    private fun String.parseIntTag(): Int? =
        Regex("""[-+]?\d+""").find(this)?.value?.toIntOrNull()

    /**
     * Recover "Artist" / "Title" from a title formatted as `Artist - Title`,
     * used only when the file has no ARTIST tag at all. Falls back to the file
     * name (sans extension) when the title tag is also missing.
     *
     * Only the spaced hyphen-minus (` - `) is treated as the separator. En/em
     * dashes (`–`, `—`) are routinely used *inside* a title (e.g. "Heroine —
     * Pat B Remix"), so splitting on them would mangle good titles. Returns
     * `(null, originalTitle)` when nothing can be confidently derived.
     */
    private fun deriveArtistFromTitle(rawTitle: String?, filePath: String): Pair<String?, String?> {
        val base = rawTitle?.takeIf { it.isNotBlank() }
            ?: File(filePath).nameWithoutExtension.takeIf { it.isNotBlank() }
            ?: return null to rawTitle
        val idx = base.indexOf(" - ")
        if (idx <= 0) return null to rawTitle
        val artist = base.substring(0, idx).trim()
        val title = base.substring(idx + 3).trim()
        return if (artist.isNotEmpty() && title.isNotEmpty()) artist to title else null to rawTitle
    }

    private fun parseTrackNumber(raw: String?): Pair<Int?, Int?> {
        if (raw == null) return null to null
        return if (raw.contains('/')) {
            val parts = raw.split('/')
            parts[0].trim().toIntOrNull() to parts.getOrNull(1)?.trim()?.toIntOrNull()
        } else {
            raw.trim().toIntOrNull() to null
        }
    }

    private fun parseYear(raw: String?): Int? {
        if (raw == null) return null
        // Handle full date strings like "2023-01-15"
        return raw.take(4).toIntOrNull()
    }

    private fun detectCodec(mimeType: String?, filePath: String): AudioCodec {
        // Opus and Vorbis share the Ogg container, so MediaMetadataRetriever often
        // reports both as "audio/ogg". ALAC and AAC share the MP4 container, so
        // both report as "audio/mp4" on some Android versions. Check explicit
        // codec MIMEs first, then fall back to the file extension.
        return when {
            mimeType?.contains("flac") == true -> AudioCodec.FLAC
            mimeType?.contains("mpeg") == true -> AudioCodec.MP3
            mimeType?.contains("alac") == true -> AudioCodec.ALAC
            mimeType?.contains("mp4a") == true || mimeType?.contains("aac") == true ||
                mimeType?.contains("mp4") == true -> {
                val fromExt = detectCodecFromExtension(filePath)
                if (fromExt == AudioCodec.ALAC) AudioCodec.ALAC else AudioCodec.AAC
            }
            mimeType?.contains("opus") == true -> AudioCodec.OPUS
            mimeType?.contains("vorbis") == true -> AudioCodec.OGG_VORBIS
            mimeType?.contains("ogg") == true -> {
                val fromExt = detectCodecFromExtension(filePath)
                if (fromExt == AudioCodec.OPUS) AudioCodec.OPUS else AudioCodec.OGG_VORBIS
            }
            mimeType?.contains("wav") == true || mimeType?.contains("wave") == true -> AudioCodec.WAV
            mimeType?.contains("aiff") == true -> AudioCodec.AIFF
            mimeType?.contains("x-ms-wma") == true -> AudioCodec.WMA
            else -> detectCodecFromExtension(filePath)
        }
    }

    private fun detectCodecFromExtension(filePath: String): AudioCodec {
        return when (filePath.substringAfterLast('.').lowercase()) {
            "flac" -> AudioCodec.FLAC
            "mp3" -> AudioCodec.MP3
            "alac" -> AudioCodec.ALAC
            "m4a", "aac", "mp4" -> AudioCodec.AAC
            "ogg", "oga" -> AudioCodec.OGG_VORBIS
            "opus" -> AudioCodec.OPUS
            "wav" -> AudioCodec.WAV
            "aif", "aiff" -> AudioCodec.AIFF
            "ape" -> AudioCodec.APE
            "wma" -> AudioCodec.WMA
            else -> AudioCodec.UNKNOWN
        }
    }

    /**
     * Look for a sidecar cover art file in the track's folder. Matches the common
     * naming conventions (cover.*, folder.*, album.*, front.*, AlbumArt*.jpg) case-
     * insensitively, prefers larger images when multiple candidates exist, and
     * returns the file's absolute path (Coil will load it directly). Returns null
     * when `filePath` isn't a real filesystem path (e.g. a content:// URI) or the
     * folder has no recognisable image.
     *
     * Pass a [cache] keyed by parent directory to avoid re-listing the same folder
     * for every track in an album.
     */
    private fun findFolderCoverArt(
        filePath: String,
        cache: MutableMap<String, String?>? = null
    ): String? {
        val parent = File(filePath).parentFile ?: return null
        if (!parent.isDirectory) return null
        val parentKey = parent.absolutePath
        cache?.let { if (it.containsKey(parentKey)) return it[parentKey] }
        val resolved = scanFolderCoverArt(parent)
        cache?.put(parentKey, resolved)
        return resolved
    }

    /**
     * Match an image file in the same directory whose stem equals the audio
     * file's stem. Common naming convention from yt-dlp, Bandcamp, and
     * various TIDAL/Qobuz rip workflows.
     *
     * Uses direct File.exists() rather than listFiles() — under Android's
     * scoped storage (API 33+) listFiles() on /storage/emulated/0/...
     * returns null even with READ_MEDIA_AUDIO/READ_MEDIA_IMAGES granted,
     * but exists()/length() on a known indexed media path works. We pay
     * up to 8 stat() calls per art-less track (4 extensions × 2 cases) to
     * cover this.
     */
    private fun findPerTrackSidecar(filePath: String): String? {
        val file = File(filePath)
        val parent = file.parentFile ?: return null
        val stem = file.nameWithoutExtension
        for (ext in IMAGE_EXTENSIONS) {
            val lower = File(parent, "$stem.$ext")
            if (lower.exists()) return lower.absolutePath
            val upper = File(parent, "$stem.${ext.uppercase()}")
            if (upper.exists()) return upper.absolutePath
        }
        return null
    }

    private fun scanFolderCoverArt(parent: File): String? {
        val files = parent.listFiles() ?: return null
        val candidates = files.filter { f ->
            if (!f.isFile) return@filter false
            val name = f.name.lowercase()
            val ext = name.substringAfterLast('.', "")
            if (ext !in IMAGE_EXTENSIONS) return@filter false
            val stem = name.substringBeforeLast('.')
            stem in COVER_STEMS || COVER_STEM_PREFIXES.any { stem.startsWith(it) }
        }
        if (candidates.isEmpty()) return null
        // Prefer exact stem match over prefix match, then the largest file
        // (Windows Media Player writes both AlbumArt.jpg and AlbumArtSmall.jpg).
        return candidates
            .sortedWith(
                compareByDescending<File> { f ->
                    val stem = f.name.lowercase().substringBeforeLast('.')
                    if (stem in COVER_STEMS) 1 else 0
                }.thenByDescending { it.length() }
            )
            .first()
            .absolutePath
    }

    companion object {
        private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp")
        private val COVER_STEMS = setOf(
            "cover", "folder", "album", "albumart", "front", "artwork"
        )
        private val COVER_STEM_PREFIXES = setOf(
            "albumart" // AlbumArt_{GUID}_Large.jpg (WMP)
        )
    }

    private fun cacheArtwork(artworkBytes: ByteArray, filePath: String): String {
        val hash = MessageDigest.getInstance("MD5")
            .digest(filePath.toByteArray())
            .joinToString("") { "%02x".format(it) }
        val cacheFile = File(artworkCacheDir, "$hash.jpg")

        if (!cacheFile.exists()) {
            try {
                FileOutputStream(cacheFile).use { it.write(artworkBytes) }
            } catch (_: Exception) {
                return filePath // fallback
            }
        }

        return cacheFile.absolutePath
    }
}
