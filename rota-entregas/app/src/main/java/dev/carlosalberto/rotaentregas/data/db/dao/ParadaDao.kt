package dev.carlosalberto.rotaentregas.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import dev.carlosalberto.rotaentregas.data.db.entity.ParadaEntity
import dev.carlosalberto.rotaentregas.data.model.StatusParada
import kotlinx.coroutines.flow.Flow

@Dao
interface ParadaDao {

    @Insert
    suspend fun inserirTodas(paradas: List<ParadaEntity>): List<Long>

    @Insert
    suspend fun inserir(parada: ParadaEntity): Long

    @Update
    suspend fun atualizar(parada: ParadaEntity)

    @Update
    suspend fun atualizarTodas(paradas: List<ParadaEntity>)

    @Query("SELECT * FROM paradas ORDER BY criadaEm DESC")
    fun observarTodas(): Flow<List<ParadaEntity>>

    @Query("SELECT * FROM paradas WHERE status != :entregue AND status != :falhou ORDER BY ordemNaRota ASC")
    fun observarAtivas(
        entregue: StatusParada = StatusParada.ENTREGUE,
        falhou: StatusParada = StatusParada.FALHOU
    ): Flow<List<ParadaEntity>>

    @Query("SELECT * FROM paradas WHERE status = :status")
    suspend fun listarPorStatus(status: StatusParada): List<ParadaEntity>

    @Query("SELECT * FROM paradas WHERE id = :id")
    suspend fun buscarPorId(id: Long): ParadaEntity?

    @Query("DELETE FROM paradas WHERE id = :id")
    suspend fun excluir(id: Long)

    @Query("DELETE FROM paradas WHERE status = :entregue OR status = :falhou")
    suspend fun limparConcluidas(
        entregue: StatusParada = StatusParada.ENTREGUE,
        falhou: StatusParada = StatusParada.FALHOU
    )

    @Query("UPDATE paradas SET ordemNaRota = NULL WHERE status = :aCaminho")
    suspend fun limparOrdemDaRota(aCaminho: StatusParada = StatusParada.A_CAMINHO)
}
