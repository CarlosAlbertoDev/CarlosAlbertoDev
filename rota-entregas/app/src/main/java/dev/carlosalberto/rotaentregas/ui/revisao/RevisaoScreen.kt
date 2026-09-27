package dev.carlosalberto.rotaentregas.ui.revisao

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.carlosalberto.rotaentregas.data.db.entity.ParadaEntity
import dev.carlosalberto.rotaentregas.data.model.StatusParada
import dev.carlosalberto.rotaentregas.util.ImagemUtils

@Composable
fun RevisaoScreen(
    viewModel: RevisaoViewModel = viewModel(),
    aoIrParaMapa: () -> Unit
) {
    val paradas by viewModel.paradas.collectAsState()
    val processandoIds by viewModel.processandoIds.collectAsState()

    val pendentes = paradas.filter { it.precisaConfirmacao }
    val confirmadas = paradas.filterNot { it.precisaConfirmacao }

    Scaffold { paddingInterno ->
        Column(modifier = Modifier.fillMaxSize().padding(paddingInterno).padding(16.dp)) {
            Text("Revisão dos endereços", style = MaterialTheme.typography.headlineSmall)

            if (pendentes.isNotEmpty()) {
                Text(
                    text = "${pendentes.size} endereço(s) precisam de confirmação (faltou número, CEP " +
                        "ou outra informação para montar a rota)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(pendentes, key = { it.id }) { parada ->
                    CardParadaPendente(
                        parada = parada,
                        processando = parada.id in processandoIds,
                        aoConfirmarCep = { cep -> viewModel.confirmarComCep(parada, cep) },
                        aoReprocessarFoto = { uri -> viewModel.reprocessarComNovaFoto(parada, uri) },
                        aoExcluir = { viewModel.excluirParada(parada.id) }
                    )
                }
                items(confirmadas, key = { it.id }) { parada ->
                    CardParadaConfirmada(parada = parada, aoExcluir = { viewModel.excluirParada(parada.id) })
                }
            }

            Button(
                onClick = aoIrParaMapa,
                enabled = confirmadas.isNotEmpty(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Ir para o mapa e montar rota (${confirmadas.size} prontas)")
            }
        }
    }
}

@Composable
private fun CardParadaPendente(
    parada: ParadaEntity,
    processando: Boolean,
    aoConfirmarCep: (String) -> Unit,
    aoReprocessarFoto: (android.net.Uri) -> Unit,
    aoExcluir: () -> Unit
) {
    val contexto = LocalContext.current
    var cepDigitado by remember { mutableStateOf(parada.cep) }
    var uriFoto by remember { mutableStateOf<android.net.Uri?>(null) }

    val lancadorCamera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { sucesso ->
        if (sucesso) uriFoto?.let(aoReprocessarFoto)
    }

    Card(modifier = Modifier.fillMaxWidth().padding(4.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = parada.enderecoCompleto.ifBlank { "Endereço não identificado" },
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = aoExcluir) { Icon(Icons.Filled.Delete, contentDescription = "Excluir") }
            }
            Text(
                text = "${parada.quantidadePacotes} pacote(s) · texto lido: \"" +
                    parada.textoOcrBruto.take(80).replace("\n", " ") + "\"",
                style = MaterialTheme.typography.bodySmall
            )

            if (processando) {
                CircularProgressIndicator(modifier = Modifier.padding(top = 8.dp))
            } else {
                Row(
                    modifier = Modifier.padding(top = 8.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = cepDigitado,
                        onValueChange = { cepDigitado = it },
                        label = { Text("CEP") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Button(onClick = { aoConfirmarCep(cepDigitado) }, enabled = cepDigitado.length >= 8) {
                        Text("Buscar")
                    }
                }
                OutlinedButton(
                    onClick = {
                        val uri = ImagemUtils.criarUriParaFoto(contexto)
                        uriFoto = uri
                        lancadorCamera.launch(uri)
                    },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                ) {
                    Icon(Icons.Filled.CameraAlt, contentDescription = null)
                    Text(" Enviar nova foto", modifier = Modifier.padding(start = 4.dp))
                }
            }
        }
    }
}

@Composable
private fun CardParadaConfirmada(parada: ParadaEntity, aoExcluir: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().padding(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(parada.enderecoCompleto, style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = "${parada.quantidadePacotes} pacote(s) · ${rotuloStatus(parada.status)}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            IconButton(onClick = aoExcluir) { Icon(Icons.Filled.Delete, contentDescription = "Excluir") }
        }
    }
}

private fun rotuloStatus(status: StatusParada): String = when (status) {
    StatusParada.PENDENTE_ROTA -> "aguardando localização no mapa"
    StatusParada.A_CAMINHO -> "pronta para a rota"
    StatusParada.ENTREGUE -> "entregue"
    StatusParada.FALHOU -> "entrega falhou"
    StatusParada.PENDENTE_DADOS -> "precisa de confirmação"
}
