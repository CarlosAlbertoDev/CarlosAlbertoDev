package dev.carlosalberto.rotaentregas.data.repository

import dev.carlosalberto.rotaentregas.data.db.dao.EntregaDao
import dev.carlosalberto.rotaentregas.data.db.entity.EntregaRegistroEntity
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields
import java.util.Locale

enum class Granularidade { DIARIA, SEMANAL, MENSAL }

data class ResumoPeriodo(
    val chave: String,
    val rotulo: String,
    val entregasConcluidas: Int,
    val pacotesEntregues: Int,
    val tentativasFalhas: Int,
    val valorTotal: Double
)

class EntregaRepository(private val entregaDao: EntregaDao) {

    fun observarTodas(): Flow<List<EntregaRegistroEntity>> = entregaDao.observarTodas()

    fun agregarPorPeriodo(
        registros: List<EntregaRegistroEntity>,
        granularidade: Granularidade,
        zonaHoraria: ZoneId = ZoneId.systemDefault()
    ): List<ResumoPeriodo> {
        val formatoRotuloDia = DateTimeFormatter.ofPattern("dd/MM", Locale("pt", "BR"))
        val formatoRotuloMes = DateTimeFormatter.ofPattern("MM/yyyy", Locale("pt", "BR"))
        val camposDeSemana = WeekFields.of(Locale("pt", "BR"))

        val agrupado = registros.groupBy { registro ->
            val dataHora = Instant.ofEpochMilli(registro.dataHoraEpochMillis).atZone(zonaHoraria)
            when (granularidade) {
                Granularidade.DIARIA -> dataHora.toLocalDate().toString()
                Granularidade.SEMANAL -> {
                    val ano = dataHora.get(camposDeSemana.weekBasedYear())
                    val semana = dataHora.get(camposDeSemana.weekOfWeekBasedYear())
                    "%d-S%02d".format(ano, semana)
                }
                Granularidade.MENSAL -> dataHora.toLocalDate().withDayOfMonth(1).toString()
            }
        }

        return agrupado.entries
            .sortedByDescending { it.key }
            .map { (chave, itens) ->
                val primeiraDataHora = Instant.ofEpochMilli(itens.first().dataHoraEpochMillis).atZone(zonaHoraria)
                val rotulo = when (granularidade) {
                    Granularidade.DIARIA -> primeiraDataHora.format(formatoRotuloDia)
                    Granularidade.SEMANAL -> chave
                    Granularidade.MENSAL -> primeiraDataHora.format(formatoRotuloMes)
                }
                val concluidas = itens.filter { it.sucesso }
                ResumoPeriodo(
                    chave = chave,
                    rotulo = rotulo,
                    entregasConcluidas = concluidas.size,
                    pacotesEntregues = concluidas.sumOf { it.quantidadePacotes },
                    tentativasFalhas = itens.size - concluidas.size,
                    valorTotal = concluidas.sumOf { it.valorTotal }
                )
            }
    }
}
