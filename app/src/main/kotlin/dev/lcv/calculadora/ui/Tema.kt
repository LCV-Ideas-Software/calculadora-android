/*
 * Copyright © 2026 LCV Ideas & Software
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package dev.lcv.calculadora.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Sistema de design do porte.
 *
 * A forma segue o que a Proton faz nos aplicativos Android dela, que é a
 * referência de porte que o operador apontou: as cores são nomeadas pelo
 * **papel** que cumprem — `textoNorm`, `textoFraco`, `separador` —, não pelo
 * matiz, ficam num objeto próprio e são publicadas por `CompositionLocal`, ao
 * mesmo tempo em que alimentam o `MaterialTheme`, de modo que todo componente
 * nativo já nasce com a identidade certa.
 *
 * Os valores vêm de duas medições, sem cor inventada: os papéis de
 * `calculadora-app/src/App.css` e `src/components` e as cores de
 * `brand/lcv-ideas-software-mark.svg`.
 *
 * O produto web tem uma aparência só, clara. O port acompanha; um tema escuro
 * exigiria decidir tons que a marca ainda não definiu, e inventar paleta é o
 * oposto do que um porte faz. Fica declarado como trabalho futuro, dependente
 * de decisão do operador.
 */
data class CoresLcv(
    // Marca, como estão no ativo.
    val marcaMarinho: Color,
    val marcaArdosia: Color,
    val marcaCeu: Color,
    val marcaVerde: Color,
    val marcaAmbar: Color,
    // Papéis.
    val fundoNorm: Color,
    val fundoBaixo: Color,
    val superficie: Color,
    val superficieCartao: Color,
    val superficieCampo: Color,
    val separador: Color,
    val textoNorm: Color,
    val textoFraco: Color,
    val textoApagado: Color,
    val foco: Color,
    // Destaques por cartão: no web cada comparação tem um gradiente próprio; no
    // port esse papel cabe às três cores de destaque da marca.
    val destaqueCartao: Color,
    val destaqueGlobal: Color,
    val destaqueSaldo: Color,
    val vencedor: Color,
    // A pílula da melhor opção, abaixo dos cartões: `bg-green-50 text-green-800 border-green-200` do web. O Tailwind 4
    // define esses tons em OKLCH; aqui estão convertidos para sRGB, onde os três cabem sem ajuste.
    val melhorOpcaoFundo: Color,
    val melhorOpcaoTexto: Color,
    val melhorOpcaoBorda: Color,
    // O selo "⭐ MELHOR": `bg-amber-400 text-amber-900` do web (ComparisonCard.tsx). O âmbar 400 do Tailwind 4 fica fora
    // do sRGB; o tom aqui é o que sobra ao cortá-lo para o sRGB, o mesmo que o navegador mostra numa tela sRGB.
    val seloFundo: Color,
    val seloTexto: Color,
    // As pílulas pequenas do web: "provável" em `bg-orange-100 text-orange-700` (CompraReaisPanel.tsx), "🌙 Plantão" em
    // `bg-amber-100 text-amber-800` e "⚡ Contingência" em `bg-orange-100 text-orange-800` (ComparisonCard.tsx).
    val provavelFundo: Color,
    val provavelTexto: Color,
    val plantaoFundo: Color,
    val plantaoTexto: Color,
    val contingenciaFundo: Color,
    val contingenciaTexto: Color,
    // A qualidade do backtest (BacktestPanel.tsx): os fundos do web e, no texto, o tom 800 da mesma cor no lugar do 700
    // do web, com o qual "Excelente" e "Boa" ficam abaixo do contraste AA (desvio declarado na especificação, decisão
    // do operador de 04/10/2026).
    val qualidadeExcelenteFundo: Color,
    val qualidadeExcelenteTexto: Color,
    val qualidadeBoaFundo: Color,
    val qualidadeBoaTexto: Color,
    val qualidadeAtencaoFundo: Color,
    val qualidadeAtencaoTexto: Color,
    // O cartão de cenário do modo cobrado em reais (CompraReaisPanel.tsx): o fundo de todos, o do provável e o contorno
    // dele, o `text-slate-700` do título e do valor do acréscimo e o `text-slate-900` do total.
    val cenarioFundo: Color,
    val cenarioProvavelFundo: Color,
    val cenarioProvavelContorno: Color,
    val cenarioTexto: Color,
    val cenarioTotal: Color,
) {
    companion object {
        val Claro = CoresLcv(
            marcaMarinho = Color(0xFF101827),
            marcaArdosia = Color(0xFF334155),
            marcaCeu = Color(0xFF7DD3FC),
            marcaVerde = Color(0xFF34D399),
            marcaAmbar = Color(0xFFF59E0B),
            fundoNorm = Color(0xFFF8FAFC),
            fundoBaixo = Color(0xFFEEF2F7),
            superficie = Color(0xFFFFFFFF),
            superficieCartao = Color(0xCCFFFFFF),
            superficieCampo = Color(0xE6FFFFFF),
            separador = Color(0x5994A3B8),
            textoNorm = Color(0xFF101827),
            textoFraco = Color(0xFF475569),
            textoApagado = Color(0xFF475569),
            foco = Color(0xFFF59E0B),
            destaqueCartao = Color(0x1A7DD3FC),
            destaqueGlobal = Color(0x1A34D399),
            destaqueSaldo = Color(0x1AF59E0B),
            vencedor = Color(0xFFF59E0B),
            melhorOpcaoFundo = Color(0xFFF0FDF4),
            melhorOpcaoTexto = Color(0xFF016630),
            melhorOpcaoBorda = Color(0xFFB9F8CF),
            seloFundo = Color(0xFFFFB900),
            seloTexto = Color(0xFF7B3306),
            provavelFundo = Color(0xFFFFEDD4),
            provavelTexto = Color(0xFFCA3500),
            plantaoFundo = Color(0xFFFEF3C6),
            plantaoTexto = Color(0xFF973C00),
            contingenciaFundo = Color(0xFFFFEDD4),
            contingenciaTexto = Color(0xFF9F2D00),
            // `rgba(22,163,74,0.12)`, `rgba(234,179,8,0.12)` e `rgba(220,38,38,0.12)` no web. Os textos do web são os
            // tons 700 da escala hexadecimal do Tailwind 3 (#15803D, #A16207, #B91C1C); aqui, os 800 da mesma escala.
            qualidadeExcelenteFundo = Color(0xFF16A34A).copy(alpha = 0.12f),
            qualidadeExcelenteTexto = Color(0xFF166534),
            qualidadeBoaFundo = Color(0xFFEAB308).copy(alpha = 0.12f),
            qualidadeBoaTexto = Color(0xFF854D0E),
            qualidadeAtencaoFundo = Color(0xFFDC2626).copy(alpha = 0.12f),
            qualidadeAtencaoTexto = Color(0xFF991B1B),
            // `rgba(51,65,85,0.04)`; o provável, `rgba(234,88,12,0.08)` com `outline: 2px solid rgba(234,88,12,0.5)`.
            cenarioFundo = Color(0xFF334155).copy(alpha = 0.04f),
            cenarioProvavelFundo = Color(0xFFEA580C).copy(alpha = 0.08f),
            cenarioProvavelContorno = Color(0xFFEA580C).copy(alpha = 0.5f),
            cenarioTexto = Color(0xFF314158),
            cenarioTotal = Color(0xFF0F172B),
        )
    }
}

/** Raios do web: 16 dp nos cartões, 12 dp nos campos. */
data class FormasLcv(
    val cartao: Dp = 16.dp,
    val campo: Dp = 12.dp,
)

/** Espaçamentos recorrentes, para não haver número solto nas telas. */
data class EspacosLcv(
    val entreSecoes: Dp = 16.dp,
    val entreCampos: Dp = 12.dp,
    val entreLinhas: Dp = 6.dp,
    val interno: Dp = 16.dp,
)

val LocalCores = staticCompositionLocalOf { CoresLcv.Claro }
val LocalFormas = staticCompositionLocalOf { FormasLcv() }
val LocalEspacos = staticCompositionLocalOf { EspacosLcv() }

object Tema {
    val cores: CoresLcv
        @Composable @ReadOnlyComposable get() = LocalCores.current

    val formas: FormasLcv
        @Composable @ReadOnlyComposable get() = LocalFormas.current

    val espacos: EspacosLcv
        @Composable @ReadOnlyComposable get() = LocalEspacos.current
}

private fun CoresLcv.paletaMaterial() = lightColorScheme(
    primary = marcaMarinho,
    onPrimary = Color.White,
    secondary = marcaArdosia,
    onSecondary = Color.White,
    tertiary = marcaAmbar,
    onTertiary = marcaMarinho,
    background = fundoNorm,
    onBackground = textoNorm,
    surface = superficie,
    onSurface = textoNorm,
    onSurfaceVariant = textoFraco,
    outline = separador,
)

@Composable
fun CalculadoraTheme(
    cores: CoresLcv = CoresLcv.Claro,
    conteudo: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalCores provides cores,
        LocalFormas provides FormasLcv(),
        LocalEspacos provides EspacosLcv(),
    ) {
        MaterialTheme(colorScheme = cores.paletaMaterial(), content = conteudo)
    }
}
