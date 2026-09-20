/*
 * Copyright © 2026 LCV Ideas & Software
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package dev.lcv.calculadora

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import dev.lcv.calculadora.ui.CalculadoraApp
import dev.lcv.calculadora.ui.CalculadoraTheme

/** Activity única: toda a navegação acontece dentro do Compose. */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // `enableEdgeToEdge()` sem argumento segue o modo escuro do aparelho e
        // pintaria os ícones das barras de branco; a interface é sempre clara
        // (desvio declarado na especificação), então as barras são declaradas
        // claras, para que os ícones fiquem escuros e legíveis em qualquer modo.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        setContent {
            CalculadoraTheme {
                CalculadoraApp()
            }
        }
    }
}
