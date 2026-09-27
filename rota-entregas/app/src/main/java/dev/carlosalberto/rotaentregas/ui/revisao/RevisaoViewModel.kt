package dev.carlosalberto.rotaentregas.ui.revisao

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.carlosalberto.rotaentregas.ServiceLocator
import dev.carlosalberto.rotaentregas.data.db.entity.ParadaEntity
import dev.carlosalberto.rotaentregas.data.model.StatusParada
import dev.carlosalberto.rotaentregas.data.parser.AddressParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class RevisaoViewModel : ViewModel() {

    private val paradaRepository = ServiceLocator.paradaRepository
    private val ocrProcessor = ServiceLocator.ocrProcessor

    val paradas: StateFlow<List<ParadaEntity>> = paradaRepository.observarTodas()
        .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000), emptyList())

    private val _processandoIds = MutableStateFlow<Set<Long>>(emptySet())
    val processandoIds: StateFlow<Set<Long>> = _processandoIds

    fun confirmarComCep(parada: ParadaEntity, cep: String) {
        viewModelScope.launch {
            marcarProcessando(parada.id, true)
            paradaRepository.aplicarCep(parada.id, cep)
            marcarProcessando(parada.id, false)
        }
    }

    fun reprocessarComNovaFoto(parada: ParadaEntity, novaFoto: Uri) {
        viewModelScope.launch {
            marcarProcessando(parada.id, true)
            val resultado = runCatching {
                val texto = ocrProcessor.reconhecerTexto(novaFoto)
                AddressParser.parse(texto)
            }.getOrNull()

            if (resultado != null) {
                val mesclada = parada.copy(
                    logradouro = parada.logradouro.ifBlank { resultado.logradouro },
                    numero = parada.numero.ifBlank { resultado.numero },
                    complemento = parada.complemento.ifBlank { resultado.complemento },
                    bairro = parada.bairro.ifBlank { resultado.bairro },
                    cidade = parada.cidade.ifBlank { resultado.cidade },
                    uf = parada.uf.ifBlank { resultado.uf },
                    cep = parada.cep.ifBlank { resultado.cep },
                    textoOcrBruto = parada.textoOcrBruto + "\n---\n" + resultado.textoOcrBruto
                )
                paradaRepository.confirmarDadosDaParada(parada.id, mesclada)
            }
            marcarProcessando(parada.id, false)
        }
    }

    fun excluirParada(id: Long) {
        viewModelScope.launch { paradaRepository.excluir(id) }
    }

    private fun marcarProcessando(id: Long, processando: Boolean) {
        _processandoIds.value = if (processando) _processandoIds.value + id else _processandoIds.value - id
    }
}

val ParadaEntity.precisaConfirmacao: Boolean
    get() = status == StatusParada.PENDENTE_DADOS
