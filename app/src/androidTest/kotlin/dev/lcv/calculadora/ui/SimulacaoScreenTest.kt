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
import androidx.compose.ui.input.key.Key
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
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
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
import androidx.compose.ui.text.style.TextAlign
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

    // CALANDR-35 — a barra superior rola com o conteúdo, como o cabeçalho do web: sai ao descer e só volta no topo.
    // Com a fonte no máximo numa tela estreita ela tem um quarto da altura, e fixa tomava esse espaço o tempo todo. Os
    // gestos são de toque, o caminho real da rolagem aninhada que a recolhe.

    /** O título fora do conteúdo rolável: o mesmo texto aparece no NOTICE, que a tela de licenças mostra. */
    private fun tituloDaBarra() =
        compose.onNode(hasText(contexto.getString(R.string.titulo)) and !hasAnyAncestor(hasScrollAction()))

    /** A barra inteira na tela: a caixa do título recortada pelos pais tem a altura do próprio título. */
    private fun assertBarraInteira() {
        val titulo = tituloDaBarra().assertIsDisplayed().fetchSemanticsNode()
        assertEquals(
            "altura visível do título da barra",
            titulo.size.height.toFloat(),
            titulo.boundsInRoot.height,
            0.5f,
        )
    }

    @Test
    fun comAFonteNoMaximoEmTelaEstreitaABarraSaiAoRolarEVoltaNoTopo() {
        montarNoAparelho(TELA_ESTREITA_FONTE_MAXIMA)
        assertBarraInteira()

        compose.onRoot().performTouchInput { swipeUp() }
        tituloDaBarra().assertIsNotDisplayed()

        // Um gesto pode não chegar ao topo de uma tela longa: até dez, parando quando a barra reaparece.
        repeat(10) { if (!tituloDaBarra().isDisplayed()) compose.onRoot().performTouchInput { swipeDown() } }
        assertBarraInteira()
    }

    @Test
    fun aoVoltarDasLicencasComABarraRecolhidaASimulacaoApareceNoTopoComABarraInteira() {
        montarNoAparelho(TELA_ESTREITA_FONTE_MAXIMA)
        compose.onNodeWithText(contexto.getString(R.string.acao_licencas)).performClick()
        compose.onRoot().performTouchInput { swipeUp() }
        tituloDaBarra().assertIsNotDisplayed()

        Espresso.pressBack()
        compose.onNodeWithText(contexto.getString(R.string.subtitulo)).assertIsDisplayed()
        assertBarraInteira()
    }
}

/**
 * Os aparelhos simulados. A fonte vai até 200% no Android 14, e o override de teste passa pela mesma curva não linear
 * do aparelho: o `Density` que ele monta converte sp pela `FontScaling` do Compose. 360 dp é a largura dos telefones
 * estreitos comuns e 411 dp a do Pixel 2 do aparelho gerenciado.
 */
private val TELA_ESTREITA_FONTE_MAXIMA =
    DeviceConfigurationOverride.FontScale(2f) then DeviceConfigurationOverride.ForcedSize(DpSize(360.dp, 720.dp))
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
