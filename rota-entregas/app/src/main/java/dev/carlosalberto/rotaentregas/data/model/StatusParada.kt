package dev.carlosalberto.rotaentregas.data.model

/**
 * Ciclo de vida de uma parada de entrega dentro de uma rota.
 *
 * PENDENTE_DADOS: o OCR não conseguiu extrair informação suficiente (ex.: falta número
 * da residência ou CEP) — a parada não entra no cálculo de rota até ser confirmada.
 * PENDENTE_ROTA: dados completos, aguardando geocodificação/inclusão na rota ativa.
 * A_CAMINHO: faz parte da rota ativa calculada, ainda não visitada.
 * ENTREGUE: confirmada com sucesso.
 * FALHOU: tentativa sem sucesso — fica marcada em amarelo no mapa e é removida da
 * sequência de rota (desconectada), podendo ser reinserida manualmente.
 */
enum class StatusParada {
    PENDENTE_DADOS,
    PENDENTE_ROTA,
    A_CAMINHO,
    ENTREGUE,
    FALHOU
}
