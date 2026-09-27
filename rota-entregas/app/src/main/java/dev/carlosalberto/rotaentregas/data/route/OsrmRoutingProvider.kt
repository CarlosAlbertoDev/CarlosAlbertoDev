package dev.carlosalberto.rotaentregas.data.route

import dev.carlosalberto.rotaentregas.data.geocode.Coordenada
import dev.carlosalberto.rotaentregas.data.remote.OsrmApi
import java.util.Locale

/**
 * Usa um servidor OSRM (público ou auto-hospedado) para obter custo/geometria
 * seguindo o grafo real das ruas — a única forma de "avaliar o fluxo da rua"
 * (mão única, necessidade de contorno) mencionada no pedido original.
 */
class OsrmRoutingProvider(private val osrmApi: OsrmApi) : RoutingProvider {

    override val respeitaSentidoDasRuas: Boolean = true

    private fun formatarCoordenadas(pontos: List<Coordenada>): String =
        pontos.joinToString(";") { p ->
            String.format(Locale.US, "%.6f,%.6f", p.longitude, p.latitude)
        }

    override suspend fun matrizDeCusto(pontos: List<Coordenada>): Array<DoubleArray>? = runCatching {
        val resposta = osrmApi.matrizDeDistancias(formatarCoordenadas(pontos))
        val distancias = resposta.distances ?: return@runCatching null
        if (resposta.code != "Ok") return@runCatching null
        Array(distancias.size) { i -> DoubleArray(distancias[i].size) { j -> distancias[i][j] ?: Double.MAX_VALUE } }
    }.getOrNull()

    override suspend fun geometriaDaRota(pontosEmOrdem: List<Coordenada>): List<Coordenada>? = runCatching {
        if (pontosEmOrdem.size < 2) return@runCatching null
        val resposta = osrmApi.calcularRota(formatarCoordenadas(pontosEmOrdem))
        if (resposta.code != "Ok") return@runCatching null
        resposta.routes?.firstOrNull()?.geometry?.coordinates?.map { par ->
            Coordenada(latitude = par[1], longitude = par[0])
        }
    }.getOrNull()
}
