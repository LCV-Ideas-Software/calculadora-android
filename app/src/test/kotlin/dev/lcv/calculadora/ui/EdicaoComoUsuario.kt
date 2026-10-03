/*
 * Copyright © 2026 LCV Ideas & Software
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package dev.lcv.calculadora.ui

import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.maxLength

/**
 * Uma edição do usuário encenada na JVM: a mudança e, no mesmo `edit`, a transformação de entrada do campo, com o
 * limite de dígitos dele. É o mesmo `TextFieldBuffer`, com as mudanças registradas, que a plataforma entrega à
 * transformação; os caminhos reais (tecla, colagem, acessibilidade) são dos testes de tela.
 */
internal fun TextFieldState.editarComoUsuario(
    tipo: TipoNumerico = TipoNumerico.DINHEIRO,
    edicao: TextFieldBuffer.() -> Unit,
) {
    val transformacao = DigitosDeCaixa.maxLength(tipo.maximoDeDigitos)
    edit {
        edicao()
        with(transformacao) { transformInput() }
    }
}

/** O usuário digita ou cola [texto] sobre a seleção atual. */
internal fun TextFieldState.digitarComoUsuario(texto: String, tipo: TipoNumerico = TipoNumerico.DINHEIRO) =
    editarComoUsuario(tipo) { replace(selection.min, selection.max, texto) }
