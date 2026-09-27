package dev.carlosalberto.rotaentregas.ui.nav

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dev.carlosalberto.rotaentregas.ui.config.ConfiguracoesScreen
import dev.carlosalberto.rotaentregas.ui.importar.ImportarScreen
import dev.carlosalberto.rotaentregas.ui.mapa.MapaScreen
import dev.carlosalberto.rotaentregas.ui.painel.PainelScreen
import dev.carlosalberto.rotaentregas.ui.revisao.RevisaoScreen

private object Rotas {
    const val IMPORTAR = "importar"
    const val REVISAO = "revisao"
    const val MAPA = "mapa"
    const val PAINEL = "painel"
    const val CONFIG = "config"
}

private data class ItemBarraInferior(val rota: String, val rotulo: String, val icone: androidx.compose.ui.graphics.vector.ImageVector)

private val itensBarraInferior = listOf(
    ItemBarraInferior(Rotas.IMPORTAR, "Importar", Icons.Filled.AddAPhoto),
    ItemBarraInferior(Rotas.MAPA, "Rota", Icons.Filled.Map),
    ItemBarraInferior(Rotas.PAINEL, "Painel", Icons.Filled.BarChart),
    ItemBarraInferior(Rotas.CONFIG, "Ajustes", Icons.Filled.Settings)
)

@Composable
fun RotaEntregasNavHost() {
    val navController = rememberNavController()

    Scaffold(
        bottomBar = {
            val backStackEntry by navController.currentBackStackEntryAsState()
            val destinoAtual = backStackEntry?.destination
            NavigationBar {
                itensBarraInferior.forEach { item ->
                    NavigationBarItem(
                        selected = destinoAtual?.hierarchy?.any { it.route == item.rota } == true,
                        onClick = {
                            navController.navigate(item.rota) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(item.icone, contentDescription = item.rotulo) },
                        label = { Text(item.rotulo) }
                    )
                }
            }
        }
    ) { paddingInterno ->
        NavHost(
            navController = navController,
            startDestination = Rotas.IMPORTAR,
            modifier = Modifier.padding(paddingInterno)
        ) {
            composable(Rotas.IMPORTAR) {
                ImportarScreen(aoConcluirImportacao = {
                    navController.navigate(Rotas.REVISAO) { launchSingleTop = true }
                })
            }
            composable(Rotas.REVISAO) {
                RevisaoScreen(aoIrParaMapa = {
                    navController.navigate(Rotas.MAPA) {
                        popUpTo(Rotas.IMPORTAR) { inclusive = false }
                        launchSingleTop = true
                    }
                })
            }
            composable(Rotas.MAPA) { MapaScreen() }
            composable(Rotas.PAINEL) { PainelScreen() }
            composable(Rotas.CONFIG) { ConfiguracoesScreen() }
        }
    }
}
