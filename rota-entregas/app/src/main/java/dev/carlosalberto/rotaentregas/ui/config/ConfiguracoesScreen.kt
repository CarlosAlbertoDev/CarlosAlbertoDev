package dev.carlosalberto.rotaentregas.ui.config

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun ConfiguracoesScreen(viewModel: ConfiguracoesViewModel = viewModel()) {
    val valorAtual by viewModel.valorPorPacote.collectAsState()
    val osrmUrlAtual by viewModel.osrmBaseUrl.collectAsState()

    var textoValor by remember { mutableStateOf("") }
    var textoOsrmUrl by remember { mutableStateOf("") }

    LaunchedEffect(valorAtual) { if (textoValor.isEmpty()) textoValor = "%.2f".format(valorAtual) }
    LaunchedEffect(osrmUrlAtual) { if (textoOsrmUrl.isEmpty()) textoOsrmUrl = osrmUrlAtual }

    Scaffold { paddingInterno ->
        Column(modifier = Modifier.fillMaxSize().padding(paddingInterno).padding(16.dp)) {
            Text("Configurações", style = MaterialTheme.typography.headlineSmall)

            Text(
                "Valor pago por pacote entregue",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 20.dp, bottom = 8.dp)
            )
            OutlinedTextField(
                value = textoValor,
                onValueChange = { textoValor = it },
                label = { Text("Valor por pacote (R$)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = { textoValor.toDoubleOrNull()?.let(viewModel::salvarValorPorPacote) },
                modifier = Modifier.padding(top = 8.dp)
            ) { Text("Salvar valor") }

            Text(
                "Servidor de roteamento (OSRM)",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 28.dp, bottom = 4.dp)
            )
            Text(
                "Usado para calcular a rota seguindo as ruas de verdade (respeita mão única). " +
                    "O padrão é um servidor público de demonstração; para uso intenso, aponte para " +
                    "sua própria instância OSRM.",
                style = MaterialTheme.typography.bodySmall
            )
            OutlinedTextField(
                value = textoOsrmUrl,
                onValueChange = { textoOsrmUrl = it },
                label = { Text("URL base do OSRM") },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )
            Button(
                onClick = { viewModel.salvarOsrmUrl(textoOsrmUrl) },
                modifier = Modifier.padding(top = 8.dp)
            ) { Text("Salvar servidor") }

            OutlinedButton(
                onClick = { viewModel.limparEntregasConcluidas() },
                modifier = Modifier.fillMaxWidth().padding(top = 32.dp)
            ) { Text("Limpar entregues/falhas do mapa") }
        }
    }
}
