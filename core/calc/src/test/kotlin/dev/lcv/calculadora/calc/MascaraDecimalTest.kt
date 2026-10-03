/*
 * Copyright © 2026 LCV Ideas & Software
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package dev.lcv.calculadora.calc

import dev.lcv.calculadora.calc.CamposNumericos.CASAS_DINHEIRO
import dev.lcv.calculadora.calc.CamposNumericos.CASAS_PERCENTUAL
import dev.lcv.calculadora.calc.CamposNumericos.CASAS_TAXA
import dev.lcv.calculadora.calc.CamposNumericos.DIGITOS_DINHEIRO
import dev.lcv.calculadora.calc.CamposNumericos.DIGITOS_PERCENTUAL
import dev.lcv.calculadora.calc.CamposNumericos.DIGITOS_TAXA
import kotlin.test.Test
import kotlin.test.assertEquals

/** A máscara de caixa dos campos numéricos (CALANDR-27). */
class MascaraDecimalTest {

    private fun dinheiro(novo: String, anterior: String = "") = mascaraDecimal(novo, anterior, CASAS_DINHEIRO, DIGITOS_DINHEIRO)

    /** Digita [teclas] uma a uma no fim do campo, como o teclado faz. */
    private fun digitar(teclas: String, casas: Int, maximo: Int): List<String> =
        teclas.runningFold("") { campo, tecla -> mascaraDecimal(campo + tecla, campo, casas, maximo) }.drop(1)

    @Test
    fun `cada digito entra pela direita, com ponto de milhar e virgula automaticos`() {
        assertEquals(
            listOf("0,01", "0,12", "1,23", "12,34", "123,45", "1.234,56", "12.345,67"),
            digitar("1234567", CASAS_DINHEIRO, DIGITOS_DINHEIRO),
        )
    }

    @Test
    fun `cada campo usa as suas casas decimais`() {
        assertEquals("5,7340", digitar("57340", CASAS_TAXA, DIGITOS_TAXA).last())
        assertEquals("3,50", digitar("350", CASAS_PERCENTUAL, DIGITOS_PERCENTUAL).last())
    }

    @Test
    fun `colar um valor ja formatado ou com simbolos guarda so os digitos`() {
        assertEquals("1.234,56", dinheiro("R$ 1.234,56"))
        assertEquals("1,23", dinheiro("000123"))
        assertEquals("0,10", dinheiro("-10"))
    }

    @Test
    fun `sem digito o campo fica vazio, e zero digitado e um valor`() {
        assertEquals("", dinheiro("abc"))
        assertEquals("", dinheiro(""))
        assertEquals("0,00", dinheiro("0"))
        assertEquals("0,05", dinheiro("0,005", anterior = "0,00"))
    }

    @Test
    fun `apagar desloca para a direita e, sem digito significativo, esvazia o campo`() {
        assertEquals("1,23", dinheiro("12,3", anterior = "12,34"))
        assertEquals("0,10", dinheiro("1,0", anterior = "1,00"))
        assertEquals("", dinheiro("0,0", anterior = "0,01"))
        assertEquals("", dinheiro("0,0", anterior = "0,00"))
    }

    @Test
    fun `trocar o texto inteiro por zero e digitar zero, e so apagar o ultimo caractere esvazia`() {
        // Selecionar o valor e digitar 0, ou o preenchimento automático: um spread de 0 %, e não o padrão.
        assertEquals("0,00", dinheiro("0", anterior = "5,50"))
        assertEquals("0,00", dinheiro("0", anterior = "0,10"))
        assertEquals("0,00", mascaraDecimal("0", "1,18", CASAS_PERCENTUAL, DIGITOS_PERCENTUAL))
        // A tecla de apagar tira o último caractere: aí sim o campo volta ao padrão.
        assertEquals("", dinheiro("0,0", anterior = "0,01"))
    }

    @Test
    fun `um digito alem do limite e recusado e o campo fica como estava`() {
        val cheio = "999.999.999.999,99"
        assertEquals(cheio, digitar("99999999999999", CASAS_DINHEIRO, DIGITOS_DINHEIRO).last())
        assertEquals(cheio, dinheiro(cheio + "9", anterior = cheio))
        assertEquals("999,99", mascaraDecimal("999,999", "999,99", CASAS_PERCENTUAL, DIGITOS_PERCENTUAL))
    }

    @Test
    fun `o que a mascara produz e lido pelo parser do motor`() {
        assertDecimal("999999999999.99", parseNumeroLocalizado(dinheiro("99999999999999")))
        assertDecimal("1234.56", parseNumeroLocalizado(dinheiro("123456")))
        assertDecimal("9999.9999", parseNumeroLocalizado(mascaraDecimal("99999999", "", CASAS_TAXA, DIGITOS_TAXA)))
        assertDecimal("0", parseNumeroLocalizado(dinheiro("0")))
    }
}
