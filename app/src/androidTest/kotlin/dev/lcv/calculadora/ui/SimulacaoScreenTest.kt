/*
 * Copyright © 2026 LCV Ideas & Software
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package dev.lcv.calculadora.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextInputSelection
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.withKeyDown
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.lcv.calculadora.R
import dev.lcv.calculadora.calc.ContextoOperacional
import dev.lcv.calculadora.calc.Formatacao
import dev.lcv.calculadora.calc.Parametros
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * O que se prova aqui é o que só um aparelho prova: que a tela compõe, que cada campo responde pelos caminhos reais
 * de entrada (tecla física, ações de semântica que a acessibilidade e o preenchimento automático usam) e que o
 * resultado chega à árvore e a deixa quando os números mudam. As regras puras são dos testes de JVM; a aritmética é
 * do `:core:calc`.
 *
 * As linhas A1 a A20 são as da seção 5 da especificação v2.7 da CALANDR-27. Convenções da seção: o foco vem de
 * `requestFocus()` no começo de cada sequência e depois de tocar outro nó, porque a tecla não pede foco; nunca se
 * toca o campo no meio de uma sequência, porque o toque põe o cursor; o texto cru é `InputText`, o exibido é
 * `EditableText` e o cursor é `TextSelectionRange`, nas coordenadas do exibido.
 */
@RunWith(AndroidJUnit4::class)
class SimulacaoScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val contexto = InstrumentationRegistry.getInstrumentation().targetContext

    /** A tela no mesmo contêiner rolável do aplicativo (`CalculadoraApp`). */
    private fun montar(salvo: SavedStateHandle = SavedStateHandle()): SimulacaoViewModel {
        val vm = viewModelEmMemoria(salvo)
        compose.setContent {
            CalculadoraTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) { SimulacaoScreen(vm) }
            }
        }
        return vm
    }

    private fun campo(marca: String) = compose.onNodeWithTag(marca)

    private fun SemanticsNodeInteraction.cru(esperado: String) =
        assert(SemanticsMatcher.expectValue(SemanticsProperties.InputText, AnnotatedString(esperado)))

    private fun SemanticsNodeInteraction.exibe(esperado: String) =
        assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString(esperado)))

    private fun SemanticsNodeInteraction.selecao(esperada: TextRange) =
        assert(SemanticsMatcher.expectValue(SemanticsProperties.TextSelectionRange, esperada))

    /** Cru, exibido e o cursor no fim do exibido. */
    private fun SemanticsNodeInteraction.mostra(cru: String, exibido: String) {
        cru(cru)
        exibe(exibido)
        selecao(TextRange(exibido.length))
    }

    private fun tecla(digito: Char): Key = when (digito) {
        '0' -> Key.Zero
        '1' -> Key.One
        '2' -> Key.Two
        '3' -> Key.Three
        '4' -> Key.Four
        '5' -> Key.Five
        '6' -> Key.Six
        '7' -> Key.Seven
        '8' -> Key.Eight
        '9' -> Key.Nine
        else -> error("não é dígito: $digito")
    }

    /** Teclas físicas, todas num mesmo bloco de entrada (sem recomposição entre elas). */
    private fun SemanticsNodeInteraction.teclas(teclas: List<Key>): SemanticsNodeInteraction {
        performKeyInput { teclas.forEach { pressKey(it) } }
        compose.waitForIdle()
        return this
    }

    private fun SemanticsNodeInteraction.teclar(tecla: Key) = teclas(listOf(tecla))

    /** Cada dígito no próprio bloco de entrada, como uma pessoa digitando. */
    private fun SemanticsNodeInteraction.digitar(digitos: String): SemanticsNodeInteraction {
        digitos.forEach { teclar(tecla(it)) }
        return this
    }

    private fun SemanticsNodeInteraction.selecionarTudo(): SemanticsNodeInteraction {
        performKeyInput { withKeyDown(Key.CtrlLeft) { pressKey(Key.A) } }
        compose.waitForIdle()
        return this
    }

    private fun SemanticsNodeInteraction.desfazer(): SemanticsNodeInteraction {
        performKeyInput { withKeyDown(Key.CtrlLeft) { pressKey(Key.Z) } }
        compose.waitForIdle()
        return this
    }

    private fun SemanticsNodeInteraction.focar(): SemanticsNodeInteraction {
        performScrollTo()
        requestFocus()
        compose.waitForIdle()
        return this
    }

    /** O estado de partida de uma variante, escrito pelo próprio teste na thread principal. */
    private fun partirDe(estado: TextFieldState, cru: String) {
        compose.runOnIdle { estado.setTextAndPlaceCursorAtEnd(cru) }
    }

    private fun calcular() {
        campo(Marcas.CALCULAR).performScrollTo().performClick()
        compose.waitForIdle()
    }

    private fun assertResultadoVisivel() {
        // Espera o cálculo, mas quem reprova é a asserção abaixo, com mensagem, e não o tempo esgotado.
        runCatching {
            compose.waitUntil(TEMPO_LIMITE) {
                compose.onAllNodesWithTag(Marcas.RESULTADO).fetchSemanticsNodes().isNotEmpty()
            }
        }
        campo(Marcas.RESULTADO).performScrollTo().assertIsDisplayed()
    }

    private fun assertResultadoOculto() {
        compose.waitForIdle()
        compose.onAllNodesWithTag(Marcas.RESULTADO).assertCountEquals(0)
    }

    private fun abrirParametros() {
        compose.onNodeWithText("Personalizar parâmetros", substring = true).performScrollTo().performClick()
        compose.waitForIdle()
    }

    private fun marcarDcc() {
        campo(Marcas.DCC).performClick()
        compose.waitForIdle()
    }

    // Os três caminhos de ponta a ponta do formulário.

    @Test
    fun formularioApareceComOsControlesDoProdutoWeb() {
        montar()

        campo(Marcas.DCC).assertIsDisplayed()
        campo(Marcas.VALOR).assertIsDisplayed().assert(hasText("💵 Valor em USD"))
        campo(Marcas.CALCULAR).assertIsDisplayed()
    }

    @Test
    fun calcularComValorValidoMostraOResultado() {
        montar()

        campo(Marcas.VALOR).focar().digitar("100000")
        calcular()

        assertResultadoVisivel()
    }

    @Test
    fun marcarDccTrocaOFormularioParaOsCenariosEmReais() {
        montar()

        marcarDcc()
        campo(Marcas.VALOR).focar().digitar("100000")
        calcular()
        compose.waitUntil(TEMPO_LIMITE) {
            compose.onAllNodesWithTag(Marcas.CENARIOS).fetchSemanticsNodes().isNotEmpty()
        }

        campo(Marcas.CENARIOS).performScrollTo().assertIsDisplayed()
    }

    // A1 — os dígitos entram pela direita, tecla a tecla, nos sete campos.

    private val duasCasas = listOf("0,01", "0,12", "1,23", "12,34", "123,45", "1.234,56")
    private val quatroCasas = listOf("0,0001", "0,0012", "0,0123", "0,1234", "1,2345", "12,3456", "123,4567", "1.234,5678")

    private fun SemanticsNodeInteraction.digitarConferindo(digitos: String, exibidos: List<String>) {
        focar()
        digitos.forEachIndexed { indice, digito ->
            teclar(tecla(digito))
            mostra(digitos.take(indice + 1), exibidos[indice])
        }
    }

    @Test
    fun a1ValorEVetEntramPelaDireita() {
        montar()

        campo(Marcas.VALOR).digitarConferindo("123456", duasCasas)
        campo(Marcas.VET_SALDO).digitarConferindo("12345678", quatroCasas)
    }

    @Test
    fun a1OsQuatroParametrosEntramPelaDireita() {
        montar()
        abrirParametros()

        for (marca in listOf(Marcas.SPREAD_CARTAO, Marcas.IOF, Marcas.SPREAD_ABERTO, Marcas.SPREAD_FECHADO)) {
            campo(marca).digitarConferindo("12345", duasCasas.take(5))
        }
    }

    @Test
    fun a1AFaturaEntraPelaDireita() {
        montar()
        marcarDcc()

        campo(Marcas.FATURA).digitarConferindo("123456", duasCasas)
    }

    // A2 — várias teclas sem recomposição entre elas.

    @Test
    fun a2VariasTeclasNumBlocoSo() {
        montar()

        val valor = campo(Marcas.VALOR).focar()
        valor.teclas(listOf(Key.One, Key.Zero, Key.Zero))
        valor.mostra("100", "1,00")
        valor.teclas(listOf(Key.Backspace, Key.Backspace, Key.Backspace))
        valor.mostra("", "")
    }

    // A3 — apagar até esvaziar é o padrão.

    @Test
    fun a3ApagarAteEsvaziarEOPadrao() {
        val vm = montar()

        val valor = campo(Marcas.VALOR).focar()
        valor.digitar("1").teclar(Key.Backspace)
        valor.mostra("", "")

        valor.focar().digitar("100000")
        abrirParametros()
        campo(Marcas.SPREAD_CARTAO).focar().digitar("1").teclar(Key.Backspace)
        campo(Marcas.SPREAD_CARTAO).mostra("", "")
        calcular()
        assertResultadoVisivel()
        val parametros = compose.runOnIdle { vm.estado.value.simulacao!!.simulacao.entrada.parametros }
        assertEquals("o spread vazio é o padrão", Parametros.PADRAO, parametros)
    }

    // A4 — zero explícito não é o padrão.

    @Test
    fun a4ZeroExplicitoNoIof() {
        val vm = montar()
        marcarDcc()

        campo(Marcas.VALOR).focar().digitar("100000")
        abrirParametros()
        campo(Marcas.IOF).focar().digitar("0")
        campo(Marcas.IOF).mostra("0", "0,00")
        // O modo cobrado em reais calcula sem rede, dentro do próprio `calcular()`.
        calcular()
        val total = compose.runOnIdle { vm.estado.value.compraEmReais!!.dccPura.totalBrl }
        assertEquals("IOF de 0%, e não os 3,5% do padrão", BigDecimal("1000.00"), total)
    }

    // A5 — um cursor fora do fim volta para o fim, e a tecla age no fim.

    private fun a5(vm: SimulacaoViewModel, porCursor: SemanticsNodeInteraction.(Int) -> Unit) {
        val valor = campo(Marcas.VALOR)
        for ((acao, esperado) in listOf(Key.One to ("1001" to "10,01"), Key.Backspace to ("10" to "0,10"))) {
            for (posicao in 0..2) {
                partirDe(vm.campoValor, "100")
                valor.focar()
                valor.porCursor(posicao)
                compose.waitForIdle()
                valor.selecao(TextRange("1,00".length))
                valor.teclar(acao)
                valor.mostra(esperado.first, esperado.second)
            }
        }
    }

    @Test
    fun a5CursorPelaSemanticaVoltaParaOFim() {
        val vm = montar()
        a5(vm) { posicao -> performTextInputSelection(TextRange(posicao)) }
    }

    @Test
    fun a5CursorPelaSetaVoltaParaOFim() {
        val vm = montar()
        a5(vm) { _ -> teclar(Key.DirectionLeft) }
    }

    // A5b — seleção parcial vira cursor no fim (decisão 3).

    @Test
    fun a5bSelecaoParcialViraCursorNoFim() {
        montar()

        val valor = campo(Marcas.VALOR).focar().digitar("12345")
        calcular()
        assertResultadoVisivel()
        valor.focar().performTextInputSelection(TextRange(1, 3))
        compose.waitForIdle()
        valor.selecao(TextRange("123,45".length))
        valor.cru("12345")
        assertResultadoVisivel()
    }

    // A6 — selecionar tudo e uma tecla troca o valor.

    @Test
    fun a6SelecionarTudoETeclaTrocaOValor() {
        val vm = montar()

        val valor = campo(Marcas.VALOR)
        for ((acao, esperado) in listOf(Key.Zero to ("0" to "0,00"), Key.Seven to ("7" to "0,07"), Key.Backspace to ("" to ""))) {
            partirDe(vm.campoValor, "123")
            valor.focar().selecionarTudo()
            valor.selecao(TextRange(0, "1,23".length))
            valor.teclar(acao)
            valor.mostra(esperado.first, esperado.second)
        }
    }

    // A7 — selecionar tudo pela semântica e inserir no cursor.

    @Test
    fun a7SetSelectionEInsertTextAtCursor() {
        val vm = montar()

        partirDe(vm.campoValor, "12")
        val valor = campo(Marcas.VALOR).focar()
        valor.performTextInputSelection(TextRange(0, 2))
        valor.performTextInput("0")
        compose.waitForIdle()
        valor.mostra("0", "0,00")
    }

    // A8 — uma tecla recusada guarda o valor e o resultado.

    @Test
    fun a8TeclaRecusadaGuardaOValorEOResultado() {
        montar()
        abrirParametros()

        val campos = listOf(
            Triple(Marcas.VALOR, "12345678901234", "123.456.789.012,34"),
            Triple(Marcas.VET_SALDO, "12345678", "1.234,5678"),
            Triple(Marcas.SPREAD_CARTAO, "10000", "100,00"),
        )
        for ((marca, digitos, _) in campos) campo(marca).focar().digitar(digitos)
        calcular()
        assertResultadoVisivel()

        for ((marca, digitos, exibido) in campos) {
            val no = campo(marca).focar()
            no.teclar(Key.A)
            no.mostra(digitos, exibido)
            no.teclar(Key.Nine)
            no.mostra(digitos, exibido)
        }
        assertResultadoVisivel()
    }

    // A8b — selecionar tudo e colar os mesmos dígitos formatados não muda nada (regra 4.3.5).

    @Test
    fun a8bOsMesmosDigitosFormatadosSobreTudo() {
        montar()

        val valor = campo(Marcas.VALOR).focar().digitar("550")
        calcular()
        assertResultadoVisivel()
        valor.focar().selecionarTudo()
        valor.performTextInput("R$ 5,50")
        compose.waitForIdle()
        valor.cru("550")
        valor.selecao(TextRange(0, "5,50".length))
        assertResultadoVisivel()
    }

    // A8c — uma mudança só de seleção guarda o resultado.

    @Test
    fun a8cSoSelecaoGuardaOResultado() {
        montar()

        val valor = campo(Marcas.VALOR).focar().digitar("550")
        calcular()
        assertResultadoVisivel()
        valor.focar().selecionarTudo()
        compose.waitForIdle()
        assertResultadoVisivel()
        valor.cru("550")
    }

    // A8d — redigitar o mesmo dígito sobre a seleção é edição de conteúdo recusada, e não grava desfazer.

    @Test
    fun a8dRedigitarOMesmoDigitoSobreASelecao() {
        montar()

        val valor = campo(Marcas.VALOR).focar().digitar("5")
        valor.selecionarTudo().teclar(Key.Five)
        valor.cru("5")
        valor.selecao(TextRange(0, "0,05".length))
        valor.desfazer()
        valor.cru("")
    }

    // A9 — uma letra sobre a seleção é recusada (decisão 2).

    @Test
    fun a9LetraSobreASelecaoERecusada() {
        montar()

        val valor = campo(Marcas.VALOR).focar().digitar("550")
        calcular()
        assertResultadoVisivel()
        valor.focar().selecionarTudo().teclar(Key.A)
        valor.cru("550")
        valor.selecao(TextRange(0, "5,50".length))
        assertResultadoVisivel()
    }

    // A10 — uma inserção formatada mais longa que o limite em caracteres, mas dentro dele em dígitos.

    @Test
    fun a10InsercaoFormatadaDentroDoLimiteEmDigitos() {
        val vm = montar()
        abrirParametros()

        for ((marca, texto, esperado) in listOf(
            Triple(Marcas.VALOR, "R$ 1.234.567,89", "123456789" to "1.234.567,89"),
            Triple(Marcas.SPREAD_CARTAO, "R$ 12,34", "1234" to "12,34"),
            Triple(Marcas.VET_SALDO, "R$ 1,2345", "12345" to "1,2345"),
        )) {
            val no = campo(marca).focar()
            no.performTextInput(texto)
            compose.waitForIdle()
            no.mostra(esperado.first, esperado.second)
        }

        // Só os dígitos contam (decisão 1): com o cursor no fim, entram depois dos que havia.
        val valor = campo(Marcas.VALOR)
        partirDe(vm.campoValor, "550")
        valor.focar().performTextInput("R$ 1,00")
        compose.waitForIdle()
        valor.mostra("550100", "5.501,00")
        // Sobre tudo selecionado, trocam o valor.
        partirDe(vm.campoValor, "550")
        valor.focar().selecionarTudo().performTextInput("R$ 1,00")
        compose.waitForIdle()
        valor.mostra("100", "1,00")
    }

    // A11 — uma inserção além do limite é recusada inteira.

    @Test
    fun a11InsercaoAlemDoLimiteERecusadaInteira() {
        montar()
        abrirParametros()

        campo(Marcas.VALOR).focar().performTextInput("123456789012345")
        campo(Marcas.SPREAD_CARTAO).focar().performTextInput("123456")
        compose.waitForIdle()
        campo(Marcas.VALOR).mostra("", "")
        campo(Marcas.SPREAD_CARTAO).mostra("", "")
    }

    // A12 — SetText, o caminho da acessibilidade e do preenchimento automático (só esse caminho).

    @Test
    fun a12SetTextDaAcessibilidade() {
        val vm = montar()

        val valor = campo(Marcas.VALOR)
        valor.focar().performTextReplacement("1.234,56")
        compose.waitForIdle()
        valor.mostra("123456", "1.234,56")
        partirDe(vm.campoValor, "")
        valor.focar().performTextReplacement("100")
        compose.waitForIdle()
        valor.mostra("100", "1,00")
    }

    // A13 — desfazer volta ao número calculado, e o resultado volta sem calcular (decisão 13).

    @Test
    fun a13DesfazerVoltaAoNumeroCalculado() {
        montar()

        val valor = campo(Marcas.VALOR).focar().digitar("550")
        calcular()
        assertResultadoVisivel()
        valor.focar().digitar("1")
        valor.cru("5501")
        assertResultadoOculto()
        valor.desfazer()
        valor.mostra("550", "5,50")
        assertResultadoVisivel()
    }

    // A14 — o padrão aparece com o campo parado e vazio (decisão 7).

    private fun padraoDe(marca: String): String = when (marca) {
        Marcas.SPREAD_CARTAO -> contexto.getString(R.string.parametro_padrao, Formatacao.percentual(Parametros.SPREAD_CARTAO_PADRAO))
        Marcas.IOF -> contexto.getString(R.string.parametro_padrao_iof, Formatacao.percentual(Parametros.IOF_CARTAO_PADRAO))
        Marcas.SPREAD_ABERTO -> contexto.getString(
            R.string.parametro_padrao_aberto,
            Formatacao.percentual(Parametros.SPREAD_GLOBAL_ABERTO_PADRAO),
            ContextoOperacional.ABERTURA_MERCADO_HORA,
            ContextoOperacional.FECHAMENTO_MERCADO_HORA,
        )
        Marcas.SPREAD_FECHADO -> contexto.getString(
            R.string.parametro_padrao_fechado,
            Formatacao.percentual(Parametros.SPREAD_GLOBAL_FECHADO_PADRAO),
            ContextoOperacional.ABERTURA_MERCADO_HORA,
            ContextoOperacional.FECHAMENTO_MERCADO_HORA,
        )
        else -> error("não é parâmetro: $marca")
    }

    private fun assertPadraoVisivel(marca: String) {
        // Rola até o campo, e não até o texto: abaixo da área visível, o nó do texto (árvore não mesclada) tem limites
        // recortados, e a rolagem por ele não o alcança (medido em 03/10/2026, nos dois últimos parâmetros).
        campo(marca).performScrollTo()
        compose.onNode(
            SemanticsMatcher.expectValue(SemanticsProperties.Text, listOf(AnnotatedString(padraoDe(marca)))) and
                hasAnyAncestor(hasTestTag(marca)),
            useUnmergedTree = true,
        ).assertIsDisplayed()
    }

    @Test
    fun a14OPadraoApareceComOCampoParado() {
        montar()
        abrirParametros()

        for (marca in listOf(Marcas.SPREAD_CARTAO, Marcas.IOF, Marcas.SPREAD_ABERTO, Marcas.SPREAD_FECHADO)) {
            assertPadraoVisivel(marca)
        }
    }

    // Decisão do operador de 03/10/2026: nos campos de valor, o exemplo ("1.000,00") só aparece com o campo em foco,
    // para não ser lido como um valor digitado; o padrão parado é só dos parâmetros (A14).
    @Test
    fun oExemploDoValorSoApareceComFoco() {
        montar()

        val exemplo = contexto.getString(R.string.exemplo_valor)
        fun exemploNoValor() = compose.onAllNodes(
            SemanticsMatcher.expectValue(SemanticsProperties.Text, listOf(AnnotatedString(exemplo))) and
                hasAnyAncestor(hasTestTag(Marcas.VALOR)),
            useUnmergedTree = true,
        )
        exemploNoValor().assertCountEquals(0)
        campo(Marcas.VALOR).focar()
        exemploNoValor().assertCountEquals(1)
    }

    // A17 — as chaves da 1.0.1 são ignoradas (decisão 6).

    @Test
    fun a17AsChavesDaVersaoAnteriorSaoIgnoradas() {
        montar(SavedStateHandle(mapOf("valor" to "100", "iof" to "12.345678")))

        val valor = campo(Marcas.VALOR).focar().digitar("1")
        valor.mostra("1", "0,01")
        abrirParametros()
        campo(Marcas.IOF).cru("")
        assertPadraoVisivel(Marcas.IOF)
    }

    // A20 — os limites dos percentuais.

    @Test
    fun a20CemPorCentoEOLimite() {
        montar()

        campo(Marcas.VALOR).focar().digitar("100000")
        abrirParametros()
        val spread = campo(Marcas.SPREAD_CARTAO).focar().digitar("10000")
        spread.exibe("100,00")
        calcular()
        assertResultadoVisivel()

        spread.focar().selecionarTudo().digitar("10001")
        spread.exibe("100,01")
        calcular()
        compose.onNodeWithText(contexto.getString(R.string.erro_parametro_invalido)).performScrollTo().assertIsDisplayed()
    }

    // Decisão 9 — no modo cobrado em reais, os spreads da Conta Global somem, porque o motor os ignora.

    @Test
    fun noModoCobradoEmReaisOsSpreadsDaContaGlobalSomem() {
        montar()
        marcarDcc()
        abrirParametros()

        campo(Marcas.SPREAD_CARTAO).performScrollTo().assertIsDisplayed()
        campo(Marcas.IOF).performScrollTo().assertIsDisplayed()
        compose.onAllNodesWithTag(Marcas.SPREAD_ABERTO).assertCountEquals(0)
        compose.onAllNodesWithTag(Marcas.SPREAD_FECHADO).assertCountEquals(0)
    }
}

/** Cinco segundos cobrem a composição e o `viewModelScope` sem rede. */
private const val TEMPO_LIMITE = 5_000L
