/*
 * Copyright © 2026 LCV Ideas & Software
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package dev.lcv.calculadora.ui

import dev.lcv.calculadora.calc.Formatacao
import dev.lcv.calculadora.calc.Modalidade
import dev.lcv.calculadora.calc.Moedas
import dev.lcv.calculadora.calc.Opcao
import dev.lcv.calculadora.calc.Simulacao
import dev.lcv.calculadora.calc.melhorOpcao
import java.time.format.DateTimeFormatter

private val DATA_BR: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

/**
 * Texto da simulação para a folha de compartilhamento nativa. No produto web
 * isto montava uma mensagem de WhatsApp; no Android o destino é escolhido pelo
 * sistema, então o que sobra é o texto.
 */
fun textoDaSimulacao(simulacao: Simulacao): String {
    val entrada = simulacao.entrada
    val simbolo = Moedas.simbolo(entrada.moeda)
    val melhor = melhorOpcao(simulacao)
    val linhas = mutableListOf(
        "Compra de $simbolo ${Formatacao.reais(entrada.valorOriginal)} em ${entrada.dataCompra.format(DATA_BR)}",
    )
    (simulacao.cartao as? Modalidade.Suportada)?.let {
        linhas += "Cartão: R$ ${Formatacao.reais(it.custo.valorTotalBrl)} (VET ${Formatacao.taxa(it.custo.vet)})"
        linhas += "PTAX de fechamento: ${it.dataCotacao?.format(DATA_BR) ?: "data indisponível"}"
    }
    (simulacao.global as? Modalidade.Suportada)?.let {
        linhas += "Conta global: R$ ${Formatacao.reais(it.custo.valorTotalBrl)} (VET ${Formatacao.taxa(it.custo.vet)})"
        linhas += "Fonte: " + when (it.fonteSpot) {
            dev.lcv.calculadora.calc.FonteSpot.AWESOME_API -> "AwesomeAPI"
            dev.lcv.calculadora.calc.FonteSpot.YAHOO_FINANCE -> "Yahoo Finance"
            dev.lcv.calculadora.calc.FonteSpot.ULTIMO_SPOT_SALVO -> "último spot salvo (contingência, máximo 24 h)"
            else -> "PTAX de fechamento (contingência): ${it.dataCotacao?.format(DATA_BR)}"
        }
        it.instanteCotacao?.let { instante ->
            linhas += "Cotação spot: " + instante.atZone(dev.lcv.calculadora.calc.ContextoOperacional.FUSO_BRASILIA)
                .format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))
        }
        if (it.plantao == true) linhas += "Spread de plantão aplicado."
        linhas += "A conta global usa a cotação disponível agora; não reconstrói uma compra histórica."
    }
    simulacao.saldoExistente?.let {
        linhas += "Saldo já carregado: R$ ${Formatacao.reais(it.valorTotalBrl)} (VET ${Formatacao.taxa(it.vetInformado)})"
    }
    if (melhor != null) {
        linhas += "Mais barata: ${nomeDaOpcao(melhor)}"
    }
    return linhas.joinToString("\n")
}

private fun nomeDaOpcao(opcao: Opcao): String = when (opcao) {
    Opcao.CARTAO -> "cartão de crédito"
    Opcao.CONTA_GLOBAL -> "conta global"
    Opcao.SALDO_EXISTENTE -> "saldo já carregado"
}
