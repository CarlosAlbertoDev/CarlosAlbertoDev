package dev.carlosalberto.rotaentregas.data.remote

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object NetworkModule {

    // Nominatim exige um User-Agent identificável (política de uso da OSM Foundation).
    private class UserAgentInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val requisicao = chain.request().newBuilder()
                .header("User-Agent", "RotaEntregas-Android/1.0 (contato: carlosalberto.m.jr@hotmail.com)")
                .build()
            return chain.proceed(requisicao)
        }
    }

    private val clienteHttp = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .addInterceptor(UserAgentInterceptor())
        .build()

    private fun criarRetrofit(baseUrl: String) = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(clienteHttp)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val viaCepApi: ViaCepApi by lazy {
        criarRetrofit("https://viacep.com.br/").create(ViaCepApi::class.java)
    }

    val nominatimApi: NominatimApi by lazy {
        criarRetrofit("https://nominatim.openstreetmap.org/").create(NominatimApi::class.java)
    }

    /** URL base do OSRM; pode ser trocada nas Configurações para apontar a um servidor próprio. */
    fun criarOsrmApi(baseUrl: String): OsrmApi =
        criarRetrofit(baseUrl).create(OsrmApi::class.java)
}
