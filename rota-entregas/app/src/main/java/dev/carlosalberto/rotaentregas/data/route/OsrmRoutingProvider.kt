package dev.carlosalberto.rotaentregas.data.route

import dev.carlosalberto.rotaentregas.data.geocode.Coordenada
import dev.carlosalberto.rotaentregas.data.remote.OsrmApi
import java.util.Locale

/**
 * Usa um servidor OSRM (público ou auto-hospedado) para obter custo seguindo o grafo
 * real das ruas — a única forma de "avaliar o fluxo da rua" (mão única, necessidade de
 * contorno) mencionada no pedido original — e o traçado completo por todas as paradas,
 * com instruções de manobra separadas por perna para a navegação passo a passo.
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

    /**
     * Rota passando por TODAS as paradas em [pontosEmOrdem] (origem + paradas na ordem já
     * decidida pelo otimizador), com geometria completa para desenhar o traçado inteiro no
     * mapa — como o modo "vários destinos" do Google Maps — e instruções de manobra
     * separadas por perna, para a navegação continuar sendo feita passo a passo.
     */
    suspend fun calcularRotaMultiParada(pontosEmOrdem: List<Coordenada>): RotaMultiParada? = runCatching {
        if (pontosEmOrdem.size < 2) return@runCatching null
        val resposta = osrmApi.calcularRota(formatarCoordenadas(pontosEmOrdem))
        if (resposta.code != "Ok") return@runCatching null
        val rota = resposta.routes?.firstOrNull() ?: return@runCatching null
        val geometria = rota.geometry?.coordinates?.map { par -> Coordenada(latitude = par[1], longitude = par[0]) }
            ?: return@runCatching null

        val passosPorPerna = rota.legs.orEmpty().map { perna ->
            perna.steps.orEmpty().mapNotNull { passo ->
                val local = passo.maneuver?.location?.takeIf { it.size == 2 } ?: return@mapNotNull null
                PassoNavegacao(
                    instrucao = traduzirManobra(passo.maneuver.type, passo.maneuver.modifier, passo.name),
                    distanciaMetros = passo.distance,
                    localizacaoManobra = Coordenada(latitude = local[1], longitude = local[0])
                )
            }
        }

        RotaMultiParada(geometriaCompleta = geometria, passosPorPerna = passosPorPerna)
    }.getOrNull()

    private fun traduzirManobra(tipo: String?, modificador: String?, nomeRua: String?): String {
        val rua = nomeRua?.takeIf { it.isNotBlank() }?.let { " na $it" } ?: ""
        return when (tipo) {
            "depart" -> "Siga em frente$rua"
            "arrive" -> "Você chegou ao destino"
            "roundabout", "rotary" -> "Entre na rotatória$rua"
            "exit roundabout", "exit rotary" -> "Saia da rotatória$rua"
            "merge", "on ramp" -> "Continue$rua"
            "off ramp" -> "Saia da via$rua"
            "fork" -> if (modificador?.contains("left") == true) "Mantenha-se à esquerda$rua" else "Mantenha-se à direita$rua"
            "end of road" -> if (modificador?.contains("left") == true) {
                "No fim da via, vire à esquerda$rua"
            } else {
                "No fim da via, vire à direita$rua"
            }
            else -> when (modificador) {
                "uturn" -> "Faça o retorno$rua"
                "sharp left" -> "Vire à esquerda (fechada)$rua"
                "left" -> "Vire à esquerda$rua"
                "slight left" -> "Mantenha-se à esquerda$rua"
                "straight" -> "Siga em frente$rua"
                "slight right" -> "Mantenha-se à direita$rua"
                "right" -> "Vire à direita$rua"
                "sharp right" -> "Vire à direita (fechada)$rua"
                else -> "Siga em frente$rua"
            }
        }
    }
}
