/*
 * Copyright © 2026 LCV Ideas & Software
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package dev.lcv.calculadora.ui

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.delete
import androidx.compose.ui.text.TextRange
import dev.lcv.calculadora.calc.Formatacao
import java.math.BigDecimal
import java.math.BigInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * As regras próprias dos campos numéricos (especificação v2.7 da CALANDR-27, seções 4.3, 4.4 e 4.6), na JVM, pela
 * edição encenada de [editarComoUsuario]. Os caminhos reais (tecla, colagem, acessibilidade) são dos testes de tela.
 */
class CampoDeCaixaTest {

    private fun estado(cru: String, selecao: TextRange = TextRange(cru.length)) =
        TextFieldState(cru, selecao)

    private fun TextFieldState.digitar(texto: String, tipo: TipoNumerico = TipoNumerico.DINHEIRO) =
        digitarComoUsuario(texto, tipo)

    private fun TextFieldState.assertCru(esperado: String, selecao: TextRange = TextRange(esperado.length)) {
        assertEquals(esperado, text.toString(), "texto cru")
        assertEquals(selecao, selection, "seleção")
    }

    // 4.3.1 — mudança só de seleção.

    @Test
    fun `selecionar tudo fica selecionado`() {
        val campo = estado("123", TextRange(3))
        campo.editarComoUsuario { selection = TextRange(0, 3) }
        campo.assertCru("123", TextRange(0, 3))
    }

    @Test
    fun `selecionar tudo de tras para frente tambem e selecionar tudo`() {
        val campo = estado("123", TextRange(3))
        campo.editarComoUsuario { selection = TextRange(3, 0) }
        campo.assertCru("123", TextRange(3, 0))
    }

    @Test
    fun `um cursor no meio volta para o fim`() {
        for (posicao in 0..2) {
            val campo = estado("100")
            campo.editarComoUsuario { selection = TextRange(posicao) }
            campo.assertCru("100")
        }
    }

    @Test
    fun `uma selecao parcial vira cursor no fim`() {
        val campo = estado("12345")
        campo.editarComoUsuario { selection = TextRange(1, 3) }
        campo.assertCru("12345")
    }

    // 4.3.2 e 4.3.3 — só dígitos contam, e uma inserção sem dígito algum é recusada inteira (decisão 2).

    @Test
    fun `uma letra sobre o numero selecionado e recusada e a selecao fica`() {
        val campo = estado("550", TextRange(0, 3))
        campo.digitar("a")
        campo.assertCru("550", TextRange(0, 3))
    }

    @Test
    fun `colar so simbolos nao muda nada`() {
        val campo = estado("550")
        campo.digitar("-,.")
        campo.assertCru("550")
    }

    @Test
    fun `colar texto formatado aproveita so os digitos, que entram pela direita`() {
        val campo = estado("550")
        campo.digitar("R$ 1,00")
        campo.assertCru("550100")
    }

    @Test
    fun `colar texto formatado sobre tudo selecionado troca o valor`() {
        val campo = estado("550", TextRange(0, 3))
        campo.digitar("R$ 1,00")
        campo.assertCru("100")
    }

    @Test
    fun `digitos de outros alfabetos nao contam`() {
        val campo = estado("")
        // Dígitos árabe-índicos e de largura total: `isDigit` os aceitaria, a regra só aceita ASCII.
        campo.digitar("١٢３")
        campo.assertCru("")
    }

    // 4.3.4 — zeros à esquerda: vazio é o padrão, "0" é zero explícito.

    @Test
    fun `zero num campo vazio e zero explicito`() {
        val campo = estado("")
        campo.digitar("0")
        campo.assertCru("0")
    }

    @Test
    fun `zeros a esquerda colados somem`() {
        val campo = estado("")
        campo.digitar("007")
        campo.assertCru("7")
        val zeros = estado("")
        zeros.digitar("000")
        zeros.assertCru("0")
    }

    // 4.3.5 — uma tecla recusada não muda o estado.

    @Test
    fun `zero sobre zero nao muda nada`() {
        val campo = estado("0")
        campo.digitar("0")
        campo.assertCru("0")
    }

    @Test
    fun `os mesmos digitos formatados sobre tudo selecionado deixam a selecao inteira`() {
        val campo = estado("550", TextRange(0, 3))
        campo.digitar("R$ 5,50")
        campo.assertCru("550", TextRange(0, 3))
    }

    @Test
    fun `redigitar o mesmo digito sobre a selecao deixa a selecao inteira`() {
        val campo = estado("5", TextRange(0, 1))
        campo.digitar("5")
        campo.assertCru("5", TextRange(0, 1))
    }

    // 4.3.6 — aceito: o texto cru normalizado e o cursor no fim.

    @Test
    fun `cada digito entra pela direita com o cursor no fim`() {
        val campo = estado("")
        for ((tecla, esperado) in listOf("1" to "1", "2" to "12", "3" to "123")) {
            campo.digitar(tecla)
            campo.assertCru(esperado)
        }
    }

    @Test
    fun `apagar o ultimo digito ate esvaziar volta ao padrao`() {
        val campo = estado("10")
        campo.editarComoUsuario { delete(length - 1, length) }
        campo.assertCru("1")
        campo.editarComoUsuario { delete(length - 1, length) }
        campo.assertCru("")
    }

    @Test
    fun `apagar com tudo selecionado esvazia`() {
        val campo = estado("123", TextRange(0, 3))
        campo.editarComoUsuario { delete(0, length) }
        campo.assertCru("")
    }

    // 4.3.7 — o limite de dígitos é o `maxLength` nativo, depois da regra.

    @Test
    fun `um digito alem do limite e recusado`() {
        val campo = estado("12345678901234")
        campo.digitar("5")
        campo.assertCru("12345678901234")
        val percentual = estado("10000")
        percentual.digitar("1", TipoNumerico.PERCENTUAL)
        percentual.assertCru("10000")
        val taxa = estado("12345678")
        taxa.digitar("9", TipoNumerico.TAXA)
        taxa.assertCru("12345678")
    }

    @Test
    fun `uma colagem alem do limite e recusada inteira`() {
        val campo = estado("")
        campo.digitar("123456789012345")
        campo.assertCru("")
    }

    @Test
    fun `o limite conta digitos, e nao os simbolos colados`() {
        val dinheiro = estado("")
        dinheiro.digitar("R$ 1.234.567,89")
        dinheiro.assertCru("123456789")
        val percentual = estado("")
        percentual.digitar("R$ 12,34", TipoNumerico.PERCENTUAL)
        percentual.assertCru("1234")
        val taxa = estado("")
        taxa.digitar("R$ 1,2345", TipoNumerico.TAXA)
        taxa.assertCru("12345")
    }

    // 4.4 — o formatador.

    private fun formatar(cru: String, casas: Int): String {
        val campo = TextFieldState(cru)
        campo.edit { with(FormatoDeCaixa(casas)) { transformOutput() } }
        return campo.text.toString()
    }

    @Test
    fun `campo vazio mostra vazio, para o rotulo e o padrao continuarem valendo`() {
        assertEquals("", formatar("", 2))
        assertEquals("", formatar("", 4))
    }

    @Test
    fun `exemplos da regra de separadores`() {
        assertEquals("0,00", formatar("0", 2))
        assertEquals("0,05", formatar("5", 2))
        assertEquals("123,45", formatar("12345", 2))
        assertEquals("1.234,56", formatar("123456", 2))
        assertEquals("123.456,78", formatar("12345678", 2))
        assertEquals("123.456.789.012,34", formatar("12345678901234", 2))
        assertEquals("0,0001", formatar("1", 4))
        assertEquals("1.234,5678", formatar("12345678", 4))
    }

    // A15 — o formatador do campo e o `Formatacao` público do motor dizem o mesmo número.
    @Test
    fun `o campo formata como o motor, em todo prefixo`() {
        val dinheiro = (1..14).map { "12345678901234".take(it) } + "0"
        for (cru in dinheiro) {
            assertEquals(Formatacao.reais(BigDecimal(BigInteger(cru), 2)), formatar(cru, 2), "duas casas, cru $cru")
        }
        val taxa = (1..8).map { "12345678".take(it) } + "0"
        for (cru in taxa) {
            assertEquals(Formatacao.taxa(BigDecimal(BigInteger(cru), 4)), formatar(cru, 4), "quatro casas, cru $cru")
        }
    }

    // 4.6 — a conversão para o motor.

    @Test
    fun `vazio e o padrao, zero e zero, e o cru entra com as casas do campo`() {
        assertNull(bigDecimalDe("", 2))
        assertEquals(BigDecimal("0.00"), bigDecimalDe("0", 2))
        assertEquals(BigDecimal("1234.56"), bigDecimalDe("123456", 2))
        assertEquals(BigDecimal("5.7340"), bigDecimalDe("57340", 4))
    }
}
