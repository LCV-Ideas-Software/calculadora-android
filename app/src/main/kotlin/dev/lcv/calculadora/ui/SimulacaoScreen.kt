/*
 * Copyright © 2026 LCV Ideas & Software
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package dev.lcv.calculadora.ui

import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.lcv.calculadora.R
import dev.lcv.calculadora.calc.AnaliseCompraEmReais
import dev.lcv.calculadora.calc.BandasSensibilidade
import dev.lcv.calculadora.calc.CenarioCompraEmReais
import dev.lcv.calculadora.calc.CenarioCusto
import dev.lcv.calculadora.calc.CustoConversao
import dev.lcv.calculadora.calc.ErroEntrada
import dev.lcv.calculadora.calc.FonteSpot
import dev.lcv.calculadora.calc.Formatacao
import dev.lcv.calculadora.calc.Modalidade
import dev.lcv.calculadora.calc.Moedas
import dev.lcv.calculadora.calc.MotivoIndisponibilidade
import dev.lcv.calculadora.calc.Opcao
import dev.lcv.calculadora.calc.Parametros
import dev.lcv.calculadora.calc.QualidadeBacktest
import dev.lcv.calculadora.calc.Simulacao
import dev.lcv.calculadora.calc.melhorOpcao
import dev.lcv.calculadora.data.backtest.ResumoBacktest
import dev.lcv.calculadora.data.simulacao.ResultadoSimulacao
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private val DATA_BR: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

/** Marcas usadas pelos testes de Compose. */
object Marcas {
    const val VALOR = "campo-valor"
    const val CALCULAR = "acao-calcular"
    const val RESULTADO = "resultado"
    const val CENARIOS = "cenarios-em-reais"
    const val DCC = "caixa-dcc"
}

@Composable
fun SimulacaoScreen(viewModel: SimulacaoViewModel, modifier: Modifier = Modifier) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    val contexto = LocalContext.current
    val emReais = estado.modo == Modo.COBRADO_EM_REAIS

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Tema.espacos.entreCampos),
    ) {
        CaixaDcc(emReais) { marcado ->
            viewModel.mudarModo(if (marcado) Modo.COBRADO_EM_REAIS else Modo.SIMULACAO)
        }

        if (!emReais) {
            CampoEmCaixa(stringResource(R.string.campo_moeda)) {
                SeletorDeMoeda(estado.moeda, viewModel::mudarMoeda)
            }
        }

        CampoEmCaixa(stringResource(R.string.campo_data)) {
            SeletorDeData(estado.dataCompra, viewModel::mudarData)
        }

        CampoEmCaixa(
            rotulo = if (emReais) {
                stringResource(R.string.campo_valor_reais)
            } else {
                stringResource(R.string.campo_valor, estado.moeda)
            },
        ) {
            CampoNumerico(
                estado.valor,
                viewModel::mudarValor,
                modifier = Modifier.testTag(Marcas.VALOR),
                exemplo = stringResource(R.string.exemplo_valor),
            )
        }

        if (emReais) {
            CampoEmCaixa(
                rotulo = stringResource(R.string.campo_fatura),
                dica = stringResource(R.string.dica_fatura),
            ) {
                CampoNumerico(
                    estado.valorFatura,
                    viewModel::mudarValorFatura,
                    exemplo = stringResource(R.string.exemplo_fatura),
                )
            }
        } else {
            CampoEmCaixa(
                rotulo = stringResource(R.string.campo_vet_saldo),
                dica = stringResource(R.string.dica_vet_saldo),
            ) {
                CampoNumerico(
                    estado.vetSaldo,
                    viewModel::mudarVetSaldo,
                    exemplo = stringResource(R.string.exemplo_vet),
                )
            }
        }

        ParametrosRecolhiveis(estado, viewModel)

        Button(
            onClick = viewModel::calcular,
            enabled = !estado.carregando,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(Marcas.CALCULAR),
        ) {
            Text(stringResource(R.string.acao_calcular), fontWeight = FontWeight.Bold)
        }

        estado.erro?.let { erro ->
            Aviso(
                texto = stringResource(
                    when (erro) {
                        ErroEntrada.VALOR_INVALIDO -> R.string.erro_valor_invalido
                        ErroEntrada.DATA_AUSENTE -> R.string.erro_data_ausente
                    },
                ),
                fundo = Tema.cores.destaqueSaldo,
                cor = Tema.cores.textoNorm,
            )
        }

        if (estado.carregando) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Text(stringResource(R.string.carregando), fontSize = 12.sp, color = Tema.cores.textoFraco)
        }

        if (estado.falhou) {
            Aviso(stringResource(R.string.erro_falha), Tema.cores.destaqueSaldo, Tema.cores.textoNorm)
        }

        estado.simulacao?.let { resultado ->
            Resultado(resultado) { compartilhar(contexto, textoDaSimulacao(resultado.simulacao)) }
        }

        val analise = estado.compraEmReais
        val valorEmReais = estado.valorEmReais
        if (analise != null && valorEmReais != null) {
            CompraEmReais(analise, valorEmReais)
        }
    }
}

/** A caixa de seleção que abre o modo cobrado em reais, como no topo do formulário do web. */
@Composable
private fun CaixaDcc(marcado: Boolean, aoMudar: (Boolean) -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(Marcas.DCC),
        shape = RoundedCornerShape(Tema.formas.cartao),
        color = Tema.cores.superficieCartao,
        border = BorderStroke(1.dp, Tema.cores.separador),
    ) {
        Row(
            modifier = Modifier
                .clickable { aoMudar(!marcado) }
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = marcado,
                onCheckedChange = aoMudar,
                colors = CheckboxDefaults.colors(checkedColor = Tema.cores.foco),
            )
            Text(
                text = stringResource(R.string.campo_dcc),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = Tema.cores.textoFraco,
            )
        }
    }
}

@Composable
private fun SeletorDeMoeda(moeda: String, aoMudar: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Moedas.SUPORTADAS.forEach { codigo ->
            FilterChip(
                selected = moeda == codigo,
                onClick = { aoMudar(codigo) },
                label = { Text(codigo, fontWeight = FontWeight.SemiBold) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Tema.cores.marcaMarinho,
                    selectedLabelColor = androidx.compose.ui.graphics.Color.White,
                ),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SeletorDeData(data: LocalDate, aoMudar: (LocalDate) -> Unit) {
    var aberto by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = data.format(DATA_BR),
            fontWeight = FontWeight.SemiBold,
            color = Tema.cores.textoNorm,
        )
        OutlinedButton(
            onClick = { aberto = true },
            shape = RoundedCornerShape(Tema.formas.campo),
            border = BorderStroke(1.dp, Tema.cores.separador),
        ) {
            Text(stringResource(R.string.acao_escolher_data), color = Tema.cores.textoFraco)
        }
    }

    if (aberto) {
        // O seletor do Material trabalha em milissegundos UTC; os dois sentidos
        // usam o mesmo fuso para não deslocar o dia.
        val estadoDoSeletor = rememberDatePickerState(
            initialSelectedDateMillis = data.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { aberto = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        estadoDoSeletor.selectedDateMillis?.let { millis ->
                            aoMudar(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
                        }
                        aberto = false
                    },
                ) { Text(stringResource(R.string.acao_confirmar)) }
            },
            dismissButton = {
                TextButton(onClick = { aberto = false }) {
                    Text(stringResource(R.string.acao_cancelar))
                }
            },
        ) {
            DatePicker(state = estadoDoSeletor)
        }
    }
}

/** A seção recolhível de parâmetros que o web abre com `<details>`. */
@Composable
private fun ParametrosRecolhiveis(estado: EstadoTela, viewModel: SimulacaoViewModel) {
    var aberto by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Tema.formas.cartao),
        color = Tema.cores.superficieCartao,
        border = BorderStroke(1.dp, Tema.cores.separador),
    ) {
        Column(
            modifier = Modifier
                .clickable { aberto = !aberto }
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = stringResource(R.string.parametros_titulo),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = Tema.cores.textoFraco,
            )
            AnimatedVisibility(visible = aberto) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ParametroNumerico(
                        stringResource(R.string.parametro_spread_cartao),
                        estado.spreadCartaoPercent,
                        viewModel::mudarSpreadCartao,
                    )
                    ParametroNumerico(
                        stringResource(R.string.parametro_iof_cartao),
                        estado.iofPercent,
                        viewModel::mudarIof,
                    )
                    ParametroNumerico(
                        stringResource(R.string.parametro_spread_global_aberto),
                        estado.spreadGlobalAbertoPercent,
                        viewModel::mudarSpreadGlobalAberto,
                    )
                    ParametroNumerico(
                        stringResource(R.string.parametro_spread_global_fechado),
                        estado.spreadGlobalFechadoPercent,
                        viewModel::mudarSpreadGlobalFechado,
                    )
                }
            }
        }
    }
}

@Composable
private fun ParametroNumerico(rotulo: String, valor: String, aoMudar: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(rotulo, fontSize = 10.sp, color = Tema.cores.textoApagado)
        CampoNumerico(valor, aoMudar, exemplo = stringResource(R.string.parametro_automatico))
    }
}

@Composable
private fun Resultado(resultado: ResultadoSimulacao, aoCompartilhar: () -> Unit) {
    val simulacao = resultado.simulacao
    val melhor = melhorOpcao(simulacao)
    val moeda = simulacao.entrada.moeda
    val global = simulacao.global

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(Marcas.RESULTADO),
        verticalArrangement = Arrangement.spacedBy(Tema.espacos.entreSecoes),
    ) {
        if (global is Modalidade.Indisponivel &&
            global.motivo == MotivoIndisponibilidade.MOEDA_SEM_CONTA_GLOBAL
        ) {
            Aviso(
                texto = stringResource(R.string.aviso_sem_global, moeda),
                fundo = Tema.cores.destaqueSaldo,
                cor = Tema.cores.textoNorm,
            )
        }

        CartaoComparacao(
            icone = stringResource(R.string.icone_cartao),
            titulo = stringResource(R.string.cartao_credito),
            modalidade = simulacao.cartao,
            moeda = moeda,
            tinta = Tema.cores.destaqueCartao,
            vencedor = melhor == Opcao.CARTAO,
        )
        if (global is Modalidade.Suportada) {
            CartaoComparacao(
                icone = stringResource(R.string.icone_global),
                titulo = stringResource(R.string.cartao_global),
                modalidade = global,
                moeda = moeda,
                tinta = Tema.cores.destaqueGlobal,
                vencedor = melhor == Opcao.CONTA_GLOBAL,
            )
        }
        simulacao.saldoExistente?.let { saldo ->
            CartaoVidro(tinta = Tema.cores.destaqueSaldo, destacado = melhor == Opcao.SALDO_EXISTENTE) {
                CabecalhoCartao(
                    stringResource(R.string.icone_saldo),
                    stringResource(R.string.cartao_saldo),
                    melhor == Opcao.SALDO_EXISTENTE,
                )
                Linha(stringResource(R.string.rotulo_vet), Formatacao.taxa(saldo.vetInformado), apagado = true)
                HorizontalDivider(color = Tema.cores.separador)
                Linha(
                    stringResource(R.string.rotulo_total),
                    "R$ " + Formatacao.reais(saldo.valorTotalBrl),
                    forte = true,
                )
            }
        }

        melhor?.let { opcao ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                Pilula(
                    texto = stringResource(R.string.melhor_opcao, stringResource(rotuloDaOpcao(opcao))),
                    fundo = Tema.cores.destaqueGlobal,
                    cor = Tema.cores.textoNorm,
                )
            }
        }

        PainelParametros(simulacao.entrada.parametros)
        (simulacao.sensibilidadeCartao ?: simulacao.sensibilidadeGlobal)?.let { PainelSensibilidade(it) }
        resultado.backtest?.let { PainelBacktest(it, simulacao) }

        OutlinedButton(
            onClick = aoCompartilhar,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(Tema.formas.campo),
            border = BorderStroke(1.dp, Tema.cores.separador),
        ) {
            Text(stringResource(R.string.acao_compartilhar), color = Tema.cores.textoFraco)
        }
    }
}

@Composable
private fun CartaoComparacao(
    icone: String,
    titulo: String,
    modalidade: Modalidade,
    moeda: String,
    tinta: androidx.compose.ui.graphics.Color,
    vencedor: Boolean,
) {
    CartaoVidro(tinta = tinta, destacado = vencedor) {
        CabecalhoCartao(icone, titulo, vencedor)
        when (modalidade) {
            is Modalidade.Suportada -> {
                Linha(stringResource(R.string.rotulo_taxa), "R$ " + Formatacao.taxa(modalidade.taxaUtilizada))
                Linha(
                    stringResource(R.string.rotulo_fonte),
                    stringResource(rotuloDaFonte(modalidade.fonteSpot)),
                    apagado = true,
                )
                Custo(modalidade.custo, moeda)
                Indicadores(modalidade)
            }

            is Modalidade.Indisponivel -> Text(
                text = stringResource(
                    when (modalidade.motivo) {
                        MotivoIndisponibilidade.PTAX_INDISPONIVEL -> R.string.indisponivel_ptax
                        MotivoIndisponibilidade.CAMBIO_GLOBAL_INDISPONIVEL -> R.string.indisponivel_cambio
                        MotivoIndisponibilidade.MOEDA_SEM_CONTA_GLOBAL -> R.string.aviso_sem_global
                    },
                    moeda,
                ),
                fontSize = 13.sp,
                color = Tema.cores.textoFraco,
            )
        }
    }
}

@Composable
private fun CabecalhoCartao(icone: String, titulo: String, vencedor: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(icone, fontSize = 18.sp)
            Text(titulo, fontWeight = FontWeight.Bold, color = Tema.cores.textoNorm)
        }
        if (vencedor) {
            Pilula(stringResource(R.string.selo_vencedor), Tema.cores.vencedor, Tema.cores.vencedorTexto)
        }
    }
}

@Composable
private fun Custo(custo: CustoConversao, moeda: String) {
    Linha(
        stringResource(R.string.rotulo_spread),
        Formatacao.percentual(custo.spread) +
            " (" + Moedas.simbolo(moeda) + " " + Formatacao.reais(custo.valorSpread) + ")",
    )
    Linha(
        stringResource(R.string.rotulo_iof),
        Formatacao.percentual(custo.iof) + " (R$ " + Formatacao.reais(custo.valorIof) + ")",
    )
    HorizontalDivider(color = Tema.cores.separador)
    Linha(stringResource(R.string.rotulo_total), "R$ " + Formatacao.reais(custo.valorTotalBrl), forte = true)
    Linha(stringResource(R.string.rotulo_vet), "R$ " + Formatacao.taxa(custo.vet), apagado = true)
}

@Composable
private fun Indicadores(modalidade: Modalidade.Suportada) {
    val contingencia = modalidade.fonteSpot == FonteSpot.ULTIMO_SPOT_SALVO ||
        modalidade.fonteSpot == FonteSpot.PTAX_CONTINGENCIA
    if (modalidade.plantao == true || contingencia) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (modalidade.plantao == true) {
                Pilula(stringResource(R.string.pilula_plantao), Tema.cores.destaqueSaldo, Tema.cores.textoNorm)
            }
            if (contingencia) {
                Pilula(stringResource(R.string.pilula_contingencia), Tema.cores.destaqueSaldo, Tema.cores.textoNorm)
            }
        }
    }
}

@Composable
private fun PainelParametros(parametros: Parametros) {
    CartaoVidro {
        TituloPainel(stringResource(R.string.parametros_vigentes_titulo), Tema.cores.textoFraco)
        Linha(stringResource(R.string.parametro_vigente_iof_cartao), Formatacao.percentual(parametros.iofCartao))
        Linha(stringResource(R.string.parametro_vigente_spread_cartao), Formatacao.percentual(parametros.spreadCartao))
        Linha(stringResource(R.string.parametro_vigente_iof_global), Formatacao.percentual(parametros.iofGlobal))
        Linha(
            stringResource(R.string.parametro_vigente_spread_aberto),
            Formatacao.percentual(parametros.spreadGlobalAberto),
        )
        Linha(
            stringResource(R.string.parametro_vigente_spread_fechado),
            Formatacao.percentual(parametros.spreadGlobalFechado),
        )
    }
}

@Composable
private fun PainelSensibilidade(bandas: BandasSensibilidade) {
    CartaoVidro(tinta = Tema.cores.destaqueCartao) {
        TituloPainel(stringResource(R.string.sensibilidade_titulo), Tema.cores.marcaArdosia)
        BandaSensibilidade(stringResource(R.string.banda_otimista), bandas.otimista)
        BandaSensibilidade(stringResource(R.string.banda_base), bandas.base)
        BandaSensibilidade(stringResource(R.string.banda_pessimista), bandas.pessimista)
    }
}

@Composable
private fun BandaSensibilidade(rotulo: String, custo: CustoConversao) {
    Linha(
        rotulo,
        "R$ " + Formatacao.reais(custo.valorTotalBrl) +
            " " + stringResource(R.string.vet_entre_parenteses, Formatacao.taxa(custo.vet)),
    )
}

@Composable
private fun PainelBacktest(resumo: ResumoBacktest, simulacao: Simulacao) {
    CartaoVidro(tinta = Tema.cores.destaqueGlobal) {
        TituloPainel(stringResource(R.string.backtest_titulo), Tema.cores.marcaArdosia)
        Linha(
            stringResource(R.string.backtest_mape),
            resumo.mapePercent?.let { Formatacao.reais(it) + "%" } ?: "—",
        )
        simulacao.erroBacktest?.let {
            Linha(stringResource(R.string.backtest_erro_atual), Formatacao.percentual(it))
        }
        Linha(stringResource(R.string.backtest_observacoes), resumo.observacoes.toString(), apagado = true)
        resumo.qualidade?.let { qualidade ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                Pilula(
                    texto = stringResource(rotuloDaQualidade(qualidade)),
                    fundo = when (qualidade) {
                        QualidadeBacktest.EXCELENTE -> Tema.cores.destaqueGlobal
                        QualidadeBacktest.BOA -> Tema.cores.destaqueCartao
                        QualidadeBacktest.ATENCAO -> Tema.cores.destaqueSaldo
                    },
                    cor = Tema.cores.textoNorm,
                )
            }
        }
    }
}

@Composable
private fun CompraEmReais(analise: AnaliseCompraEmReais, valorEmReais: BigDecimal) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(Marcas.CENARIOS),
        verticalArrangement = Arrangement.spacedBy(Tema.espacos.entreCampos),
    ) {
        CartaoVidro(tinta = Tema.cores.destaqueCartao) {
            Text(
                text = stringResource(R.string.em_reais_titulo),
                fontWeight = FontWeight.Bold,
                color = Tema.cores.textoNorm,
            )
            Text(
                text = stringResource(R.string.em_reais_intro, Formatacao.reais(valorEmReais)),
                fontSize = 12.sp,
                lineHeight = 17.sp,
                color = Tema.cores.textoFraco,
            )
        }

        val provavel = analise.diagnostico?.cenarioProvavel
        CartaoCenario(analise.adquirenciaLocal, provavel)
        CartaoCenario(analise.dccPura, provavel)
        CartaoCenario(analise.duplaConversao, provavel)

        analise.diagnostico?.let { diagnostico ->
            CartaoVidro(tinta = Tema.cores.destaqueGlobal) {
                TituloPainel(stringResource(R.string.diagnostico_titulo), Tema.cores.marcaArdosia)
                Linha(
                    stringResource(R.string.diagnostico_valor),
                    "R$ " + Formatacao.reais(diagnostico.valorFaturaBrl),
                )
                Linha(
                    stringResource(R.string.diagnostico_acrescimo),
                    Formatacao.reais(diagnostico.markupImplicitoPercent) + "%",
                )
                Text(
                    text = if (diagnostico.cenarioProvavel == CenarioCompraEmReais.INDETERMINADO) {
                        stringResource(R.string.diagnostico_indeterminado)
                    } else {
                        stringResource(descricaoDoCenario(diagnostico.cenarioProvavel))
                    },
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    color = Tema.cores.textoFraco,
                )
            }
        }

        Aviso(stringResource(R.string.dica_anti_dcc), Tema.cores.destaqueSaldo, Tema.cores.textoNorm)
        Aviso(stringResource(R.string.nota_conta_global), Tema.cores.fundoBaixo, Tema.cores.textoFraco)
    }
}

@Composable
private fun CartaoCenario(cenario: CenarioCusto, provavel: CenarioCompraEmReais?) {
    val destaque = provavel == cenario.cenario
    CartaoVidro(
        tinta = if (destaque) Tema.cores.destaqueSaldo else androidx.compose.ui.graphics.Color.Transparent,
        destacado = destaque,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(iconeDoCenario(cenario.cenario)), fontSize = 16.sp)
                Text(
                    text = stringResource(rotuloDoCenario(cenario.cenario)),
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = Tema.cores.textoNorm,
                )
            }
            if (destaque) {
                Pilula(stringResource(R.string.pilula_provavel), Tema.cores.vencedor, Tema.cores.vencedorTexto)
            }
        }
        Linha(stringResource(R.string.rotulo_total), "R$ " + Formatacao.reais(cenario.totalBrl), forte = true)
        Linha(
            stringResource(R.string.rotulo_acrescimo),
            "+ R$ " + Formatacao.reais(cenario.custoAdicionalBrl) +
                " (" + Formatacao.reais(cenario.custoAdicionalPercent) + "%)",
        )
        Text(
            text = stringResource(descricaoDoCenario(cenario.cenario)),
            fontSize = 11.sp,
            lineHeight = 15.sp,
            color = Tema.cores.textoApagado,
        )
    }
}

/** Caixa de aviso colorida, como as do web. */
@Composable
private fun Aviso(texto: String, fundo: androidx.compose.ui.graphics.Color, cor: androidx.compose.ui.graphics.Color) {
    Text(
        text = texto,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        color = cor,
        modifier = Modifier
            .fillMaxWidth()
            .background(fundo, RoundedCornerShape(Tema.formas.cartao))
            .padding(12.dp),
    )
}

private fun rotuloDaFonte(fonte: FonteSpot?): Int = when (fonte) {
    FonteSpot.AWESOME_API -> R.string.fonte_awesome
    FonteSpot.YAHOO_FINANCE -> R.string.fonte_yahoo
    FonteSpot.ULTIMO_SPOT_SALVO -> R.string.fonte_ultimo_salvo
    FonteSpot.PTAX_CONTINGENCIA -> R.string.fonte_ptax_contingencia
    null -> R.string.fonte_ptax
}

private fun rotuloDaOpcao(opcao: Opcao): Int = when (opcao) {
    Opcao.CARTAO -> R.string.cartao_credito
    Opcao.CONTA_GLOBAL -> R.string.cartao_global
    Opcao.SALDO_EXISTENTE -> R.string.cartao_saldo
}

private fun rotuloDaQualidade(qualidade: QualidadeBacktest): Int = when (qualidade) {
    QualidadeBacktest.EXCELENTE -> R.string.qualidade_excelente
    QualidadeBacktest.BOA -> R.string.qualidade_boa
    QualidadeBacktest.ATENCAO -> R.string.qualidade_atencao
}

private fun rotuloDoCenario(cenario: CenarioCompraEmReais): Int = when (cenario) {
    CenarioCompraEmReais.ADQUIRENCIA_LOCAL -> R.string.cenario_adquirencia
    CenarioCompraEmReais.DCC_PURA -> R.string.cenario_dcc
    CenarioCompraEmReais.DUPLA_CONVERSAO -> R.string.cenario_dupla
    CenarioCompraEmReais.INDETERMINADO -> R.string.cenario_indeterminado
}

private fun iconeDoCenario(cenario: CenarioCompraEmReais): Int = when (cenario) {
    CenarioCompraEmReais.ADQUIRENCIA_LOCAL -> R.string.icone_adquirencia
    CenarioCompraEmReais.DCC_PURA -> R.string.icone_dcc
    CenarioCompraEmReais.DUPLA_CONVERSAO -> R.string.icone_dupla
    CenarioCompraEmReais.INDETERMINADO -> R.string.icone_dcc
}

private fun descricaoDoCenario(cenario: CenarioCompraEmReais): Int = when (cenario) {
    CenarioCompraEmReais.ADQUIRENCIA_LOCAL -> R.string.descricao_adquirencia
    CenarioCompraEmReais.DCC_PURA -> R.string.descricao_dcc
    CenarioCompraEmReais.DUPLA_CONVERSAO -> R.string.descricao_dupla
    CenarioCompraEmReais.INDETERMINADO -> R.string.diagnostico_indeterminado
}

/** Folha de compartilhamento nativa, no lugar do endereço de WhatsApp do web. */
private fun compartilhar(contexto: Context, texto: String) {
    val envio = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, texto)
    }
    contexto.startActivity(Intent.createChooser(envio, null))
}
