package dev.carlosalberto.rotaentregas.data.repository

import dev.carlosalberto.rotaentregas.data.db.dao.EntregaDao
import dev.carlosalberto.rotaentregas.data.db.dao.ParadaDao
import dev.carlosalberto.rotaentregas.data.db.entity.EntregaRegistroEntity
import dev.carlosalberto.rotaentregas.data.db.entity.ParadaEntity
import dev.carlosalberto.rotaentregas.data.geocode.GeocodingRepository
import dev.carlosalberto.rotaentregas.data.model.EnderecoReconhecido
import dev.carlosalberto.rotaentregas.data.model.StatusParada
import dev.carlosalberto.rotaentregas.data.settings.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.util.UUID

class ParadaRepository(
    private val paradaDao: ParadaDao,
    private val entregaDao: EntregaDao,
    private val geocodingRepository: GeocodingRepository,
    private val settingsRepository: SettingsRepository
) {

    fun observarTodas(): Flow<List<ParadaEntity>> = paradaDao.observarTodas()

    fun observarAtivas(): Flow<List<ParadaEntity>> = paradaDao.observarAtivas()

    suspend fun buscarPorId(id: Long): ParadaEntity? = paradaDao.buscarPorId(id)

    /** Persiste um lote de endereços reconhecidos por OCR como novas paradas. */
    suspend fun importarLote(enderecos: List<EnderecoReconhecido>): List<Long> {
        val loteId = UUID.randomUUID().toString()
        val entidades = enderecos.map { endereco ->
            ParadaEntity(
                loteImportacaoId = loteId,
                textoOcrBruto = endereco.textoOcrBruto,
                logradouro = endereco.logradouro,
                numero = endereco.numero,
                complemento = endereco.complemento,
                bairro = endereco.bairro,
                cidade = endereco.cidade,
                uf = endereco.uf,
                cep = endereco.cep,
                quantidadePacotes = endereco.quantidadePacotes,
                status = if (endereco.faltamDadosEssenciais) StatusParada.PENDENTE_DADOS else StatusParada.PENDENTE_ROTA
            )
        }
        return paradaDao.inserirTodas(entidades)
    }

    /** Aplica a confirmação manual (CEP digitado ou nova foto) a uma parada com dados incompletos. */
    suspend fun confirmarDadosDaParada(id: Long, atualizacao: ParadaEntity) {
        paradaDao.atualizar(
            atualizacao.copy(
                status = if (atualizacao.faltamDadosEssenciais) StatusParada.PENDENTE_DADOS else StatusParada.PENDENTE_ROTA,
                atualizadaEm = System.currentTimeMillis()
            )
        )
    }

    suspend fun aplicarCep(id: Long, cep: String): Boolean {
        val resposta = geocodingRepository.consultarCep(cep) ?: return false
        val parada = paradaDao.buscarPorId(id) ?: return false
        val atualizada = parada.copy(
            cep = resposta.cep ?: cep,
            logradouro = resposta.logradouro?.takeIf { it.isNotBlank() } ?: parada.logradouro,
            bairro = resposta.bairro?.takeIf { it.isNotBlank() } ?: parada.bairro,
            cidade = resposta.localidade ?: parada.cidade,
            uf = resposta.uf ?: parada.uf,
            atualizadaEm = System.currentTimeMillis()
        )
        confirmarDadosDaParada(id, atualizada)
        return true
    }

    /** Geocodifica (obtém lat/lng) todas as paradas com endereço completo mas sem coordenada ainda. */
    suspend fun geocodificarPendentes() {
        val pendentes = paradaDao.listarPorStatus(StatusParada.PENDENTE_ROTA)
        for (parada in pendentes) {
            val coordenada = geocodingRepository.geocodificar(parada.enderecoCompleto)
            if (coordenada != null) {
                paradaDao.atualizar(
                    parada.copy(
                        latitude = coordenada.latitude,
                        longitude = coordenada.longitude,
                        status = StatusParada.A_CAMINHO,
                        atualizadaEm = System.currentTimeMillis()
                    )
                )
            }
            // Se a geocodificação falhar, a parada permanece PENDENTE_ROTA para nova tentativa
            // (ex.: quando a conexão voltar) sem bloquear as demais.
        }
    }

    suspend fun salvarOrdemDaRota(idsNaOrdem: List<Long>) {
        idsNaOrdem.forEachIndexed { indice, id ->
            val parada = paradaDao.buscarPorId(id) ?: return@forEachIndexed
            paradaDao.atualizar(parada.copy(ordemNaRota = indice))
        }
    }

    /** Confirma sucesso ou falha da entrega e grava o registro histórico usado no painel. */
    suspend fun confirmarEntrega(id: Long, sucesso: Boolean) {
        val parada = paradaDao.buscarPorId(id) ?: return
        val novoStatus = if (sucesso) StatusParada.ENTREGUE else StatusParada.FALHOU
        paradaDao.atualizar(
            parada.copy(status = novoStatus, ordemNaRota = null, atualizadaEm = System.currentTimeMillis())
        )

        val valorPorPacote = settingsRepository.valorPorPacote.first()
        entregaDao.registrar(
            EntregaRegistroEntity(
                paradaId = parada.id,
                enderecoResumo = parada.enderecoCompleto,
                quantidadePacotes = parada.quantidadePacotes,
                valorPorPacoteNoMomento = valorPorPacote,
                valorTotal = valorPorPacote * parada.quantidadePacotes,
                sucesso = sucesso
            )
        )
    }

    /** Reinsere uma parada que falhou de volta na rota ativa (nova tentativa). */
    suspend fun reenviarParaRota(id: Long) {
        val parada = paradaDao.buscarPorId(id) ?: return
        paradaDao.atualizar(parada.copy(status = StatusParada.A_CAMINHO, ordemNaRota = null))
    }

    suspend fun excluir(id: Long) = paradaDao.excluir(id)

    suspend fun limparConcluidas() = paradaDao.limparConcluidas()
}
