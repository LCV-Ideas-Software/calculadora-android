/*
 * Copyright © 2026 LCV Ideas & Software
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package dev.lcv.calculadora.ui

import androidx.compose.foundation.text.input.TextFieldState
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.SavedStateHandleSaveableApi
import androidx.lifecycle.viewmodel.compose.saveable
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.lcv.calculadora.calc.AnaliseCompraEmReais
import dev.lcv.calculadora.calc.ContextoOperacional
import dev.lcv.calculadora.calc.EntradaSimulacao
import dev.lcv.calculadora.calc.ErroEntrada
import dev.lcv.calculadora.calc.Moedas
import dev.lcv.calculadora.calc.Parametros
import dev.lcv.calculadora.calc.analisarCompraEmReais
import dev.lcv.calculadora.calc.validarEntrada
import dev.lcv.calculadora.data.simulacao.ResultadoSimulacao
import dev.lcv.calculadora.data.simulacao.Simulador
import java.math.BigDecimal
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.Job
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
 * Tudo o que a tela mostra, fora os campos numéricos, que são estados próprios
 * do ViewModel. O resultado guarda em [entradas] os números com que foi
 * calculado, e a tela só o mostra enquanto os campos tiverem esses números
 * ([visivelCom]): um número calculado com outros dados ao lado do formulário
 * editado mentiria.
 */
data class EstadoTela(
    val dataCompra: LocalDate,
    val modo: Modo = Modo.SIMULACAO,
    val moeda: String = Moedas.SUPORTADAS.first(),
    /** O texto cru dos sete campos no instante do cálculo, na ordem de [SimulacaoViewModel.entradasAtuais]. */
    val entradas: List<String>? = null,
    val erro: ErroEntrada? = null,
    val carregando: Boolean = false,
    val simulacao: ResultadoSimulacao? = null,
    val compraEmReais: AnaliseCompraEmReais? = null,
    /** Principal em reais da análise acima, para o cabeçalho do resultado. */
    val valorEmReais: BigDecimal? = null,
    /** Alguma coisa quebrou fora do previsto — rede, disco. O texto explica. */
    val falhou: Boolean = false,
) {
    /**
     * O estado como a tela deve mostrá-lo com os campos em [atuais]: inteiro se
     * são os números do cálculo; sem erro, carregamento, resultado nem falha se
     * não são (decisão 13 do operador, CALANDR-27). Voltar aos números
     * calculados mostra o resultado de novo, sem calcular. É uma comparação, e
     * não um evento: dá a mesma resposta a qualquer momento.
     */
    fun visivelCom(atuais: List<String>): EstadoTela =
        if (entradas == atuais) {
            this
        } else {
            copy(erro = null, carregando = false, simulacao = null, compraEmReais = null, valorEmReais = null, falhou = false)
        }
}

@OptIn(SavedStateHandleSaveableApi::class)
@HiltViewModel
class SimulacaoViewModel @Inject constructor(
    private val simulador: Simulador,
    private val relogio: Clock,
    private val salvo: SavedStateHandle,
) : ViewModel() {

    // Os campos numéricos (CALANDR-27): o texto cru em dígitos, num `TextFieldState` que a plataforma edita, salva
    // e restaura com o `TextFieldState.Saver` (texto, seleção e desfazer). Escritos só na thread principal, nunca
    // trocados por outra instância: o salvamento guarda a que o `saveable` criou. As chaves são novas e explícitas:
    // as da 1.0.1 guardavam texto livre, que o `Saver` não lê, e a chave implícita levaria o nome da classe, que o
    // R8 renomeia. O opt-in `SavedStateHandleSaveableApi` é consciente (decisão 5 do operador).
    val campoValor: TextFieldState = salvo.saveable(key = "digitos_valor", saver = TextFieldState.Saver) { TextFieldState() }
    val campoFatura: TextFieldState = salvo.saveable(key = "digitos_fatura", saver = TextFieldState.Saver) { TextFieldState() }
    val campoVet: TextFieldState = salvo.saveable(key = "digitos_vet", saver = TextFieldState.Saver) { TextFieldState() }
    val campoSpreadCartao: TextFieldState =
        salvo.saveable(key = "digitos_spread_cartao", saver = TextFieldState.Saver) { TextFieldState() }
    val campoIof: TextFieldState = salvo.saveable(key = "digitos_iof", saver = TextFieldState.Saver) { TextFieldState() }
    val campoSpreadAberto: TextFieldState =
        salvo.saveable(key = "digitos_spread_aberto", saver = TextFieldState.Saver) { TextFieldState() }
    val campoSpreadFechado: TextFieldState =
        salvo.saveable(key = "digitos_spread_fechado", saver = TextFieldState.Saver) { TextFieldState() }

    private val campos = listOf(campoValor, campoFatura, campoVet, campoSpreadCartao, campoIof, campoSpreadAberto, campoSpreadFechado)

    init {
        // Os valores da 1.0.1 são ignorados e apagados (decisão 6 do operador): migrá-los seria um leitor próprio.
        CHAVES_DA_1_0_1.forEach { salvo.remove<Any?>(it) }
    }

    private var requisicao: Job? = null
    private var revisao = 0L
    private val _estado = MutableStateFlow(EstadoTela(
        dataCompra = salvo.get<String>("data")?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: hoje(),
        modo = salvo.get<String>("modo")?.let { runCatching { Modo.valueOf(it) }.getOrNull() } ?: Modo.SIMULACAO,
        moeda = salvo.get<String>("moeda")?.takeIf(Moedas::isSuportada) ?: Moedas.SUPORTADAS.first(),
    ))
    val estado: StateFlow<EstadoTela> = _estado.asStateFlow()

    /** O texto cru dos sete campos agora, lido direto dos estados. */
    fun entradasAtuais(): List<String> = campos.map { it.text.toString() }

    /** Hoje em Brasília, que é o fuso do produto — não o do aparelho. */
    private fun hoje(): LocalDate =
        LocalDate.ofInstant(relogio.instant(), ContextoOperacional.FUSO_BRASILIA)

    fun mudarModo(modo: Modo) = limpando { it.copy(modo = modo) }

    fun mudarMoeda(moeda: String) = limpando { it.copy(moeda = moeda) }

    fun mudarData(data: LocalDate) = limpando { it.copy(dataCompra = data) }

    /** Data, modo e moeda apagam o resultado e o erro anteriores de vez. */
    private fun limpando(transformacao: (EstadoTela) -> EstadoTela) {
        revisao++
        requisicao?.cancel()
        requisicao = null
        _estado.update {
            transformacao(it).copy(
                entradas = null,
                erro = null,
                carregando = false,
                simulacao = null,
                compraEmReais = null,
                valorEmReais = null,
                falhou = false,
            )
        }
        val e = _estado.value
        salvo["data"] = e.dataCompra.toString()
        salvo["modo"] = e.modo.name
        salvo["moeda"] = e.moeda
    }

    fun calcular() {
        limpando { it }
        val minhaRevisao = revisao
        // Os números são lidos uma vez e guardados com o que o cálculo produzir, inclusive um erro de entrada.
        val entradas = entradasAtuais()
        _estado.update { it.copy(entradas = entradas) }
        fun numero(campo: TextFieldState, tipo: TipoNumerico) = bigDecimalDe(entradas[campos.indexOf(campo)], tipo.casas)

        val atual = _estado.value
        val cobradoEmReais = atual.modo == Modo.COBRADO_EM_REAIS
        val valor = numero(campoValor, TipoNumerico.DINHEIRO)
        val spreadCartao = numero(campoSpreadCartao, TipoNumerico.PERCENTUAL)
        val iof = numero(campoIof, TipoNumerico.PERCENTUAL)
        // No modo cobrado em reais o motor não usa os spreads da Conta Global, e a tela os esconde: valem o padrão.
        val spreadAberto = if (cobradoEmReais) null else numero(campoSpreadAberto, TipoNumerico.PERCENTUAL)
        val spreadFechado = if (cobradoEmReais) null else numero(campoSpreadFechado, TipoNumerico.PERCENTUAL)
        val opcional = if (cobradoEmReais) numero(campoFatura, TipoNumerico.DINHEIRO) else numero(campoVet, TipoNumerico.TAXA)
        // A data existe sempre nesta tela, porque o seletor começa em hoje;
        // `ErroEntrada.DATA_AUSENTE` fica inalcançável daqui, e a regra segue
        // coberta pelos testes do `:core:calc`.
        val erro = validarEntrada(valor, temDataCompra = true, cobradoEmReais = cobradoEmReais)
            ?: when {
                !cobradoEmReais && atual.dataCompra > hoje() -> ErroEntrada.DATA_FUTURA
                listOfNotNull(spreadCartao, iof, spreadAberto, spreadFechado)
                    .any { it < BigDecimal.ZERO || it > CEM_POR_CENTO } -> ErroEntrada.PARAMETRO_INVALIDO
                opcional != null && opcional.signum() <= 0 -> ErroEntrada.OPCIONAL_INVALIDO
                else -> null
            }
        if (erro != null || valor == null) {
            _estado.update { it.copy(erro = erro ?: ErroEntrada.VALOR_INVALIDO) }
            return
        }
        // Em branco, o padrão do motor.
        val parametros = Parametros.PADRAO.comSobreposicoes(
            spreadCartaoPercent = spreadCartao,
            iofPercent = iof,
            spreadGlobalAbertoPercent = spreadAberto,
            spreadGlobalFechadoPercent = spreadFechado,
        )

        if (cobradoEmReais) {
            val analise = analisarCompraEmReais(
                valorReais = valor,
                iof = parametros.iofCartao,
                spreadCartao = parametros.spreadCartao,
                valorFaturaBrl = opcional,
            )
            _estado.update { it.copy(compraEmReais = analise, valorEmReais = valor) }
            return
        }

        val entrada = EntradaSimulacao(
            moeda = atual.moeda,
            valorOriginal = valor,
            dataCompra = atual.dataCompra,
            vetSaldoExistente = opcional,
            parametros = parametros,
        )
        // O carregamento acende antes de lançar a corrotina, e não dentro dela:
        // o `viewModelScope` usa `Dispatchers.Main.immediate`, que faria as duas
        // coisas parecerem a mesma, mas a tela tem de reagir ao toque
        // independentemente de qual despachante estiver instalado.
        _estado.update { it.copy(carregando = true) }
        // Editar um número não cancela a requisição: o resultado chega marcado com os números dele e só aparece se
        // os campos voltarem a eles. Um novo cálculo, a data, o modo e a moeda cancelam (`limpando`).
        requisicao = viewModelScope.launch {
            // Cancelamento não é falha: quando o ViewModel morre, a corrotina
            // tem de morrer junto, e não virar um estado de erro na tela.
            val resultado = try {
                simulador.simular(entrada)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
            if (minhaRevisao == revisao) {
                _estado.update { it.copy(carregando = false, simulacao = resultado, falhou = resultado == null) }
            }
        }
    }

    private companion object {
        /** As chaves dos campos numéricos na 1.0.1, com texto livre. */
        val CHAVES_DA_1_0_1 = listOf("valor", "vet", "fatura", "spreadCartao", "iof", "spreadAberto", "spreadFechado")
        val CEM_POR_CENTO = BigDecimal("100")
    }
}
