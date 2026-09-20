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
    val vencedorTexto: Color,
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
            textoFraco = Color(0xFF64748B),
            textoApagado = Color(0xFF94A3B8),
            foco = Color(0xFFF59E0B),
            destaqueCartao = Color(0x1A7DD3FC),
            destaqueGlobal = Color(0x1A34D399),
            destaqueSaldo = Color(0x1AF59E0B),
            vencedor = Color(0xFFF59E0B),
            vencedorTexto = Color(0xFF101827),
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
