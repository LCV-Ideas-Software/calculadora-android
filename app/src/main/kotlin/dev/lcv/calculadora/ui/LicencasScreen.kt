/*
 * Copyright © 2026 LCV Ideas & Software
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package dev.lcv.calculadora.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.lcv.calculadora.R

/**
 * A AGPL exige que a licença acompanhe o programa. Os três textos não são
 * cópias versionadas aqui: são os próprios arquivos da raiz do repositório,
 * levados aos assets pelo build, de modo que não existe versão divergente.
 *
 * Esses arquivos foram escritos para um editor de 80 colunas, e num telefone
 * cada linha deles quebra de novo — o que produz uma coluna em ziguezague,
 * ilegível. Por isso o texto passa por [emBlocos] antes de ser desenhado: as
 * palavras são as do arquivo, apenas o lugar das quebras é do aparelho.
 */
@Composable
fun LicencasScreen(modifier: Modifier = Modifier) {
    val contexto = LocalContext.current
    val secoes = remember {
        listOf(
            R.string.licencas_aviso to emBlocos(lerAsset(contexto, "NOTICE")),
            R.string.licencas_terceiros to emBlocos(lerAsset(contexto, "THIRDPARTY.md")),
            R.string.licencas_aplicativo to emBlocos(lerAsset(contexto, "LICENSE")),
        )
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Tema.espacos.entreSecoes),
    ) {
        secoes.forEach { (titulo, blocos) ->
            Column(verticalArrangement = Arrangement.spacedBy(Tema.espacos.entreLinhas)) {
                Text(
                    text = stringResource(titulo),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = Tema.cores.textoNorm,
                )
                HorizontalDivider(color = Tema.cores.separador)
                blocos.forEach { Desenhar(it) }
            }
        }
    }
}

@Composable
private fun Desenhar(bloco: Bloco) {
    when (bloco) {
        is Bloco.Titulo -> Text(
            text = bloco.texto,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = Tema.cores.textoNorm,
            modifier = Modifier.fillMaxWidth(),
        )

        // Justificado e com recuo de primeira linha, como texto corrido impresso.
        // A hifenização automática entra junto: justificar sem ela abre rios de
        // espaço numa coluna estreita de telefone.
        is Bloco.Paragrafo -> Text(
            text = bloco.texto,
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 13.sp,
                lineHeight = 19.sp,
                color = Tema.cores.textoFraco,
                textAlign = TextAlign.Justify,
                textIndent = TextIndent(firstLine = 18.sp),
                hyphens = Hyphens.Auto,
                lineBreak = LineBreak.Paragraph,
            ),
            modifier = Modifier.fillMaxWidth(),
        )

        // Uma tabela de cinco colunas não cabe num telefone: cada linha vira um
        // bloco com o nome do componente em destaque e os demais campos abaixo,
        // rotulados pelo cabeçalho da própria tabela. Os campos ficam empilhados,
        // e não em duas colunas, porque valores como um SHA completo espremem o
        // rótulo até quebrar.
        is Bloco.Registro -> CartaoVidro {
            Text(
                text = bloco.titulo,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Tema.cores.textoNorm,
            )
            bloco.campos.forEach { (rotulo, valor) ->
                Text(rotulo, fontSize = 10.sp, lineHeight = 13.sp, color = Tema.cores.textoApagado)
                Text(valor, fontSize = 12.sp, lineHeight = 16.sp, color = Tema.cores.textoNorm)
            }
        }
    }
}

private sealed interface Bloco {
    data class Titulo(val texto: String) : Bloco
    data class Paragrafo(val texto: String) : Bloco
    data class Registro(val titulo: String, val campos: List<Pair<String, String>>) : Bloco
}

/**
 * Converte o arquivo em blocos prontos para a tela. Só reconhece o que os três
 * arquivos realmente usam: título de markdown, tabela de markdown e parágrafo
 * separado por linha em branco. Dentro do parágrafo, a quebra de linha do
 * arquivo é artefato do editor e vira espaço — é isso que devolve o texto ao
 * fluxo natural da largura do telefone.
 */
private fun emBlocos(texto: String): List<Bloco> {
    val blocos = mutableListOf<Bloco>()
    val paragrafo = StringBuilder()
    var cabecalho: List<String>? = null

    fun fecharParagrafo() {
        if (paragrafo.isNotEmpty()) {
            blocos += Bloco.Paragrafo(paragrafo.toString())
            paragrafo.setLength(0)
        }
    }

    texto.lineSequence().forEach { linha ->
        val corpo = linha.trim()
        when {
            corpo.isEmpty() -> {
                fecharParagrafo()
                cabecalho = null
            }

            corpo.startsWith("#") -> {
                fecharParagrafo()
                blocos += Bloco.Titulo(semMarcacao(corpo.trimStart('#').trim()))
            }

            corpo.startsWith("|") -> {
                fecharParagrafo()
                val celulas = corpo.trim('|').split('|').map { semMarcacao(it.trim()) }
                val separadora = celulas.all { it.isNotEmpty() && it.all { c -> c == '-' || c == ':' } }
                when {
                    separadora -> Unit
                    cabecalho == null -> cabecalho = celulas
                    else -> blocos += registro(cabecalho.orEmpty(), celulas)
                }
            }

            else -> {
                if (paragrafo.isNotEmpty()) paragrafo.append(' ')
                paragrafo.append(semMarcacao(corpo))
            }
        }
    }
    fecharParagrafo()
    return blocos
}

/** Primeira célula é o nome; as demais viram rótulo/valor pelo cabeçalho. */
private fun registro(cabecalho: List<String>, celulas: List<String>): Bloco.Registro =
    Bloco.Registro(
        titulo = celulas.firstOrNull().orEmpty(),
        campos = celulas.drop(1)
            .mapIndexed { i, valor -> cabecalho.getOrElse(i + 1) { "" } to valor }
            .filter { (rotulo, valor) -> rotulo.isNotEmpty() && valor.isNotEmpty() },
    )

/** Crase, negrito e link de markdown não são texto: são instrução de formato. */
private fun semMarcacao(texto: String): String = texto
    .replace(LINK, "$1")
    .replace("**", "")
    .replace("`", "")

private val LINK = Regex("""\[([^]]*)]\([^)]*\)""")

private fun lerAsset(contexto: android.content.Context, nome: String): String =
    contexto.assets.open(nome).bufferedReader().use { it.readText() }
