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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldState
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
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
import dev.lcv.calculadora.calc.ContextoOperacional
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
    const val FATURA = "campo-fatura"
    const val VET_SALDO = "campo-vet-saldo"
    const val SPREAD_CARTAO = "parametro-spread-cartao"
    const val IOF = "parametro-iof"
    const val SPREAD_ABERTO = "parametro-spread-aberto"
    const val SPREAD_FECHADO = "parametro-spread-fechado"
    const val CALCULAR = "acao-calcular"
    const val RESULTADO = "resultado"
    const val CARTAO_CARTAO = "resultado-cartao"
    const val CARTAO_GLOBAL = "resultado-conta-global"
    const val CARTAO_SALDO = "resultado-saldo-existente"
    const val SELO = "selo-vencedor"
    const val MELHOR_OPCAO = "pilula-melhor-opcao"
    const val CENARIOS = "cenarios-em-reais"
    const val DCC = "caixa-dcc"
}

@Composable
fun SimulacaoScreen(viewModel: SimulacaoViewModel, modifier: Modifier = Modifier) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    // O resultado guardado, conferido contra os campos de agora (decisão 13 do operador, CALANDR-27): ler os
    // estados dos campos aqui, na composição, faz qualquer mudança de texto refazer a comparação.
    val visivel = estado.visivelCom(viewModel.entradasAtuais())
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
            mostrarRotulo = false,
            rotulo = if (emReais) {
                stringResource(R.string.campo_valor_reais)
            } else {
                stringResource(R.string.campo_valor, estado.moeda)
            },
        ) {
            CampoNumerico(
                viewModel.campoValor,
                rotulo = if (emReais) stringResource(R.string.campo_valor_reais) else stringResource(R.string.campo_valor, estado.moeda),
                tipo = TipoNumerico.DINHEIRO,
                modifier = Modifier.testTag(Marcas.VALOR),
                exemplo = stringResource(R.string.exemplo_valor),
            )
        }

        if (emReais) {
            CampoEmCaixa(
                rotulo = stringResource(R.string.campo_fatura),
                mostrarRotulo = false,
                dica = stringResource(R.string.dica_fatura),
            ) {
                CampoNumerico(
                    viewModel.campoFatura,
                    rotulo = stringResource(R.string.campo_fatura),
                    tipo = TipoNumerico.DINHEIRO,
                    modifier = Modifier.testTag(Marcas.FATURA),
                    exemplo = stringResource(R.string.exemplo_fatura),
                )
            }
        } else {
            CampoEmCaixa(
                rotulo = stringResource(R.string.campo_vet_saldo),
                mostrarRotulo = false,
                dica = stringResource(R.string.dica_vet_saldo),
            ) {
                CampoNumerico(
                    viewModel.campoVet,
                    rotulo = stringResource(R.string.campo_vet_saldo),
                    tipo = TipoNumerico.TAXA,
                    modifier = Modifier.testTag(Marcas.VET_SALDO),
                    exemplo = stringResource(R.string.exemplo_vet),
                )
            }
        }

        ParametrosRecolhiveis(emReais, viewModel)

        Button(
            onClick = viewModel::calcular,
            enabled = !visivel.carregando,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(Marcas.CALCULAR),
        ) {
            Text(stringResource(R.string.acao_calcular), fontWeight = FontWeight.Bold)
        }

        visivel.erro?.let { erro ->
            Aviso(
                texto = stringResource(
                    when (erro) {
                        ErroEntrada.VALOR_INVALIDO -> R.string.erro_valor_invalido
                        ErroEntrada.DATA_AUSENTE -> R.string.erro_data_ausente
                        ErroEntrada.DATA_FUTURA -> R.string.erro_data_futura
                        ErroEntrada.PARAMETRO_INVALIDO -> R.string.erro_parametro_invalido
                        ErroEntrada.OPCIONAL_INVALIDO -> R.string.erro_opcional_invalido
                    },
                ),
                fundo = Tema.cores.destaqueSaldo,
                cor = Tema.cores.textoNorm,
            )
        }

        if (visivel.carregando) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Text(stringResource(R.string.carregando), fontSize = 12.sp, color = Tema.cores.textoFraco)
        }

        if (visivel.falhou) {
            Aviso(stringResource(R.string.erro_falha), Tema.cores.destaqueSaldo, Tema.cores.textoNorm)
        }

        visivel.simulacao?.let { resultado ->
            Resultado(resultado) { compartilhar(contexto, textoDaSimulacao(resultado.simulacao)) }
        }

        val analise = visivel.compraEmReais
        val valorEmReais = visivel.valorEmReais
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
                .toggleable(value = marcado, role = Role.Checkbox, onValueChange = aoMudar)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = marcado,
                onCheckedChange = null,
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

    // Quando a data e o botão não cabem lado a lado (fonte grande, tela estreita), o botão desce inteiro para a linha
    // de baixo, ainda à direita, em vez de ser espremido até partir a palavra (CALANDR-32). O `FlowRow` decide pela
    // largura mínima do item com `weight`, e a do botão é a dele inteiro: o rótulo é uma palavra só.
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = data.format(DATA_BR),
            fontWeight = FontWeight.SemiBold,
            color = Tema.cores.textoNorm,
        )
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
            OutlinedButton(
                onClick = { aberto = true },
                shape = RoundedCornerShape(Tema.formas.campo),
                border = BorderStroke(1.dp, Tema.cores.separador),
            ) {
                Text(stringResource(R.string.acao_escolher_data), color = Tema.cores.textoFraco)
            }
        }
    }

    if (aberto) {
        // O seletor do Material trabalha em milissegundos UTC; os dois sentidos
        // usam o mesmo fuso para não deslocar o dia.
        val estadoDoSeletor = rememberDatePickerState(
            initialSelectedDateMillis = data.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            selectableDates = object : androidx.compose.material3.SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                    Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate() <= LocalDate.now(ContextoOperacional.FUSO_BRASILIA)
                override fun isSelectableYear(year: Int): Boolean = year <= LocalDate.now(ContextoOperacional.FUSO_BRASILIA).year
            },
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

/**
 * A seção recolhível de parâmetros que o web abre com `<details>`. No modo cobrado em reais o motor não usa os
 * spreads da Conta Global, e os dois campos somem (decisão 9 do operador, CALANDR-27).
 */
@Composable
private fun ParametrosRecolhiveis(emReais: Boolean, viewModel: SimulacaoViewModel) {
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
                    // O fundo de cada campo diz o padrão que vale com ele vazio, tirado das constantes do motor
                    // (CALANDR-27): "Auto" sugeria uma busca que não existe.
                    ParametroNumerico(
                        stringResource(R.string.parametro_spread_cartao),
                        viewModel.campoSpreadCartao,
                        stringResource(R.string.parametro_padrao, Formatacao.percentual(Parametros.SPREAD_CARTAO_PADRAO)),
                        Modifier.testTag(Marcas.SPREAD_CARTAO),
                    )
                    // Um só IOF vale para o cartão e a Conta Global; o teste do `:core:calc` garante que os dois
                    // padrões são iguais, para este texto não mentir sobre a Conta Global.
                    ParametroNumerico(
                        stringResource(R.string.parametro_iof_cartao),
                        viewModel.campoIof,
                        stringResource(R.string.parametro_padrao_iof, Formatacao.percentual(Parametros.IOF_CARTAO_PADRAO)),
                        Modifier.testTag(Marcas.IOF),
                    )
                    if (!emReais) {
                        ParametroNumerico(
                            stringResource(R.string.parametro_spread_global_aberto),
                            viewModel.campoSpreadAberto,
                            stringResource(
                                R.string.parametro_padrao_aberto,
                                Formatacao.percentual(Parametros.SPREAD_GLOBAL_ABERTO_PADRAO),
                                ContextoOperacional.ABERTURA_MERCADO_HORA,
                                ContextoOperacional.FECHAMENTO_MERCADO_HORA,
                            ),
                            Modifier.testTag(Marcas.SPREAD_ABERTO),
                        )
                        ParametroNumerico(
                            stringResource(R.string.parametro_spread_global_fechado),
                            viewModel.campoSpreadFechado,
                            stringResource(
                                R.string.parametro_padrao_fechado,
                                Formatacao.percentual(Parametros.SPREAD_GLOBAL_FECHADO_PADRAO),
                                ContextoOperacional.ABERTURA_MERCADO_HORA,
                                ContextoOperacional.FECHAMENTO_MERCADO_HORA,
                            ),
                            Modifier.testTag(Marcas.SPREAD_FECHADO),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ParametroNumerico(
    rotulo: String,
    estado: TextFieldState,
    padrao: String,
    modifier: Modifier = Modifier,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        CampoNumerico(
            estado,
            rotulo = rotulo,
            tipo = TipoNumerico.PERCENTUAL,
            modifier = modifier,
            exemplo = padrao,
            exemploParado = true,
        )
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
            marca = Marcas.CARTAO_CARTAO,
            icone = stringResource(R.string.icone_cartao),
            titulo = stringResource(R.string.cartao_credito),
            modalidade = simulacao.cartao,
            moeda = moeda,
            tinta = Tema.cores.destaqueCartao,
            vencedor = melhor == Opcao.CARTAO,
        )
        if (global is Modalidade.Suportada) {
            CartaoComparacao(
                marca = Marcas.CARTAO_GLOBAL,
                icone = stringResource(R.string.icone_global),
                titulo = stringResource(R.string.cartao_global),
                modalidade = global,
                moeda = moeda,
                tinta = Tema.cores.destaqueGlobal,
                vencedor = melhor == Opcao.CONTA_GLOBAL,
            )
        }
        simulacao.saldoExistente?.let { saldo ->
            CartaoResultado(Marcas.CARTAO_SALDO, Tema.cores.destaqueSaldo, vencedor = melhor == Opcao.SALDO_EXISTENTE) {
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
                PilulaMelhorOpcao(stringResource(R.string.melhor_opcao, stringResource(rotuloDaOpcao(opcao))))
            }
        }

        Text(stringResource(R.string.aviso_comparacao_temporal), color = Tema.cores.textoFraco, fontSize = 12.sp)
        PainelParametros(simulacao.entrada.parametros)
        simulacao.sensibilidadeCartao?.let { PainelSensibilidade(it, stringResource(R.string.cartao_credito)) }
        simulacao.sensibilidadeGlobal?.let { PainelSensibilidade(it, stringResource(R.string.cartao_global)) }
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
    marca: String,
    icone: String,
    titulo: String,
    modalidade: Modalidade,
    moeda: String,
    tinta: androidx.compose.ui.graphics.Color,
    vencedor: Boolean,
) {
    CartaoResultado(marca, tinta, vencedor) {
        CabecalhoCartao(icone, titulo, vencedor)
        when (modalidade) {
            is Modalidade.Suportada -> {
                Linha(stringResource(R.string.rotulo_taxa), "R$ " + Formatacao.taxa(modalidade.taxaUtilizada))
                Linha(
                    stringResource(R.string.rotulo_fonte),
                    stringResource(rotuloDaFonte(modalidade.fonteSpot)),
                    apagado = true,
                )
                modalidade.dataCotacao?.let { Linha(stringResource(R.string.data_ptax), it.format(DATA_BR)) }
                modalidade.instanteCotacao?.let {
                    Linha(stringResource(R.string.instante_spot), it.atZone(ContextoOperacional.FUSO_BRASILIA)
                        .format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")))
                }
                Custo(modalidade.custo)
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

/**
 * O cartão de resultado com o selo do vencedor no canto, fora do fluxo, como no web: `absolute -top-2.5 -right-2.5`
 * sobre o `glass-card relative` (ComparisonCard.tsx). Fora do fluxo, o selo não disputa largura com o título, que o
 * espremia até quebrá-lo letra a letra (CALANDR-32). Fica fora do `Surface`, que recorta o conteúdo ao próprio formato.
 */
@Composable
private fun CartaoResultado(
    marca: String,
    tinta: androidx.compose.ui.graphics.Color,
    vencedor: Boolean,
    conteudo: @Composable ColumnScope.() -> Unit,
) {
    Box(modifier = Modifier.fillMaxWidth().testTag(marca)) {
        CartaoVidro(tinta = tinta, destacado = vencedor, conteudo = conteudo)
        if (vencedor) {
            SeloVencedor(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = AVANCO_DO_SELO, y = -AVANCO_DO_SELO)
                    .testTag(Marcas.SELO),
            )
        }
    }
}

@Composable
private fun SeloVencedor(modifier: Modifier = Modifier) {
    Pilula(
        texto = stringResource(R.string.selo_vencedor),
        fundo = Tema.cores.vencedor,
        cor = Tema.cores.vencedorTexto,
        modifier = modifier.shadow(3.dp, RoundedCornerShape(percent = 50)),
    )
}

/**
 * Com a fonte grande o selo do canto cresce para baixo e passa a descer sobre a primeira linha do cartão. O título
 * reserva então, no fim da linha, só a largura que o selo ocupa dentro do conteúdo, mais o vão de
 * [ESPACO_ANTES_DO_SELO], e quebra antes dele em vez de passar por baixo. Com a fonte padrão o selo fica acima do
 * conteúdo e nada é reservado, como no web. O selo é medido de novo, sem ser desenhado e fora da semântica, para a
 * reserva acompanhar o tamanho real dele. O web não reserva: desvio declarado na especificação, por decisão do
 * operador em 03/10/2026.
 */
@Composable
private fun ReservaDoSelo() {
    val interno = Tema.espacos.interno
    SeloVencedor(
        Modifier
            .clearAndSetSemantics {}
            .layout { medivel, restricoes ->
                val selo = medivel.measure(Constraints())
                val avanco = AVANCO_DO_SELO.roundToPx()
                val recuo = interno.roundToPx()
                val largura = if (selo.height - avanco > recuo) {
                    (selo.width - avanco - recuo + ESPACO_ANTES_DO_SELO.roundToPx()).coerceIn(0, restricoes.maxWidth)
                } else {
                    0
                }
                layout(largura, 0) {}
            },
    )
}

@Composable
private fun CabecalhoCartao(icone: String, titulo: String, vencedor: Boolean) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(icone, fontSize = 18.sp)
        Spacer(Modifier.width(8.dp))
        Text(titulo, modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, color = Tema.cores.textoNorm)
        if (vencedor) ReservaDoSelo()
    }
}

/**
 * A pílula da melhor opção abaixo dos cartões, como no web: `px-4 py-2 rounded-full bg-green-50 text-green-800 text-sm
 * font-bold border border-green-200` (ResultPanel.tsx), com 38 dp de altura na fonte padrão. Até a 1.0.2 ela usava a
 * [Pilula] dos indicadores, de 10 sp (CALANDR-32). No CSS a borda fica fora do padding; aqui ela é desenhada dentro da
 * caixa, e por isso o recuo tem 1 dp a mais de cada lado. O espaçamento entre letras é o normal do web, e não os 0,5 sp
 * que o `bodyLarge` do Material 3 daria; quando o texto quebra, as linhas ficam centralizadas, como o `text-center` do
 * contêiner no web.
 */
@Composable
private fun PilulaMelhorOpcao(texto: String) {
    val forma = RoundedCornerShape(percent = 50)
    Text(
        text = texto,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.sp,
        textAlign = TextAlign.Center,
        fontWeight = FontWeight.Bold,
        color = Tema.cores.melhorOpcaoTexto,
        modifier = Modifier
            .testTag(Marcas.MELHOR_OPCAO)
            .background(Tema.cores.melhorOpcaoFundo, forma)
            .border(1.dp, Tema.cores.melhorOpcaoBorda, forma)
            .padding(horizontal = 17.dp, vertical = 9.dp),
    )
}

/**
 * O quanto o selo sobressai do cartão, para cima e para a direita. No web, os 10 px de `-top-2.5 -right-2.5` contam de
 * dentro da borda de 1 px do cartão (o deslocamento absoluto do CSS parte da face interna da borda, o limite do
 * padding), ou seja, 9 px além dela.
 */
private val AVANCO_DO_SELO = 9.dp

/** O vão entre o fim do título e o selo, quando o título reserva espaço para ele. */
private val ESPACO_ANTES_DO_SELO = 8.dp

@Composable
private fun Custo(custo: CustoConversao) {
    Linha(
        stringResource(R.string.rotulo_spread),
        Formatacao.percentual(custo.spread) +
            " (R$ " + Formatacao.reais(custo.valorSpread) + ")",
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
        // Como os `inline-block` do web: a pílula que não cabe ao lado da outra desce para a linha de baixo, em vez de
        // ser espremida até partir a palavra (CALANDR-32).
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
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
private fun PainelSensibilidade(bandas: BandasSensibilidade, modalidade: String) {
    CartaoVidro(tinta = Tema.cores.destaqueCartao) {
        TituloPainel(stringResource(R.string.sensibilidade_titulo) + " — " + modalidade, Tema.cores.marcaArdosia)
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
        // Como no web (`ml-auto` no cabeçalho do cenário, CompraReaisPanel.tsx): a pílula é medida primeiro e mantém a
        // largura dela; o título fica com o resto e quebra entre palavras (CALANDR-32).
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
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

/** Os rótulos curtos da melhor opção no web (`useSimulation.ts`), e não os títulos dos cartões (CALANDR-32). */
private fun rotuloDaOpcao(opcao: Opcao): Int = when (opcao) {
    Opcao.CARTAO -> R.string.opcao_cartao
    Opcao.CONTA_GLOBAL -> R.string.opcao_global
    Opcao.SALDO_EXISTENTE -> R.string.opcao_saldo
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
