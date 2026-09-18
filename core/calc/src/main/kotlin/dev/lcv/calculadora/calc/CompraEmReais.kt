/*
 * Copyright © 2026 LCV Ideas & Software
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package dev.lcv.calculadora.calc

import java.math.BigDecimal

/**
 * Compra internacional cobrada em reais.
 *
 * Quando um lojista estrangeiro precifica em BRL, o custo final depende de qual
 * mecânica de liquidação ocorreu, e a simulação clássica (moeda × PTAX + spread
 * + IOF) não modela nenhuma delas. Três cenários, a partir do preço em reais do
 * checkout.
 *
 * Base legal do IOF: Decreto 6.306/2007, art. 15-B, VII (redação do Decreto
 * 12.499/2025) — o critério é o domicílio/liquidação do lojista no exterior,
 * não a moeda do checkout; base de cálculo é o montante em reais (art. 14).
 */
enum class CenarioCompraEmReais {
    /** Transação doméstica (adquirente/MoR brasileiro). Sem IOF e sem spread. */
    ADQUIRENCIA_LOCAL,

    /** O lojista já converteu para BRL com markup próprio; o emissor só cobra IOF. */
    DCC_PURA,

    /** BRL de vitrine liquidado em moeda estrangeira: conversão dupla, spread e IOF. */
    DUPLA_CONVERSAO,

    /** Só no diagnóstico reverso: o acréscimo da fatura não bate com nenhum cenário. */
    INDETERMINADO,
}

data class CenarioCusto(
    val cenario: CenarioCompraEmReais,
    val totalBrl: BigDecimal,
    val custoAdicionalBrl: BigDecimal,
    val custoAdicionalPercent: BigDecimal,
)

/** Diagnóstico reverso a partir do valor efetivamente cobrado na fatura. */
data class DiagnosticoFatura(
    val valorFaturaBrl: BigDecimal,
    val markupImplicitoPercent: BigDecimal,
    val cenarioProvavel: CenarioCompraEmReais,
)

data class AnaliseCompraEmReais(
    val adquirenciaLocal: CenarioCusto,
    val dccPura: CenarioCusto,
    val duplaConversao: CenarioCusto,
    val diagnostico: DiagnosticoFatura?,
)

/** Tolerância do diagnóstico reverso: ±0,5 ponto percentual. */
private val TOLERANCIA_DIAGNOSTICO = BigDecimal("0.005")

fun analisarCompraEmReais(
    valorReais: BigDecimal,
    iof: BigDecimal,
    spreadCartao: BigDecimal,
    valorFaturaBrl: BigDecimal? = null,
): AnaliseCompraEmReais {
    require(valorReais.signum() > 0) { "O valor em reais deve ser positivo." }
    val um = BigDecimal.ONE
    val fatorDcc = um + iof
    val fatorDupla = (um + spreadCartao) * (um + iof)

    // Cada saída é arredondada a partir da expressão exata, nunca de outra
    // saída já arredondada: custo adicional = principal × (fator − 1), e não
    // total arredondado − principal, que dobraria o arredondamento (achado da
    // revisão por pares, CALC-R1-01).
    val totalLocal = valorReais.emReais()
    val totalDcc = (valorReais * fatorDcc).emReais()
    val totalDupla = (valorReais * fatorDupla).emReais()
    val adicionalDcc = (valorReais * iof).emReais()
    val adicionalDupla = (valorReais * (fatorDupla - um)).emReais()

    val diagnostico = valorFaturaBrl?.takeIf { it.signum() > 0 }?.let { fatura ->
        val markup = fatura.dividirPor(valorReais) - um
        val alvoDcc = iof
        val alvoDupla = fatorDupla - um
        val cenario = when {
            markup.abs() <= TOLERANCIA_DIAGNOSTICO -> CenarioCompraEmReais.ADQUIRENCIA_LOCAL
            (markup - alvoDcc).abs() <= TOLERANCIA_DIAGNOSTICO -> CenarioCompraEmReais.DCC_PURA
            (markup - alvoDupla).abs() <= TOLERANCIA_DIAGNOSTICO -> CenarioCompraEmReais.DUPLA_CONVERSAO
            else -> CenarioCompraEmReais.INDETERMINADO
        }
        DiagnosticoFatura(
            valorFaturaBrl = fatura.emReais(),
            markupImplicitoPercent = (markup * CEM).emReais(),
            cenarioProvavel = cenario,
        )
    }

    return AnaliseCompraEmReais(
        adquirenciaLocal = CenarioCusto(
            cenario = CenarioCompraEmReais.ADQUIRENCIA_LOCAL,
            totalBrl = totalLocal,
            custoAdicionalBrl = BigDecimal.ZERO.emReais(),
            custoAdicionalPercent = BigDecimal.ZERO.emReais(),
        ),
        dccPura = CenarioCusto(
            cenario = CenarioCompraEmReais.DCC_PURA,
            totalBrl = totalDcc,
            custoAdicionalBrl = adicionalDcc,
            custoAdicionalPercent = (iof * CEM).emReais(),
        ),
        duplaConversao = CenarioCusto(
            cenario = CenarioCompraEmReais.DUPLA_CONVERSAO,
            totalBrl = totalDupla,
            custoAdicionalBrl = adicionalDupla,
            custoAdicionalPercent = ((fatorDupla - um) * CEM).emReais(),
        ),
        diagnostico = diagnostico,
    )
}
