/*
 * Copyright © 2026 LCV Ideas & Software
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package dev.lcv.calculadora.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.foundation.text.input.insert
import androidx.compose.foundation.text.input.placeCursorAtEnd
import java.math.BigDecimal
import java.math.BigInteger
import java.text.DecimalFormatSymbols
import java.util.Locale

/*
 * Os campos numéricos de caixa (decisão do operador, 02/10/2026, CALANDR-27): o valor de cada campo é o texto cru em
 * dígitos de um `TextFieldState`, e só os dígitos contam, entrando pela direita, como no campo Valor do produto web
 * (`applyCurrencyMask`). Vazio é "usar o padrão" (ou "não informado", nos opcionais); "0" é zero explícito. A
 * plataforma cuida da edição, da seleção, do teclado, da área de transferência, da acessibilidade, do desfazer e do
 * salvamento; aqui ficam só as quatro peças próprias da especificação v2.7: a regra de entrada, o formatador, a
 * conversão para o motor e, no ViewModel, a comparação de validade do resultado.
 */

/** Casas decimais e limite de dígitos de cada tipo de campo numérico. */
enum class TipoNumerico(val casas: Int, val maximoDeDigitos: Int) {
    /** Reais e moeda estrangeira: 2 casas, até 999.999.999.999,99. */
    DINHEIRO(2, 14),

    /** VET (R$ por unidade da moeda): 4 casas, até 9.999,9999. */
    TAXA(4, 8),

    /** Percentuais dos parâmetros: 2 casas, até 999,99 (o motor recusa acima de 100 %, com a mensagem dele). */
    PERCENTUAL(2, 5),
}

/**
 * A regra de entrada (especificação v2.7, seção 4.3). Vem antes do `maxLength` nativo, que conta os dígitos depois
 * de uma colagem formatada ser reduzida a eles e recusa por inteiro o que passar do limite.
 *
 * Toda seleção que a regra deixa passar é o cursor no fim ou o texto inteiro. Por isso uma edição só acontece no
 * fim ou sobre tudo, e tomar os dígitos da proposta em ordem é "entrar pela direita". A restauração e o desfazer não
 * passam por aqui, mas devolvem estados que passaram.
 *
 * O opt-in `ExperimentalFoundationApi` é a decisão 4 do operador: `TextFieldBuffer.changes` é o jeito nativo de
 * separar uma mudança só de seleção de uma edição e de achar o texto inserido.
 */
@OptIn(ExperimentalFoundationApi::class)
object DigitosDeCaixa : InputTransformation {
    override fun TextFieldBuffer.transformInput() {
        if (changes.changeCount == 0) {
            // Só a seleção mudou (4.3.1). O texto inteiro selecionado fica, para "selecionar tudo" e uma tecla
            // trocarem o valor; um cursor fora do fim ou uma seleção parcial vira cursor no fim (decisão 3).
            // Reverter aqui congelaria o cursor.
            val tudo = selection.min == 0 && selection.max == length && length > 0
            if (!tudo) placeCursorAtEnd()
            return
        }
        val proposta = asCharSequence()
        // Uma inserção sem dígito algum é recusada inteira, e a seleção fica (4.3.3, decisão 2): uma letra sobre o
        // número selecionado não o apaga.
        var inseriu = false
        var inseriuDigito = false
        for (indice in 0 until changes.changeCount) {
            val faixa = changes.getRange(indice)
            for (posicao in faixa.min until faixa.max) {
                inseriu = true
                if (proposta[posicao].ehDigito()) inseriuDigito = true
            }
        }
        if (inseriu && !inseriuDigito) {
            revertAllChanges()
            return
        }
        // Só dígitos ASCII contam (4.3.2), sem zeros à esquerda: vazio fica vazio, e zeros viram um "0" (4.3.4).
        val digitos = buildString { proposta.forEach { if (it.ehDigito()) append(it) } }
        val normal = if (digitos.isEmpty()) "" else digitos.trimStart('0').ifEmpty { "0" }
        // Uma tecla que não muda o número não muda nada, nem a seleção, e não grava desfazer (4.3.5).
        if (normal.contentEquals(originalText)) {
            revertAllChanges()
            return
        }
        replace(0, length, normal)
        placeCursorAtEnd()
    }

    private fun Char.ehDigito() = this in '0'..'9'
}

/**
 * O formatador (especificação v2.7, seção 4.4): mostra o texto cru com [casas] decimais no padrão brasileiro,
 * `123456` com duas casas como `1.234,56`. Só insere caracteres, e sempre antes do último dígito: assim o último
 * caractere mostrado é o último dígito, e o fim do texto cru corresponde sem ambiguidade ao fim do mostrado, onde o
 * cursor fica. Os separadores são os do `Formatacao` do motor (pt-BR), não os do aparelho.
 */
data class FormatoDeCaixa(val casas: Int) : OutputTransformation {
    init {
        require(casas >= 1) { "o formatador precisa de ao menos uma casa decimal" }
    }

    override fun TextFieldBuffer.transformOutput() {
        val tamanho = length
        // Vazio mostra vazio: o rótulo e o padrão continuam valendo.
        if (tamanho == 0) return
        if (tamanho <= casas) {
            insert(0, "0" + SEPARADOR_DECIMAL + "0".repeat(casas - tamanho))
            return
        }
        // Do maior índice para o menor, para cada inserção não deslocar as seguintes; nunca no índice 0.
        insert(tamanho - casas, SEPARADOR_DECIMAL)
        var indice = tamanho - casas - 3
        while (indice > 0) {
            insert(indice, SEPARADOR_DE_MILHAR)
            indice -= 3
        }
    }

    private companion object {
        val SIMBOLOS: DecimalFormatSymbols = DecimalFormatSymbols.getInstance(Locale.forLanguageTag("pt-BR"))
        val SEPARADOR_DECIMAL = SIMBOLOS.decimalSeparator.toString()
        val SEPARADOR_DE_MILHAR = SIMBOLOS.groupingSeparator.toString()
    }
}

/**
 * O número que o motor recebe de um campo (especificação v2.7, seção 4.6): vazio é `null`, o padrão; o cru entra
 * com as casas do campo, então "0" é zero explícito.
 */
internal fun bigDecimalDe(cru: String, casas: Int): BigDecimal? =
    if (cru.isEmpty()) null else BigDecimal(BigInteger(cru), casas)
