package dev.carlosalberto.rotaentregas.data.route

import dev.carlosalberto.rotaentregas.data.geocode.Coordenada
import dev.carlosalberto.rotaentregas.util.Haversine

/** Fallback usado sem internet ou quando o servidor de roteamento por ruas falha. */
class HaversineRoutingProvider : RoutingProvider {

    override val respeitaSentidoDasRuas: Boolean = false

    override suspend fun matrizDeCusto(pontos: List<Coordenada>): Array<DoubleArray> =
        Array(pontos.size) { i ->
            DoubleArray(pontos.size) { j ->
                if (i == j) 0.0
                else Haversine.distanciaMetros(
                    pontos[i].latitude, pontos[i].longitude,
                    pontos[j].latitude, pontos[j].longitude
                )
            }
        }

    // Sem dados de ruas não há geometria real; o mapa cai para linhas retas entre paradas.
    override suspend fun geometriaDaRota(pontosEmOrdem: List<Coordenada>): List<Coordenada>? = null
}
