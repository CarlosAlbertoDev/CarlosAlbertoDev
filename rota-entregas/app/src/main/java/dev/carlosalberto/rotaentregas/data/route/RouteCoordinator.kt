package dev.carlosalberto.rotaentregas.data.route

import dev.carlosalberto.rotaentregas.data.db.entity.ParadaEntity
import dev.carlosalberto.rotaentregas.data.geocode.Coordenada
import dev.carlosalberto.rotaentregas.data.remote.NetworkModule
import dev.carlosalberto.rotaentregas.data.repository.ParadaRepository
import dev.carlosalberto.rotaentregas.data.settings.SettingsRepository
import dev.carlosalberto.rotaentregas.util.Haversine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.collect

/**
 * Ponto único que decide QUANDO recalcular a rota e aplica o resultado no banco.
 * Chamado tanto manualmente (usuário pede nova rota) quanto automaticamente pelo
 * [dev.carlosalberto.rotaentregas.data.location.LocationTracker] quando a posição
 * muda o suficiente para justificar um novo cálculo — como no Google Maps.
 */
class RouteCoordinator(
    private val paradaRepository: ParadaRepository,
    private val settingsRepository: SettingsRepository
) {

    suspend fun recalcularRotaAtiva(origemAtual: Coordenada): ResultadoRota? {
        paradaRepository.geocodificarPendentes()

        val ativas = paradaRepository.observarAtivas().first()
            .filter { it.latitude != null && it.longitude != null }
        if (ativas.isEmpty()) return null

        val osrmUrl = settingsRepository.osrmBaseUrl.first()
        val otimizador = RouteOptimizer(provedorPrincipal = OsrmRoutingProvider(NetworkModule.criarOsrmApi(osrmUrl)))

        val coordenadas = ativas.map { Coordenada(it.latitude!!, it.longitude!!) }
        val resultado = otimizador.otimizar(origemAtual, coordenadas)

        val idsNaOrdem = resultado.ordemDosIndices.map { ativas[it].id }
        paradaRepository.salvarOrdemDaRota(idsNaOrdem)

        return resultado
    }

    /** Paradas ativas já ordenadas conforme a última rota calculada, prontas para desenhar no mapa. */
    suspend fun paradasOrdenadas(): List<ParadaEntity> =
        paradaRepository.observarAtivas().first().sortedBy { it.ordemNaRota ?: Int.MAX_VALUE }

    /**
     * Recalcula a rota pendente automaticamente quando o entregador se desloca o
     * suficiente para que a sequência calculada deixe de fazer sentido — o mesmo
     * comportamento de um app de navegação turn-by-turn. Usa dois limiares para não
     * disparar recálculos excessivos a cada leitura de GPS:
     * - distância mínima percorrida desde o último recálculo ([limiarDistanciaMetros]);
     * - intervalo mínimo de tempo entre recálculos ([limiarTempoMillis]).
     */
    suspend fun monitorarEReotimizarContinuamente(
        localizacoes: Flow<Coordenada>,
        limiarDistanciaMetros: Double = 80.0,
        limiarTempoMillis: Long = 20_000L,
        aoRecalcular: suspend (ResultadoRota?) -> Unit
    ) {
        var ultimaOrigemRecalculada: Coordenada? = null
        var ultimoRecalculoEm = 0L

        localizacoes.collect { posicaoAtual ->
            val agora = System.currentTimeMillis()
            val distanciaDesdeUltimoRecalculo = ultimaOrigemRecalculada?.let {
                Haversine.distanciaMetros(it.latitude, it.longitude, posicaoAtual.latitude, posicaoAtual.longitude)
            } ?: Double.MAX_VALUE

            val passouTempoMinimo = agora - ultimoRecalculoEm >= limiarTempoMillis
            val moveuOSuficiente = distanciaDesdeUltimoRecalculo >= limiarDistanciaMetros

            if (passouTempoMinimo && moveuOSuficiente) {
                ultimaOrigemRecalculada = posicaoAtual
                ultimoRecalculoEm = agora
                aoRecalcular(recalcularRotaAtiva(posicaoAtual))
            }
        }
    }
}
