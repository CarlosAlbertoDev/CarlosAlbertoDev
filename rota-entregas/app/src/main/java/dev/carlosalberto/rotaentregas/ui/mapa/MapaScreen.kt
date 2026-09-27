package dev.carlosalberto.rotaentregas.ui.mapa

import android.Manifest
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
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

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun MapaScreen(viewModel: MapaViewModel = viewModel()) {
    val estado by viewModel.uiState.collectAsState()
    val contexto = LocalContext.current
    var paradaSelecionada by remember { mutableStateOf<ParadaEntity?>(null) }

    val permissaoLocalizacao = rememberPermissionState(Manifest.permission.ACCESS_FINE_LOCATION)

    LaunchedEffect(permissaoLocalizacao.status) {
        if (permissaoLocalizacao.status.isGranted) {
            viewModel.iniciarAcompanhamento()
        } else {
            permissaoLocalizacao.launchPermissionRequest()
        }
    }

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { viewModel.recalcularAgora() },
                icon = { Icon(Icons.Filled.Refresh, contentDescription = null) },
                text = { Text(if (estado.calculandoRota) "Recalculando..." else "Recalcular rota") }
            )
        }
    ) { paddingInterno ->
        Column(modifier = Modifier.fillMaxSize().padding(paddingInterno)) {
            if (!estado.respeitandoSentidoDasRuas && estado.geometriaRota == null && estado.paradasAtivas.isNotEmpty()) {
                Snackbar(modifier = Modifier.padding(8.dp)) {
                    Text("Sem internet: usando distância em linha reta (sem considerar mão única das ruas)")
                }
            }

            Box(modifier = Modifier.weight(1f)) {
                MapaOsm(
                    estado = estado,
                    aoClicarParada = { paradaSelecionada = it }
                )
            }

            ResumoRota(estado = estado)
        }
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

@Composable
private fun ResumoRota(estado: MapaUiState) {
    Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
        Text(
            text = "${estado.paradasAtivas.size} parada(s) na rota · " +
                "${estado.paradasFalhas.size} com falha · ${estado.paradasEntregues.size} entregues hoje",
            style = MaterialTheme.typography.bodyMedium
        )
        estado.paradasAtivas.firstOrNull()?.let { proxima ->
            Text(
                text = "Próxima parada: ${proxima.enderecoCompleto}",
                style = MaterialTheme.typography.bodySmall
            )
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
            controller.setZoom(14.0)
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
                icon = MarcadorFactory.criarPino(contexto, cor)
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
                icon = MarcadorFactory.criarPino(contexto, MarcadorFactory.COR_USUARIO, tamanhoDp = 28)
                title = "Você está aqui"
            }
            mapView.overlays.add(marcadorUsuario)
            mapView.controller.animateTo(GeoPoint(local.latitude, local.longitude))
        }

        val pontosRota = estado.geometriaRota?.map { GeoPoint(it.latitude, it.longitude) }
            ?: buildList {
                estado.localizacaoAtual?.let { add(GeoPoint(it.latitude, it.longitude)) }
                estado.paradasAtivas.forEach { p ->
                    if (p.latitude != null && p.longitude != null) add(GeoPoint(p.latitude, p.longitude))
                }
            }
        if (pontosRota.size >= 2) {
            val linha = Polyline(mapView).apply {
                setPoints(pontosRota)
                outlinePaint.color = MarcadorFactory.COR_PROXIMA
                outlinePaint.strokeWidth = 8f
            }
            mapView.overlays.add(linha)
        }

        mapView.invalidate()
    }

    AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())
}
