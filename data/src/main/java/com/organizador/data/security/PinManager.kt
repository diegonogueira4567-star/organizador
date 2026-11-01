package com.organizador.data.security

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.organizador.data.security.Crypto.hexToBytes
import com.organizador.data.security.Crypto.toHex
import com.organizador.domain.model.TimeProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

private val Context.securityStore: DataStore<Preferences> by preferencesDataStore(name = "security")

sealed interface PinCheck {
    data object Ok : PinCheck
    data class Wrong(val attemptsLeft: Int) : PinCheck
    data class LockedOut(val untilEpochMillis: Long) : PinCheck
    data object NotSet : PinCheck
}

/**
 * PIN armazenado somente como hash PBKDF2 + sal (nunca em texto). Os dados ficam em DataStore privado,
 * fora do backup. Após tentativas erradas consecutivas, aplica bloqueio temporário crescente.
 */
@Singleton
class PinManager @Inject constructor(
    @ApplicationContext context: Context,
    private val time: TimeProvider,
) {
    private val store = context.securityStore
    private val hashKey = stringPreferencesKey("pin_hash")
    private val saltKey = stringPreferencesKey("pin_salt")
    private val iterKey = intPreferencesKey("pin_iterations")
    private val failsKey = intPreferencesKey("pin_fails")
    private val lockKey = longPreferencesKey("pin_locked_until")

    suspend fun isSet(): Boolean = store.data.first()[hashKey] != null

    suspend fun setPin(pin: String) {
        require(pin.length in 4..12 && pin.all { it.isDigit() }) { "O PIN deve ter de 4 a 12 dígitos" }
        val salt = Crypto.randomBytes(16)
        val hash = derive(pin, salt, ITERATIONS)
        store.edit {
            it[hashKey] = hash.toHex()
            it[saltKey] = salt.toHex()
            it[iterKey] = ITERATIONS
            it[failsKey] = 0
            it.remove(lockKey)
        }
    }

    suspend fun clear() {
        store.edit { it.clear() }
    }

    suspend fun verify(pin: String): PinCheck {
        val prefs = store.data.first()
        val hash = prefs[hashKey] ?: return PinCheck.NotSet
        val salt = prefs[saltKey] ?: return PinCheck.NotSet
        val now = time.now().toEpochMilli()
        prefs[lockKey]?.let { if (it > now) return PinCheck.LockedOut(it) }
        val ok = Crypto.constantTimeEquals(derive(pin, salt.hexToBytes(), prefs[iterKey] ?: ITERATIONS), hash.hexToBytes())
        if (ok) {
            store.edit { it[failsKey] = 0; it.remove(lockKey) }
            return PinCheck.Ok
        }
        val fails = (prefs[failsKey] ?: 0) + 1
        return if (fails % MAX_ATTEMPTS == 0) {
            // 30 s, 60 s, 120 s... limitado a 1 h.
            val penalty = (30_000L shl ((fails / MAX_ATTEMPTS) - 1).coerceAtMost(7)).coerceAtMost(3_600_000L)
            store.edit { it[failsKey] = fails; it[lockKey] = now + penalty }
            PinCheck.LockedOut(now + penalty)
        } else {
            store.edit { it[failsKey] = fails }
            PinCheck.Wrong(MAX_ATTEMPTS - fails % MAX_ATTEMPTS)
        }
    }

    private suspend fun derive(pin: String, salt: ByteArray, iterations: Int): ByteArray =
        withContext(Dispatchers.Default) { Crypto.pbkdf2(pin.toCharArray(), salt, iterations) }

    companion object {
        const val ITERATIONS = 60_000
        const val MAX_ATTEMPTS = 5
    }
}
