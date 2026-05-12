package app.nexstream.player.license

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LicencePreferences @Inject constructor(
    @ApplicationContext context: Context
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

    fun clearAll() = prefs.edit().clear().apply()
}