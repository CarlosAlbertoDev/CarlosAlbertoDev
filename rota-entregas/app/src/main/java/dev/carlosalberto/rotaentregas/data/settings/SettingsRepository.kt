package dev.carlosalberto.rotaentregas.data.settings

import android.content.Context
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "configuracoes_rota_entregas")

/** Preferências do usuário: valor cobrado por pacote e servidor de roteamento por ruas. */
class SettingsRepository(private val context: Context) {

    private object Chaves {
        val VALOR_POR_PACOTE = doublePreferencesKey("valor_por_pacote")
        val OSRM_BASE_URL = stringPreferencesKey("osrm_base_url")
    }

    companion object {
        const val VALOR_POR_PACOTE_PADRAO = 2.00
        const val OSRM_URL_PADRAO = "https://router.project-osrm.org/"
    }

    val valorPorPacote: Flow<Double> = context.dataStore.data.map { prefs ->
        prefs[Chaves.VALOR_POR_PACOTE] ?: VALOR_POR_PACOTE_PADRAO
    }

    val osrmBaseUrl: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[Chaves.OSRM_BASE_URL] ?: OSRM_URL_PADRAO
    }

    suspend fun definirValorPorPacote(valor: Double) {
        context.dataStore.edit { it[Chaves.VALOR_POR_PACOTE] = valor }
    }

    suspend fun definirOsrmBaseUrl(url: String) {
        context.dataStore.edit { it[Chaves.OSRM_BASE_URL] = url.ifBlank { OSRM_URL_PADRAO } }
    }
}
