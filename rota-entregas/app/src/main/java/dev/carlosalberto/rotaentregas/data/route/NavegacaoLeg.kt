package dev.carlosalberto.rotaentregas.data.route

import dev.carlosalberto.rotaentregas.data.geocode.Coordenada

/** Uma instrução de manobra ("vire à direita na Rua X"), no formato de um app de navegação. */
data class PassoNavegacao(
    val instrucao: String,
    val distanciaMetros: Double,
    val duracaoSegundos: Double,
    val localizacaoManobra: Coordenada,
    /** Modificador bruto do OSRM ("left", "right", "straight"...), para desenhar a seta girada. */
    val modificador: String?
)

/**
 * Rota completa passando por TODAS as paradas pendentes em ordem — para desenhar o
 * traçado inteiro no mapa, como o modo "vários destinos" do Google Maps — junto com as
 * instruções de manobra separadas por perna (uma lista de passos por trecho entre duas
 * paradas consecutivas). [passosPorPerna][0] é sempre a perna sendo navegada agora, da
 * posição atual até a próxima parada.
 */
data class RotaMultiParada(
    val geometriaCompleta: List<Coordenada>,
    val passosPorPerna: List<List<PassoNavegacao>>
)
