package dev.carlosalberto.rotaentregas.data.geocode

import dev.carlosalberto.rotaentregas.data.remote.NetworkModule
import dev.carlosalberto.rotaentregas.data.remote.ViaCepResposta
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class Coordenada(val latitude: Double, val longitude: Double)

/**
 * Combina duas fontes públicas gratuitas:
 * - ViaCEP: transforma um CEP digitado/confirmado em logradouro/bairro/cidade/UF.
 * - Nominatim (OpenStreetMap): transforma o endereço textual em latitude/longitude.
 *
 * Um pequeno cache em memória evita bater a mesma consulta várias vezes na mesma
 * sessão (a política de uso do Nominatim pede no máximo 1 req/s).
 */
class GeocodingRepository {

    private val cacheGeocodificacao = mutableMapOf<String, Coordenada?>()

    suspend fun consultarCep(cep: String): ViaCepResposta? = withContext(Dispatchers.IO) {
        val cepLimpo = cep.filter { it.isDigit() }
        if (cepLimpo.length != 8) return@withContext null
        runCatching { NetworkModule.viaCepApi.consultarCep(cepLimpo) }
            .getOrNull()
            ?.takeIf { it.erro != true }
    }

    suspend fun geocodificar(enderecoTextoCompleto: String): Coordenada? = withContext(Dispatchers.IO) {
        cacheGeocodificacao[enderecoTextoCompleto]?.let { return@withContext it }

        val resultado = runCatching {
            NetworkModule.nominatimApi.buscar(consulta = enderecoTextoCompleto)
        }.getOrNull()?.firstOrNull()

        val coordenada = resultado?.let {
            Coordenada(it.lat.toDoubleOrNull() ?: return@let null, it.lon.toDoubleOrNull() ?: return@let null)
        }

        cacheGeocodificacao[enderecoTextoCompleto] = coordenada
        coordenada
    }
}
