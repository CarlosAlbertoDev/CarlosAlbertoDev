package dev.carlosalberto.rotaentregas.ui.painel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.carlosalberto.rotaentregas.data.repository.Granularidade
import dev.carlosalberto.rotaentregas.data.repository.ResumoPeriodo
import java.text.NumberFormat
import java.util.Locale

private val formatoMoeda: NumberFormat = NumberFormat.getCurrencyInstance(Locale("pt", "BR"))

@Composable
fun PainelScreen(viewModel: PainelViewModel = viewModel()) {
    val estado by viewModel.uiState.collectAsState()
    val abas = listOf(Granularidade.DIARIA to "Diário", Granularidade.SEMANAL to "Semanal", Granularidade.MENSAL to "Mensal")

    Scaffold { paddingInterno ->
        Column(modifier = Modifier.fillMaxSize().padding(paddingInterno).padding(16.dp)) {
            Text("Acompanhamento de entregas", style = MaterialTheme.typography.headlineSmall)

            CardResumoHoje(
                entregas = estado.entregasHoje,
                pacotes = estado.pacotesHoje,
                valor = estado.valorHoje,
                modifier = Modifier.padding(vertical = 16.dp)
            )

            val indiceSelecionado = abas.indexOfFirst { it.first == estado.granularidade }
            TabRow(selectedTabIndex = indiceSelecionado.coerceAtLeast(0)) {
                abas.forEachIndexed { indice, (granularidade, rotulo) ->
                    Tab(
                        selected = indice == indiceSelecionado,
                        onClick = { viewModel.selecionarGranularidade(granularidade) },
                        text = { Text(rotulo) }
                    )
                }
            }

            if (estado.resumos.isEmpty()) {
                Text(
                    "Nenhuma entrega registrada ainda neste período.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 24.dp)
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(estado.resumos, key = { it.chave }) { resumo -> LinhaResumoPeriodo(resumo) }
                }
            }
        }
    }
}

@Composable
private fun CardResumoHoje(entregas: Int, pacotes: Int, valor: Double, modifier: Modifier = Modifier) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Hoje", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                ItemMetrica(rotulo = "Entregas", valor = entregas.toString())
                ItemMetrica(rotulo = "Pacotes", valor = pacotes.toString())
                ItemMetrica(rotulo = "Faturado", valor = formatoMoeda.format(valor))
            }
        }
    }
}

@Composable
private fun ItemMetrica(rotulo: String, valor: String) {
    Column {
        Text(valor, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(rotulo, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun LinhaResumoPeriodo(resumo: ResumoPeriodo) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(resumo.rotulo, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                Text(
                    "${resumo.entregasConcluidas} entrega(s) · ${resumo.pacotesEntregues} pacote(s)" +
                        if (resumo.tentativasFalhas > 0) " · ${resumo.tentativasFalhas} falha(s)" else "",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Text(
                formatoMoeda.format(resumo.valorTotal),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
