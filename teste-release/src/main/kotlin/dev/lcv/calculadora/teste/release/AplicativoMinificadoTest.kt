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
import java.time.Duration
import java.time.Instant
import java.util.regex.Pattern
import org.json.JSONException
import org.json.JSONObject
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
        instrumentacao.context.startActivity(intencao)
        assertTrue("o aplicativo não abriu", aparelho.wait(Until.hasObject(By.pkg(PACOTE).depth(0)), ESPERA))
        achar(By.text(CALCULAR))
    }

    /**
     * O aviso de que as cotações não vieram é o mesmo para a fonte fora do ar e para uma falha do próprio
     * aplicativo — inclusive a que o R8 causaria no Retrofit, no JSON ou no Room —, e o título do cartão aparece
     * mesmo com a cotação indisponível (revisão da #74). Por isso o teste sonda as fontes por conta própria: a
     * que responde obriga o aplicativo a mostrar a cotação dela, pelo rótulo da fonte, que só existe com a cotação
     * disponível. Sem fonte alguma, o teste confere que o aplicativo não caiu e se declara pulado, não verde.
     *
     * O câmbio só conta com cotação de até 24 h, a regra do próprio aplicativo (CALANDR-36): no fim de semana a
     * fonte responde com a cotação de sexta, o aplicativo a recusa e a Conta Global vai para a PTAX de contingência.
     * Sem câmbio vigente, o teste confere essa contingência e se declara pulado, porque a AwesomeAPI e o Yahoo não
     * foram exercitados.
     */
    @Test
    fun aSimulacaoMostraACotacaoDeCadaFonteQueResponde() {
        val ptax = responde(OLINDA) || responde(CSV_BCB)
        val cambio = vigente(AWESOME) { it.getJSONObject("USDBRL").getLong("timestamp") } ||
            vigente(YAHOO) {
                it.getJSONObject("chart").getJSONArray("result").getJSONObject(0).getJSONObject("meta")
                    .getLong("regularMarketTime")
            }
        Log.i(ROTULO, "fontes que responderam ao teste: PTAX=$ptax, câmbio vigente=$cambio")
        preencherOValorECalcular()
        if (!ptax && !cambio) {
            assertNotNull("nem resultado nem aviso depois de calcular", esperarRolando(By.text(Pattern.compile("$CARTAO|$SEM_COTACAO")), ESPERA_REDE))
            assertTrue("o processo do aplicativo morreu", processoVivo())
            assumeTrue("nenhuma fonte de cotação respondeu ao teste: a rede do minificado não foi exercitada", false)
        }
        if (ptax) {
            assertNotNull(
                "a PTAX respondeu ao teste, mas o cartão não mostrou a cotação do Banco Central",
                esperarRolando(By.text(FONTE_PTAX), ESPERA_REDE),
            )
        }
        if (cambio) {
            val fonte = esperarRolando(By.text(Pattern.compile("$FONTE_AWESOME|$FONTE_YAHOO")), ESPERA_REDE)
            assertNotNull("o câmbio respondeu ao teste, mas a Conta Global não mostrou a AwesomeAPI nem o Yahoo", fonte)
            Log.i(ROTULO, "a Conta Global usou: ${fonte!!.text}")
        } else {
            // Uma cotação que voltou a ser vigente entre a sonda e o cálculo também vale: o rótulo dela prova a rede.
            val fonte = esperarRolando(
                By.text(Pattern.compile("$FONTE_AWESOME|$FONTE_YAHOO|${Pattern.quote(FONTE_CONTINGENCIA)}")),
                ESPERA_REDE,
            )
            assertNotNull("sem câmbio vigente, a Conta Global não mostrou a PTAX de contingência", fonte)
            Log.i(ROTULO, "sem câmbio vigente, a Conta Global usou: ${fonte!!.text}")
            assertTrue("o processo do aplicativo morreu", processoVivo())
            assumeTrue(
                "nenhuma fonte de câmbio tem cotação de até 24 h: a AwesomeAPI e o Yahoo não foram exercitados",
                fonte.text != FONTE_CONTINGENCIA,
            )
        }
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
     * Espera o elemento rolando a tela, porque o UI Automator só vê o que está nela: desce até o fim e volta, até
     * o prazo, já que a ordem dos cartões depende de qual sai mais barato.
     */
    private fun esperarRolando(seletor: BySelector, espera: Long): UiObject2? {
        val limite = SystemClock.uptimeMillis() + espera
        var sentido = Direction.DOWN
        while (SystemClock.uptimeMillis() < limite) {
            aparelho.findObject(seletor)?.let { return it }
            val rolou = aparelho.findObject(By.scrollable(true))?.scroll(sentido, 0.5f) ?: false
            if (!rolou) sentido = if (sentido == Direction.DOWN) Direction.UP else Direction.DOWN
            SystemClock.sleep(500)
        }
        return null
    }

    /** O corpo que a fonte devolve ao próprio teste, pelos mesmos endereços que o aplicativo consulta, ou `null`. */
    private fun consultar(endereco: String): String? = try {
        val conexao = URL(endereco).openConnection() as HttpURLConnection
        conexao.connectTimeout = 15_000
        conexao.readTimeout = 15_000
        conexao.setRequestProperty("User-Agent", "calculadora-android-teste-release")
        try {
            if (conexao.responseCode != HttpURLConnection.HTTP_OK) null
            else conexao.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conexao.disconnect()
        }
    } catch (erro: IOException) {
        Log.i(ROTULO, "a fonte $endereco não respondeu ao teste: $erro")
        null
    }

    private fun responde(endereco: String): Boolean = consultar(endereco) != null

    /**
     * A fonte de câmbio responde ao teste com uma cotação de até [IDADE_MAXIMA_CAMBIO], como o aplicativo exige.
     * [instante] lê do corpo o instante da cotação, em segundos desde a época.
     */
    private fun vigente(endereco: String, instante: (JSONObject) -> Long): Boolean {
        val corpo = consultar(endereco) ?: return false
        val quando = try {
            Instant.ofEpochSecond(instante(JSONObject(corpo)))
        } catch (erro: JSONException) {
            Log.i(ROTULO, "a fonte $endereco respondeu sem o instante da cotação: $erro")
            return false
        }
        val idade = Duration.between(quando, Instant.now())
        Log.i(ROTULO, "a fonte $endereco respondeu com a cotação de $quando, com ${idade.toMinutes()} min")
        return !idade.isNegative && idade <= IDADE_MAXIMA_CAMBIO
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
        const val FONTE_CONTINGENCIA = "PTAX (contingência)"

        // A idade máxima da cotação de câmbio que o aplicativo aceita (`IDADE_MAXIMA_SPOT`, no `:core:data`).
        val IDADE_MAXIMA_CAMBIO: Duration = Duration.ofHours(24)

        // Os mesmos endereços e caminhos de `Fontes.kt` (`:core:data`), com uma data já publicada.
        const val OLINDA = "https://olinda.bcb.gov.br/olinda/servico/PTAX/versao/v1/odata/" +
            "CotacaoDolarDia(dataCotacao=@dataCotacao)?@dataCotacao='09-30-2026'&\$top=1&\$format=json"
        const val CSV_BCB = "https://www4.bcb.gov.br/Download/fechamento/20260930.csv"
        const val AWESOME = "https://economia.awesomeapi.com.br/json/last/USD-BRL"
        const val YAHOO = "https://query1.finance.yahoo.com/v8/finance/chart/BRL=X"
    }
}
