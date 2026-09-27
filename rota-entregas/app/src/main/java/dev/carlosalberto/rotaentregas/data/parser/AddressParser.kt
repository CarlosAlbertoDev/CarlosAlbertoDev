package dev.carlosalberto.rotaentregas.data.parser

import dev.carlosalberto.rotaentregas.data.model.EnderecoReconhecido
import java.util.Locale

/**
 * Extrai campos estruturados de endereço a partir do texto bruto devolvido pelo OCR.
 *
 * O texto de origem é ruidoso (quebras de linha arbitrárias, maiúsculas/minúsculas
 * inconsistentes, abreviações), então a estratégia é: primeiro extrair e "remover" do
 * texto os campos que têm um padrão bem definido (CEP, quantidade de pacotes, UF),
 * sobrando um texto mais limpo para localizar logradouro/número/bairro/cidade.
 */
object AddressParser {

    private val REGEX_CEP = Regex("""(\d{5})-?(\d{3})""")

    private val REGEX_QUANTIDADE = Regex(
        """(\d+)\s*(pacotes?|volumes?|itens?|encomendas?|caixas?)|(pacotes?|volumes?|itens?|encomendas?|caixas?)\s*[:\-]?\s*(\d+)|[x×]\s*(\d+)\b""",
        RegexOption.IGNORE_CASE
    )

    private val PREFIXOS_LOGRADOURO = listOf(
        "rua", "r.", "avenida", "av.", "av", "travessa", "tv.", "alameda", "al.",
        "rodovia", "rod.", "estrada", "praça", "praca", "largo", "via", "quadra", "qd",
        "condominio", "condomínio"
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
    private val REGEX_CIDADE_UF = Regex("""([A-Za-zÀ-ÿ .]{2,})\s*[-/]\s*([A-Za-z]{2})\b""")

    fun parse(textoOcr: String): EnderecoReconhecido {
        val linhas = textoOcr.lines().map { it.trim() }.filter { it.isNotBlank() }
        val textoCompleto = linhas.joinToString(" \n ")

        val cep = REGEX_CEP.find(textoCompleto)?.let { "${it.groupValues[1]}-${it.groupValues[2]}" } ?: ""

        val quantidade = extrairQuantidade(textoCompleto)

        val complemento = REGEX_COMPLEMENTO.find(textoCompleto)?.value?.trim() ?: ""

        val bairroPorLabel = linhas.firstNotNullOfOrNull { linha ->
            REGEX_BAIRRO_LABEL.find(linha)?.groupValues?.get(1)?.trim()
        } ?: ""

        var cidade = ""
        var uf = ""
        for (linha in linhas) {
            val match = REGEX_CIDADE_UF.find(linha) ?: continue
            val ufCandidata = match.groupValues[2].uppercase(Locale.ROOT)
            if (ufCandidata in UFS_VALIDAS) {
                cidade = match.groupValues[1].trim().trimEnd(',')
                uf = ufCandidata
                break
            }
        }

        val (logradouro, numero) = extrairLogradouroENumero(linhas)

        val bairro = bairroPorLabel.ifBlank {
            // Heurística: quando não há rótulo explícito "Bairro:", tentamos a linha
            // que sobra entre a linha do logradouro e a linha de cidade/UF/CEP.
            inferirBairro(linhas, logradouro, cidade)
        }

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

    private fun extrairQuantidade(texto: String): Int {
        val match = REGEX_QUANTIDADE.find(texto) ?: return 1
        val numeroTexto = match.groupValues.drop(1).firstOrNull { it.isNotBlank() && it.all(Char::isDigit) }
        return numeroTexto?.toIntOrNull()?.coerceIn(1, 999) ?: 1
    }

    private fun extrairLogradouroENumero(linhas: List<String>): Pair<String, String> {
        val linhaLogradouro = linhas.firstOrNull { linha ->
            val inicio = linha.trim().lowercase(Locale.ROOT)
            PREFIXOS_LOGRADOURO.any { prefixo -> inicio.startsWith("$prefixo ") || inicio == prefixo }
        } ?: linhas.firstOrNull { it.any(Char::isLetter) } ?: ""

        if (linhaLogradouro.isBlank()) return "" to ""

        // Formatos comuns: "Rua Tal, 123", "Rua Tal 123", "Rua Tal Nº 123 - Bairro"
        val partesPorVirgula = linhaLogradouro.split(",")
        var logradouro = partesPorVirgula.first().trim()
        var numero = ""

        val matchLabel = REGEX_NUMERO_LABEL.find(linhaLogradouro)
        if (matchLabel != null) {
            numero = matchLabel.groupValues[1]
        } else if (partesPorVirgula.size > 1) {
            val restoAposVirgula = partesPorVirgula[1].trim()
            val matchNumero = Regex("""^(\d+[a-zA-Z]?)""").find(restoAposVirgula)
            if (matchNumero != null) numero = matchNumero.groupValues[1]
        } else {
            // Sem vírgula/rótulo: pega o primeiro número isolado que não seja o CEP.
            val matchNumero = Regex("""\b(\d{1,5}[a-zA-Z]?)\b""").find(logradouro)
            if (matchNumero != null && matchNumero.value.length < 5) {
                numero = matchNumero.groupValues[1]
                logradouro = logradouro.replace(matchNumero.value, "").trim().trimEnd(',', '-').trim()
            }
        }

        return logradouro to numero
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
