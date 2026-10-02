/*
 * Copyright © 2026 LCV Ideas & Software
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package dev.lcv.calculadora.teste.release

import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.regex.Pattern

/**
 * Os fluxos críticos do aplicativo minificado pelo R8 (CALANDR-26), de fora do
 * processo dele: o teste só vê a tela, como a pessoa, e por isso não depende de
 * nome de classe que o R8 renomeia. Cada fluxo exercita uma parte que o R8 pode
 * quebrar em execução: o grafo do Hilt ao abrir, o Retrofit, o OkHttp e o JSON
 * na simulação com cotação ao vivo, o motor no modo cobrado em reais e os
 * `assets` na tela de licenças. Uma queda do processo faz o elemento seguinte
 * não aparecer, e o teste cai.
 */
@RunWith(AndroidJUnit4::class)
class AplicativoMinificadoTest {

    private val instrumentacao = InstrumentationRegistry.getInstrumentation()
    private val aparelho = UiDevice.getInstance(instrumentacao)

    @Before
    fun abrir() {
        aparelho.pressHome()
        val intencao = instrumentacao.context.packageManager.getLaunchIntentForPackage(PACOTE)
        assertNotNull("o aplicativo $PACOTE não está instalado", intencao)
        intencao!!.addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK or android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        instrumentacao.context.startActivity(intencao)
        assertTrue("o aplicativo não abriu", aparelho.wait(Until.hasObject(By.pkg(PACOTE).depth(0)), ESPERA))
        achar(By.text(CALCULAR))
    }

    @Test
    fun aSimulacaoComCotacaoAoVivoMostraOResultadoOuOAvisoDoProprioAplicativo() {
        preencherOValorECalcular()
        // Sem rede, ou com a fonte fora do ar, o aplicativo avisa; os dois desfechos provam que o minificado não caiu.
        val desfecho = esperarRolando(By.text(Pattern.compile("$CARTAO|$SEM_COTACAO")), ESPERA_REDE)
        assertNotNull("nem resultado nem aviso depois de calcular", desfecho)
        // Diz no logcat da CI qual caminho rodou: com o resultado, a rede, o Retrofit e o JSON foram exercitados.
        Log.i(ROTULO, "desfecho da simulação: ${desfecho!!.text}")
        assertTrue("o processo do aplicativo morreu", processoVivo())
    }

    @Test
    fun oModoCobradoEmReaisMostraOsTresCenarios() {
        achar(By.textContains(DCC)).click()
        preencherOValorECalcular()
        assertNotNull("sem o cenário de adquirência local", esperarRolando(By.text(ADQUIRENCIA), ESPERA))
        assertNotNull("sem o cenário de DCC pura", esperarRolando(By.text(DCC_PURA), ESPERA))
        assertNotNull("sem o cenário de dupla conversão", esperarRolando(By.text(DUPLA_CONVERSAO), ESPERA))
        assertTrue("o processo do aplicativo morreu", processoVivo())
    }

    @Test
    fun aTelaDeLicencasLeOsTextosEmbarcados() {
        achar(By.text(LICENCAS)).click()
        assertNotNull(achar(By.text(COPYRIGHT)))
        assertTrue("o processo do aplicativo morreu", processoVivo())
    }

    private fun preencherOValorECalcular() {
        achar(By.clazz("android.widget.EditText")).text = "100"
        esconderOTeclado()
        tocarNoFormulario(By.text(CALCULAR))
    }

    /** O teclado na tela cobre o botão; voltar o fecha, e só é pedido com ele aberto, para não sair da tela. */
    private fun esconderOTeclado() {
        if (aparelho.executeShellCommand("dumpsys input_method").contains("mInputShown=true")) {
            aparelho.pressBack()
            aparelho.waitForIdle()
        }
    }

    /** Acha um elemento do topo da tela (a caixa do modo cobrado em reais, o acesso às licenças). */
    private fun achar(seletor: BySelector): UiObject2 =
        requireNotNull(aparelho.wait(Until.findObject(seletor), ESPERA)) { "não achei $seletor na tela" }

    /**
     * Toca um elemento do formulário depois de rolar até o fim dele. O UI Automator toca no centro da parte visível,
     * e no Pixel 2 da CI o botão Calcular aparecia com só 10 px na borda da tela: o toque caía na borda e o cálculo
     * nem começava (medido em 02/10/2026).
     */
    private fun tocarNoFormulario(seletor: BySelector) {
        val rolavel = aparelho.findObject(By.scrollable(true))
        var voltas = 0
        // `scroll` devolve se ainda há o que rolar; dez voltas sobram para o formulário mais longo.
        while (rolavel != null && voltas++ < 10 && rolavel.scroll(Direction.DOWN, 1.0f)) Unit
        achar(seletor).click()
    }

    /** Espera o elemento rolando a tela: o resultado nasce abaixo do botão, e o UI Automator só vê o que está na tela. */
    private fun esperarRolando(seletor: BySelector, espera: Long): UiObject2? {
        val limite = SystemClock.uptimeMillis() + espera
        while (SystemClock.uptimeMillis() < limite) {
            aparelho.findObject(seletor)?.let { return it }
            aparelho.findObject(By.scrollable(true))?.scroll(Direction.DOWN, 0.5f)
            SystemClock.sleep(500)
        }
        return null
    }

    private fun processoVivo(): Boolean = aparelho.executeShellCommand("pidof $PACOTE").isNotBlank()

    private companion object {
        const val PACOTE = "dev.lcv.calculadora"
        const val ROTULO = "AplicativoMinificado"
        const val ESPERA = 15_000L
        const val ESPERA_REDE = 60_000L
        const val CALCULAR = "Calcular"
        const val DCC = "O lojista estrangeiro cobrou em reais"
        const val CARTAO = "Cartão de Crédito"
        const val SEM_COTACAO = "Não foi possível obter as cotações agora\\. Tente de novo\\."
        const val ADQUIRENCIA = "Adquirência local (doméstica)"
        const val DCC_PURA = "DCC pura (só IOF)"
        const val DUPLA_CONVERSAO = "Dupla conversão (spread + IOF)"
        const val LICENCAS = "Licenças"
        const val COPYRIGHT = "Copyright © 2026 LCV Ideas & Software"
    }
}
