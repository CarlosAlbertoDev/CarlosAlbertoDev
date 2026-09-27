package dev.carlosalberto.rotaentregas.data.parser

import dev.carlosalberto.rotaentregas.data.model.EnderecoReconhecido
import java.util.Locale

/**
 * Extrai campos estruturados de endereço a partir do texto bruto devolvido pelo OCR.
 *
 * Cobre dois formatos reais bem diferentes:
 * - Uma foto por endereço (etiqueta de pacote, papel manuscrito etc.) → [parse].
 * - Um print de tela com uma LISTA de paradas de um app de entregas (várias paradas
 *   por foto, cada bloco no padrão "Entrega" / "#código" / endereço / cidade /
 *   "Coletar N pacote(s)") → [parseMultiplos], que separa cada bloco em uma parada.
 *
 * O texto de origem é ruidoso (quebras de linha arbitrárias, maiúsculas/minúsculas
 * inconsistentes, abreviações, truncamento visual com "..." quando a tela corta o
 * texto), então a estratégia é sempre trabalhar linha a linha em vez de tentar um
 * único regex gigante sobre o texto inteiro.
 */
object AddressParser {

    private val REGEX_CEP = Regex("""(\d{5})-?(\d{3})""")

    private val REGEX_QUANTIDADE = Regex(
        """(\d+)\s*(pacotes?|volumes?|itens?|encomendas?|caixas?)|(pacotes?|volumes?|itens?|encomendas?|caixas?)\s*[:\-]?\s*(\d+)|[x×]\s*(\d+)\b""",
        RegexOption.IGNORE_CASE
    )

    /** Telas de itinerário usam "Coletar um pacote" / "Coletar 2 pacotes" (número por extenso ou dígito). */
    private val REGEX_COLETAR = Regex("""coletar\s+(\w+)\s+pacotes?""", RegexOption.IGNORE_CASE)

    private val NUMEROS_POR_EXTENSO = mapOf(
        "um" to 1, "uma" to 1, "dois" to 2, "duas" to 2, "tres" to 3, "três" to 3,
        "quatro" to 4, "cinco" to 5, "seis" to 6, "sete" to 7, "oito" to 8, "nove" to 9, "dez" to 10
    )

    private val PREFIXOS_LOGRADOURO = listOf(
        "rua", "r.", "avenida", "av.", "av", "travessa", "tv.", "alameda", "al.",
        "rodovia", "rod.", "estrada", "praça", "praca", "largo", "via", "quadra", "qd",
        "condominio", "condomínio"
    )

    // Detecta "Rua 4", "Avenida 9" etc. no início da linha: o número faz parte do NOME da
    // rua, não é o número da casa.
    private val REGEX_PREFIXO_SEGUIDO_DE_NUMERO = Regex(
        "^(" + PREFIXOS_LOGRADOURO.joinToString("|") { Regex.escape(it) } + """)\s+(\d{1,3})\b""",
        RegexOption.IGNORE_CASE
    )

    private val UFS_VALIDAS = setOf(
        "AC", "AL", "AP", "AM", "BA", "CE", "DF", "ES", "GO", "MA", "MT", "MS", "MG",
        "PA", "PB", "PR", "PE", "PI", "RJ", "RN", "RS", "RO", "RR", "SC", "SP", "SE", "TO"
    )

    private val REGEX_NUMERO_LABEL = Regex("""n[ºo°]?\s*[:.]?\s*(\d+[a-zA-Z]?)""", RegexOption.IGNORE_CASE)
    private val REGEX_COMPLEMENTO = Regex(
        """(apto?\.?\s*\d+\w*|bloco\s*\w+|casa\s*\d*\w*|fundos|sobrado|térreo|terreo|conjunto\s*\d+)""",
        RegexOption.IGNORE_CASE
    )
    private val REGEX_BAIRRO_LABEL = Regex("""bairro\s*[:\-]?\s*(.+)""", RegexOption.IGNORE_CASE)

    // "Cidade - UF" ou "Cidade/UF" (endereço digitado à mão / formulário).
    private val REGEX_CIDADE_UF_COM_SEPARADOR = Regex("""([A-Za-zÀ-ÿ .]{2,})\s*[-/]\s*([A-Za-z]{2})\b""")
    // "Cidade UF 12345678 Brazil" (etiqueta de remessa Amazon: sem hífen/traço nenhum).
    private val REGEX_CIDADE_UF_CEP = Regex("""([A-Za-zÀ-ÿ]{2,})\s+([A-Za-z]{2})\s+(\d{8})\b""")

    /** Uma foto = um endereço (etiqueta de pacote, anotação manuscrita, formulário). */
    fun parse(textoOcr: String): EnderecoReconhecido {
        val todasAsLinhas = textoOcr.lines().map { it.trim() }.filter { it.isNotBlank() }

        // Etiqueta de "Pickup" (coleta/devolução): o topo da etiqueta traz o endereço do
        // centro de distribuição, e o endereço do cliente (o que interessa aqui) vem
        // depois do marcador "Pickup ID". Sem esse recorte, a cidade/UF do centro de
        // distribuição seria capturada por engano em vez da do cliente.
        val indicePickup = todasAsLinhas.indexOfFirst { it.contains("pickup id", ignoreCase = true) }
        val linhas = if (indicePickup >= 0) todasAsLinhas.drop(indicePickup + 1) else todasAsLinhas
        val textoCompleto = linhas.joinToString(" \n ")

        val cep = REGEX_CEP.find(textoCompleto)?.let { "${it.groupValues[1]}-${it.groupValues[2]}" } ?: ""
        val quantidade = extrairQuantidade(textoCompleto)
        val complemento = REGEX_COMPLEMENTO.find(textoCompleto)?.value?.trim() ?: ""

        val bairroPorLabel = linhas.firstNotNullOfOrNull { linha ->
            REGEX_BAIRRO_LABEL.find(linha)?.groupValues?.get(1)?.trim()
        } ?: ""

        val (cidade, uf) = extrairCidadeEUf(linhas)
        val (logradouro, numero, bairroTrailing) = extrairLogradouroNumeroEBairro(linhas)

        val bairro = bairroPorLabel.ifBlank { bairroTrailing.ifBlank { inferirBairro(linhas, logradouro, cidade) } }

        return EnderecoReconhecido(
            textoOcrBruto = textoOcr,
            logradouro = logradouro,
            numero = numero,
            complemento = complemento,
            bairro = bairro,
            cidade = cidade,
            uf = uf,
            cep = cep,
            quantidadePacotes = quantidade
        )
    }

    // Linha de código de rota tipo "SSB9-CA01" (aparece tanto atrás de "#" na lista de
    // itinerário quanto solta na tela de escaneamento de pacotes).
    private val REGEX_CODIGO_ROTA = Regex("""\b[A-Z0-9]{3,6}-[A-Z0-9]{2,6}\b""")
    private val REGEX_CODIGO_BARRAS = Regex("""\bTBR\w{6,}\b""", RegexOption.IGNORE_CASE)
    private val REGEX_TIPO_EMBALAGEM = Regex(
        """^\(?\s*[pmg]\s*\)?\s*caixa\s*$|^sacola(\s+pl[aá]stica)?$|^envelope$|^pacote$""",
        RegexOption.IGNORE_CASE
    )

    /**
     * Uma foto = uma lista de paradas. Cobre dois layouts de app de entrega observados:
     * - lista de itinerário: cada parada começa em "Entrega" ou "Entregar HH:MM - HH:MM";
     * - escaneamento de pacotes: cada parada começa em uma linha de asteriscos (código
     *   de rastreio mascarado), seguida do endereço e depois de "ID do pacote"/"Código de
     *   barras"/tipo de embalagem, sem linha de cidade nem quantidade explícita (cada
     *   bloco = 1 pacote).
     * Se nenhum marcador for encontrado, a imagem não é uma lista — cai para [parse]
     * tratando o texto inteiro como um único endereço.
     */
    fun parseMultiplos(textoOcr: String): List<EnderecoReconhecido> {
        val linhas = textoOcr.lines().map { it.trim() }.filter { it.isNotBlank() }
            .filterNot { it.all(Char::isDigit) } // remove números soltos dos "badges" de posição da parada

        val indicesCabecalho = linhas.indices.filter { ehCabecalhoDeParada(linhas[it]) }
        if (indicesCabecalho.isEmpty()) {
            return listOf(parse(textoOcr))
        }

        return indicesCabecalho.mapIndexedNotNull { posicao, inicio ->
            val fim = indicesCabecalho.getOrElse(posicao + 1) { linhas.size }
            montarEnderecoDeBloco(linhas.subList(inicio + 1, fim))
        }
    }

    private fun ehCabecalhoDeParada(linha: String): Boolean =
        linha.equals("Entrega", ignoreCase = true) ||
            linha.startsWith("Entregar", ignoreCase = true) ||
            Regex("""^\*{4,}$""").matches(linha)

    private fun ehLinhaDeRuidoDeBloco(linha: String): Boolean {
        val l = linha.trim()
        return l.startsWith("#") ||
            l.contains("senha única", ignoreCase = true) ||
            l.contains("id do pacote", ignoreCase = true) ||
            l.contains("código de barras", ignoreCase = true) ||
            l.contains("codigo de barras", ignoreCase = true) ||
            REGEX_CODIGO_ROTA.containsMatchIn(l) ||
            REGEX_CODIGO_BARRAS.containsMatchIn(l) ||
            REGEX_TIPO_EMBALAGEM.matches(l)
    }

    private fun montarEnderecoDeBloco(bloco: List<String>): EnderecoReconhecido? {
        val linhasUteis = bloco.filterNot(::ehLinhaDeRuidoDeBloco)

        val linhaQuantidade = linhasUteis.firstOrNull { REGEX_COLETAR.containsMatchIn(it) }
        // Na tela de escaneamento de pacotes cada bloco já representa exatamente 1 pacote
        // (1 código de barras); só a lista de itinerário traz "Coletar N pacote(s)".
        val quantidade = linhaQuantidade?.let { extrairQuantidadeDeColeta(it) } ?: 1

        val linhasEndereco = linhasUteis.filterNot { REGEX_COLETAR.containsMatchIn(it) }
        val linhaEndereco = linhasEndereco.getOrNull(0) ?: return null
        val linhaCidade = linhasEndereco.getOrNull(1).orEmpty()

        val (logradouro, numero, bairro) = extrairNumeroEBairroTrailing(linhaEndereco)
        if (logradouro.isBlank()) return null

        return EnderecoReconhecido(
            textoOcrBruto = (listOfNotNull(linhaEndereco, linhaCidade.ifBlank { null }, linhaQuantidade)).joinToString("\n"),
            logradouro = logradouro,
            numero = numero,
            bairro = bairro,
            cidade = linhaCidade,
            quantidadePacotes = quantidade
        )
    }

    private fun extrairQuantidadeDeColeta(linha: String): Int {
        val palavra = REGEX_COLETAR.find(linha)?.groupValues?.get(1)?.lowercase(Locale.ROOT) ?: return 1
        palavra.toIntOrNull()?.let { return it.coerceIn(1, 999) }
        return NUMEROS_POR_EXTENSO[palavra] ?: 1
    }

    private fun extrairQuantidade(texto: String): Int {
        REGEX_COLETAR.find(texto)?.let { return extrairQuantidadeDeColeta(it.value) }
        val match = REGEX_QUANTIDADE.find(texto) ?: return 1
        val numeroTexto = match.groupValues.drop(1).firstOrNull { it.isNotBlank() && it.all(Char::isDigit) }
        return numeroTexto?.toIntOrNull()?.coerceIn(1, 999) ?: 1
    }

    private fun extrairCidadeEUf(linhas: List<String>): Pair<String, String> {
        for (linha in linhas) {
            REGEX_CIDADE_UF_CEP.find(linha)?.let { match ->
                val ufCandidata = match.groupValues[2].uppercase(Locale.ROOT)
                if (ufCandidata in UFS_VALIDAS) return match.groupValues[1].trim() to ufCandidata
            }
        }
        for (linha in linhas) {
            REGEX_CIDADE_UF_COM_SEPARADOR.find(linha)?.let { match ->
                val ufCandidata = match.groupValues[2].uppercase(Locale.ROOT)
                if (ufCandidata in UFS_VALIDAS) return match.groupValues[1].trim().trimEnd(',') to ufCandidata
            }
        }
        return "" to ""
    }

    private fun extrairLogradouroNumeroEBairro(linhas: List<String>): Triple<String, String, String> {
        val linhaLogradouro = linhas.firstOrNull { linha ->
            val inicio = linha.trim().lowercase(Locale.ROOT)
            PREFIXOS_LOGRADOURO.any { prefixo -> inicio.startsWith("$prefixo ") || inicio == prefixo }
        } ?: linhas.firstOrNull { it.any(Char::isLetter) } ?: ""

        if (linhaLogradouro.isBlank()) return Triple("", "", "")

        // Formatos: "Rua Tal, 123", "Rua Tal Nº 123", "Rua Tal 123 Bairro" (sem vírgula).
        val partesPorVirgula = linhaLogradouro.split(",")
        val matchLabel = REGEX_NUMERO_LABEL.find(linhaLogradouro)

        return when {
            matchLabel != null -> Triple(partesPorVirgula.first().trim(), matchLabel.groupValues[1], "")
            partesPorVirgula.size > 1 -> {
                val restoAposVirgula = partesPorVirgula[1].trim()
                val matchNumero = Regex("""^(\d+[a-zA-Z]?)""").find(restoAposVirgula)
                val numero = matchNumero?.groupValues?.get(1) ?: ""
                val bairro = matchNumero?.let { restoAposVirgula.removePrefix(it.value).trim().trimStart('-', ' ') } ?: ""
                Triple(partesPorVirgula.first().trim(), numero, bairro)
            }
            else -> extrairNumeroEBairroTrailing(linhaLogradouro)
        }
    }

    /**
     * Linha sem vírgula nem rótulo "Nº": localiza o primeiro número isolado (o número da
     * casa) e separa o texto em volta dele — o que vem antes é o nome da rua, o que sobra
     * depois costuma ser o bairro (comum em etiquetas de remessa e telas de itinerário,
     * ex.: "Rua Doutor Monte 1044 Centro").
     */
    private fun extrairNumeroEBairroTrailing(linha: String): Triple<String, String, String> {
        // Nomes de rua numéricos ("Rua 4", "Avenida 9", comuns em loteamentos) têm um
        // número logo após o prefixo que NÃO é o número da casa — sem esse desvio, "Rua 4
        // 125" seria lido como número "4" e "125" sobraria como se fosse bairro.
        val matchNomeDeRuaNumerico = REGEX_PREFIXO_SEGUIDO_DE_NUMERO.find(linha)
        val inicioBusca = matchNomeDeRuaNumerico?.range?.last?.plus(1) ?: 0

        val matchNumero = Regex("""\b(\d{1,5}[a-zA-Z]?)\b""").find(linha, inicioBusca)
        if (matchNumero == null || matchNumero.value.length >= 5) {
            return Triple(linha.trim(), "", "")
        }
        val logradouro = linha.substring(0, matchNumero.range.first).trim().trimEnd(',', '-').trim()
        val bairro = linha.substring(matchNumero.range.last + 1).trim().trimStart(',', '-').trim()
        return Triple(logradouro, matchNumero.groupValues[1], bairro)
    }

    /**
     * Consolida entradas que são o MESMO endereço (mesma rua/número/cidade) em uma única
     * parada, somando a quantidade de pacotes — é o caso da tela de escaneamento de
     * pacotes, onde cada código de barras (TBR...) é 1 pacote, mas vários pacotes podem
     * ser para a mesma parada. "Quantidade de pacotes" e "quantidade de paradas" são
     * coisas diferentes: aqui é onde a segunda deixa de contar de mais por causa da
     * primeira. Endereços sem rua identificada (faltando confirmação) nunca são mesclados
     * entre si, para não juntar por engano duas leituras ruins e diferentes.
     */
    fun mesclarPorEndereco(enderecos: List<EnderecoReconhecido>): List<EnderecoReconhecido> {
        val agrupados = LinkedHashMap<String, EnderecoReconhecido>()
        enderecos.forEachIndexed { indice, endereco ->
            val chave = chaveDeEndereco(endereco) ?: "sem-chave-$indice"
            val existente = agrupados[chave]
            agrupados[chave] = if (existente == null) {
                endereco
            } else {
                existente.copy(
                    quantidadePacotes = existente.quantidadePacotes + endereco.quantidadePacotes,
                    bairro = existente.bairro.ifBlank { endereco.bairro },
                    cidade = existente.cidade.ifBlank { endereco.cidade },
                    uf = existente.uf.ifBlank { endereco.uf },
                    cep = existente.cep.ifBlank { endereco.cep },
                    complemento = existente.complemento.ifBlank { endereco.complemento },
                    textoOcrBruto = existente.textoOcrBruto + "\n---\n" + endereco.textoOcrBruto
                )
            }
        }
        return agrupados.values.toList()
    }

    private fun chaveDeEndereco(endereco: EnderecoReconhecido): String? {
        if (endereco.logradouro.isBlank()) return null
        fun normalizar(s: String) = s.trim().lowercase(Locale.ROOT).replace(Regex("""\s+"""), " ")
        return "${normalizar(endereco.logradouro)}|${normalizar(endereco.numero)}|${normalizar(endereco.cidade)}"
    }

    private fun inferirBairro(linhas: List<String>, logradouro: String, cidade: String): String {
        if (logradouro.isBlank()) return ""
        val indiceLogradouro = linhas.indexOfFirst { it.contains(logradouro, ignoreCase = true) }
        if (indiceLogradouro == -1 || indiceLogradouro + 1 >= linhas.size) return ""

        for (i in (indiceLogradouro + 1) until linhas.size) {
            val candidata = linhas[i]
            val pareceCep = REGEX_CEP.containsMatchIn(candidata)
            val pareceCidade = cidade.isNotBlank() && candidata.contains(cidade, ignoreCase = true)
            val soNumeros = candidata.all { it.isDigit() || it.isWhitespace() || it == '-' }
            if (!pareceCep && !pareceCidade && !soNumeros && candidata.length in 3..40) {
                return candidata.trim().trimEnd(',')
            }
        }
        return ""
    }
}
