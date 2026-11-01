package com.organizador.data.security

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Primitivas criptográficas usadas por backup e bloqueio. Apenas APIs da plataforma (JCA), sem bibliotecas extras.
 * - Derivação de chave: PBKDF2-HMAC-SHA256 com sal aleatório.
 * - Cifra: AES-256-GCM (autenticada), IV aleatório de 12 bytes, AAD = nome da entrada.
 */
object Crypto {
    private val random = SecureRandom()
    const val GCM_IV_BYTES = 12
    const val GCM_TAG_BITS = 128

    fun randomBytes(size: Int): ByteArray = ByteArray(size).also { random.nextBytes(it) }

    fun pbkdf2(secret: CharArray, salt: ByteArray, iterations: Int, keyBits: Int = 256): ByteArray {
        val spec = PBEKeySpec(secret, salt, iterations, keyBits)
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    fun encrypt(key: ByteArray, plain: ByteArray, aad: ByteArray? = null): ByteArray {
        val iv = randomBytes(GCM_IV_BYTES)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_BITS, iv))
        aad?.let(cipher::updateAAD)
        return iv + cipher.doFinal(plain)
    }

    /** @throws WrongKeyException se a senha estiver errada ou o conteúdo tiver sido adulterado. */
    fun decrypt(key: ByteArray, data: ByteArray, aad: ByteArray? = null): ByteArray {
        if (data.size < GCM_IV_BYTES + GCM_TAG_BITS / 8) throw WrongKeyException()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_BITS, data, 0, GCM_IV_BYTES))
        aad?.let(cipher::updateAAD)
        return try {
            cipher.doFinal(data, GCM_IV_BYTES, data.size - GCM_IV_BYTES)
        } catch (e: AEADBadTagException) {
            throw WrongKeyException()
        }
    }

    fun sha256(data: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(data).toHex()

    fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean = MessageDigest.isEqual(a, b)

    fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
    fun String.hexToBytes(): ByteArray = chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}

class WrongKeyException : Exception("Senha incorreta ou conteúdo adulterado")
