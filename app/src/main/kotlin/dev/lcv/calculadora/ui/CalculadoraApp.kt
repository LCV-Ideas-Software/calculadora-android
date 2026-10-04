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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
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
    // O cabeçalho do web rola com a página: sai ao descer e só volta no topo. A barra faz o mesmo pelo
    // recolhimento do Material 3, que recolhe a altura inteira medida, também quando a fonte grande a deixa mais
    // alta que os 64 dp padrão; fixa, ela tomava um quarto da tela com a fonte no máximo. Com o teclado aberto ela
    // não se move: a rolagem que leva o campo em foco para cima do teclado também chega a ela, pela rolagem
    // aninhada, e a deixaria recolhida pela metade, cortada (CALANDR-31).
    val teclado = WindowInsets.ime
    val densidade = LocalDensity.current
    val recolhimento = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(
        canScroll = remember(teclado, densidade) { { teclado.getBottom(densidade) == 0 } },
    )

    BackHandler(enabled = emLicencas) { emLicencas = false }

    Scaffold(
        containerColor = Color.Transparent,
        modifier = Modifier
            .background(Brush.verticalGradient(listOf(Tema.cores.fundoNorm, Tema.cores.fundoBaixo)))
            .nestedScroll(recolhimento.nestedScrollConnection),
        topBar = { BarraSuperior(emLicencas, recolhimento) { emLicencas = !emLicencas } },
    ) { espacamento ->
        val rolagem = rememberScrollState()
        // O container de rolagem é um só; sem isto, trocar de tela mantém o
        // deslocamento e a pessoa cai no meio do texto da licença. A barra volta
        // junto: o `scrollTo` não passa pela rolagem aninhada e a deixaria
        // recolhida no topo da outra tela.
        LaunchedEffect(emLicencas) {
            rolagem.scrollTo(0)
            recolhimento.state.heightOffset = 0f
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(espacamento)
                // O recuo do Scaffold já cobre as barras do sistema; o do teclado acrescenta só o que ele ocupa a
                // mais, para o campo em foco rolar para cima dele (CALANDR-31, com `adjustResize` no manifesto).
                .consumeWindowInsets(espacamento)
                .imePadding()
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
private fun BarraSuperior(emLicencas: Boolean, recolhimento: TopAppBarScrollBehavior, aoAlternar: () -> Unit) {
    TopAppBar(
        // Transparente também com o conteúdo rolado: a cor de rolagem padrão do Material pintaria a faixa da barra
        // de status, que o web não tem.
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
            scrolledContainerColor = Color.Transparent,
        ),
        scrollBehavior = recolhimento,
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
