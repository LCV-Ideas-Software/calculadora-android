/*
 * Copyright © 2026 LCV Ideas & Software
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package dev.lcv.calculadora.data.rede

import java.io.IOException
import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.ResponseBody
import okio.Buffer
import okio.ForwardingSource
import okio.buffer

/** Limita também respostas chunked e de erro, antes de o Retrofit armazená-las. */
internal class LimiteResposta : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val resposta = chain.proceed(chain.request())
        val corpo = resposta.body
        if (corpo.contentLength() > MAXIMO) {
            resposta.close()
            throw IOException("Resposta excede 1 MiB")
        }
        val limitado = object : ResponseBody() {
            private val origem = object : ForwardingSource(corpo.source()) {
                private var lidos = 0L
                override fun read(sink: Buffer, byteCount: Long): Long {
                    val n = super.read(sink, minOf(byteCount, MAXIMO - lidos + 1))
                    if (n > 0) lidos += n
                    if (lidos > MAXIMO) throw IOException("Resposta excede 1 MiB")
                    return n
                }
            }.buffer()
            override fun contentType() = corpo.contentType()
            override fun contentLength() = corpo.contentLength()
            override fun source() = origem
        }
        return resposta.newBuilder().body(limitado).build()
    }

    private companion object { const val MAXIMO = 1_048_576L }
}
