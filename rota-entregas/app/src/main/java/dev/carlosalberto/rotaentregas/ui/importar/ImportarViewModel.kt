package dev.carlosalberto.rotaentregas.ui.importar

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.carlosalberto.rotaentregas.ServiceLocator
import dev.carlosalberto.rotaentregas.data.model.EnderecoReconhecido
import dev.carlosalberto.rotaentregas.data.parser.AddressParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

enum class StatusProcessamento { AGUARDANDO, PROCESSANDO, CONCLUIDO, ERRO }

data class ImagemImportada(
    val uri: Uri,
    val status: StatusProcessamento = StatusProcessamento.AGUARDANDO,
    // Uma foto pode conter um único endereço (etiqueta de pacote) ou uma lista inteira
    // de paradas (print da tela de itinerário de um app de entregas).
    val enderecos: List<EnderecoReconhecido> = emptyList()
)

data class ImportarUiState(
    val imagens: List<ImagemImportada> = emptyList(),
    val processando: Boolean = false,
    val importacaoConcluida: Boolean = false
)

class ImportarViewModel : ViewModel() {

    private val ocrProcessor = ServiceLocator.ocrProcessor
    private val paradaRepository = ServiceLocator.paradaRepository

    private val _uiState = MutableStateFlow(ImportarUiState())
    val uiState: StateFlow<ImportarUiState> = _uiState

    fun adicionarImagens(uris: List<Uri>) {
        if (uris.isEmpty()) return
        _uiState.value = _uiState.value.copy(
            imagens = _uiState.value.imagens + uris.map { ImagemImportada(uri = it) }
        )
    }

    fun removerImagem(uri: Uri) {
        _uiState.value = _uiState.value.copy(imagens = _uiState.value.imagens.filterNot { it.uri == uri })
    }

    fun processarTodasEImportar() {
        if (_uiState.value.processando) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(processando = true)

            val imagensAtualizadas = _uiState.value.imagens.toMutableList()
            for (i in imagensAtualizadas.indices) {
                val item = imagensAtualizadas[i]
                if (item.status == StatusProcessamento.CONCLUIDO) continue

                imagensAtualizadas[i] = item.copy(status = StatusProcessamento.PROCESSANDO)
                _uiState.value = _uiState.value.copy(imagens = imagensAtualizadas.toList())

                val resultado = runCatching {
                    val texto = ocrProcessor.reconhecerTexto(item.uri)
                    AddressParser.parseMultiplos(texto)
                }

                imagensAtualizadas[i] = if (resultado.isSuccess) {
                    item.copy(status = StatusProcessamento.CONCLUIDO, enderecos = resultado.getOrDefault(emptyList()))
                } else {
                    item.copy(status = StatusProcessamento.ERRO)
                }
                _uiState.value = _uiState.value.copy(imagens = imagensAtualizadas.toList())
            }

            val enderecosReconhecidos = imagensAtualizadas.flatMap { it.enderecos }
            if (enderecosReconhecidos.isNotEmpty()) {
                paradaRepository.importarLote(enderecosReconhecidos)
            }

            _uiState.value = _uiState.value.copy(processando = false, importacaoConcluida = true)
        }
    }

    fun reiniciar() {
        _uiState.value = ImportarUiState()
    }
}
