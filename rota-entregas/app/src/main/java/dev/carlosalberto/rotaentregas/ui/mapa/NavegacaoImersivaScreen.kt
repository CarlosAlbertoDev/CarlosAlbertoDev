package dev.carlosalberto.rotaentregas.ui.mapa

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.util.Locale

/**
 * Modo de navegação em tela cheia: mapa girando conforme a direção do deslocamento,
 * banner grande de instrução com seta indicando a manobra, velocímetro e distância/tempo
 * restantes — no estilo de apps como Waze/Google Maps. Diferença conhecida: como o mapa
 * usado (osmdroid) é 2D, não há a inclinação/perspectiva 3D desses apps, só o giro.
 */
@Composable
fun NavegacaoImersiva(estado: MapaUiState, aoFechar: () -> Unit) {
    Dialog(
        onDismissRequest = aoFechar,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0B1F2A))) {
            MapaNavegacaoImersiva(estado = estado)

            Column(modifier = Modifier.fillMaxSize()) {
                CartaoInstrucaoGrande(estado = estado)
                Box(modifier = Modifier.weight(1f))
                BarraInferiorNavegacao(estado = estado, aoFechar = aoFechar)
            }

            VelocimetroChip(
                velocidadeMetrosPorSegundo = estado.localizacaoAtual?.velocidadeMetrosPorSegundo ?: 0f,
                modifier = Modifier.align(Alignment.BottomStart).padding(start = 16.dp, bottom = 96.dp)
            )
        }
    }
}

@Composable
private fun CartaoInstrucaoGrande(estado: MapaUiState) {
    val navegacao = estado.navegacaoAtual ?: return
    val passoAtual = navegacao.passosDaPernaAtual.getOrNull(estado.indicePassoAtual)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF00695C))
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (passoAtual == null) {
            Icon(Icons.Filled.Flag, contentDescription = null, tint = Color.White, modifier = Modifier.size(48.dp))
        } else {
            Icon(
                Icons.Filled.ArrowUpward,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(48.dp).rotate(anguloParaModificador(passoAtual.modificador))
            )
        }
        Column(modifier = Modifier.padding(start = 16.dp)) {
            estado.distanciaAteProximaManobraMetros?.let { distancia ->
                Text(
                    text = formatarDistancia(distancia),
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White
                )
            }
            Text(
                text = passoAtual?.instrucao ?: "Você chegou a ${navegacao.endereco}",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White
            )
        }
    }
}

private fun anguloParaModificador(modificador: String?): Float = when (modificador) {
    "uturn" -> 180f
    "sharp left" -> -135f
    "left" -> -90f
    "slight left" -> -45f
    "slight right" -> 45f
    "right" -> 90f
    "sharp right" -> 135f
    else -> 0f
}

@Composable
private fun VelocimetroChip(velocidadeMetrosPorSegundo: Float, modifier: Modifier = Modifier) {
    val kmh = (velocidadeMetrosPorSegundo * 3.6f).toInt().coerceAtLeast(0)
    Box(
        modifier = modifier
            .size(72.dp)
            .clip(CircleShape)
            .background(Color.White),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = "$kmh", style = MaterialTheme.typography.titleLarge, color = Color.Black)
            Text(text = "km/h", style = MaterialTheme.typography.labelSmall, color = Color.Black)
        }
    }
}

@Composable
private fun BarraInferiorNavegacao(estado: MapaUiState, aoFechar: () -> Unit) {
    val navegacao = estado.navegacaoAtual
    val passosRestantes = navegacao?.passosDaPernaAtual?.drop(estado.indicePassoAtual).orEmpty()
    val distanciaRestante = passosRestantes.sumOf { it.distanciaMetros }
    val tempoRestanteMinutos = (passosRestantes.sumOf { it.duracaoSegundos } / 60).toInt().coerceAtLeast(0)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF0B1F2A))
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text("$tempoRestanteMinutos min", style = MaterialTheme.typography.headlineSmall, color = Color(0xFF69F0AE))
            Text(
                text = "${formatarDistancia(distanciaRestante)} · ${navegacao?.endereco.orEmpty()}",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
                maxLines = 1
            )
        }
        IconButton(onClick = aoFechar) {
            Icon(Icons.Filled.Close, contentDescription = "Sair da navegação", tint = Color.White)
        }
    }
}

private fun formatarDistancia(metros: Double): String =
    if (metros >= 1000) String.format(Locale("pt", "BR"), "%.1f km", metros / 1000)
    else "${metros.toInt()} m"

@Composable
private fun MapaNavegacaoImersiva(estado: MapaUiState) {
    val contexto = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mapView = remember {
        MapView(contexto).apply {
            setMultiTouchControls(false)
            controller.setZoom(18.5)
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

        estado.navegacaoAtual?.geometriaCompleta?.map { GeoPoint(it.latitude, it.longitude) }
            ?.takeIf { it.size >= 2 }
            ?.let { pontos ->
                mapView.overlays.add(
                    Polyline(mapView).apply {
                        setPoints(pontos)
                        outlinePaint.color = MarcadorFactory.COR_PROXIMA
                        outlinePaint.strokeWidth = 14f
                    }
                )
            }

        estado.paradasAtivas.firstOrNull()?.let { proxima ->
            val lat = proxima.latitude
            val lon = proxima.longitude
            if (lat != null && lon != null) {
                mapView.overlays.add(
                    Marker(mapView).apply {
                        position = GeoPoint(lat, lon)
                        icon = MarcadorFactory.criarPino(contexto, MarcadorFactory.COR_PROXIMA, rotulo = "1")
                    }
                )
            }
        }

        estado.localizacaoAtual?.let { local ->
            mapView.overlays.add(
                Marker(mapView).apply {
                    position = GeoPoint(local.latitude, local.longitude)
                    icon = MarcadorFactory.criarPino(contexto, MarcadorFactory.COR_USUARIO, tamanhoDp = 30)
                }
            )
            // Gira o mapa conforme a direção do deslocamento, para a rota aparecer sempre
            // "para a frente" — a aproximação possível de perspectiva de condução sem um
            // motor de mapa com suporte a câmera 3D.
            mapView.setMapOrientation(-local.direcaoGraus)
            mapView.controller.animateTo(GeoPoint(local.latitude, local.longitude))
        }

        mapView.invalidate()
    }

    AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())
}
