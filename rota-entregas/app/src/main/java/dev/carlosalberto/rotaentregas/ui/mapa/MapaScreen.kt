package dev.carlosalberto.rotaentregas.ui.mapa

import android.Manifest
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import dev.carlosalberto.rotaentregas.data.db.entity.ParadaEntity
import dev.carlosalberto.rotaentregas.data.model.StatusParada
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.util.Locale

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun MapaScreen(viewModel: MapaViewModel = viewModel()) {
    val estado by viewModel.uiState.collectAsState()
    val contexto = LocalContext.current
    var paradaSelecionada by remember { mutableStateOf<ParadaEntity?>(null) }
    var modoImersivo by remember { mutableStateOf(false) }

    val permissaoLocalizacao = rememberPermissionState(Manifest.permission.ACCESS_FINE_LOCATION)

    LaunchedEffect(permissaoLocalizacao.status) {
        if (permissaoLocalizacao.status.isGranted) {
            viewModel.iniciarAcompanhamento()
        } else {
            permissaoLocalizacao.launchPermissionRequest()
        }
    }

    Scaffold { paddingInterno ->
        Column(modifier = Modifier.fillMaxSize().padding(paddingInterno)) {
            if (estado.navegacaoAtual == null && !estado.calculandoRota && estado.paradasAtivas.isNotEmpty()) {
                Snackbar(modifier = Modifier.padding(8.dp)) {
                    Text("Sem instruções de navegação no momento (sem internet ou aguardando localização)")
                }
            }

            Box(modifier = Modifier.weight(1f)) {
                MapaOsm(
                    estado = estado,
                    aoClicarParada = { paradaSelecionada = it }
                )
                CartaoInstrucao(
                    estado = estado,
                    modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(12.dp)
                )
            }

            ResumoRota(estado = estado, aoIniciarNavegacao = { modoImersivo = true })
        }
    }

    if (modoImersivo) {
        NavegacaoImersiva(estado = estado, aoFechar = { modoImersivo = false })
    }

    paradaSelecionada?.let { parada ->
        ModalBottomSheet(onDismissRequest = { paradaSelecionada = null }) {
            DetalheParadaBottomSheet(
                parada = parada,
                aoConfirmar = { sucesso ->
                    viewModel.confirmarEntrega(parada.id, sucesso)
                    paradaSelecionada = null
                },
                aoReenviar = {
                    viewModel.reenviarParaRota(parada.id)
                    paradaSelecionada = null
                }
            )
        }
    }
}

/** Banner de instrução no topo do mapa, no estilo de um app de navegação: uma manobra por vez. */
@Composable
private fun CartaoInstrucao(estado: MapaUiState, modifier: Modifier = Modifier) {
    val navegacao = estado.navegacaoAtual ?: return
    val passoAtual = navegacao.passosDaPernaAtual.getOrNull(estado.indicePassoAtual)

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Navigation,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary
                )
                Column(modifier = Modifier.padding(start = 10.dp)) {
                    Text(
                        text = passoAtual?.instrucao ?: "Você chegou",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    estado.distanciaAteProximaManobraMetros?.let { distancia ->
                        Text(
                            text = formatarDistancia(distancia),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }
            Text(
                text = "Destino: ${navegacao.endereco}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

private fun formatarDistancia(metros: Double): String =
    if (metros >= 1000) String.format(Locale("pt", "BR"), "%.1f km", metros / 1000)
    else "${metros.toInt()} m"

@Composable
private fun ResumoRota(estado: MapaUiState, aoIniciarNavegacao: () -> Unit) {
    // A rota é recalculada sozinha: periodicamente conforme o entregador se desloca,
    // na hora se ele sair do trajeto sugerido, e a cada entrega confirmada/marcada como
    // falha — não há necessidade de um botão manual de recálculo.
    Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
        Text(
            text = "${estado.paradasAtivas.size} parada(s) na rota · " +
                "${estado.paradasFalhas.size} com falha · ${estado.paradasEntregues.size} entregues hoje" +
                if (estado.calculandoRota) " · recalculando..." else "",
            style = MaterialTheme.typography.bodyMedium
        )
        if (estado.navegacaoAtual != null) {
            Button(
                onClick = aoIniciarNavegacao,
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                Text(" Navegar", modifier = Modifier.padding(start = 4.dp))
            }
        }
    }
}

@Composable
private fun DetalheParadaBottomSheet(
    parada: ParadaEntity,
    aoConfirmar: (Boolean) -> Unit,
    aoReenviar: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
        Text(parada.enderecoCompleto, style = MaterialTheme.typography.titleMedium)
        Text(
            "${parada.quantidadePacotes} pacote(s)",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
        )

        if (parada.status == StatusParada.FALHOU) {
            Text(
                "Esta entrega falhou e está fora da rota ativa.",
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            Button(onClick = aoReenviar, modifier = Modifier.fillMaxWidth()) {
                Text("Tentar novamente (voltar para a rota)")
            }
        } else {
            Button(
                onClick = { aoConfirmar(true) },
                colors = ButtonDefaults.buttonColors(containerColor = androidx.compose.ui.graphics.Color(0xFF2E7D32)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null)
                Text(" Confirmar entrega", modifier = Modifier.padding(start = 6.dp))
            }
            OutlinedButton(
                onClick = { aoConfirmar(false) },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            ) {
                Icon(Icons.Filled.Warning, contentDescription = null)
                Text(" Marcar como falha", modifier = Modifier.padding(start = 6.dp))
            }
        }
    }
}

@Composable
private fun MapaOsm(estado: MapaUiState, aoClicarParada: (ParadaEntity) -> Unit) {
    val contexto = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mapView = remember {
        MapView(contexto).apply {
            setMultiTouchControls(true)
            // Os botões +/- nativos do osmdroid ficam mal posicionados quando o
            // tamanho do mapa muda dinamicamente (ex.: aviso de "sem internet"
            // aparecendo/sumindo) e acabam sobrepondo o texto de resumo abaixo do
            // mapa; o gesto de pinça (já habilitado acima) cobre a mesma função.
            zoomController.setVisibility(org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER)
            controller.setZoom(16.0)
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onDetach()
        }
    }

    LaunchedEffect(estado, mapView) {
        mapView.overlays.clear()

        estado.paradasAtivas.forEachIndexed { indice, parada ->
            val lat = parada.latitude ?: return@forEachIndexed
            val lon = parada.longitude ?: return@forEachIndexed
            val cor = if (indice == 0) MarcadorFactory.COR_PROXIMA else MarcadorFactory.COR_PENDENTE
            val marcador = Marker(mapView).apply {
                position = GeoPoint(lat, lon)
                icon = MarcadorFactory.criarPino(contexto, cor, rotulo = "${indice + 1}")
                title = "${indice + 1}. ${parada.enderecoCompleto}"
                setOnMarkerClickListener { _, _ -> aoClicarParada(parada); true }
            }
            mapView.overlays.add(marcador)
        }

        estado.paradasFalhas.forEach { parada ->
            val lat = parada.latitude ?: return@forEach
            val lon = parada.longitude ?: return@forEach
            val marcador = Marker(mapView).apply {
                position = GeoPoint(lat, lon)
                icon = MarcadorFactory.criarPino(contexto, MarcadorFactory.COR_FALHOU)
                title = "Falhou: ${parada.enderecoCompleto}"
                setOnMarkerClickListener { _, _ -> aoClicarParada(parada); true }
            }
            mapView.overlays.add(marcador)
        }

        estado.localizacaoAtual?.let { local ->
            val marcadorUsuario = Marker(mapView).apply {
                position = GeoPoint(local.latitude, local.longitude)
                icon = MarcadorFactory.criarSetaDirecao(contexto, MarcadorFactory.COR_USUARIO)
                rotation = local.direcaoGraus
                title = "Você está aqui"
            }
            mapView.overlays.add(marcadorUsuario)
            mapView.controller.animateTo(GeoPoint(local.latitude, local.longitude))
        }

        // Traçado completo por todas as paradas pendentes, como o modo de vários destinos
        // do Google Maps; sem internet, cai para uma linha reta só até a próxima parada.
        val proximaParada = estado.paradasAtivas.firstOrNull()
        val pontosRota = estado.navegacaoAtual?.geometriaCompleta?.map { GeoPoint(it.latitude, it.longitude) }
            ?: estado.localizacaoAtual?.let { local ->
                proximaParada?.takeIf { it.latitude != null && it.longitude != null }?.let { parada ->
                    listOf(GeoPoint(local.latitude, local.longitude), GeoPoint(parada.latitude!!, parada.longitude!!))
                }
            }.orEmpty()

        if (pontosRota.size >= 2) {
            val linha = Polyline(mapView).apply {
                setPoints(pontosRota)
                outlinePaint.color = MarcadorFactory.COR_PROXIMA
                outlinePaint.strokeWidth = 10f
            }
            mapView.overlays.add(linha)
        }

        mapView.invalidate()
    }

    AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())
}
