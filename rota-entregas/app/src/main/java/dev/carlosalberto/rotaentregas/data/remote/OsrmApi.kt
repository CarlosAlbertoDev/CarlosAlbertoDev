package dev.carlosalberto.rotaentregas.data.remote

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

data class OsrmTabelaResposta(
    val code: String,
    val durations: List<List<Double?>>? = null,
    val distances: List<List<Double?>>? = null
)

data class OsrmGeometria(val coordinates: List<List<Double>>? = null)
data class OsrmRota(val geometry: OsrmGeometria?, val distance: Double, val duration: Double)
data class OsrmRotaResposta(val code: String, val routes: List<OsrmRota>? = null)

/**
 * Cliente para um servidor OSRM (Open Source Routing Machine), que calcula
 * distância/tempo seguindo o grafo real das ruas — respeitando sentidos únicos e
 * necessidade de retorno — em vez de linha reta. Por padrão aponta para a instância
 * pública de demonstração (router.project-osrm.org); em produção o ideal é apontar
 * para uma instância própria (auto-hospedada) para não depender de limites de uso.
 */
interface OsrmApi {

    @GET("table/v1/driving/{coordenadas}")
    suspend fun matrizDeDistancias(
        @Path(value = "coordenadas", encoded = true) coordenadasLonLat: String,
        @Query("annotations") anotacoes: String = "distance,duration"
    ): OsrmTabelaResposta

    @GET("route/v1/driving/{coordenadas}")
    suspend fun calcularRota(
        @Path(value = "coordenadas", encoded = true) coordenadasLonLat: String,
        @Query("overview") overview: String = "full",
        @Query("geometries") geometrias: String = "geojson"
    ): OsrmRotaResposta
}
