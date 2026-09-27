package dev.carlosalberto.rotaentregas.data.remote

import retrofit2.http.GET
import retrofit2.http.Query

data class NominatimResultado(
    val lat: String,
    val lon: String,
    val display_name: String? = null
)

interface NominatimApi {
    @GET("search")
    suspend fun buscar(
        @Query("q") consulta: String,
        @Query("format") formato: String = "json",
        @Query("limit") limite: Int = 1,
        @Query("countrycodes") paisCodigo: String = "br"
    ): List<NominatimResultado>
}
