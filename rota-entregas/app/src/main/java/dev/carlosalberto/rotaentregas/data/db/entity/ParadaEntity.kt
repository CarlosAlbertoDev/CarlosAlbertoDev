package dev.carlosalberto.rotaentregas.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import dev.carlosalberto.rotaentregas.data.model.StatusParada

/**
 * Uma parada de entrega: um endereço reconhecido a partir de uma foto, com a
 * quantidade de pacotes a entregar ali. Pode representar mais de um pacote no
 * mesmo endereço (paradaCount = número de itens, não necessariamente 1 por parada).
 */
@Entity(tableName = "paradas")
data class ParadaEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,

    val loteImportacaoId: String,

    // Texto bruto reconhecido pelo OCR, mantido para auditoria/reprocessamento.
    val textoOcrBruto: String,

    val logradouro: String = "",
    val numero: String = "",
    val complemento: String = "",
    val bairro: String = "",
    val cidade: String = "",
    val uf: String = "",
    val cep: String = "",

    val quantidadePacotes: Int = 1,

    val latitude: Double? = null,
    val longitude: Double? = null,

    val status: StatusParada = StatusParada.PENDENTE_DADOS,

    // Posição na sequência da rota ativa (0 = próxima parada). Null quando não faz
    // parte da rota atual (dados incompletos ou entrega que falhou).
    val ordemNaRota: Int? = null,

    val observacao: String = "",

    val criadaEm: Long = System.currentTimeMillis(),
    val atualizadaEm: Long = System.currentTimeMillis()
) {
    val enderecoCompleto: String
        get() = buildString {
            append(logradouro)
            if (numero.isNotBlank()) append(", $numero")
            if (complemento.isNotBlank()) append(" - $complemento")
            if (bairro.isNotBlank()) append(" - $bairro")
            if (cidade.isNotBlank()) append(", $cidade")
            if (uf.isNotBlank()) append("/$uf")
            if (cep.isNotBlank()) append(" - CEP $cep")
        }

    /** Faltam dados essenciais para geocodificar e montar rota com confiança. */
    val faltamDadosEssenciais: Boolean
        get() = logradouro.isBlank() || numero.isBlank() || (cep.isBlank() && (bairro.isBlank() || cidade.isBlank()))
}
