/*
 * Copyright © 2026 LCV Ideas & Software
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package dev.lcv.calculadora.ui

import androidx.compose.ui.text.TextRange
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.testing.viewModelScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A16: a morte do processo devolve o texto e a seleção de cada campo, e nenhum resultado. A simulação é a oficial
 * do `lifecycle-viewmodel-testing` (`ViewModelScenario.recreate()`): salva o estado pelo registro, passa-o por um
 * `Parcel` de verdade, recria o dono e o `ViewModel`. Por causa do `Parcel`, roda no aparelho (decisão do operador,
 * 03/10/2026, Discussion #80).
 */
@RunWith(AndroidJUnit4::class)
class RestauracaoDosCamposTest {

    private val instrumentacao = InstrumentationRegistry.getInstrumentation()

    @Test
    fun aMorteDoProcessoDevolveOsCamposSemOResultado() {
        viewModelScenario { viewModelEmMemoria(createSavedStateHandle()) }.use { cenario ->
            // O estado dos campos só se escreve na thread principal (especificação, seção 4.1).
            instrumentacao.runOnMainSync {
                val vm = cenario.viewModel
                vm.mudarModo(Modo.COBRADO_EM_REAIS)
                vm.campoValor.edit { replace(0, length, "123456") }
                vm.campoIof.edit {
                    replace(0, length, "350")
                    selection = TextRange(0, length)
                }
                vm.calcular()
            }
            // As conferências ficam fora do `runOnMainSync`: uma falha na thread principal derrubaria o processo.
            var calculado: Any? = null
            instrumentacao.runOnMainSync { calculado = cenario.viewModel.estado.value.compraEmReais }
            assertNotNull("o cálculo em reais termina dentro do próprio calcular()", calculado)

            cenario.recreate()

            var valor = ""
            var selecaoDoValor = TextRange.Zero
            var iof = ""
            var selecaoDoIof = TextRange.Zero
            var modo: Modo? = null
            var resultadoRestaurado: Any? = Unit
            instrumentacao.runOnMainSync {
                val novo = cenario.viewModel
                valor = novo.campoValor.text.toString()
                selecaoDoValor = novo.campoValor.selection
                iof = novo.campoIof.text.toString()
                selecaoDoIof = novo.campoIof.selection
                modo = novo.estado.value.modo
                resultadoRestaurado = novo.estado.value.compraEmReais
            }
            assertEquals("123456", valor)
            assertEquals(TextRange(6), selecaoDoValor)
            assertEquals("350", iof)
            assertEquals("a seleção também volta", TextRange(0, 3), selecaoDoIof)
            assertEquals(Modo.COBRADO_EM_REAIS, modo)
            assertNull("nenhum resultado volta com a restauração", resultadoRestaurado)
        }
    }
}
