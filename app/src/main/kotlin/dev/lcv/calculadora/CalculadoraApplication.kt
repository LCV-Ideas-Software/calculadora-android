/*
 * Copyright © 2026 LCV Ideas & Software
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package dev.lcv.calculadora

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/** Raiz do grafo de dependências: o Hilt precisa de uma aplicação anotada. */
@HiltAndroidApp
class CalculadoraApplication : Application()
