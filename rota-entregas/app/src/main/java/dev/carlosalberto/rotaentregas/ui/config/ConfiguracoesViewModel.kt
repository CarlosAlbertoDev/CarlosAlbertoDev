package dev.carlosalberto.rotaentregas.ui.config

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.carlosalberto.rotaentregas.ServiceLocator
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ConfiguracoesViewModel : ViewModel() {

    private val settingsRepository = ServiceLocator.settingsRepository
    private val paradaRepository = ServiceLocator.paradaRepository

    val valorPorPacote: StateFlow<Double> = settingsRepository.valorPorPacote
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 2.0)

    val osrmBaseUrl: StateFlow<String> = settingsRepository.osrmBaseUrl
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    fun salvarValorPorPacote(valor: Double) {
        viewModelScope.launch { settingsRepository.definirValorPorPacote(valor) }
    }

    fun salvarOsrmUrl(url: String) {
        viewModelScope.launch { settingsRepository.definirOsrmBaseUrl(url) }
    }

    fun limparEntregasConcluidas() {
        viewModelScope.launch { paradaRepository.limparConcluidas() }
    }
}
