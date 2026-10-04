/*
 * Copyright © 2026 LCV Ideas & Software
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package dev.lcv.calculadora.teste.release

import android.content.Intent
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.regex.Pattern
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

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
        // Dados limpos a cada fluxo: uma cotação guardada no Room se passaria por uma lida da rede.
        aparelho.executeShellCommand("pm clear $PACOTE")
        aparelho.pressHome()
        val intencao = instrumentacao.context.packageManager.getLaunchIntentForPackage(PACOTE)
        assertNotNull("o aplicativo $PACOTE não está instalado", intencao)
        intencao!!.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
        // Depois que o `pm clear` volta, o sistema ainda pode matar o aplicativo aberto logo em seguida (CI da #97, em
        // 04/10/2026: aberto 0,8 s depois do `pm clear` e morto 0,3 s mais tarde). Por isso há uma segunda abertura; o
        // fluxo só cai se nem ela abrir. Cada abertura só conta com o formulário na tela, e não com a janela do pacote,
        // que pode aparecer antes de o processo morrer.
        val abriu = (1..2).any {
            instrumentacao.context.startActivity(intencao)
            aparelho.wait(Until.hasObject(By.text(CALCULAR)), ESPERA)
        }
        assertTrue("o aplicativo não abriu", abriu)
    }

    /**
     * O aviso de que as cotações não vieram é o mesmo para a fonte fora do ar e para uma falha do próprio
     * aplicativo — inclusive a que o R8 causaria no Retrofit, no JSON ou no Room —, e o título do cartão aparece
     * mesmo com a cotação indisponível (revisão da #74). Por isso o teste sonda a PTAX por conta própria: se ela
     * responde, o cartão tem de mostrar a cotação do Banco Central, pelo rótulo da fonte, que só existe com a cotação
     * disponível. Sem rede, o teste confere que o aplicativo não caiu e se declara pulado, não verde.
     *
     * O câmbio não é julgado aqui (CALANDR-36, decisão do operador de 04/10/2026, Discussion #98). É o aplicativo que
     * decide se aceita a cotação: com mais de 24 h, como no fim de semana, ele a recusa e a Conta Global vai para a
     * PTAX de contingência. Essa regra é coberta pelos testes de unidade do `:core:data`. O rótulo da AwesomeAPI ou do
     * Yahoo prova a rede do câmbio no minificado, e o fluxo passa. As reservas (o último valor salvo e a PTAX de
     * contingência) não provam, e o fluxo se declara pulado. Com a PTAX no ar, a Conta Global sempre mostra uma fonte;
     * sem fonte alguma, o fluxo cai.
     */
    @Test
    fun aSimulacaoMostraACotacaoDeCadaFonteQueResponde() {
        val ptax = responde(OLINDA) || responde(CSV_BCB)
        Log.i(ROTULO, "a PTAX respondeu ao teste: $ptax")
        preencherOValorECalcular()
        if (ptax) {
            assertNotNull(
                "a PTAX respondeu ao teste, mas o cartão não mostrou a cotação do Banco Central",
                esperarRolando(By.text(FONTE_PTAX), ESPERA_REDE),
            )
        } else {
            assertNotNull("nem resultado nem aviso depois de calcular", esperarRolando(By.text(Pattern.compile("$CARTAO|$SEM_COTACAO")), ESPERA_REDE))
        }
        val rotulos = "$FONTE_AWESOME|$FONTE_YAHOO|$FONTE_SALVO|${Pattern.quote(FONTE_CONTINGENCIA)}"
        val mostrada = esperarRolando(By.text(Pattern.compile(rotulos)), if (ptax) ESPERA_REDE else ESPERA)?.text
        Log.i(ROTULO, "a Conta Global usou: $mostrada")
        if (ptax) assertNotNull("a PTAX respondeu ao teste, mas a Conta Global não mostrou fonte alguma", mostrada)
        assertTrue("o processo do aplicativo morreu", processoVivo())
        assumeTrue(
            "a Conta Global não mostrou a AwesomeAPI nem o Yahoo: a rede do câmbio não foi provada",
            mostrada == FONTE_AWESOME || mostrada == FONTE_YAHOO,
        )
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
        // Teclas de verdade, pelo caminho da tecla física (CALANDR-27): os dígitos entram pelos centavos, e
        // 1 e quatro 0 viram 100,00, lido de volta antes de calcular.
        achar(By.clazz("android.widget.EditText")).click()
        aparelho.pressKeyCode(KeyEvent.KEYCODE_1)
        repeat(4) { aparelho.pressKeyCode(KeyEvent.KEYCODE_0) }
        assertNotNull("o campo não mostrou 100,00 depois das teclas", aparelho.wait(Until.findObject(By.text("100,00")), ESPERA))
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
        while (rolavel != null && voltas < 10 && rolavel.scroll(Direction.DOWN, 1.0f)) voltas++
        achar(seletor).click()
    }

    /**
     * Espera o elemento rolando a tela, porque o UI Automator só vê o que está nela: desce e sobe até o prazo, com a
     * rolagem do próprio UI Automator (`scrollUntil`), que para ao achar o elemento. Neste aplicativo o UI Automator
     * não recebe o evento de rolagem, e sem ele `scroll` responde "fim" a cada passo: a busca antiga, que trocava de
     * sentido a cada "fim", oscilava meia tela para baixo e para cima e não chegava ao cartão da Conta Global (medido
     * em 04/10/2026). Sem o evento, `scrollUntil` ainda rola cinco vezes antes de desistir.
     */
    private fun esperarRolando(seletor: BySelector, espera: Long): UiObject2? {
        val limite = SystemClock.uptimeMillis() + espera
        var sentido = Direction.DOWN
        while (SystemClock.uptimeMillis() < limite) {
            aparelho.findObject(seletor)?.let { return it }
            aparelho.findObject(By.scrollable(true))?.scrollUntil(sentido, Until.findObject(seletor))?.let { return it }
            sentido = if (sentido == Direction.DOWN) Direction.UP else Direction.DOWN
            SystemClock.sleep(500)
        }
        return null
    }

    /** A fonte responde ao próprio teste, pelos mesmos endereços que o aplicativo consulta. */
    private fun responde(endereco: String): Boolean = try {
        val conexao = URL(endereco).openConnection() as HttpURLConnection
        conexao.connectTimeout = 15_000
        conexao.readTimeout = 15_000
        conexao.setRequestProperty("User-Agent", "calculadora-android-teste-release")
        try {
            conexao.responseCode == HttpURLConnection.HTTP_OK
        } finally {
            conexao.disconnect()
        }
    } catch (erro: IOException) {
        Log.i(ROTULO, "a fonte $endereco não respondeu ao teste: $erro")
        false
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

        // Os rótulos de fonte só aparecem com a cotação disponível (`CartaoComparacao`); "Último valor salvo" e
        // "PTAX (contingência)" são reservas da Conta Global e não provam a rede.
        const val FONTE_PTAX = "PTAX do Banco Central"
        const val FONTE_AWESOME = "AwesomeAPI"
        const val FONTE_YAHOO = "Yahoo Finance"
        const val FONTE_SALVO = "Último valor salvo"
        const val FONTE_CONTINGENCIA = "PTAX (contingência)"

        // Os mesmos endereços e caminhos de `Fontes.kt` (`:core:data`), com uma data já publicada.
        const val OLINDA = "https://olinda.bcb.gov.br/olinda/servico/PTAX/versao/v1/odata/" +
            "CotacaoDolarDia(dataCotacao=@dataCotacao)?@dataCotacao='09-30-2026'&\$top=1&\$format=json"
        const val CSV_BCB = "https://www4.bcb.gov.br/Download/fechamento/20260930.csv"
    }
}
