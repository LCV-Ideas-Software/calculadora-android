/*
 * Copyright © 2026 LCV Ideas & Software
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package dev.lcv.calculadora.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.maxLength
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldLabelPosition
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import java.util.Locale

/**
 * Os átomos visuais do produto web, traduzidos para componentes nativos: o
 * `field-box` que embrulha cada entrada, o `glass-card` dos resultados, as
 * pílulas de indicador e a linha rótulo/valor. Ficam num arquivo só porque são
 * o vocabulário compartilhado das telas.
 */

/** `field-box`: cartão com rótulo pequeno acima, dica opcional e o campo dentro. */
@Composable
fun CampoEmCaixa(
    rotulo: String,
    modifier: Modifier = Modifier,
    dica: String? = null,
    mostrarRotulo: Boolean = true,
    conteudo: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Tema.formas.cartao),
        color = Tema.cores.superficieCartao,
        border = BorderStroke(1.dp, Tema.cores.separador),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (mostrarRotulo) Text(
                text = rotulo,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = Tema.cores.textoFraco,
            )
            if (dica != null) {
                Text(text = dica, fontSize = 10.sp, color = Tema.cores.textoApagado)
            }
            conteudo()
        }
    }
}

/**
 * `glass-input`: campo claro, cantos de 12 dp, foco na cor de destaque da marca.
 *
 * O campo numérico de caixa (CALANDR-27): o [estado] guarda só os dígitos, que
 * [DigitosDeCaixa] aceita pela direita, e [FormatoDeCaixa] os mostra no padrão
 * brasileiro com as casas do [tipo]. Edição, seleção, teclado, área de
 * transferência, acessibilidade, desfazer e salvamento são da plataforma.
 *
 * Com [exemploParado], o rótulo fica sempre recolhido acima, para o [exemplo]
 * aparecer também com o campo parado e vazio: é o "Padrão: x%" dos parâmetros
 * (decisão 7 do operador). Nos campos de valor, o exemplo ("1.000,00") só
 * aparece com o campo em foco, para não ser lido como um valor digitado
 * (decisão do operador, 03/10/2026).
 */
@Composable
fun CampoNumerico(
    estado: TextFieldState,
    rotulo: String,
    tipo: TipoNumerico,
    modifier: Modifier = Modifier,
    exemplo: String? = null,
    exemploParado: Boolean = false,
) {
    // O limite vem depois da regra, para uma colagem formatada ser reduzida a dígitos antes de ser contada.
    val entrada = remember(tipo) { DigitosDeCaixa.maxLength(tipo.maximoDeDigitos) }
    val saida = remember(tipo) { FormatoDeCaixa(tipo.casas) }
    OutlinedTextField(
        state = estado,
        label = { Text(rotulo) },
        labelPosition = TextFieldLabelPosition.Attached(alwaysMinimize = exemploParado),
        inputTransformation = entrada,
        outputTransformation = saida,
        modifier = modifier.fillMaxWidth(),
        lineLimits = TextFieldLineLimits.SingleLine,
        placeholder = exemplo?.let { { Text(it, color = Tema.cores.textoApagado) } },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        shape = RoundedCornerShape(Tema.formas.campo),
        textStyle = MaterialTheme.typography.bodyMedium,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = Tema.cores.superficieCampo,
            unfocusedContainerColor = Tema.cores.superficieCampo,
            focusedBorderColor = Tema.cores.foco,
            unfocusedBorderColor = Tema.cores.separador,
            focusedTextColor = Tema.cores.textoNorm,
            unfocusedTextColor = Tema.cores.textoNorm,
        ),
    )
}

/** `glass-card`: superfície dos resultados, com tinta opcional por variante. */
@Composable
fun CartaoVidro(
    modifier: Modifier = Modifier,
    tinta: Color = Color.Transparent,
    destacado: Boolean = false,
    conteudo: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Tema.formas.cartao),
        color = Tema.cores.superficieCartao,
        border = BorderStroke(
            width = if (destacado) 2.dp else 1.dp,
            color = if (destacado) Tema.cores.vencedor else Tema.cores.separador,
        ),
        shadowElevation = if (destacado) 6.dp else 2.dp,
    ) {
        Column(
            modifier = Modifier
                .background(tinta)
                .padding(Tema.espacos.interno),
            verticalArrangement = Arrangement.spacedBy(Tema.espacos.entreLinhas),
            content = conteudo,
        )
    }
}

/**
 * Indicador curto em pílula, o `rounded-full` do web, com o espaçamento normal entre letras do web, e não os 0,5 sp
 * que o `bodyLarge` do Material 3 daria. O padrão é o das pílulas pequenas, `text-[10px] font-bold px-2 py-0.5`, com a
 * linha de 1,5 vez a fonte: o `text-[10px]` não muda a altura da linha, e vale o `line-height: 1.5` da página, como na
 * pílula "provável". Sem ela, a pílula herdava os 24 sp do `bodyLarge` (CALANDR-32). Passam os próprios valores o selo
 * (peso e recuo), as pílulas de plantão e contingência (a linha do `text-sm` do contêiner) e a pílula do backtest
 * (tamanho, linha e recuo).
 */
@Composable
fun Pilula(
    texto: String,
    fundo: Color,
    cor: Color,
    modifier: Modifier = Modifier,
    tamanho: TextUnit = 10.sp,
    alturaDaLinha: TextUnit = 1.5.em,
    peso: FontWeight = FontWeight.Bold,
    recuo: PaddingValues = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
) {
    Text(
        text = texto,
        fontSize = tamanho,
        lineHeight = alturaDaLinha,
        letterSpacing = 0.sp,
        fontWeight = peso,
        color = cor,
        modifier = modifier
            .background(fundo, RoundedCornerShape(percent = 50))
            .padding(recuo),
    )
}

/**
 * Linha rótulo à esquerda, valor à direita — a `Row` dos painéis do web. Quando os dois não cabem lado a lado (fonte
 * grande, tela estreita), o rótulo fica com a largura dele e o valor quebra entre palavras no espaço ao lado, ainda à
 * direita; se nem a maior palavra do valor cabe ali, o valor desce para a linha de baixo. O `FlowRow` decide pela
 * largura mínima do item com `weight`, que num texto é a da maior palavra. Antes, o rótulo espremia o valor até
 * parti-lo no meio do número (CALANDR-32). No web os dois lados encolhem e quebram entre palavras, o que o `FlexBox`
 * oficial, ainda experimental, reproduziria: desvio declarado na especificação, por decisões do operador em 03 e
 * 04/10/2026.
 */
@Composable
fun Linha(
    rotulo: String,
    valor: String,
    apagado: Boolean = false,
    forte: Boolean = false,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        itemVerticalAlignment = Alignment.Bottom,
    ) {
        Text(
            text = rotulo,
            fontSize = if (forte) 14.sp else 13.sp,
            fontWeight = if (forte) FontWeight.Bold else FontWeight.Normal,
            color = Tema.cores.textoFraco,
        )
        Text(
            text = valor,
            modifier = Modifier.weight(1f),
            fontSize = if (forte) 20.sp else 13.sp,
            fontWeight = when {
                forte -> FontWeight.ExtraBold
                apagado -> FontWeight.Normal
                else -> FontWeight.SemiBold
            },
            color = if (apagado) Tema.cores.textoApagado else Tema.cores.textoNorm,
            textAlign = TextAlign.End,
        )
    }
}

/**
 * Título curto e maiúsculo dos painéis de informação do web. O web põe o `h4` em maiúsculas só
 * na tela (`uppercase` do CSS), e o leitor de tela recebe o texto original; aqui também: o texto
 * aparece em maiúsculas e o TalkBack lê a forma original (decisão do operador de 08/10/2026,
 * CALANDR-44).
 */
@Composable
fun TituloPainel(texto: String, cor: Color) {
    Text(
        text = texto.uppercase(Locale.forLanguageTag("pt-BR")),
        modifier = Modifier.semantics { contentDescription = texto },
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.8.sp,
        color = cor,
    )
}
