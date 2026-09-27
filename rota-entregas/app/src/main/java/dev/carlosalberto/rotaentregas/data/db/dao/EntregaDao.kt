package dev.carlosalberto.rotaentregas.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import dev.carlosalberto.rotaentregas.data.db.entity.EntregaRegistroEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EntregaDao {

    @Insert
    suspend fun registrar(registro: EntregaRegistroEntity): Long

    @Query("SELECT * FROM entregas_registro WHERE dataHoraEpochMillis >= :desdeEpochMillis ORDER BY dataHoraEpochMillis DESC")
    fun observarDesde(desdeEpochMillis: Long): Flow<List<EntregaRegistroEntity>>

    @Query("SELECT * FROM entregas_registro ORDER BY dataHoraEpochMillis DESC")
    fun observarTodas(): Flow<List<EntregaRegistroEntity>>
}
