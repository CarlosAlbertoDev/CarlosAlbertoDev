package dev.carlosalberto.rotaentregas.ui.mapa

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.carlosalberto.rotaentregas.ServiceLocator
import dev.carlosalberto.rotaentregas.data.db.entity.ParadaEntity
import dev.carlosalberto.rotaentregas.data.geocode.Coordenada
import dev.carlosalberto.rotaentregas.data.model.StatusParada
import dev.carlosalberto.rotaentregas.data.route.NavegacaoAtual
import dev.carlosalberto.rotaentregas.data.route.ResultadoRota
import dev.carlosalberto.rotaentregas.util.Haversine
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/** Distância até a manobra atual abaixo da qual consideramos que ela já foi feita. */
private const val LIMIAR_CHEGADA_MANOBRA_METROS = 30.0

data class MapaUiState(
    val paradasAtivas: List<ParadaEntity> = emptyList(),
    val paradasFalhas: List<ParadaEntity> = emptyList(),
    val paradasEntregues: List<ParadaEntity> = emptyList(),
    val localizacaoAtual: Coordenada? = null,
    // Navegação só até a PRÓXIMA parada (como um GPS normal) — nunca a rota inteira de uma vez.
    val navegacaoAtual: NavegacaoAtual? = null,
    val indicePassoAtual: Int = 0,
    val distanciaAteProximaManobraMetros: Double? = null,
    val respeitandoSentidoDasRuas: Boolean = false,
    val calculandoRota: Boolean = false,
    val mensagem: String? = null
)

class MapaViewModel : ViewModel() {

    private val paradaRepository = ServiceLocator.paradaRepository
    private val routeCoordinator = ServiceLocator.routeCoordinator
    private val locationTracker = ServiceLocator.locationTracker

    private val _uiState = MutableStateFlow(MapaUiState())
    val uiState: StateFlow<MapaUiState> = _uiState

    private var jobMonitoramento: Job? = null

    init {
        viewModelScope.launch {
            combine(
                paradaRepository.observarAtivas(),
                paradaRepository.observarTodas()
            ) { ativas, todas -> ativas to todas }.collect { (ativas, todas) ->
                _uiState.value = _uiState.value.copy(
                    paradasAtivas = ativas.sortedBy { it.ordemNaRota ?: Int.MAX_VALUE },
                    paradasFalhas = todas.filter { it.status == StatusParada.FALHOU },
                    paradasEntregues = todas.filter { it.status == StatusParada.ENTREGUE }
                )
            }
        }
    }

    /** Chamado assim que a permissão de localização é concedida na tela. */
    fun iniciarAcompanhamento() {
        if (jobMonitoramento != null) return
        jobMonitoramento = viewModelScope.launch {
            // Uma única inscrição no GPS: cada nova posição atualiza o marcador "você está
            // aqui" (onEach), avalia se a manobra atual já foi passada e, em seguida, é
            // avaliada para decidir se recalcula a ordem das paradas.
            val localizacoesComAtualizacaoDeEstado = locationTracker.localizacoes().onEach { localizacao ->
                _uiState.value = _uiState.value.copy(localizacaoAtual = localizacao)
                avaliarProgressoDaManobra(localizacao)
            }
            routeCoordinator.monitorarEReotimizarContinuamente(
                localizacoes = localizacoesComAtualizacaoDeEstado
            ) { resultado ->
                atualizarNavegacaoAposRecalculo(resultado)
            }
        }
    }

    fun recalcularAgora() {
        val origem = _uiState.value.localizacaoAtual ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(calculandoRota = true, mensagem = null)
            val resultado = routeCoordinator.recalcularRotaAtiva(origem)
            atualizarNavegacaoAposRecalculo(resultado)
        }
    }

    private suspend fun atualizarNavegacaoAposRecalculo(resultado: ResultadoRota?) {
        val origem = _uiState.value.localizacaoAtual
        val navegacao = if (resultado != null && origem != null) {
            routeCoordinator.calcularNavegacaoParaProximaParada(origem)
        } else {
            null
        }
        _uiState.value = _uiState.value.copy(
            calculandoRota = false,
            navegacaoAtual = navegacao,
            indicePassoAtual = 0,
            distanciaAteProximaManobraMetros = null,
            respeitandoSentidoDasRuas = resultado?.respeitouSentidoDasRuas ?: false,
            mensagem = if (resultado == null) "Nenhuma parada pronta para rota ainda" else _uiState.value.mensagem
        )
    }

    private fun avaliarProgressoDaManobra(localizacao: Coordenada) {
        val estado = _uiState.value
        val passos = estado.navegacaoAtual?.leg?.passos ?: return
        if (estado.indicePassoAtual >= passos.size) return

        val manobra = passos[estado.indicePassoAtual].localizacaoManobra
        val distancia = Haversine.distanciaMetros(
            localizacao.latitude, localizacao.longitude, manobra.latitude, manobra.longitude
        )

        _uiState.value = if (distancia < LIMIAR_CHEGADA_MANOBRA_METROS && estado.indicePassoAtual < passos.size - 1) {
            estado.copy(indicePassoAtual = estado.indicePassoAtual + 1, distanciaAteProximaManobraMetros = null)
        } else {
            estado.copy(distanciaAteProximaManobraMetros = distancia)
        }
    }

    fun confirmarEntrega(id: Long, sucesso: Boolean) {
        viewModelScope.launch {
            paradaRepository.confirmarEntrega(id, sucesso)
            recalcularAgora()
        }
    }

    fun reenviarParaRota(id: Long) {
        viewModelScope.launch {
            paradaRepository.reenviarParaRota(id)
            recalcularAgora()
        }
    }

    fun limparMensagem() {
        _uiState.value = _uiState.value.copy(mensagem = null)
    }
}
