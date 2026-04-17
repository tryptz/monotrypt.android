package tf.monochrome.android.data.device

import kotlinx.coroutines.flow.firstOrNull
import tf.monochrome.android.data.preferences.PreferencesManager
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stable per-install device UUID. Survives sign-out; cleared with
 * [PreferencesManager.clearAllData] only. The same local id is reused to
 * identify this device across account switches — the remote row in
 * `public.user_devices` is keyed by (user_id, local_id) via [DeviceRegistry].
 */
@Singleton
class DeviceIdProvider @Inject constructor(
    private val prefs: PreferencesManager,
) {
    suspend fun getOrCreate(): String {
        prefs.deviceLocalId.firstOrNull()?.let { return it }
        val id = UUID.randomUUID().toString()
        prefs.setDeviceLocalId(id)
        return id
    }
}
