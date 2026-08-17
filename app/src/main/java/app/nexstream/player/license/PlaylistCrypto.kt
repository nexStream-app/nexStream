package app.nexstream.player.license

import android.util.Base64
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object PlaylistCrypto {
    /**
     * Decrypts a per-device AES-256-CBC encrypted string where the key is SHA-256(licenceKey).
     * Mirrors encryptWithDeviceKey / decryptWithDeviceKey in the PHP backend.
     * Returns the original string unchanged if decryption fails (e.g. plaintext from older system).
     */
    fun decryptPassword(encrypted: String, licenceKey: String): String {
        if (encrypted.isEmpty()) return ""
        return try {
            val keyBytes = MessageDigest.getInstance("SHA-256")
                .digest(licenceKey.toByteArray(Charsets.UTF_8))
            val raw = Base64.decode(encrypted, Base64.DEFAULT)
            if (raw.size <= 16) return encrypted
            val iv         = raw.copyOfRange(0, 16)
            val ciphertext = raw.copyOfRange(16, raw.size)
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                SecretKeySpec(keyBytes, "AES"),
                IvParameterSpec(iv)
            )
            String(cipher.doFinal(ciphertext), Charsets.UTF_8)
        } catch (_: Exception) {
            encrypted
        }
    }
}
