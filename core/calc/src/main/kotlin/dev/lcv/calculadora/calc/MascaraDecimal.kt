/*
 * Copyright © 2026 LCV Ideas & Software
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package dev.lcv.calculadora.calc

import java.math.BigDecimal
import java.math.BigInteger

/**
 * A máscara de caixa dos campos numéricos (decisão do operador, 02/10/2026,
 * CALANDR-27), a mesma do campo Valor do produto web (`applyCurrencyMask`,
 * `SimulationForm.tsx`), estendida a todos os campos: só os dígitos contam e
 * entram pela direita, com [casas] decimais, no padrão brasileiro — `123456`
 * com duas casas é `"1.234,56"`, `350` é `"3,50"`.
 *
 * [anterior] é o texto que o campo mostrava. Ele decide dois casos que o texto
 * novo sozinho não decide:
 * - apagar até sobrarem só zeros esvazia o campo, que é o "usar o padrão" dos
 *   parâmetros; sem isso `"0,01"` apagado voltaria a `"0,00"` e o campo nunca
 *   ficaria vazio;
 * - um dígito além de [maximoDeDigitos] é recusado e o campo fica como estava,
 *   como um caixa que não aceita mais uma tecla.
 *
 * Digitar zero num campo vazio dá `"0,00"`: zero é um valor válido (um spread
 * de 0 %), diferente de deixar o padrão. O resultado é lido pelo
 * [parseNumeroLocalizado] sem mudança no motor.
 */
fun mascaraDecimal(novo: String, anterior: String, casas: Int, maximoDeDigitos: Int): String {
    require(casas >= 0 && maximoDeDigitos > casas) { "casas e limite de dígitos incompatíveis" }
    val digitos = novo.filter { it in '0'..'9' }
    if (digitos.isEmpty()) return ""
    val significativos = digitos.trimStart('0')
    if (significativos.isEmpty() && novo.length < anterior.length) return ""
    if (significativos.length > maximoDeDigitos) return anterior
    val valor = BigDecimal(BigInteger(significativos.ifEmpty { "0" }), casas)
    return Formatacao.formatador(casas).format(valor)
}

/** Os limites de dígitos de cada tipo de campo, com as casas que cada um usa. */
object CamposNumericos {
    /** Reais e moeda estrangeira: 2 casas, até 12 algarismos inteiros, o máximo que o [parseNumeroLocalizado] lê. */
    const val CASAS_DINHEIRO = 2
    const val DIGITOS_DINHEIRO = 14

    /** VET (R$ por unidade da moeda): 4 casas, até 9.999,9999. */
    const val CASAS_TAXA = 4
    const val DIGITOS_TAXA = 8

    /** Percentuais dos parâmetros: 2 casas, até 999,99 (o motor recusa acima de 100 %, com a mensagem dele). */
    const val CASAS_PERCENTUAL = 2
    const val DIGITOS_PERCENTUAL = 5
}
