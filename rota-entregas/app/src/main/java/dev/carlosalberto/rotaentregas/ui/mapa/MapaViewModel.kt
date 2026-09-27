package dev.carlosalberto.rotaentregas.ui.mapa

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.carlosalberto.rotaentregas.ServiceLocator
import dev.carlosalberto.rotaentregas.data.db.entity.ParadaEntity
import dev.carlosalberto.rotaentregas.data.geocode.Coordenada
import dev.carlosalberto.rotaentregas.data.model.StatusParada
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

data class MapaUiState(
    val paradasAtivas: List<ParadaEntity> = emptyList(),
    val paradasFalhas: List<ParadaEntity> = emptyList(),
    val paradasEntregues: List<ParadaEntity> = emptyList(),
    val localizacaoAtual: Coordenada? = null,
    val geometriaRota: List<Coordenada>? = null,
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
            // aqui" (onEach) e, em seguida, é avaliada para decidir se recalcula a rota.
            val localizacoesComAtualizacaoDeEstado = locationTracker.localizacoes().onEach { localizacao ->
                _uiState.value = _uiState.value.copy(localizacaoAtual = localizacao)
            }
            routeCoordinator.monitorarEReotimizarContinuamente(
                localizacoes = localizacoesComAtualizacaoDeEstado
            ) { resultado ->
                _uiState.value = _uiState.value.copy(calculandoRota = false)
                if (resultado != null) {
                    _uiState.value = _uiState.value.copy(
                        geometriaRota = resultado.geometria,
                        respeitandoSentidoDasRuas = resultado.respeitouSentidoDasRuas
                    )
                }
            }
        }
    }

    fun recalcularAgora() {
        val origem = _uiState.value.localizacaoAtual ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(calculandoRota = true, mensagem = null)
            val resultado = routeCoordinator.recalcularRotaAtiva(origem)
            _uiState.value = _uiState.value.copy(
                calculandoRota = false,
                geometriaRota = resultado?.geometria,
                respeitandoSentidoDasRuas = resultado?.respeitouSentidoDasRuas ?: false,
                mensagem = if (resultado == null) "Nenhuma parada pronta para rota ainda" else null
            )
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
