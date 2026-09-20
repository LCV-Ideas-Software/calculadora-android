/*
 * Copyright © 2026 LCV Ideas & Software
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package dev.lcv.calculadora.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.lcv.calculadora.calc.AnaliseCompraEmReais
import dev.lcv.calculadora.calc.ContextoOperacional
import dev.lcv.calculadora.calc.EntradaSimulacao
import dev.lcv.calculadora.calc.ErroEntrada
import dev.lcv.calculadora.calc.Moedas
import dev.lcv.calculadora.calc.Parametros
import dev.lcv.calculadora.calc.analisarCompraEmReais
import dev.lcv.calculadora.calc.parseNumeroLocalizado
import dev.lcv.calculadora.calc.validarEntrada
import dev.lcv.calculadora.data.simulacao.ResultadoSimulacao
import dev.lcv.calculadora.data.simulacao.Simulador
import java.math.BigDecimal
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Os dois modos do produto web: a simulação de compra em moeda estrangeira e a
 * compra cobrada em reais, que no web vive em endpoint próprio porque a
 * mecânica é outra.
 */
enum class Modo { SIMULACAO, COBRADO_EM_REAIS }

/**
 * Tudo o que a tela mostra. O resultado é limpo a cada mudança de entrada: um
 * número calculado com outros dados ao lado do formulário editado mentiria.
 */
data class EstadoTela(
    val dataCompra: LocalDate,
    val modo: Modo = Modo.SIMULACAO,
    val moeda: String = Moedas.SUPORTADAS.first(),
    val valor: String = "",
    val vetSaldo: String = "",
    val valorFatura: String = "",
    // Sobreposições do formulário do web, em porcentagem; vazio mantém o padrão.
    val spreadCartaoPercent: String = "",
    val iofPercent: String = "",
    val spreadGlobalAbertoPercent: String = "",
    val spreadGlobalFechadoPercent: String = "",
    val erro: ErroEntrada? = null,
    val carregando: Boolean = false,
    val simulacao: ResultadoSimulacao? = null,
    val compraEmReais: AnaliseCompraEmReais? = null,
    /** Principal em reais da análise acima, para o cabeçalho do resultado. */
    val valorEmReais: BigDecimal? = null,
    /** Alguma coisa quebrou fora do previsto — rede, disco. O texto explica. */
    val falhou: Boolean = false,
)

@HiltViewModel
class SimulacaoViewModel @Inject constructor(
    private val simulador: Simulador,
    private val relogio: Clock,
) : ViewModel() {

    private val _estado = MutableStateFlow(EstadoTela(dataCompra = hoje()))
    val estado: StateFlow<EstadoTela> = _estado.asStateFlow()

    /** Hoje em Brasília, que é o fuso do produto — não o do aparelho. */
    private fun hoje(): LocalDate =
        LocalDate.ofInstant(relogio.instant(), ContextoOperacional.FUSO_BRASILIA)

    fun mudarModo(modo: Modo) = limpando { it.copy(modo = modo) }

    fun mudarMoeda(moeda: String) = limpando { it.copy(moeda = moeda) }

    fun mudarValor(valor: String) = limpando { it.copy(valor = valor) }

    fun mudarData(data: LocalDate) = limpando { it.copy(dataCompra = data) }

    fun mudarVetSaldo(vet: String) = limpando { it.copy(vetSaldo = vet) }

    fun mudarValorFatura(valor: String) = limpando { it.copy(valorFatura = valor) }

    fun mudarSpreadCartao(valor: String) = limpando { it.copy(spreadCartaoPercent = valor) }

    fun mudarIof(valor: String) = limpando { it.copy(iofPercent = valor) }

    fun mudarSpreadGlobalAberto(valor: String) = limpando { it.copy(spreadGlobalAbertoPercent = valor) }

    fun mudarSpreadGlobalFechado(valor: String) = limpando { it.copy(spreadGlobalFechadoPercent = valor) }

    /** Toda edição invalida o resultado anterior e o erro anterior. */
    private fun limpando(transformacao: (EstadoTela) -> EstadoTela) {
        _estado.update {
            transformacao(it).copy(
                erro = null,
                simulacao = null,
                compraEmReais = null,
                valorEmReais = null,
                falhou = false,
            )
        }
    }

    /** As quatro sobreposições do formulário; em branco, o padrão do motor. */
    private fun parametrosDe(atual: EstadoTela): Parametros = Parametros.PADRAO.comSobreposicoes(
        spreadCartaoPercent = parseNumeroLocalizado(atual.spreadCartaoPercent),
        iofPercent = parseNumeroLocalizado(atual.iofPercent),
        spreadGlobalAbertoPercent = parseNumeroLocalizado(atual.spreadGlobalAbertoPercent),
        spreadGlobalFechadoPercent = parseNumeroLocalizado(atual.spreadGlobalFechadoPercent),
    )

    fun calcular() {
        val atual = _estado.value
        val cobradoEmReais = atual.modo == Modo.COBRADO_EM_REAIS
        // A data existe sempre nesta tela, porque o seletor começa em hoje;
        // `ErroEntrada.DATA_AUSENTE` fica inalcançável daqui, e a regra segue
        // coberta pelos testes do `:core:calc`.
        val erro = validarEntrada(atual.valor, temDataCompra = true, cobradoEmReais = cobradoEmReais)
        if (erro != null) {
            _estado.update { it.copy(erro = erro) }
            return
        }
        val valor = parseNumeroLocalizado(atual.valor) ?: return
        val parametros = parametrosDe(atual)

        if (cobradoEmReais) {
            val analise = analisarCompraEmReais(
                valorReais = valor,
                iof = parametros.iofCartao,
                spreadCartao = parametros.spreadCartao,
                valorFaturaBrl = parseNumeroLocalizado(atual.valorFatura),
            )
            _estado.update { it.copy(compraEmReais = analise, valorEmReais = valor) }
            return
        }

        val entrada = EntradaSimulacao(
            moeda = atual.moeda,
            valorOriginal = valor,
            dataCompra = atual.dataCompra,
            vetSaldoExistente = parseNumeroLocalizado(atual.vetSaldo),
            parametros = parametros,
        )
        // O carregamento acende antes de lançar a corrotina, e não dentro dela:
        // o `viewModelScope` usa `Dispatchers.Main.immediate`, que faria as duas
        // coisas parecerem a mesma, mas a tela tem de reagir ao toque
        // independentemente de qual despachante estiver instalado.
        _estado.update { it.copy(carregando = true) }
        viewModelScope.launch {
            // Cancelamento não é falha: quando o ViewModel morre, a corrotina
            // tem de morrer junto, e não virar um estado de erro na tela.
            val resultado = try {
                simulador.simular(entrada)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
            _estado.update {
                it.copy(carregando = false, simulacao = resultado, falhou = resultado == null)
            }
        }
    }
}
