package dev.carlosalberto.rotaentregas.ui.importar

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import dev.carlosalberto.rotaentregas.util.ImagemUtils

@Composable
fun ImportarScreen(
    viewModel: ImportarViewModel = viewModel(),
    aoConcluirImportacao: () -> Unit
) {
    val estado by viewModel.uiState.collectAsState()
    val contexto = LocalContext.current
    var uriFotoPendente by remember { mutableStateOf<Uri?>(null) }

    val lancadorCamera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { sucesso ->
        if (sucesso) uriFotoPendente?.let { viewModel.adicionarImagens(listOf(it)) }
    }

    val lancadorGaleria = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(maxItems = 20)
    ) { uris -> viewModel.adicionarImagens(uris) }

    androidx.compose.runtime.LaunchedEffect(estado.importacaoConcluida) {
        if (estado.importacaoConcluida) {
            viewModel.reiniciar()
            aoConcluirImportacao()
        }
    }

    Scaffold { paddingInterno ->
        Column(modifier = Modifier.fillMaxSize().padding(paddingInterno).padding(16.dp)) {
            Text(
                text = "Importar endereços",
                style = MaterialTheme.typography.headlineSmall
            )
            Text(
                text = "Tire fotos ou selecione da galeria. O app reconhece endereço, número e " +
                    "quantidade de pacotes automaticamente.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    val uri = ImagemUtils.criarUriParaFoto(contexto)
                    uriFotoPendente = uri
                    lancadorCamera.launch(uri)
                }) {
                    Icon(Icons.Filled.CameraAlt, contentDescription = null)
                    Text(" Câmera", modifier = Modifier.padding(start = 4.dp))
                }
                OutlinedButton(onClick = {
                    lancadorGaleria.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }) {
                    Icon(Icons.Filled.PhotoLibrary, contentDescription = null)
                    Text(" Galeria", modifier = Modifier.padding(start = 4.dp))
                }
            }

            Text(
                text = "${estado.imagens.size} foto(s) adicionada(s)",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(vertical = 12.dp)
            )

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(estado.imagens, key = { it.uri }) { imagem ->
                    ItemImagemImportada(imagem = imagem, aoRemover = { viewModel.removerImagem(imagem.uri) })
                }
            }

            Button(
                onClick = { viewModel.processarTodasEImportar() },
                enabled = estado.imagens.isNotEmpty() && !estado.processando,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (estado.processando) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White)
                    Text(" Processando...", modifier = Modifier.padding(start = 8.dp))
                } else {
                    Text("Processar e continuar")
                }
            }
        }
    }
}

@Composable
private fun ItemImagemImportada(imagem: ImagemImportada, aoRemover: () -> Unit) {
    Card {
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AsyncImage(
                model = imagem.uri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(64.dp).aspectRatio(1f)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = imagem.endereco?.logradouro?.ifBlank { null } ?: "Aguardando processamento",
                    style = MaterialTheme.typography.bodyMedium
                )
                if (imagem.endereco != null) {
                    Text(
                        text = "Nº ${imagem.endereco.numero.ifBlank { "?" }} · " +
                            "${imagem.endereco.quantidadePacotes} pacote(s)",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            Box(modifier = Modifier.size(28.dp), contentAlignment = Alignment.Center) {
                when (imagem.status) {
                    StatusProcessamento.AGUARDANDO -> {}
                    StatusProcessamento.PROCESSANDO -> CircularProgressIndicator(modifier = Modifier.size(20.dp))
                    StatusProcessamento.CONCLUIDO -> Icon(
                        Icons.Filled.CheckCircle, contentDescription = null, tint = Color(0xFF2E7D32)
                    )
                    StatusProcessamento.ERRO -> Icon(
                        Icons.Filled.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error
                    )
                }
            }
            IconButton(onClick = aoRemover) {
                Icon(Icons.Filled.Close, contentDescription = "Remover")
            }
        }
    }
}
