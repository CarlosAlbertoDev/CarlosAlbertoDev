package dev.carlosalberto.rotaentregas.ui.painel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.carlosalberto.rotaentregas.ServiceLocator
import dev.carlosalberto.rotaentregas.data.db.entity.EntregaRegistroEntity
import dev.carlosalberto.rotaentregas.data.repository.Granularidade
import dev.carlosalberto.rotaentregas.data.repository.ResumoPeriodo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class PainelUiState(
    val granularidade: Granularidade = Granularidade.DIARIA,
    val resumos: List<ResumoPeriodo> = emptyList(),
    val entregasHoje: Int = 0,
    val pacotesHoje: Int = 0,
    val valorHoje: Double = 0.0
)

class PainelViewModel : ViewModel() {

    private val entregaRepository = ServiceLocator.entregaRepository

    private val _granularidade = MutableStateFlow(Granularidade.DIARIA)
    private val _uiState = MutableStateFlow(PainelUiState())
    val uiState: StateFlow<PainelUiState> = _uiState

    init {
        viewModelScope.launch {
            combine(entregaRepository.observarTodas(), _granularidade) { registros, granularidade ->
                registros to granularidade
            }.collect { (registros, granularidade) ->
                val resumoHoje = calcularResumoHoje(registros)
                _uiState.value = PainelUiState(
                    granularidade = granularidade,
                    resumos = entregaRepository.agregarPorPeriodo(registros, granularidade),
                    entregasHoje = resumoHoje.first,
                    pacotesHoje = resumoHoje.second,
                    valorHoje = resumoHoje.third
                )
            }
        }
    }

    fun selecionarGranularidade(granularidade: Granularidade) {
        _granularidade.value = granularidade
    }

    private fun calcularResumoHoje(registros: List<EntregaRegistroEntity>): Triple<Int, Int, Double> {
        val hoje = LocalDate.now()
        val doDia = registros.filter { registro ->
            registro.sucesso &&
                Instant.ofEpochMilli(registro.dataHoraEpochMillis).atZone(ZoneId.systemDefault()).toLocalDate() == hoje
        }
        return Triple(doDia.size, doDia.sumOf { it.quantidadePacotes }, doDia.sumOf { it.valorTotal })
    }
}
