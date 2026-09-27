package dev.carlosalberto.rotaentregas.data.model

/** Resultado da leitura de uma foto: endereço extraído por OCR + heurística, antes da persistência. */
data class EnderecoReconhecido(
    val textoOcrBruto: String,
    val logradouro: String = "",
    val numero: String = "",
    val complemento: String = "",
    val bairro: String = "",
    val cidade: String = "",
    val uf: String = "",
    val cep: String = "",
    val quantidadePacotes: Int = 1
) {
    val faltamDadosEssenciais: Boolean
        get() = logradouro.isBlank() || numero.isBlank() || (cep.isBlank() && (bairro.isBlank() || cidade.isBlank()))
}
