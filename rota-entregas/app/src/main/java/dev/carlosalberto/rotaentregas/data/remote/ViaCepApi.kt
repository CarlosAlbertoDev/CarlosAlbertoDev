package dev.carlosalberto.rotaentregas.data.remote

import retrofit2.http.GET
import retrofit2.http.Path

data class ViaCepResposta(
    val cep: String? = null,
    val logradouro: String? = null,
    val complemento: String? = null,
    val bairro: String? = null,
    val localidade: String? = null,
    val uf: String? = null,
    val erro: Boolean? = null
)

interface ViaCepApi {
    @GET("ws/{cep}/json/")
    suspend fun consultarCep(@Path("cep") cep: String): ViaCepResposta
}
