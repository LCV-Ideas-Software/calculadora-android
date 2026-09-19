/*
 * Copyright © 2026 LCV Ideas & Software
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package dev.lcv.calculadora.data.persistencia

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.math.BigDecimal
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * O SQL real dos DAOs num banco em memória do aparelho: o que os testes na
 * JVM não provam (lá os DAOs são falsos). Executado no AVD local antes da PR;
 * a CI não tem emulador.
 */
@RunWith(AndroidJUnit4::class)
class CalculadoraDatabaseTest {
    private lateinit var db: CalculadoraDatabase

    @Before
    fun abrir() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), CalculadoraDatabase::class.java).build()
    }

    @After
    fun fechar() {
        db.close()
    }

    @Test
    fun ptaxCache_guardaELeExatamente_eSubstituiPelaChave() = runBlocking {
        val dia = LocalDate.of(2026, 9, 18)
        db.ptaxCache().guardar(PtaxCacheEntity("USD", dia, BigDecimal("5.15750")))
        db.ptaxCache().guardar(PtaxCacheEntity("USD", dia, BigDecimal("5.15751")))
        assertEquals(BigDecimal("5.15751"), db.ptaxCache().buscar("USD", dia)?.taxa)
        assertNull(db.ptaxCache().buscar("EUR", dia))
        assertNull(db.ptaxCache().buscar("USD", dia.minusDays(1)))
    }

    @Test
    fun ultimoSpot_umaLinhaPorMoeda() = runBlocking {
        db.ultimoSpot().guardar(UltimoSpotEntity("USD", BigDecimal("5.140006"), obtidoEm = 1L))
        db.ultimoSpot().guardar(UltimoSpotEntity("USD", BigDecimal("5.150000"), obtidoEm = 2L))
        val linha = db.ultimoSpot().buscar("USD")
        assertEquals(BigDecimal("5.150000"), linha?.taxaCalibrada)
        assertEquals(2L, linha?.obtidoEm)
    }

    @Test
    fun backtest_janelaOrdemLimiteEPoda() = runBlocking {
        val dao = db.backtest()
        val dia = LocalDate.of(2026, 9, 18)
        fun obs(criadoEm: Long) = ObservacaoBacktestEntity(
            criadoEm = criadoEm, moeda = "USD", dataCompra = dia,
            taxaPrevista = BigDecimal("5.14"), taxaObservada = BigDecimal("5.1575"), erroPercentual = BigDecimal("0.003394"),
        )
        for (t in listOf(100L, 300L, 200L, 50L)) dao.inserir(obs(t))

        val desde100 = dao.desde(desde = 100L, limite = 10)
        assertEquals(listOf(300L, 200L, 100L), desde100.map { it.criadoEm })
        assertEquals(listOf(300L, 200L), dao.desde(desde = 100L, limite = 2).map { it.criadoEm })
        assertEquals(BigDecimal("0.003394"), desde100.first().erroPercentual)

        assertEquals(2, dao.apagarAnterioresA(antesDe = 200L))
        assertEquals(listOf(300L, 200L), dao.desde(desde = 0L, limite = 10).map { it.criadoEm })
    }
}
