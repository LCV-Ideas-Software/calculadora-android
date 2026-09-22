/*
 * Copyright © 2026 LCV Ideas & Software
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package dev.lcv.calculadora.data.cotacoes

import dev.lcv.calculadora.calc.FonteSpot
import dev.lcv.calculadora.data.DataModule
import dev.lcv.calculadora.data.assertDecimal
import dev.lcv.calculadora.data.fixture
import dev.lcv.calculadora.data.rede.AwesomeApi
import dev.lcv.calculadora.data.rede.BcbFechamento
import dev.lcv.calculadora.data.rede.BcbOlinda
import dev.lcv.calculadora.data.rede.IdentidadeAplicativo
import dev.lcv.calculadora.data.rede.YahooFinance
import java.time.LocalDate
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.assertFailsWith
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import retrofit2.Retrofit

/**
 * As quatro fontes atrás de um único servidor falso: cada teste enfileira as
 * respostas na ordem em que o provedor as pedirá, e a fila de requisições
 * prova quem foi chamado e com que caminho.
 */
class ProvedoresTest {
    private lateinit var servidor: MockWebServer
    private lateinit var cliente: OkHttpClient
    private val dia = LocalDate.of(2026, 9, 18)

    @BeforeTest
    fun subir() {
        servidor = MockWebServer()
        servidor.start()
        cliente = DataModule.okHttp(IdentidadeAplicativo("0.1.0"))
    }

    @AfterTest
    fun derrubar() {
        servidor.close()
    }

    private inline fun <reified T : Any> fonte(): T =
        Retrofit.Builder().client(cliente).baseUrl(servidor.url("/")).build().create(T::class.java)

    private fun ptaxBcb() = ProvedorPtaxBcb(fonte<BcbOlinda>(), fonte<BcbFechamento>())

    private fun spotWeb() = ProvedorSpotWeb(fonte<AwesomeApi>(), fonte<YahooFinance>(),
        java.time.Clock.fixed(java.time.Instant.parse("2026-09-18T23:00:00Z"), java.time.ZoneOffset.UTC))

    private fun resposta(codigo: Int, corpo: String = "") = MockResponse.Builder().code(codigo).body(corpo).build()

    @Test
    fun `dolar pela Olinda - uma requisicao, com o dia em MM-dd-yyyy entre aspas`() = runTest {
        servidor.enqueue(resposta(200, fixture("olinda-dolar-2026-09-18.json")))
        assertDecimal("5.15750", ptaxBcb().ptaxDoDia("USD", dia))
        assertEquals(1, servidor.requestCount)
        val pedido = servidor.takeRequest()
        // O Retrofit envia as aspas como %27; a Olinda real aceita as duas formas (medido em 19/09/2026).
        assertEquals("/olinda/servico/PTAX/versao/v1/odata/CotacaoDolarDia(dataCotacao=@dataCotacao)?\$top=1&\$format=json&@dataCotacao=%2709-18-2026%27", pedido.url.encodedPath + "?" + pedido.url.encodedQuery)
        assertEquals("calculadora-android/0.1.0 (Android; +https://calculadora.lcv.dev)", pedido.headers["User-Agent"])
    }

    @Test
    fun `euro pela Olinda - boletim de fechamento, nao a abertura`() = runTest {
        servidor.enqueue(resposta(200, fixture("olinda-eur-2026-09-18.json")))
        assertDecimal("5.91260", ptaxBcb().ptaxDoDia("EUR", dia))
        val pedido = servidor.takeRequest()
        assertEquals("/olinda/servico/PTAX/versao/v1/odata/CotacaoMoedaDia(moeda=@moeda,dataCotacao=@dataCotacao)", pedido.url.encodedPath)
        assertEquals("\$format=json&@moeda=%27EUR%27&@dataCotacao=%2709-18-2026%27", pedido.url.encodedQuery)
    }

    @Test
    fun `dia sem boletim na Olinda e dia sem cotacao - o CSV nao e consultado`() = runTest {
        servidor.enqueue(resposta(200, fixture("olinda-dolar-2026-09-13-sabado.json")))
        assertNull(ptaxBcb().ptaxDoDia("USD", LocalDate.of(2026, 9, 13)))
        assertEquals(1, servidor.requestCount, "nenhuma segunda requisicao")
    }

    @Test
    fun `Olinda indisponivel - o CSV de fechamento do mesmo dia responde`() = runTest {
        servidor.enqueue(resposta(503))
        servidor.enqueue(resposta(200, fixture("fechamento-20260918.csv")))
        assertDecimal("6.89660000", ptaxBcb().ptaxDoDia("GBP", dia))
        assertEquals(2, servidor.requestCount)
        servidor.takeRequest()
        assertEquals("/Download/fechamento/20260918.csv", servidor.takeRequest().url.encodedPath)
    }

    @Test
    fun `Olinda ilegivel tambem cai no CSV`() = runTest {
        servidor.enqueue(resposta(200, "<html>manutencao</html>"))
        servidor.enqueue(resposta(200, fixture("fechamento-20260918.csv")))
        assertDecimal("5.15750000", ptaxBcb().ptaxDoDia("USD", dia))
    }

    @Test
    fun `Olinda indisponivel e CSV inexistente (404 do dia corrente) e null`() = runTest {
        servidor.enqueue(resposta(503))
        servidor.enqueue(resposta(404))
        assertNull(ptaxBcb().ptaxDoDia("USD", dia))
        assertEquals(2, servidor.requestCount)
    }

    @Test
    fun `moeda fora da Olinda vai direto ao CSV`() = runTest {
        servidor.enqueue(resposta(200, fixture("fechamento-20260918.csv")))
        assertNull(ptaxBcb().ptaxDoDia("XYZ", dia), "moeda que o CSV nao traz")
        assertEquals(1, servidor.requestCount)
        assertEquals("/Download/fechamento/20260918.csv", servidor.takeRequest().url.encodedPath)
    }

    @Test
    fun `spot pela AwesomeAPI - bid bruto, fonte AWESOME_API, sem chave e com o par no caminho`() = runTest {
        servidor.enqueue(resposta(200, fixture("awesome-usd-brl.json")))
        val spot = assertNotNull(spotWeb().spotBruta("USD"))
        assertDecimal("5.1434", spot.taxa)
        assertEquals(FonteSpot.AWESOME_API, spot.fonte)
        val pedido = servidor.takeRequest()
        assertEquals("/json/last/USD-BRL", pedido.url.encodedPath)
        assertNull(pedido.url.encodedQuery, "nenhuma chave de API")
    }

    @Test
    fun `AwesomeAPI indisponivel - Yahoo responde, com BRL=X para o dolar`() = runTest {
        servidor.enqueue(resposta(500))
        servidor.enqueue(resposta(200, fixture("yahoo-brl-x.json")))
        val spot = assertNotNull(spotWeb().spotBruta("USD"))
        assertDecimal("5.1421", spot.taxa)
        assertEquals(FonteSpot.YAHOO_FINANCE, spot.fonte)
        servidor.takeRequest()
        assertEquals("/v8/finance/chart/BRL=X", servidor.takeRequest().url.encodedPath)
    }

    @Test
    fun `AwesomeAPI sem o campo bid tambem cai no Yahoo, e o euro pede EURBRL=X`() = runTest {
        servidor.enqueue(resposta(200, """{"EURBRL":{"code":"EUR"}}"""))
        servidor.enqueue(resposta(200, fixture("yahoo-brl-x.json")))
        assertEquals(FonteSpot.YAHOO_FINANCE, assertNotNull(spotWeb().spotBruta("EUR")).fonte)
        servidor.takeRequest()
        assertEquals("/v8/finance/chart/EURBRL=X", servidor.takeRequest().url.encodedPath)
    }

    @Test
    fun `nenhuma fonte de spot responde - null, sem excecao`() = runTest {
        servidor.enqueue(resposta(500))
        servidor.enqueue(resposta(429))
        assertNull(spotWeb().spotBruta("USD"))
        assertEquals(2, servidor.requestCount)
    }

    private fun respostaLenta() = MockResponse.Builder().code(200).body("{}").headersDelay(3, TimeUnit.SECONDS).build()

    @Test
    fun `cancelar a corrotina durante a Olinda propaga o cancelamento e nao dispara o CSV`() = runBlocking {
        servidor.enqueue(respostaLenta())
        servidor.enqueue(resposta(200, fixture("fechamento-20260918.csv")))
        val provedor = ptaxBcb()
        val tarefa = launch {
            assertFailsWith<CancellationException> { provedor.ptaxDoDia("USD", dia) }
        }
        delay(300)
        tarefa.cancel()
        tarefa.join()
        assertEquals(1, servidor.requestCount, "o CSV nao foi pedido: cancelamento nao e indisponibilidade")
    }

    @Test
    fun `cancelar a corrotina durante a AwesomeAPI propaga o cancelamento e nao dispara o Yahoo`() = runBlocking {
        servidor.enqueue(respostaLenta())
        servidor.enqueue(resposta(200, fixture("yahoo-brl-x.json")))
        val provedor = spotWeb()
        val tarefa = launch {
            assertFailsWith<CancellationException> { provedor.spotBruta("USD") }
        }
        delay(300)
        tarefa.cancel()
        tarefa.join()
        assertEquals(1, servidor.requestCount, "o Yahoo nao foi pedido")
    }

    @Test
    fun `resposta de erro tem o corpo fechado`() = runTest {
        servidor.enqueue(resposta(404, "nao encontrado"))
        assertNull(ptaxBcb().ptaxDoDia("XYZ", dia))
    }

    @Test
    fun `o cliente HTTP tem o tempo limite total de 4 s do produto web`() {
        assertEquals(4_000, cliente.callTimeoutMillis)
    }

    @Test fun `resposta muito grande nao e carregada integralmente`() = runTest {
        servidor.enqueue(resposta(200, " ".repeat(1_048_577)))
        servidor.enqueue(resposta(404))
        assertNull(ptaxBcb().ptaxDoDia("USD", dia))
    }

    @Test fun `CSV de outro dia nao e atribuido a data pedida`() = runTest {
        servidor.enqueue(resposta(503))
        servidor.enqueue(resposta(200, "17/09/2026;220;A;USD;5,1;5,2;1;1"))
        assertNull(ptaxBcb().ptaxDoDia("USD", dia))
    }

    @Test fun `corpo chunked de sucesso e limitado durante a leitura`() = runTest {
        servidor.enqueue(MockResponse.Builder().code(200).chunkedBody("x".repeat(1_048_577), 8192).build())
        val resposta = fonte<AwesomeApi>().ultima("USD-BRL")
        assertFailsWith<java.io.IOException> { resposta.body()!!.use { it.bytes() } }
    }

    @Test fun `corpo chunked de erro e limitado antes do buffering do Retrofit`() = runTest {
        servidor.enqueue(MockResponse.Builder().code(400).chunkedBody("x".repeat(1_048_577), 8192).build())
        assertFailsWith<java.io.IOException> { fonte<AwesomeApi>().ultima("USD-BRL") }
    }

    @Test fun `corpo no limite exato de um MiB e aceito`() = runTest {
        servidor.enqueue(MockResponse.Builder().code(200).chunkedBody("x".repeat(1_048_576), 8192).build())
        val resposta = fonte<AwesomeApi>().ultima("USD-BRL")
        assertEquals(1_048_576, resposta.body()!!.use { it.bytes().size })
    }

    @Test fun `Awesome com timestamp expirado tenta Yahoo vigente`() = runTest {
        servidor.enqueue(resposta(200, """{"USDBRL":{"bid":"5.00","timestamp":"1"}}"""))
        servidor.enqueue(resposta(200, fixture("yahoo-brl-x.json")))
        assertEquals(FonteSpot.YAHOO_FINANCE, assertNotNull(spotWeb().spotBruta("USD")).fonte)
        assertEquals(2, servidor.requestCount)
    }
}
