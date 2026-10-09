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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.InspectableValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextInputSelection
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.then
import androidx.compose.ui.test.withKeyDown
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.lifecycle.SavedStateHandle
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.lcv.calculadora.R
import dev.lcv.calculadora.calc.ContextoOperacional
import dev.lcv.calculadora.calc.Formatacao
import dev.lcv.calculadora.calc.Opcao
import dev.lcv.calculadora.calc.Parametros
import dev.lcv.calculadora.calc.melhorOpcao
import java.math.BigDecimal
import kotlin.math.ceil
import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

    // CALANDR-32 — a regra das linhas: o que precisa da própria largura (selo, pílula, valor) nunca é espremido pelo
    // texto ao lado, e nada quebra no meio de uma palavra. Os casos de fonte e de largura rodam o aplicativo inteiro
    // (`CalculadoraApp`, com a barra, a rolagem e as margens reais) num aparelho simulado pela API oficial de teste; a
    // altura da pílula da melhor opção é medida só com a fonte fixada, sem tela forçada, e o teste dela diz por quê. O
    // controle, com a fonte padrão numa tela larga, passa antes e depois da correção: é ele que prova que as asserções
    // não reprovam uma tela correta.

    /** O aplicativo inteiro dentro de um aparelho simulado, com o view model em memória. */
    private fun montarNoAparelho(
        aparelho: DeviceConfigurationOverride,
        vm: SimulacaoViewModel = viewModelEmMemoria(),
    ): SimulacaoViewModel {
        compose.setContent {
            DeviceConfigurationOverride(aparelho) {
                CalculadoraTheme { CalculadoraApp(vm) }
            }
        }
        return vm
    }

    /** O vencedor que o motor escolheu: pré-condição de cada caso, e não o assunto dele. */
    private fun vencedor(vm: SimulacaoViewModel): Opcao? =
        compose.runOnIdle { vm.estado.value.simulacao?.let { melhorOpcao(it.simulacao) } }

    /** O `TextLayoutResult` do nó, pela ação oficial `GetTextLayoutResult` (o resultado é o primeiro da lista). */
    private fun SemanticsNodeInteraction.layoutDoTexto(): TextLayoutResult {
        val resultados = mutableListOf<TextLayoutResult>()
        performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(resultados) }
        return resultados.first()
    }

    /** O texto aparece inteiro, sem reticências, e cada quebra de linha cai entre palavras. */
    private fun assertQuebraSoEntrePalavras(layout: TextLayoutResult, texto: String, nome: String) {
        val ultima = layout.lineCount - 1
        assertEquals("$nome: caracteres visíveis", texto.length, layout.getLineEnd(ultima, visibleEnd = true))
        for (linha in 0..ultima) {
            assertFalse("$nome: a linha $linha termina em reticências", layout.isLineEllipsized(linha))
        }
        for (linha in 0 until ultima) {
            val fim = layout.getLineEnd(linha)
            assertTrue(
                "$nome: a linha $linha quebra no meio de uma palavra: \"${texto.substring(0, fim)}|${texto.substring(fim)}\"",
                texto[fim - 1].isWhitespace() || texto[fim].isWhitespace(),
            )
        }
    }

    /** Um texto achado pelo conteúdo, rolado até a vista, inteiro e quebrado só entre palavras. */
    private fun assertTextoInteiro(texto: String, nome: String): TextLayoutResult {
        val no = compose.onNodeWithText(texto, useUnmergedTree = true).performScrollTo()
        val layout = no.layoutDoTexto()
        assertQuebraSoEntrePalavras(layout, texto, nome)
        no.assertIsDisplayed()
        return layout
    }

    /** O selo ou a pílula numa linha só, inteiro e visível. A contagem vem antes, com o número na mensagem. */
    private fun assertNumaLinha(texto: String, nome: String) {
        compose.onAllNodesWithText(texto, useUnmergedTree = true).assertCountEquals(1)
        val no = compose.onNodeWithText(texto, useUnmergedTree = true).performScrollTo()
        assertEquals("linhas de $nome \"$texto\"", 1, no.layoutDoTexto().lineCount)
        assertQuebraSoEntrePalavras(no.layoutDoTexto(), texto, nome)
        no.assertIsDisplayed()
    }

    /** A caixa do nó em pixels da raiz, sem recorte: a posição e o tamanho do próprio layout. */
    private fun caixa(no: SemanticsNodeInteraction): Rect {
        val semantica = no.fetchSemanticsNode()
        return Rect(semantica.positionInRoot, semantica.size.toSize())
    }

    /**
     * O selo do canto não cobre a primeira linha do título do próprio cartão nem invade o cartão de cima. A caixa do
     * selo inclui o fundo e o recuo da pílula; a da linha vem do layout do texto, sobre a posição do título.
     */
    private fun assertSeloSemSobreposicao(textoTitulo: String, cartaoDeCima: String) {
        val selo = caixa(compose.onNodeWithTag(Marcas.SELO, useUnmergedTree = true))
        val titulo = compose.onNodeWithText(textoTitulo, useUnmergedTree = true)
        val noTitulo = caixa(titulo)
        val layout = titulo.layoutDoTexto()
        val primeiraLinha = Rect(
            noTitulo.left + layout.getLineLeft(0),
            noTitulo.top + layout.getLineTop(0),
            noTitulo.left + layout.getLineRight(0),
            noTitulo.top + layout.getLineBottom(0),
        )
        assertFalse("o selo $selo cobre a primeira linha do título $primeiraLinha", selo.overlaps(primeiraLinha))
        val acima = caixa(compose.onNodeWithTag(cartaoDeCima))
        assertFalse("o selo $selo invade o cartão de cima $acima", selo.overlaps(acima))
    }

    /** O selo fica sobre o canto superior direito do cartão e avança para fora dele, como o `-top-2.5 -right-2.5` do web. */
    private fun assertSeloNoCanto(cartao: String) {
        val selo = caixa(compose.onNodeWithTag(Marcas.SELO, useUnmergedTree = true))
        val doCartao = caixa(compose.onNodeWithTag(cartao))
        assertTrue("o selo $selo não avança sobre o topo do cartão $doCartao", selo.top < doCartao.top)
        assertTrue("o selo $selo não avança sobre a direita do cartão $doCartao", selo.right > doCartao.right)
        assertTrue("o selo $selo não fica na metade direita do cartão $doCartao", selo.left > doCartao.center.x)
    }

    private fun calcularNoAparelho(aparelho: DeviceConfigurationOverride, valor: String, vet: String? = null): SimulacaoViewModel {
        val vm = montarNoAparelho(aparelho)
        partirDe(vm.campoValor, valor)
        vet?.let { partirDe(vm.campoVet, it) }
        calcular()
        assertResultadoVisivel()
        return vm
    }

    @Test
    fun oSeloDoVencedorFicaNumaLinhaComAFonteNoMaximoEmTelaEstreita() {
        // US$ 1.000,00 com as fontes em memória: R$ 5.608,03 na Conta Global contra R$ 5.896,40 no cartão.
        val vm = calcularNoAparelho(TELA_ESTREITA_FONTE_MAXIMA, valor = "100000")
        assertEquals("pré-condição: a Conta Global vence", Opcao.CONTA_GLOBAL, vencedor(vm))

        assertNumaLinha(contexto.getString(R.string.selo_vencedor), "o selo")
        assertTextoInteiro(contexto.getString(R.string.cartao_global), "o título do vencedor")
        assertSeloSemSobreposicao(contexto.getString(R.string.cartao_global), cartaoDeCima = Marcas.CARTAO_CARTAO)
    }

    @Test
    fun oSeloDoVencedorFicaNumaLinhaQuandoOSaldoExistenteVenceComAFonteUmPoucoMaior() {
        // O título mais longo dos três, com a fonte um pouco acima da padrão: VET de 5,0000 dá R$ 5.000,00 no saldo.
        val vm = calcularNoAparelho(PIXEL_2_FONTE_UM_POUCO_MAIOR, valor = "100000", vet = "50000")
        assertEquals("pré-condição: o saldo existente vence", Opcao.SALDO_EXISTENTE, vencedor(vm))

        assertNumaLinha(contexto.getString(R.string.selo_vencedor), "o selo")
        assertTextoInteiro(contexto.getString(R.string.cartao_saldo), "o título do vencedor")
        assertSeloSemSobreposicao(contexto.getString(R.string.cartao_saldo), cartaoDeCima = Marcas.CARTAO_GLOBAL)
    }

    @Test
    fun comAFontePadraoEmTelaEstreitaOSeloFicaNoCantoEOTituloNumaLinha() {
        // Como no web: com a fonte padrão o selo fica acima do conteúdo, e o título usa a largura inteira.
        val vm = calcularNoAparelho(TELA_ESTREITA_FONTE_PADRAO, valor = "100000")
        assertEquals("pré-condição: a Conta Global vence", Opcao.CONTA_GLOBAL, vencedor(vm))

        assertNumaLinha(contexto.getString(R.string.selo_vencedor), "o selo")
        assertEquals(
            "linhas do título do vencedor",
            1,
            assertTextoInteiro(contexto.getString(R.string.cartao_global), "o título do vencedor").lineCount,
        )
        assertSeloSemSobreposicao(contexto.getString(R.string.cartao_global), cartaoDeCima = Marcas.CARTAO_CARTAO)
        assertSeloNoCanto(Marcas.CARTAO_GLOBAL)
    }

    @Test
    fun aPilulaDoCenarioProvavelFicaNumaLinhaComAFonteGrande() {
        val vm = montarNoAparelho(PIXEL_2_FONTE_GRANDE)
        marcarDcc()
        // R$ 100,00 cobrados como R$ 109,19: acréscimo de 9,19%, o da dupla conversão — (1 + 5,5%) × (1 + 3,5%) − 1.
        partirDe(vm.campoValor, "10000")
        partirDe(vm.campoFatura, "10919")
        calcular()
        runCatching {
            compose.waitUntil(TEMPO_LIMITE) { compose.onAllNodesWithTag(Marcas.CENARIOS).fetchSemanticsNodes().isNotEmpty() }
        }

        assertNumaLinha(contexto.getString(R.string.pilula_provavel), "a pílula")
        assertTextoInteiro("Dupla conversão (spread + IOF)", "o título do cenário provável")
    }

    @Test
    fun osTotaisNaoQuebramNoMeioDoNumeroComAFonteNoMaximoEmTelaEstreita() {
        val vm = calcularNoAparelho(TELA_ESTREITA_FONTE_MAXIMA, valor = "100000")
        assertEquals("pré-condição: a Conta Global vence", Opcao.CONTA_GLOBAL, vencedor(vm))

        assertTextoInteiro("R$ 5.896,40", "o total do cartão")
        assertTextoInteiro("R$ 5.608,03", "o total da Conta Global")
    }

    @Test
    fun oBotaoDaDataFicaNumaLinhaComAFonteNoMaximoEmTelaEstreita() {
        montarNoAparelho(TELA_ESTREITA_FONTE_MAXIMA)

        assertNumaLinha(contexto.getString(R.string.acao_escolher_data), "o botão da data")
        assertTextoInteiro("18/09/2026", "a data da compra")
    }

    @Test
    fun osIndicadoresDePlantaoEContingenciaFicamNumaLinhaComAFonteNoMaximoEmTelaEstreita() {
        // Às 23h e sem nenhuma fonte de spot no ar, a Conta Global opera em plantão e na PTAX de contingência: são as
        // duas pílulas juntas, lado a lado quando cabem.
        val vm = montarNoAparelho(
            TELA_ESTREITA_FONTE_MAXIMA,
            viewModelEmMemoria(relogio = RELOGIO_DE_PLANTAO, provedorSpot = SPOT_FORA_DO_AR),
        )
        partirDe(vm.campoValor, "100000")
        calcular()
        assertResultadoVisivel()

        assertNumaLinha(contexto.getString(R.string.pilula_plantao), "a pílula")
        assertNumaLinha(contexto.getString(R.string.pilula_contingencia), "a pílula")
    }

    @Test
    fun aPilulaDaMelhorOpcaoTemAAlturaDoWeb() {
        // `text-sm px-4 py-2 border` do web: 20 px de linha, 8 px de padding e 1 px de borda, em cima e embaixo, o
        // espaçamento normal entre letras, o texto centralizado e o rótulo curto ("✅ 🌐 Conta Global", de
        // `useSimulation.ts`). Só a escala de fonte é fixada, em 1,0: o `ForcedSize` que não cabe na tela reduziria a
        // densidade do conteúdo, e a altura em dp passaria a depender dele. A marca fica na ponta de fora da pílula, e
        // a caixa do nó inclui o recuo.
        val vm = calcularNoAparelho(FONTE_PADRAO, valor = "100000")
        assertEquals("pré-condição: a Conta Global vence", Opcao.CONTA_GLOBAL, vencedor(vm))

        val no = compose.onNodeWithTag(Marcas.MELHOR_OPCAO, useUnmergedTree = true).performScrollTo()
        val altura = with(compose.density) { no.fetchSemanticsNode().size.height.toDp() }
        assertEquals("altura da pílula da melhor opção, em dp", 38f, altura.value, 1f)
        val layout = no.layoutDoTexto()
        assertEquals(
            "espaçamento entre letras da pílula da melhor opção, em sp",
            0f,
            layout.layoutInput.style.letterSpacing.value,
            0f,
        )
        assertEquals("alinhamento da pílula da melhor opção", TextAlign.Center, layout.layoutInput.style.textAlign)
        assertEquals("texto da pílula da melhor opção", "✅ 🌐 Conta Global", layout.layoutInput.text.text)
    }

    @Test
    fun controleComAFontePadraoEmTelaLargaTudoCabe() {
        val vm = calcularNoAparelho(TELA_LARGA_FONTE_PADRAO, valor = "100000")
        assertEquals("pré-condição: a Conta Global vence", Opcao.CONTA_GLOBAL, vencedor(vm))

        assertNumaLinha(contexto.getString(R.string.selo_vencedor), "o selo")
        assertEquals(
            "linhas do título do vencedor",
            1,
            assertTextoInteiro(contexto.getString(R.string.cartao_global), "o título do vencedor").lineCount,
        )
        assertTextoInteiro("R$ 5.896,40", "o total do cartão")
        assertTextoInteiro("R$ 5.608,03", "o total da Conta Global")
        assertSeloSemSobreposicao(contexto.getString(R.string.cartao_global), cartaoDeCima = Marcas.CARTAO_CARTAO)
        assertNumaLinha(contexto.getString(R.string.acao_escolher_data), "o botão da data")
    }

    // CALANDR-35 — a barra superior é o começo do conteúdo e rola com ele, como o cabeçalho do web: sai ao descer e só
    // volta no topo. Com a fonte no máximo numa tela estreita ela tem um quarto da altura, e fixa tomava esse espaço o
    // tempo todo. Os gestos são de toque, dentro do contêiner rolável: num aparelho de ponta a ponta, a borda de baixo
    // da raiz é o recuo da barra de navegação, fora dele.

    /** O título do cabeçalho: o mesmo texto aparece no NOTICE, que a tela de licenças mostra. */
    private fun tituloDoCabecalho() =
        compose.onNode(hasText(contexto.getString(R.string.titulo)) and hasAnyAncestor(hasTestTag(Marcas.CABECALHO)))

    /** O contêiner rolável do aplicativo, o único que leva o rodapé. */
    private fun conteudo() =
        compose.onNode(hasScrollAction() and hasAnyDescendant(hasText(contexto.getString(R.string.compliance))))

    /** O nó inteiro na tela: a caixa recortada pelos pais tem a altura do próprio nó. */
    private fun assertInteiro(no: SemanticsNodeInteraction, nome: String) {
        val semantica = no.assertIsDisplayed().fetchSemanticsNode()
        assertEquals("altura visível de $nome", semantica.size.height.toFloat(), semantica.boundsInRoot.height, 0.5f)
    }

    /** No topo: o cabeçalho inteiro e, logo abaixo dele, a primeira caixa do formulário inteira. */
    private fun assertTopoDaSimulacao() {
        assertInteiro(tituloDoCabecalho(), "o título do cabeçalho")
        assertInteiro(compose.onNodeWithTag(Marcas.DCC), "a caixa do DCC")
    }

    @Test
    fun comAFonteNoMaximoEmTelaEstreitaOCabecalhoSaiAoRolarESoVoltaNoTopo() {
        montarNoAparelho(TELA_ESTREITA_FONTE_MAXIMA)
        assertTopoDaSimulacao()

        conteudo().performTouchInput { swipeUp() }
        compose.onNodeWithText(contexto.getString(R.string.compliance)).performScrollTo()
        tituloDoCabecalho().assertIsNotDisplayed()

        // Um gesto lento para baixo, a partir do rodapé, sem chegar ao topo: o cabeçalho não volta no meio da página.
        conteudo().performTouchInput {
            swipeDown(startY = top + height * 0.25f, endY = top + height * 0.6f, durationMillis = 1_000)
        }
        tituloDoCabecalho().assertIsNotDisplayed()

        // Um gesto pode não chegar ao topo de uma tela longa: até dez, parando quando o cabeçalho reaparece.
        repeat(10) { if (!tituloDoCabecalho().isDisplayed()) conteudo().performTouchInput { swipeDown() } }
        assertTopoDaSimulacao()
    }

    @Test
    fun aoVoltarDasLicencasASimulacaoApareceNoTopoComOCabecalhoInteiro() {
        montarNoAparelho(TELA_ESTREITA_FONTE_MAXIMA)
        compose.onNodeWithText(contexto.getString(R.string.acao_licencas)).performClick()
        conteudo().performTouchInput { swipeUp() }
        tituloDoCabecalho().assertIsNotDisplayed()

        Espresso.pressBack()
        assertTopoDaSimulacao()
    }

    @Test
    fun umGestoQueComecaNoCabecalhoRolaAPagina() {
        // O `TopAppBar` do Material 3 traz um `pointerInput` próprio; um gesto que começa em cima dele ainda tem de
        // rolar a página, como no web. A pré-condição prova que o cabeçalho existe: sem ela, a asserção final também
        // passaria se a marca sumisse.
        montarNoAparelho(TELA_ESTREITA_FONTE_MAXIMA)
        assertInteiro(tituloDoCabecalho(), "o título do cabeçalho")
        compose.onNodeWithTag(Marcas.CABECALHO).performTouchInput { swipeUp() }
        tituloDoCabecalho().assertIsNotDisplayed()
    }

    // CALANDR-34 — o selo, as pílulas e o cartão de cenário com a aparência do web, com as decisões do operador de
    // 04/10/2026 sobre o contraste (Discussion #89). Os tamanhos são conferidos em pixels contra o que o Compose faz com
    // os valores do web: a linha arredondada para cima e cada recuo para o pixel mais próximo. A cor do texto vem do
    // estilo do layout. A do fundo vem de um pixel da tela perto da borda esquerda, onde não há texto; um fundo
    // translúcido é conferido contra o que está atrás dele, num pixel logo ao lado.

    /** A pílula medida pela marca: a linha do texto e o recuo de cada lado, em pixels, a caixa e o estilo do texto. */
    private class MedidaDePilula(
        val linha: Float,
        val recuoHorizontal: Float,
        val recuoVertical: Float,
        val caixa: Rect,
        val estilo: TextStyle,
    )

    /**
     * Com [rolar] falso, mede onde a pílula está: as caixas de um mesmo caso são medidas numa rolagem só, porque cada
     * `performScrollTo` pode mover a tela e mudar a posição na raiz.
     */
    private fun medirPilula(marca: String, indice: Int = 0, rolar: Boolean = true): MedidaDePilula {
        val no = compose.onAllNodesWithTag(marca, useUnmergedTree = true)[indice].let { if (rolar) it.performScrollTo() else it }
        val layout = no.layoutDoTexto()
        val tamanho = no.fetchSemanticsNode().size
        return MedidaDePilula(
            linha = layout.getLineBottom(0) - layout.getLineTop(0),
            recuoHorizontal = (tamanho.width - layout.size.width) / 2f,
            recuoVertical = (tamanho.height - layout.size.height) / 2f,
            caixa = caixa(no),
            estilo = layout.layoutInput.style,
        )
    }

    /** A linha de [sp] em pixels, arredondada para cima, como o Compose faz. A fonte dos casos é a padrão. */
    private fun linhaEsperada(sp: Float): Float = ceil(sp * compose.density.density)

    /** O recuo de [dp] em pixels, arredondado para o pixel mais próximo, como o Compose faz. */
    private fun recuoEsperado(dp: Float): Float = (dp * compose.density.density).roundToInt().toFloat()

    private fun px(dp: Dp): Float = with(compose.density) { dp.toPx() }

    /** A cor de um pixel da tela, nas coordenadas da raiz. */
    private fun corNaRaiz(x: Float, y: Float): Color =
        compose.onRoot().captureToImage().toPixelMap()[x.roundToInt(), y.roundToInt()]

    private fun assertCor(nome: String, esperada: Color, medida: Color) {
        val tolerancia = 3f / 255f
        assertEquals("$nome: vermelho de $medida", esperada.red, medida.red, tolerancia)
        assertEquals("$nome: verde de $medida", esperada.green, medida.green, tolerancia)
        assertEquals("$nome: azul de $medida", esperada.blue, medida.blue, tolerancia)
    }

    /** O fundo opaco da pílula, num pixel a 3 dp da borda esquerda, na metade da altura. */
    private fun assertFundoOpaco(nome: String, pilula: MedidaDePilula, esperado: Color) =
        assertCor(nome, esperado, corNaRaiz(pilula.caixa.left + px(3.dp), pilula.caixa.center.y))

    /** As sombras do nó, pelos valores que o elemento oficial do `dropShadow` publica para o inspetor. */
    private fun sombras(marca: String): List<Shadow> =
        compose.onNodeWithTag(marca, useUnmergedTree = true).fetchSemanticsNode().layoutInfo.getModifierInfo()
            .mapNotNull { it.modifier as? InspectableValue }
            .filter { it.nameFallback == "dropShadow" }
            .map { valor -> valor.inspectableElements.first { it.name == "dropShadow" }.value as Shadow }

    /** O modo cobrado em reais com o cenário provável: R$ 100,00 cobrados como R$ 109,19, a dupla conversão. */
    private fun calcularEmReais(aparelho: DeviceConfigurationOverride) {
        val vm = montarNoAparelho(aparelho)
        marcarDcc()
        partirDe(vm.campoValor, "10000")
        partirDe(vm.campoFatura, "10919")
        calcular()
        compose.waitUntil(TEMPO_LIMITE) { compose.onAllNodesWithTag(Marcas.CENARIOS).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun oSeloTemAsCoresOPesoEASombraDoWeb() {
        // `bg-amber-400 text-amber-900 text-[10px] font-extrabold px-2.5 py-1 rounded-full shadow-md` (ComparisonCard.tsx).
        val vm = calcularNoAparelho(FONTE_PADRAO, valor = "100000")
        assertEquals("pré-condição: a Conta Global vence", Opcao.CONTA_GLOBAL, vencedor(vm))
        val selo = medirPilula(Marcas.SELO)

        assertEquals("cor do texto do selo", Color(0xFF7B3306), selo.estilo.color)
        assertEquals("peso do selo", FontWeight.ExtraBold, selo.estilo.fontWeight)
        assertEquals("espaçamento entre letras do selo, em sp", 0f, selo.estilo.letterSpacing.value, 0f)
        assertFundoOpaco("fundo do selo", selo, Color(0xFFFFB900))
        // O `shadow-md`: desvio-padrão de metade do raio do CSS, no `dropShadow` como raio vezes √3 (o comentário do
        // `SeloVencedor` traz a conta).
        assertEquals(
            "sombras do selo",
            listOf(
                Shadow(5.196.dp, Color(0x1A000000), spread = (-1).dp, offset = DpOffset(0.dp, 4.dp)),
                Shadow(3.464.dp, Color(0x1A000000), spread = (-2).dp, offset = DpOffset(0.dp, 2.dp)),
            ),
            sombras(Marcas.SELO),
        )
        // Controle, igual antes e depois: a linha de 15 sp e o recuo de 10 × 4 dp.
        assertEquals("linha do selo, em px", linhaEsperada(15f), selo.linha, 0.5f)
        assertEquals("recuo horizontal do selo, em px", recuoEsperado(10f), selo.recuoHorizontal, 1f)
        assertEquals("recuo vertical do selo, em px", recuoEsperado(4f), selo.recuoVertical, 1f)
    }

    @Test
    fun aPilulaProvavelTemOTamanhoEAsCoresDoWeb() {
        // `text-[10px] font-bold bg-orange-100 text-orange-700 px-2 py-0.5 rounded-full` (CompraReaisPanel.tsx).
        calcularEmReais(FONTE_PADRAO)
        val provavel = medirPilula(Marcas.PROVAVEL)

        assertEquals("linha da pílula provável, em px", linhaEsperada(15f), provavel.linha, 0.5f)
        assertEquals("recuo horizontal da pílula provável, em px", recuoEsperado(8f), provavel.recuoHorizontal, 1f)
        assertEquals("recuo vertical da pílula provável, em px", recuoEsperado(2f), provavel.recuoVertical, 1f)
        assertEquals("espaçamento entre letras da pílula provável, em sp", 0f, provavel.estilo.letterSpacing.value, 0f)
        assertEquals("cor do texto da pílula provável", Color(0xFFCA3500), provavel.estilo.color)
        assertFundoOpaco("fundo da pílula provável", provavel, Color(0xFFFFEDD4))
    }

    @Test
    fun asPilulasDePlantaoEContingenciaTemOTamanhoAsCoresEOsVaosDoWeb() {
        // `text-[10px] font-bold px-2 py-0.5 rounded-full`, Plantão em `bg-amber-100 text-amber-800` e Contingência em
        // `bg-orange-100 text-orange-800 ml-1` (ComparisonCard.tsx), na linha do `text-sm` do contêiner.
        val vm = montarNoAparelho(
            FONTE_PADRAO,
            viewModelEmMemoria(relogio = RELOGIO_DE_PLANTAO, provedorSpot = SPOT_FORA_DO_AR),
        )
        partirDe(vm.campoValor, "100000")
        calcular()
        assertResultadoVisivel()
        compose.onNodeWithTag(Marcas.CONTINGENCIA, useUnmergedTree = true).performScrollTo()
        val plantao = medirPilula(Marcas.PLANTAO, rolar = false)
        val contingencia = medirPilula(Marcas.CONTINGENCIA, rolar = false)

        for ((nome, pilula) in listOf("Plantão" to plantao, "Contingência" to contingencia)) {
            assertEquals("linha da pílula $nome, em px", linhaEsperada(10f * 1.25f / 0.875f), pilula.linha, 0.5f)
            assertEquals("recuo horizontal da pílula $nome, em px", recuoEsperado(8f), pilula.recuoHorizontal, 1f)
            assertEquals("recuo vertical da pílula $nome, em px", recuoEsperado(2f), pilula.recuoVertical, 1f)
            assertEquals("espaçamento entre letras da pílula $nome, em sp", 0f, pilula.estilo.letterSpacing.value, 0f)
        }
        assertEquals("cor do texto do Plantão", Color(0xFF973C00), plantao.estilo.color)
        assertEquals("cor do texto da Contingência", Color(0xFF9F2D00), contingencia.estilo.color)
        assertFundoOpaco("fundo do Plantão", plantao, Color(0xFFFEF3C6))
        assertFundoOpaco("fundo da Contingência", contingencia, Color(0xFFFFEDD4))

        // Lado a lado, o `ml-1`: 4 dp entre as duas.
        assertEquals("pré-condição: as duas pílulas na mesma linha", plantao.caixa.top, contingencia.caixa.top, 1f)
        assertEquals("vão entre as pílulas, em px", recuoEsperado(4f), contingencia.caixa.left - plantao.caixa.right, 1f)
        // Da linha do VET às pílulas: os 10 px de margem do web mais os 2,2 px da linha do `text-sm` (VAO_DO_VET).
        val vet = caixa(
            compose.onNode(
                hasText(contexto.getString(R.string.rotulo_vet)) and hasAnyAncestor(hasTestTag(Marcas.CARTAO_GLOBAL)),
                useUnmergedTree = true,
            ),
        )
        assertEquals("vão entre a linha do VET e as pílulas, em dp", 12.2f, (plantao.caixa.top - vet.bottom) / compose.density.density, 0.5f)
    }

    @Test
    fun quandoAsPilulasDePlantaoEContingenciaQuebramOVaoEODoWeb() {
        // Com a fonte no máximo em 300 dp, as duas não cabem lado a lado. No web, o `space-y-2.5` dá 10 px de margem
        // embaixo do Plantão, e a linha do `text-sm` soma 2,2 px antes da Contingência.
        val vm = montarNoAparelho(
            TELA_DE_300_DP_FONTE_MAXIMA,
            viewModelEmMemoria(relogio = RELOGIO_DE_PLANTAO, provedorSpot = SPOT_FORA_DO_AR),
        )
        partirDe(vm.campoValor, "100000")
        calcular()
        assertResultadoVisivel()
        // As duas caixas numa rolagem só (ver `medirPilula`).
        compose.onNodeWithTag(Marcas.CONTINGENCIA, useUnmergedTree = true).performScrollTo()
        val plantao = caixa(compose.onNodeWithTag(Marcas.PLANTAO, useUnmergedTree = true))
        val contingencia = caixa(compose.onNodeWithTag(Marcas.CONTINGENCIA, useUnmergedTree = true))

        assertTrue("pré-condição: a Contingência desceu para a linha de baixo", contingencia.top >= plantao.bottom)
        assertEquals(
            "vão entre as pílulas que quebram, em dp",
            12.2f,
            (contingencia.top - plantao.bottom) / compose.density.density,
            0.5f,
        )
        // O `ml-1` do web é margem da própria Contingência: na linha de baixo, ela começa 4 dp para dentro.
        assertEquals(
            "recuo da Contingência na linha de baixo, em dp",
            4f,
            (contingencia.left - plantao.left) / compose.density.density,
            0.5f,
        )
    }

    @Test
    fun aQualidadeExcelenteDoBacktestTemOTamanhoEOsRotulosDoWebEAsCoresDecididas() {
        assertQualidadeDoBacktest("5.3800", "🏆 Excelente", texto = Color(0xFF166534), matiz = Color(0xFF16A34A))
        // Os rótulos do painel voltam aos do web (BacktestPanel.tsx), por decisão do operador de 04/10/2026.
        for (rotulo in listOf("🧪 BACKTEST (7 DIAS)", "MAPE 7d", "Erro atual")) {
            compose.onNodeWithText(rotulo, useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        }
        // O título aparece em maiúsculas, como o `h4` do web, e o TalkBack lê a forma original (CALANDR-44).
        compose.onNodeWithContentDescription("🧪 Backtest (7 dias)", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun aQualidadeBoaDoBacktestTemAsCoresDecididas() =
        assertQualidadeDoBacktest("5.3200", "✅ Boa", texto = Color(0xFF854D0E), matiz = Color(0xFFEAB308))

    @Test
    fun aQualidadeAtencaoDoBacktestTemAsCoresDecididas() =
        assertQualidadeDoBacktest("5.2500", "⚠️ Atenção", texto = Color(0xFF991B1B), matiz = Color(0xFFDC2626))

    /**
     * A pílula da qualidade: `px-3 py-1 rounded-full text-xs font-bold` (BacktestPanel.tsx), com o fundo do web, a cor a
     * 12%, e o texto no tom 800 da mesma cor (desvio declarado, decisão do operador de 04/10/2026).
     */
    private fun assertQualidadeDoBacktest(spot: String, rotulo: String, texto: Color, matiz: Color) {
        val vm = montarNoAparelho(FONTE_PADRAO, viewModelEmMemoria(provedorSpot = spotComInstante(spot)))
        partirDe(vm.campoValor, "100000")
        calcular()
        assertResultadoVisivel()
        compose.waitUntil(TEMPO_LIMITE) { compose.onAllNodesWithTag(Marcas.QUALIDADE).fetchSemanticsNodes().isNotEmpty() }
        val pilula = medirPilula(Marcas.QUALIDADE)

        assertEquals(
            "rótulo da qualidade",
            rotulo,
            compose.onNodeWithTag(Marcas.QUALIDADE, useUnmergedTree = true).layoutDoTexto().layoutInput.text.text,
        )
        assertEquals("tamanho da fonte da qualidade, em sp", 12f, pilula.estilo.fontSize.value, 0f)
        assertEquals("linha da qualidade, em px", linhaEsperada(16f), pilula.linha, 0.5f)
        assertEquals("recuo horizontal da qualidade, em px", recuoEsperado(12f), pilula.recuoHorizontal, 1f)
        assertEquals("recuo vertical da qualidade, em px", recuoEsperado(4f), pilula.recuoVertical, 1f)
        assertEquals("espaçamento entre letras da qualidade, em sp", 0f, pilula.estilo.letterSpacing.value, 0f)
        assertEquals("cor do texto da qualidade", texto, pilula.estilo.color)
        val y = pilula.caixa.center.y
        val atras = corNaRaiz(pilula.caixa.left - px(3.dp), y)
        assertCor(
            "fundo da qualidade sobre o painel ($atras)",
            matiz.copy(alpha = 0.12f).compositeOver(atras),
            corNaRaiz(pilula.caixa.left + px(3.dp), y),
        )
    }

    @Test
    fun asLinhasDoCenarioTemOTamanhoEOAlinhamentoDoWeb() {
        // O total: `flex justify-between items-baseline`, rótulo `text-xs` e valor `text-lg font-extrabold
        // text-slate-900`. O acréscimo: `text-xs`, com o valor em `font-semibold text-slate-700`
        // (CompraReaisPanel.tsx). Os rótulos ficam no cinza #475569 (desvio declarado, decisão do operador de
        // 04/10/2026).
        calcularEmReais(FONTE_PADRAO)
        val rotulos = compose.onAllNodesWithTag(Marcas.ROTULO_CENARIO, useUnmergedTree = true)
        val valores = compose.onAllNodesWithTag(Marcas.VALOR_CENARIO, useUnmergedTree = true)
        // Em cada cartão, a primeira linha é a do total e a segunda, a do acréscimo.
        val rotuloDoTotal = rotulos[0].performScrollTo()
        val valorDoTotal = valores[0]

        for ((nome, no) in listOf("rótulo do total" to rotuloDoTotal, "rótulo do acréscimo" to rotulos[1])) {
            val estilo = no.layoutDoTexto().layoutInput.style
            assertEquals("tamanho do $nome, em sp", 12f, estilo.fontSize.value, 0f)
            assertEquals("peso do $nome", FontWeight.Normal, estilo.fontWeight ?: FontWeight.Normal)
            assertEquals("espaçamento entre letras do $nome, em sp", 0f, estilo.letterSpacing.value, 0f)
            assertEquals("cor do $nome", Color(0xFF475569), estilo.color)
            val layout = no.layoutDoTexto()
            assertEquals("linha do $nome, em px", linhaEsperada(16f), layout.getLineBottom(0) - layout.getLineTop(0), 0.5f)
        }
        val total = valorDoTotal.layoutDoTexto()
        assertEquals("tamanho do total, em sp", 18f, total.layoutInput.style.fontSize.value, 0f)
        assertEquals("peso do total", FontWeight.ExtraBold, total.layoutInput.style.fontWeight)
        assertEquals("espaçamento entre letras do total, em sp", 0f, total.layoutInput.style.letterSpacing.value, 0f)
        assertEquals("linha do total, em px", linhaEsperada(28f), total.getLineBottom(0) - total.getLineTop(0), 0.5f)
        assertEquals("cor do total", Color(0xFF0F172B), total.layoutInput.style.color)
        // `items-baseline`: o rótulo e o valor na mesma linha de base.
        val baseRotulo = caixa(rotuloDoTotal).top + rotuloDoTotal.layoutDoTexto().firstBaseline
        val baseValor = caixa(valorDoTotal).top + total.firstBaseline
        assertEquals("linha de base do rótulo e do total, em px", baseRotulo, baseValor, 1f)

        val acrescimo = valores[1].layoutDoTexto()
        assertEquals("tamanho do acréscimo, em sp", 12f, acrescimo.layoutInput.style.fontSize.value, 0f)
        assertEquals("peso do acréscimo", FontWeight.SemiBold, acrescimo.layoutInput.style.fontWeight)
        assertEquals("cor do acréscimo", Color(0xFF314158), acrescimo.layoutInput.style.color)
        assertEquals("linha do acréscimo, em px", linhaEsperada(16f), acrescimo.getLineBottom(0) - acrescimo.getLineTop(0), 0.5f)
    }

    @Test
    fun oCabecalhoDoCartaoDeCenarioEODoWeb() {
        // O ícone `text-lg` e o título `text-sm font-bold text-slate-700` (CompraReaisPanel.tsx). O fundo do cartão não é
        // conferido por pixel: a superfície do cartão é translúcida, e a sombra de elevação aparece através dela; ele e o
        // contorno do provável são conferidos nas capturas.
        calcularEmReais(FONTE_PADRAO)
        val icone = compose.onAllNodesWithTag(Marcas.ICONE_CENARIO, useUnmergedTree = true)[0].performScrollTo().layoutDoTexto()
        assertEquals("tamanho do ícone do cenário, em sp", 18f, icone.layoutInput.style.fontSize.value, 0f)
        assertEquals("linha do ícone do cenário, em px", linhaEsperada(28f), icone.getLineBottom(0) - icone.getLineTop(0), 0.5f)
        assertEquals("espaçamento entre letras do ícone do cenário, em sp", 0f, icone.layoutInput.style.letterSpacing.value, 0f)
        val titulo = compose.onAllNodesWithTag(Marcas.TITULO_CENARIO, useUnmergedTree = true)[0].layoutDoTexto()
        assertEquals("tamanho do título do cenário, em sp", 14f, titulo.layoutInput.style.fontSize.value, 0f)
        assertEquals("linha do título do cenário, em px", linhaEsperada(20f), titulo.getLineBottom(0) - titulo.getLineTop(0), 0.5f)
        assertEquals("espaçamento entre letras do título do cenário, em sp", 0f, titulo.layoutInput.style.letterSpacing.value, 0f)
        assertEquals("cor do título do cenário", Color(0xFF314158), titulo.layoutInput.style.color)
    }
}

/**
 * Os aparelhos simulados. A fonte vai até 200% desde o Android 14, e o override de teste passa pela mesma curva não linear
 * do aparelho: o `Density` que ele monta converte sp pela `FontScaling` do Compose. 360 dp é a largura dos telefones
 * estreitos comuns e 411 dp a do Pixel 2 do aparelho gerenciado.
 */
private val TELA_ESTREITA_FONTE_MAXIMA =
    DeviceConfigurationOverride.FontScale(2f) then DeviceConfigurationOverride.ForcedSize(DpSize(360.dp, 720.dp))
/**
 * Estreita o bastante para as pílulas de plantão e contingência não caberem lado a lado com a fonte no máximo: elas
 * somam 283 dp com o vão, e aqui sobram 236 dp de largura útil no cartão (CALANDR-34). A altura cabe na área do
 * aparelho, para o `ForcedSize` não reduzir a densidade, e o dp dos casos ser o da regra.
 */
private val TELA_DE_300_DP_FONTE_MAXIMA =
    DeviceConfigurationOverride.FontScale(2f) then DeviceConfigurationOverride.ForcedSize(DpSize(300.dp, 600.dp))
private val TELA_ESTREITA_FONTE_PADRAO =
    DeviceConfigurationOverride.FontScale(1f) then DeviceConfigurationOverride.ForcedSize(DpSize(360.dp, 720.dp))
private val PIXEL_2_FONTE_UM_POUCO_MAIOR =
    DeviceConfigurationOverride.FontScale(1.15f) then DeviceConfigurationOverride.ForcedSize(DpSize(411.dp, 731.dp))
private val PIXEL_2_FONTE_GRANDE =
    DeviceConfigurationOverride.FontScale(1.3f) then DeviceConfigurationOverride.ForcedSize(DpSize(411.dp, 731.dp))
private val TELA_LARGA_FONTE_PADRAO =
    DeviceConfigurationOverride.FontScale(1f) then DeviceConfigurationOverride.ForcedSize(DpSize(600.dp, 960.dp))

/** Só a fonte padrão, na tela e na densidade do próprio aparelho. */
private val FONTE_PADRAO = DeviceConfigurationOverride.FontScale(1f)

/** Cinco segundos cobrem a composição e o `viewModelScope` sem rede. */
private const val TEMPO_LIMITE = 5_000L
