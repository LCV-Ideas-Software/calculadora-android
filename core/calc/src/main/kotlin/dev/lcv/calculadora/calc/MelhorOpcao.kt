/*
 * Copyright © 2026 LCV Ideas & Software
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package dev.lcv.calculadora.calc

import java.math.BigDecimal

enum class Opcao { CARTAO, CONTA_GLOBAL, SALDO_EXISTENTE }

/**
 * A alternativa de menor custo total entre as disponíveis. Em empate vence a
 * primeira na ordem cartão, conta global, saldo existente.
 */
fun melhorOpcao(simulacao: Simulacao): Opcao? {
    val candidatos = buildList {
        (simulacao.cartao as? Modalidade.Suportada)?.let { add(Opcao.CARTAO to it.custo.valorTotalBrl) }
        (simulacao.global as? Modalidade.Suportada)?.let { add(Opcao.CONTA_GLOBAL to it.custo.valorTotalBrl) }
        simulacao.saldoExistente?.let { add(Opcao.SALDO_EXISTENTE to it.valorTotalBrl) }
    }
    return candidatos
        .filter { (_, total) -> total.signum() > 0 }
        .minByOrNull { (_, total) -> total }
        ?.first
}

/** Entrada do formulário que impede a simulação. */
enum class ErroEntrada { VALOR_INVALIDO, DATA_AUSENTE }

/**
 * Validação do formulário, como no produto web: o valor precisa ser um número
 * positivo e, fora do modo cobrado em reais, a data da compra é obrigatória.
 */
fun validarEntrada(valorTexto: String, temDataCompra: Boolean, cobradoEmReais: Boolean): ErroEntrada? {
    val valor: BigDecimal? = parseNumeroLocalizado(valorTexto)
    if (valor == null || valor.signum() <= 0) return ErroEntrada.VALOR_INVALIDO
    if (!cobradoEmReais && !temDataCompra) return ErroEntrada.DATA_AUSENTE
    return null
}
