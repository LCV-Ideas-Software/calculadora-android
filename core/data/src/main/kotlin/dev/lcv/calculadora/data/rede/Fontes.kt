/*
 * Copyright © 2026 LCV Ideas & Software
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package dev.lcv.calculadora.data.rede

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * As quatro fontes, uma interface cada, como o produto web as chama. Nenhuma
 * exige chave e nenhuma recusa um User-Agent honesto (medido em 18/09 e
 * 19/09/2026). As respostas voltam cruas: o JSON é lido por [Leitores], que
 * conhece o formato de cada fonte, e o CSV pelo `CotacaoCsv` do motor.
 */

/** PTAX do Banco Central pelo serviço Olinda (OData). */
interface BcbOlinda {
    /** Dólar: um boletim por dia, já o de fechamento. `dataCotacao` em `MM-dd-yyyy`. */
    @GET("olinda/servico/PTAX/versao/v1/odata/CotacaoDolarDia(dataCotacao=@dataCotacao)?\$top=1&\$format=json")
    suspend fun cotacaoDolarDia(@Query("@dataCotacao", encoded = true) dataCotacao: String): Response<ResponseBody>

    /** Demais moedas: vários boletins por dia (Abertura, Intermediário, Fechamento). */
    @GET("olinda/servico/PTAX/versao/v1/odata/CotacaoMoedaDia(moeda=@moeda,dataCotacao=@dataCotacao)?\$format=json")
    suspend fun cotacaoMoedaDia(
        @Query("@moeda", encoded = true) moeda: String,
        @Query("@dataCotacao", encoded = true) dataCotacao: String,
    ): Response<ResponseBody>

    companion object {
        const val URL_BASE = "https://olinda.bcb.gov.br/"
    }
}

/** CSV de fechamento do Banco Central: contingência da PTAX. */
interface BcbFechamento {
    /** `data` em `yyyyMMdd`. O arquivo do dia corrente ainda não existe (404). */
    @GET("Download/fechamento/{data}.csv")
    suspend fun csv(@Path("data") data: String): Response<ResponseBody>

    companion object {
        const val URL_BASE = "https://www4.bcb.gov.br/"
    }
}

/** Taxa spot da AwesomeAPI (camada cacheada, sem chave, como o produto web). */
interface AwesomeApi {
    /** `par` no formato `USD-BRL`. */
    @GET("json/last/{par}")
    suspend fun ultima(@Path("par") par: String): Response<ResponseBody>

    companion object {
        const val URL_BASE = "https://economia.awesomeapi.com.br/"
    }
}

/** Contingência da taxa spot (risco aceito na spec, seção 10). */
interface YahooFinance {
    /** `simbolo` é `BRL=X` para o dólar e `EURBRL=X` para as demais. */
    @GET("v8/finance/chart/{simbolo}")
    suspend fun grafico(@Path("simbolo") simbolo: String): Response<ResponseBody>

    companion object {
        const val URL_BASE = "https://query1.finance.yahoo.com/"
    }
}
