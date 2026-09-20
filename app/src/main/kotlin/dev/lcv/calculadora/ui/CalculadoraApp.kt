/*
 * Copyright © 2026 LCV Ideas & Software
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package dev.lcv.calculadora.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import dev.lcv.calculadora.R

/**
 * Casca nativa, no modelo de porte que o operador apontou (Proton): o produto é
 * o mesmo — mesmas seções, mesmos rótulos, mesma marca —, mas a moldura é a do
 * Android. O web desenha um painel centralizado numa janela larga de navegador;
 * no telefone isso vira barra superior e conteúdo de largura cheia.
 *
 * Desvio declarado: o web anima uma tela de partículas atrás do conteúdo; aqui
 * o fundo é o gradiente claro estático da marca, porque animação permanente de
 * fundo custa bateria sem entregar informação.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalculadoraApp(viewModel: SimulacaoViewModel = hiltViewModel()) {
    var emLicencas by rememberSaveable { mutableStateOf(false) }

    BackHandler(enabled = emLicencas) { emLicencas = false }

    Scaffold(
        containerColor = Color.Transparent,
        modifier = Modifier.background(
            Brush.verticalGradient(listOf(Tema.cores.fundoNorm, Tema.cores.fundoBaixo)),
        ),
        topBar = { BarraSuperior(emLicencas) { emLicencas = !emLicencas } },
    ) { espacamento ->
        val rolagem = rememberScrollState()
        // O container de rolagem é um só; sem isto, trocar de tela mantém o
        // deslocamento e a pessoa cai no meio do texto da licença.
        LaunchedEffect(emLicencas) { rolagem.scrollTo(0) }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(espacamento)
                .indicadorDeRolagem(rolagem)
                .verticalScroll(rolagem)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(Tema.espacos.entreSecoes),
        ) {
            if (emLicencas) LicencasScreen() else SimulacaoScreen(viewModel)
            Rodape()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BarraSuperior(emLicencas: Boolean, aoAlternar: () -> Unit) {
    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
        title = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(
                    painter = painterResource(R.drawable.marca_lcv),
                    contentDescription = stringResource(R.string.marca_descricao),
                    modifier = Modifier.size(28.dp),
                )
                Column {
                    Text(
                        text = stringResource(R.string.titulo),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = Tema.cores.textoNorm,
                    )
                    if (!emLicencas) {
                        Text(
                            text = stringResource(R.string.subtitulo),
                            fontSize = 11.sp,
                            lineHeight = 14.sp,
                            color = Tema.cores.textoFraco,
                        )
                    }
                }
            }
        },
        actions = {
            TextButton(onClick = aoAlternar) {
                Text(
                    text = stringResource(
                        if (emLicencas) R.string.acao_voltar else R.string.acao_licencas,
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = Tema.cores.textoFraco,
                )
            }
        },
    )
}

/** O aviso de compliance que o rodapé do web carrega, palavra por palavra. */
@Composable
private fun Rodape() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        HorizontalDivider(color = Tema.cores.separador)
        Text(
            text = stringResource(R.string.compliance),
            fontSize = 11.sp,
            lineHeight = 16.sp,
            color = Tema.cores.textoApagado,
            textAlign = TextAlign.Justify,
        )
    }
}
