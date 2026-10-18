package com.resurgeai.core.data.progress

import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.aead.PredefinedAeadParameters
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.BeforeClass
import org.junit.Test
import java.security.GeneralSecurityException

class PhotoCryptoTest {
    companion object {
        @JvmStatic
        @BeforeClass
        fun register() = AeadConfig.register()
    }

    @Test
    fun `wrapped key opens only with the same restore secret and account`() {
        val handle = KeysetHandle.generateNew(PredefinedAeadParameters.AES256_GCM)
        val secret = java.security.SecureRandom().let { r -> ByteArray(PhotoKeys.SECRET_BYTES).also(r::nextBytes) }
        val wrapped = PhotoKeys.wrap(handle, secret, "user-a")

        val restored = PhotoKeys.unwrap(wrapped, secret, "user-a")
        assertNotNull(restored)
        val original = handle.getPrimitive(RegistryConfiguration.get(), Aead::class.java)
        val reopened = restored!!.getPrimitive(RegistryConfiguration.get(), Aead::class.java)
        val ad = ProgressRepository.ad("user-a", "p1", PhotoVariant.FULL)
        assertArrayEquals("jpeg".toByteArray(), reopened.decrypt(original.encrypt("jpeg".toByteArray(), ad), ad))

        assertNull(PhotoKeys.unwrap(wrapped, ByteArray(PhotoKeys.SECRET_BYTES), "user-a"))
        assertNull(PhotoKeys.unwrap(wrapped, secret, "user-b"))
    }

    @Test(expected = GeneralSecurityException::class)
    fun `a photo file can't be passed off as another photo`() {
        val aead = KeysetHandle.generateNew(PredefinedAeadParameters.AES256_GCM).getPrimitive(RegistryConfiguration.get(), Aead::class.java)
        val sealed = aead.encrypt("jpeg".toByteArray(), ProgressRepository.ad("u", "p1", PhotoVariant.FULL))
        aead.decrypt(sealed, ProgressRepository.ad("u", "p2", PhotoVariant.FULL))
    }
}
