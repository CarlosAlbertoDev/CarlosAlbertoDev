package dev.carlosalberto.rotaentregas.data.route

import dev.carlosalberto.rotaentregas.data.geocode.Coordenada

data class ResultadoRota(
    /** Índices em [ParadaEntity]/[coordenadasParadas] na ordem de visita otimizada. */
    val ordemDosIndices: List<Int>,
    val distanciaTotalMetros: Double,
    val respeitouSentidoDasRuas: Boolean,
    /** Geometria detalhada para desenhar no mapa; null quando só há linha reta disponível. */
    val geometria: List<Coordenada>?
)

/**
 * Monta a sequência de visita partindo da posição atual do entregador.
 *
 * Estratégia (rápida o suficiente para dezenas de paradas, no dispositivo):
 * 1) Heurística construtiva do "vizinho mais próximo" usando custo real de deslocamento
 *    (não linha reta), o que já evita a armadilha citada no pedido: um endereço perto
 *    em linha reta mas que exige contorno por causa de mão única/fluxo da rua.
 * 2) Refinamento local 2-opt (limitado a poucas passadas) para desfazer cruzamentos
 *    óbvios que a heurística gulosa deixa passar, mantendo o processamento rápido.
 */
class RouteOptimizer(
    private val provedorPrincipal: RoutingProvider,
    private val provedorReserva: RoutingProvider = HaversineRoutingProvider(),
    private val maxPassadas2Opt: Int = 20
) {

    suspend fun otimizar(origem: Coordenada, paradas: List<Coordenada>): ResultadoRota {
        if (paradas.isEmpty()) {
            return ResultadoRota(emptyList(), 0.0, provedorPrincipal.respeitaSentidoDasRuas, null)
        }

        val pontos = listOf(origem) + paradas
        val matrizPrincipal = provedorPrincipal.matrizDeCusto(pontos)
        val usandoReserva = matrizPrincipal == null
        val matriz = matrizPrincipal ?: provedorReserva.matrizDeCusto(pontos)
            ?: error("Não foi possível calcular a matriz de distâncias, nem mesmo em modo offline")

        val ordemInicial = construirPorVizinhoMaisProximo(matriz, paradas.size)
        val ordemFinal = refinarComDoisOpt(ordemInicial, matriz)
        val distanciaTotal = calcularCustoRota(ordemFinal, matriz)

        val geometria = if (!usandoReserva) {
            provedorPrincipal.geometriaDaRota(listOf(origem) + ordemFinal.map { paradas[it] })
        } else null

        return ResultadoRota(
            ordemDosIndices = ordemFinal,
            distanciaTotalMetros = distanciaTotal,
            respeitouSentidoDasRuas = !usandoReserva,
            geometria = geometria
        )
    }

    /** Índice 0 da matriz é sempre a origem; paradas são 1..n. Retorna índices 0-based nas paradas. */
    private fun construirPorVizinhoMaisProximo(matriz: Array<DoubleArray>, quantidadeParadas: Int): List<Int> {
        val visitados = BooleanArray(quantidadeParadas)
        val ordem = mutableListOf<Int>()
        var atualNaMatriz = 0 // origem

        repeat(quantidadeParadas) {
            var melhorIndice = -1
            var melhorCusto = Double.MAX_VALUE
            for (i in 0 until quantidadeParadas) {
                if (visitados[i]) continue
                val custo = matriz[atualNaMatriz][i + 1]
                if (custo < melhorCusto) {
                    melhorCusto = custo
                    melhorIndice = i
                }
            }
            visitados[melhorIndice] = true
            ordem.add(melhorIndice)
            atualNaMatriz = melhorIndice + 1
        }
        return ordem
    }

    /** 2-opt em caminho aberto: origem fixa no início, sem necessidade de retornar a ela. */
    private fun refinarComDoisOpt(ordemInicial: List<Int>, matriz: Array<DoubleArray>): List<Int> {
        if (ordemInicial.size < 3) return ordemInicial
        val rota = ordemInicial.toMutableList()

        fun custoNaMatriz(i: Int) = i + 1 // desloca índice de parada para índice na matriz (origem = 0)

        var melhorou = true
        var passada = 0
        while (melhorou && passada < maxPassadas2Opt) {
            melhorou = false
            passada++
            for (i in 0 until rota.size - 1) {
                for (j in i + 1 until rota.size) {
                    val a = if (i == 0) 0 else custoNaMatriz(rota[i - 1])
                    val b = custoNaMatriz(rota[i])
                    val c = custoNaMatriz(rota[j])
                    val d = if (j + 1 < rota.size) custoNaMatriz(rota[j + 1]) else null

                    val custoAtual = matriz[a][b] + (d?.let { matriz[c][it] } ?: 0.0)
                    val custoTrocado = matriz[a][c] + (d?.let { matriz[b][it] } ?: 0.0)

                    if (custoTrocado < custoAtual - 1e-6) {
                        rota.subList(i, j + 1).reverse()
                        melhorou = true
                    }
                }
            }
        }
        return rota
    }

    private fun calcularCustoRota(ordem: List<Int>, matriz: Array<DoubleArray>): Double {
        if (ordem.isEmpty()) return 0.0
        var total = matriz[0][ordem.first() + 1]
        for (i in 0 until ordem.size - 1) {
            total += matriz[ordem[i] + 1][ordem[i + 1] + 1]
        }
        return total
    }
}
