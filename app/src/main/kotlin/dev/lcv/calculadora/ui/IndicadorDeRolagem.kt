/*
 * Copyright © 2026 LCV Ideas & Software
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package dev.lcv.calculadora.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp

/**
 * O Compose não desenha barra de rolagem: o `verticalScroll` move o conteúdo e
 * não dá pista nenhuma de onde a pessoa está. Medido na versão que este projeto
 * usa (Foundation 1.12.1, Material 3 1.4.0), a API oficial existe pela metade —
 * há o contrato de estado `ScrollIndicatorState`, com deslocamento e tamanhos do
 * conteúdo e da janela, mas nenhum `Modifier` ou componente que o desenhe. Até
 * que a outra metade chegue, o indicador é este: um traço fino na borda direita,
 * proporcional à fração visível, que aparece enquanto se rola e some quando
 * para — o comportamento das barras que o Android desenha nas telas de sistema.
 *
 * Fica na margem de 16 dp do conteúdo, então não empurra nem cobre nada. Quando
 * o equivalente oficial chegar ao Foundation estável, este arquivo sai.
 */
@Composable
fun Modifier.indicadorDeRolagem(estado: ScrollState): Modifier {
    val opacidade by animateFloatAsState(
        targetValue = if (estado.isScrollInProgress) 1f else 0f,
        label = "opacidade-do-indicador",
    )
    val cor = Tema.cores.textoApagado

    return drawWithContent {
        drawContent()
        // `maxValue` chega como `Int.MAX_VALUE` antes da primeira medição, e
        // zero quando tudo cabe na tela: nos dois casos não há o que indicar.
        if (opacidade <= 0f || estado.maxValue <= 0 || estado.maxValue == Int.MAX_VALUE) {
            return@drawWithContent
        }

        val conteudo = (estado.maxValue + estado.viewportSize).toFloat()
        val altura = (size.height * size.height / conteudo).coerceAtLeast(ALTURA_MINIMA.toPx())
        val topo = (size.height - altura) * (estado.value.toFloat() / estado.maxValue)

        drawRoundRect(
            color = cor,
            topLeft = Offset(size.width - LARGURA.toPx(), topo),
            size = Size(LARGURA.toPx(), altura),
            cornerRadius = CornerRadius(LARGURA.toPx() / 2),
            alpha = opacidade,
        )
    }
}

private val LARGURA = 3.dp
private val ALTURA_MINIMA = 32.dp
