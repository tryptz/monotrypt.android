package tf.monochrome.android.data.device

import android.content.Context
import android.os.Build
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.serialization.Serializable
import tf.monochrome.android.BuildConfig
import tf.monochrome.android.data.auth.SupabaseAuthManager
import tf.monochrome.android.data.preferences.PreferencesManager
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "DeviceRegistry"

@Serializable
private data class SbUserDevice(
    val id: String? = null,
    val user_id: String,
    // No default — kotlinx-serialization's default behavior is to drop fields
    // that equal their default from the JSON (encodeDefaults=false), which is
    // what Supabase-kt inspects to build the `columns=` URL query. With
    // `platform = "android"` as a default, the insert shipped without a
    // `platform` column and the NOT NULL constraint on user_devices.platform
    // failed. Callers already pass it explicitly.
    val platform: String,
    val model: String? = null,
    val app_version: String? = null,
    val last_seen_at: String? = null,
)

/**
 * Upserts the current (user, device) pair in Supabase `user_devices` and
 * caches the remote UUID locally so [currentRemoteId] can be awaited
 * synchronously by the play-event push path.
 *
 * Upsert key: server-side we match on (user_id, local_id_fingerprint) by
 * looking up the existing row before inserting. We don't have a (user_id,
 * local_id) unique constraint on the cloud table (local_id isn't stored
 * there — it's an Android-only concept), so we resolve the row by
 * checking our locally cached [PreferencesManager.deviceRemoteId] first;
 * on first run we insert a new row and persist its id.
 */
@Singleton
class DeviceRegistry @Inject constructor(
    @ApplicationContext private val context: Context,
    private val authManager: SupabaseAuthManager,
    private val prefs: PreferencesManager,
    private val deviceIdProvider: DeviceIdProvider,
) {
    private val _currentRemoteId = MutableStateFlow<String?>(null)
    val currentRemoteId: StateFlow<String?> = _currentRemoteId.asStateFlow()

    /**
     * Called on app start and whenever the user profile changes. Safe to
     * call when signed-out (no-op).
     */
    suspend fun registerCurrentDevice() {
        val uid = authManager.userProfile.value?.id ?: run {
            _currentRemoteId.value = null
            return
        }
        // Ensure local id exists (tracked across sign-outs for stats continuity).
        deviceIdProvider.getOrCreate()

        val cached = prefs.deviceRemoteId.firstOrNull()
        val supabase = authManager.supabase
        val model = "${Build.MANUFACTURER} ${Build.MODEL}".trim()
        val appVersion = BuildConfig.VERSION_NAME

        runCatching {
            if (cached != null) {
                // Existing device row — just touch last_seen_at + version.
                val nowIso = Instant.now().toString()
                supabase.postgrest["user_devices"].update({
                    set("last_seen_at", nowIso)
                    set("app_version", appVersion)
                    set("model", model)
                }) {
                    filter {
                        eq("id", cached)
                        eq("user_id", uid)
                    }
                }
                _currentRemoteId.value = cached
                return@runCatching
            }

            // No cached remote id — insert and persist the returned UUID.
            val inserted = supabase.postgrest["user_devices"]
                .insert(
                    SbUserDevice(
                        user_id = uid,
                        platform = "android",
                        model = model,
                        app_version = appVersion,
                    )
                ) {
                    select()
                }
                .decodeSingleOrNull<SbUserDevice>()

            val remoteId = inserted?.id
            if (remoteId != null) {
                prefs.setDeviceRemoteId(remoteId)
                _currentRemoteId.value = remoteId
            }
        }.onFailure {
            Log.e(TAG, "registerCurrentDevice failed: ${it.message}")
        }
    }

    /** Best-effort synchronous read for play-event enrichment. */
    fun snapshotRemoteId(): String? = _currentRemoteId.value

    suspend fun clearOnSignOut() {
        prefs.setDeviceRemoteId(null)
        _currentRemoteId.value = null
    }
}
