package dev.carlosalberto.rotaentregas.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Registro histórico e imutável de uma entrega concluída (ou falha), usado pelo
 * painel de acompanhamento. É gravado no momento da confirmação/falha e não é
 * afetado por edições posteriores da parada de origem, preservando o histórico
 * de faturamento mesmo que o valor por pacote mude depois.
 */
@Entity(tableName = "entregas_registro")
data class EntregaRegistroEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val paradaId: Long,
    val enderecoResumo: String,
    val quantidadePacotes: Int,
    val valorPorPacoteNoMomento: Double,
    val valorTotal: Double,
    val sucesso: Boolean,
    val dataHoraEpochMillis: Long = System.currentTimeMillis()
)
