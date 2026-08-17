package app.nexstream.player.license

import android.content.Context
import android.media.MediaDrm
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.MessageDigest
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LicencePreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs = context.getSharedPreferences("nexstream_licence", Context.MODE_PRIVATE)

    fun saveLicenceKey(key: String) = prefs.edit().putString("licence_key", key).apply()
    fun getLicenceKey(): String? = prefs.getString("licence_key", null)
    fun clearLicenceKey() = prefs.edit().remove("licence_key").apply()
    fun hasLicence(): Boolean = !getLicenceKey().isNullOrEmpty()

    fun saveEmail(email: String) = prefs.edit().putString("email", email).apply()
    fun getEmail(): String? = prefs.getString("email", null)

    fun saveLicenceType(type: String) = prefs.edit().putString("licence_type", type).apply()
    fun getLicenceType(): String? = prefs.getString("licence_type", null)

    fun saveExpiresAt(expiresAt: String?) = prefs.edit().putString("expires_at", expiresAt).apply()
    fun getExpiresAt(): String? = prefs.getString("expires_at", null)

    fun saveDeviceLimit(limit: Int) = prefs.edit().putInt("device_limit", limit).apply()
    fun getDeviceLimit(): Int = prefs.getInt("device_limit", 1)

    fun saveTrialSyncKey(key: String) = prefs.edit().putString("trial_sync_key", key).apply()
    fun getTrialSyncKey(): String? = prefs.getString("trial_sync_key", null)
    fun clearTrialSyncKey() = prefs.edit().remove("trial_sync_key").apply()

    fun saveIsResellerAssigned(value: Boolean) = prefs.edit().putBoolean("reseller_assigned", value).apply()
    fun isResellerAssigned(): Boolean = prefs.getBoolean("reseller_assigned", false)

    fun saveResellerId(id: Int?) {
        if (id != null) prefs.edit().putInt("reseller_id", id).apply()
        else prefs.edit().remove("reseller_id").apply()
    }
    fun getResellerId(): Int? = if (prefs.contains("reseller_id")) prefs.getInt("reseller_id", 0) else null

    fun getOrCreateStableDeviceId(): String {
        // If a UUID was already persisted (devices activated on builds 0056-0057), keep it —
        // changing it again would invalidate those devices' licence registrations.
        val stored = prefs.getString("stable_device_id", null)
        if (!stored.isNullOrEmpty()) return stored

        val id = computeStableDeviceId()
        prefs.edit().putString("stable_device_id", id).apply()
        return id
    }

    @Suppress("DEPRECATION")
    private fun computeStableDeviceId(): String {
        // Widevine DRM ID is hardware-bound and persists across reinstalls
        try {
            val drm = MediaDrm(UUID(-0x121074568629b532L, -0x5c37d8232ae2de13L))
            try {
                val raw = drm.getPropertyByteArray(MediaDrm.PROPERTY_DEVICE_UNIQUE_ID)
                if (raw != null && raw.isNotEmpty()) {
                    return MessageDigest.getInstance("SHA-256").digest(raw)
                        .take(8).joinToString("") { "%02x".format(it) }.uppercase()
                }
            } finally {
                drm.release()
            }
        } catch (e: Exception) {
            android.util.Log.w("LicencePrefs", "Widevine unavailable, using ANDROID_ID fallback: ${e.message}")
        }
        val androidId = Settings.Secure.getString(
            context.contentResolver, Settings.Secure.ANDROID_ID
        ) ?: "null"
        return MessageDigest.getInstance("SHA-256").digest(androidId.toByteArray())
            .take(8).joinToString("") { "%02x".format(it) }.uppercase()
    }

    fun clearAll() = prefs.edit()
        .remove("licence_key")
        .remove("email")
        .remove("licence_type")
        .remove("expires_at")
        .remove("device_limit")
        .remove("reseller_assigned")
        .remove("reseller_id")
        .apply()
    // trial_sync_key and stable_device_id intentionally preserved — they survive licence deactivation
}