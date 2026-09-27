package dev.carlosalberto.rotaentregas.data.route

import dev.carlosalberto.rotaentregas.data.geocode.Coordenada

/** Uma instrução de manobra ("vire à direita na Rua X"), no formato de um app de navegação. */
data class PassoNavegacao(
    val instrucao: String,
    val distanciaMetros: Double,
    val localizacaoManobra: Coordenada
)

/** O trajeto até a PRÓXIMA parada apenas — nunca a rota inteira de uma vez. */
data class NavegacaoLeg(
    val geometria: List<Coordenada>,
    val passos: List<PassoNavegacao>,
    val distanciaTotalMetros: Double
)
