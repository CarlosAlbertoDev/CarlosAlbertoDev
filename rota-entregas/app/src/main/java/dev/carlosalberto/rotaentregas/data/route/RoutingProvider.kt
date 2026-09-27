package dev.carlosalberto.rotaentregas.data.route

import dev.carlosalberto.rotaentregas.data.geocode.Coordenada

/**
 * Abstrai a fonte de distâncias/tempos entre pontos. Uma implementação "ciente das
 * ruas" (OSRM) respeita mão única e a necessidade de retorno — por isso um ponto
 * fisicamente próximo pode ter custo maior que outro mais distante em linha reta.
 * Uma implementação offline cai para linha reta (Haversine) quando não há internet.
 */
interface RoutingProvider {

    /**
     * Matriz de custo (metros) entre todos os pares de [pontos], na mesma ordem.
     * matriz[i][j] = custo de ir do ponto i ao ponto j. Retorna null se a fonte
     * não conseguiu responder (ex.: sem internet, servidor indisponível).
     */
    suspend fun matrizDeCusto(pontos: List<Coordenada>): Array<DoubleArray>?

    /**
     * Geometria detalhada (lista de coordenadas) da rota seguindo [pontosEmOrdem],
     * para desenhar a polilinha real sobre as ruas no mapa. Retorna null se
     * indisponível — nesse caso o mapa desenha apenas linhas retas entre as paradas.
     */
    suspend fun geometriaDaRota(pontosEmOrdem: List<Coordenada>): List<Coordenada>?

    val respeitaSentidoDasRuas: Boolean
}
