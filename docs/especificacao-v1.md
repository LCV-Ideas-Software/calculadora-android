# Especificação — Calculadora Android v1

Decisões do operador em 18/09/2026. Este documento registra **o que foi decidido
e por quê**, para que nenhuma escolha precise ser redescoberta ou relitigada.

## 1. Do que se trata

A Calculadora Android é o **port nativo** da `calculadora-app`: um simulador que
compara, para pessoa física no Brasil, quanto custa comprar moeda estrangeira
pelo **cartão de crédito** e por uma **conta global**, considerando PTAX, taxa
spot, IOF, spread e impacto fiscal.

**Port, não clone.** O aplicativo reimplementa as *lógicas* do produto web em
código Kotlin novo. Não é um empacotamento do aplicativo existente, não carrega
webview e não executa código do produto web por baixo. Onde a plataforma Android
oferecer caminho melhor que o do navegador, o caminho melhor é o escolhido —
`BigDecimal` na aritmética é o primeiro exemplo, e está na seção 6.

**A aparência também é portada** (ordem do operador, 20/09/2026: *"se é um porte
similar e nativo, inclusive a aparência é similar"*). A interface reproduz a do
produto web — estrutura das telas, hierarquia, rótulos, espaçamentos e raios —
com componentes nativos e com as cores e o ícone da marca. A frase acima sobre
"caminho melhor da plataforma" governa a **implementação**, não a aparência do
produto: o padrão visual do framework não substitui uma decisão de produto que
já existe no web. O que não se reproduz com honestidade num aparelho — desfoque
de fundo ao vivo, animação permanente atrás do conteúdo — é registrado como
desvio declarado, nunca trocado em silêncio.

São seis, e só seis, os desvios de aparência do `:app`. Os dois primeiros
vêm da entrega (CALANDR-16). O terceiro e o quarto vêm da correção do selo do
cartão vencedor, por decisões do operador em 03 e 04/10/2026 (CALANDR-32), e
só aparecem com fonte grande ou tela estreita; no tamanho padrão, eles não
aparecem. Os dois últimos são de cor, para o texto passar no contraste mínimo
AA (4,5:1), por decisões do operador em 04/10/2026 (CALANDR-34).

1. **Não há a tela de partículas animada atrás do conteúdo.** O web anima um
   `canvas` permanente no fundo. Num telefone isso mantém a GPU e o
   recompositor acordados enquanto a tela estiver aberta, e o que a animação
   entrega é decoração, não informação. O fundo aqui é o gradiente claro
   estático da marca.
2. **Não há paleta escura.** A marca define tons claros; um tema escuro exigiria
   inventar tons que ela não tem, o que seria criar identidade em vez de portá-la.
   O tema é declaradamente claro até que a marca defina os tons escuros.
3. **Com fonte grande, o título do cartão vencedor deixa espaço para o selo.**
   O selo "⭐ MELHOR" fica sobreposto ao canto superior direito do cartão, como
   no web. No Android, a fonte cresce com a escala do sistema, mas a margem do
   cartão não; com fonte grande, o selo desceria sobre a primeira linha do
   título. Só nesse caso, a linha do título deixa livre, no fim, a parte do
   selo que fica sobre o conteúdo do cartão, mais um vão de 8 dp, e o título
   quebra antes dele. O web não faz essa reserva.
4. **Com fonte grande, só o valor quebra, e ele desce se não couber.** Nas
   linhas de rótulo e valor dos cartões, quando os dois não cabem lado a lado,
   o rótulo fica com a largura dele, e o valor quebra entre palavras no espaço
   ao lado, ainda à direita ("R$" em cima de "545,99", ao lado de "Total em
   Reais"). Quando nem a maior palavra do valor cabe ao lado do rótulo, o valor
   desce para a linha de baixo. Nada é partido no meio da palavra. No web, os
   dois lados encolhem e quebram entre palavras. O `FlexBox` oficial do
   Compose 1.12.1 faria o mesmo, mas ainda é API experimental
   (`@ExperimentalFlexBoxApi`), e o operador decidiu, em 04/10/2026, manter o
   `FlowRow`. Até a 1.0.2, a linha media o rótulo primeiro e deixava ao valor
   só a sobra, que o partia ao meio ("R$ 5.896,|40").
5. **O texto da pílula da qualidade do backtest é um tom mais escuro que o do
   web.** Os fundos são os do web; o texto usa o tom 800 da mesma cor (#166534,
   #854D0E, #991B1B) no lugar do 700 do web (#15803D, #A16207, #B91C1C). No
   fundo do painel, o tom do web deixa "🏆 Excelente" com 4,0:1 e "✅ Boa" com
   4,2:1.
6. **Os rótulos do cartão de cenário mantêm o cinza dos rótulos do app.**
   "Total em Reais" e "Acréscimo sobre o preço" usam o #475569, com o tamanho e
   o peso do web. Sobre o fundo dos cartões de cenário, o `text-slate-500` do
   web (#62748E) fica de 4,2 a 4,4:1.

Tudo o mais que difere é imposto pela plataforma, não escolhido: barra superior
e conteúdo de largura cheia no lugar do painel centralizado que o navegador
desenha numa janela larga, diálogo de data do Material no lugar do
`<input type="date">`, e a folha de compartilhamento do Android no lugar do
botão de copiar para a área de transferência. A barra superior é o começo do
conteúdo e rola com ele, como o cabeçalho do web: sai ao descer e só volta no
topo. Com o teclado aberto, quando o campo em foco precisa subir, ela rola
junto, como no web, e nunca fica por baixo da barra de status (decisão do
operador de 04/10/2026, CALANDR-35).

## 2. Escopo

Entra **tudo o que o produto web faz, exceto inteligência artificial e envio de
e-mail**.

| Unidade | Origem no produto web | Linhas |
| -- | -- | -- |
| Motor de cálculo cartão × conta global | `functions/api/calcular.js` | 476 |
| Modo "cobrado em reais" (DCC), três cenários de fatura reversa | `functions/api/compra-reais.mjs` | 87 |
| Parâmetros: IOF, spreads, calibragem, limiares | `functions/api/parametros.js` | 76 |
| Análise de sensibilidade | `functions/api/sensibilidade.mjs` | 67 |
| Contexto operacional: horário, feriado, plantão | `functions/api/contexto-operacional.mjs` | 64 |
| Backtest: erro percentual, MAPE, classificação | `functions/api/backtest.mjs` | 21 |
| Leitura do CSV de fechamento do Banco Central | `functions/api/cotacao-csv.mjs` | 17 |
| Fator de calibragem | `functions/api/_shared/calibragem.mjs` | 11 |

Aproximadamente **820 linhas de lógica de servidor** a reimplementar.

A elas se somam as lógicas que hoje vivem **no cliente web** e que também
entram — não são inteligência artificial nem envio de e-mail:

| Unidade | Origem no produto web | Linhas |
| -- | -- | -- |
| Compartilhar e copiar a simulação | `src/services/whatsapp.ts` | 79 |
| Formatação, parsing localizado e moedas suportadas | `src/services/formatting.ts` | 140 |
| Validação do formulário de simulação | `src/hooks/useSimulation.ts` | 228 |
| Modelo de leitura do backtest: contagem, MAPE, últimas 20 observações | `functions/api/backtest.js` | 50 |
| Exibição das licenças: LICENSE, NOTICE, THIRDPARTY | `src/modules/compliance/LicencasModule.tsx` | — |

No Android, o compartilhamento usa a folha de compartilhamento nativa
(`Intent.ACTION_SEND`) em vez de um endereço do WhatsApp, e a tela de licenças
é obrigação da própria AGPL. O total a portar fica em torno de **1.300 linhas**.

`src/services/storage.ts` (70 linhas) guarda apenas histórico e telemetria da
inteligência artificial, e sai com ela.

**Moedas suportadas: USD, EUR e GBP** — `SUPPORTED_CURRENCIES` em
`formatting.ts`. O backtest registra observações apenas para USD e EUR, como no
produto web.

**Fora do escopo, por decisão do operador:**

| Unidade | Motivo |
| -- | -- |
| `oraculo.ts` (327 linhas) e `oraculo-observabilidade.js` (80) | sem funcionalidade de inteligência artificial |
| `enviar-email.js` (80) e `contato.js` | sem envio de e-mail |

`functions/api/_shared/security.js` fica inteiramente de fora: contém
cabeçalhos de resposta, lista de origens permitidas, extração do IP do cliente e
*rate limiting* — tudo preocupação de servidor, sem contraparte num aplicativo
que fala direto com as fontes. A validação de entrada que importa ao aplicativo
está em `useSimulation.ts` e na leitura do payload em `calcular.js`.

Essas exclusões são coerentes com as decisões de produto já registradas no
[`README.md`](../README.md): nenhuma funcionalidade de inteligência artificial,
zero analytics, nenhuma migração de telemetria ou log de IP do produto web.

## 3. O backtest não exige servidor, e isso foi medido

O nome engana. O backtest **não é** análise histórica de mercado: é
auto-monitoramento da calibragem. A cada cálculo, o produto web grava a taxa
prevista (spot calibrado) contra a taxa observada (PTAX) e o erro percentual
entre as duas; o MAPE é calculado sobre os **últimos sete dias**, limitado aos
200 registros mais recentes, e classificado em `excelente`, `boa` ou `atencao`.

As funções são puras e somam 21 linhas. A série é alimentada pelas próprias
execuções, então o aplicativo a reproduz integralmente com persistência local.

**Consequência aceita:** a série começa vazia. Como a janela é de sete dias, ela
se preenche com o uso normal, e o MAPE passa a medir a calibragem nas execuções
daquele aparelho — o que é mais honesto do que herdar a série de outro ambiente.

## 4. Arquitetura

```
:core:calc    Kotlin puro, sem dependência de Android — motor e regras
:core:data    fontes de cotação, cache local, persistência do backtest
:app          interface Compose e ViewModels
```

O módulo `:core:calc` **não depende de Android**. Essa fronteira é deliberada:
permite executar toda a aritmética financeira na JVM, sem emulador, em
milissegundos — e é o que torna o teste da parte que lida com dinheiro barato o
bastante para ser executado a cada mudança.

### Tecnologias

Kotlin, Jetpack Compose, Material 3, ViewModel com `StateFlow`, Room para a
série do backtest, Hilt para injeção de dependência e Retrofit/OkHttp para rede.
Tudo AndroidX oficial. Esta lista previa também DataStore para preferências; a
entrega do `:app` (CALANDR-16) mostrou que não há preferência a guardar — os
parâmetros do formulário são sobreposições de uma simulação, não configuração do
usuário —, então a dependência não entrou. Guardar nada em DataStore seria
carregar uma biblioteca para não usá-la.

O projeto passa a declarar o Kotlin Gradle Plugin. O `ignore` correspondente em
[`.github/dependabot.yml`](../.github/dependabot.yml) deve ser removido na mesma
mudança — o próprio arquivo carrega essa instrução inline desde 17/09/2026.

## 5. Fontes de cotação — busca direta

O aplicativo busca as cotações **diretamente nas fontes**, sem endpoint
intermediário e sem depender de nenhum serviço nosso em execução.

| Fonte | Papel |
| -- | -- |
| BCB Olinda (`olinda.bcb.gov.br`) | PTAX oficial, dólar e demais moedas |
| CSV de fechamento do BCB (`www4.bcb.gov.br`) | contingência da PTAX |
| AwesomeAPI (`economia.awesomeapi.com.br`) | taxa spot |
| Yahoo Finance (`query1.finance.yahoo.com`) | contingência da taxa spot |

**Medido em 18/09/2026**, com User-Agent próprio do aplicativo e sem chave de
API:

| Verificação | Resultado |
| -- | -- |
| BCB Olinda, User-Agent honesto | HTTP 200, 0,11 s |
| AwesomeAPI, User-Agent honesto, sem chave | HTTP 200, 0,19 s, dados válidos |
| AwesomeAPI, sem User-Agent algum | HTTP 200 |
| CSV de fechamento do BCB, User-Agent honesto | HTTP 200, 10.682 bytes |
| Yahoo Finance, User-Agent honesto | HTTP 200, 0,94 s |
| Yahoo Finance, sem User-Agent algum | **HTTP 429** |

Três conclusões dessa medição:

1. **Nenhuma fonte recusa um User-Agent honesto.** O `Mozilla/5.0` presente no
   código do produto web é hábito herdado, não exigência. O aplicativo se
   identifica como o que é.
2. **O Yahoo Finance recusa a ausência de User-Agent** (HTTP 429). Como o
   aplicativo sempre envia o seu, não é um problema — mas é exigência real, e por
   isso fica registrada.
3. **Nenhuma fonte exige chave, e o aplicativo não usa nenhuma.** A documentação
   da AwesomeAPI é explícita: sem chave, as respostas vêm de cache; com chave,
   dados em tempo real e 100.000 requisições gratuitas. O produto web não usa
   chave e já recebe a camada cacheada; o aplicativo herda exatamente o mesmo
   comportamento. Uma chave embarcada num binário distribuído seria credencial
   exposta, e por isso não é opção.

O cache é local ao aparelho. A PTAX é diária, então um cache local atende bem;
a taxa spot muda por minuto e é cacheada por janela curta.

## 6. Aritmética: `BigDecimal`

O produto web calcula tudo em `Number` do JavaScript, que é ponto flutuante de
dupla precisão. O aplicativo usa **`BigDecimal`**, por decisão do operador.

O motivo é direto: o resultado é dinheiro, e ponto flutuante binário não
representa exatamente valores decimais. `BigDecimal` é a ferramenta correta para
essa classe de cálculo, e o fato de o produto web usar `double` é limitação do
JavaScript, não um alvo a perseguir.

**Consequência declarada:** os resultados podem diferir do produto web nas casas
menos significativas. Isso é esperado e aceito — um port não é clone. Os testes
verificam a **regra de negócio** (IOF de 3,5%, spread aplicável, calibragem),
não a igualdade com a saída de outro programa.

Escala e modo de arredondamento são fixados explicitamente em cada operação,
nunca herdados do padrão da linguagem.

## 7. Parâmetros

Os valores de IOF, spreads, calibragem e limiares do backtest são constantes do
aplicativo.

**Medido no D1 de produção em 18/09/2026:** a tabela `calc_parametros_customizados`
existe e contém 30 linhas, mas **os oito parâmetros gravados são idênticos aos
padrões do código**, incluindo `fator_calibragem_global` em `0.99934`, que é o
mesmo valor de `FATOR_CALIBRAGEM_GLOBAL_PADRAO`. O mecanismo de sobreposição
existe e foi exercitado, mas hoje não altera nada.

Portanto constantes no aplicativo reproduzem o comportamento de produção vigente,
sem divergência no lançamento.

**Risco aceito:** se o operador ajustar um parâmetro no D1 depois, o produto web
muda na hora e o aplicativo só acompanha na próxima publicação na Play. Fica
registrado como escolha consciente. Se a frequência de ajuste crescer, a decisão
se reabre.

O contexto operacional — hora, dia da semana, feriado, plantão — decide qual
spread da conta global se aplica (`aberto` ou `fechado`) e é calculado no
aplicativo, no fuso de Brasília.

**Feriados móveis são calculados, não tabelados.** No produto web,
`contexto-operacional.mjs` traz os feriados fixos numa lista e os móveis —
Carnaval, Sexta-feira Santa, Corpus Christi — numa tabela **que só existe para
2026**. Em 2027 esses dias passariam a contar como úteis e o spread aplicado
seria o errado; no web um deploy corrige, no Android é uma publicação na Play.
Por decisão do operador em 18/09/2026, o aplicativo deriva os feriados móveis da
data da Páscoa pelo algoritmo de cômputo (Meeus/Jones/Butcher), válido para
qualquer ano do calendário gregoriano, sem tabela e sem terceiro. É a mesma
lógica do `BigDecimal`: onde a plataforma permite fazer melhor que o navegador,
faz-se melhor. Os feriados fixos continuam em lista, porque são fixos.

**Os campos numéricos formatam a entrada sozinhos (decisões do operador de 02
e 03/10/2026, CALANDR-27).** Todos usam a entrada de caixa que o produto web
aplica só ao campo Valor (`applyCurrencyMask`, `SimulationForm.tsx`): só os
dígitos contam, e entram pela direita no padrão brasileiro, com o ponto de
milhar e a vírgula automáticos. As casas são as de cada campo: 2 em reais e na
moeda estrangeira (`123456` → `1.234,56`), 4 no VET (`57340` → `5,7340`) e 2
nos percentuais (`350` → `3,50`). O teclado é numérico.

Cada campo é o campo de estado do Compose. O valor é o texto cru em dígitos de
um `TextFieldState` do ViewModel; a `InputTransformation` (`DigitosDeCaixa`)
aceita só dígitos e põe o cursor no fim, e a `OutputTransformation`
(`FormatoDeCaixa`) só insere os separadores. Edição, seleção, área de
transferência, acessibilidade, desfazer e salvamento são da plataforma. As
regras:

1. Vazio vale o padrão (ou "não informado", nos opcionais), e `0` é zero
   explícito: digitar zero num campo vazio dá `0,00`, e apagar o último dígito
   até esvaziar volta ao padrão.
2. Texto colado, ditado, autopreenchido ou da acessibilidade conta só os
   dígitos, como no web: `5,5` vira `0,55`, e `100` vira `1,00`.
3. Uma tecla ou colagem sem dígito algum, ou que passaria do limite do campo, é
   recusada inteira e em silêncio: o valor, a seleção e o resultado ficam. Uma
   letra de teclado físico sobre o número selecionado não o apaga.
4. "Selecionar tudo" e uma tecla trocam o valor; um cursor em outro ponto ou
   uma seleção parcial vão para o fim.
5. O resultado guarda os números com que foi calculado e só aparece enquanto os
   sete campos tiverem esses números: voltar a eles, até pelo desfazer, o
   mostra de novo, sem calcular. Data, modo e moeda continuam apagando o
   resultado.

Um parâmetro vazio vale o padrão, e o campo diz qual mesmo parado, com o rótulo
sempre recolhido acima: "Padrão: 5,50%", "Padrão: 3,50% (cartão e Conta
Global)", "Padrão: 0,78% (dias úteis, 9h–17h, horário de Brasília)" e "Padrão:
1,18% (fora das 9h–17h de Brasília, fins de semana e feriados)", com os números
lidos das constantes de `Parametros` e as horas das de `ContextoOperacional`. O
"Auto" de antes sugeria uma busca que não existe. Nos campos de valor, o exemplo
("1.000,00") só aparece com o campo em foco, para não ser lido como um valor
digitado (decisão do operador de 03/10/2026). No modo cobrado em reais, os
dois spreads da Conta Global, que o motor não usa ali, somem. Os valores
guardados pela 1.0.1 são ignorados: os campos usam chaves novas, e as antigas
são apagadas.

## 8. Testes

O motor em `:core:calc` é testado na JVM, sem emulador.

Os casos verificam a **regra de negócio**, com valores conhecidos e resultados
conferidos à mão: incidência de IOF, spread por modalidade, aplicação da
calibragem, os três cenários do modo "cobrado em reais", a seleção de spread por
contexto operacional, o cômputo da Páscoa conferido contra datas conhecidas de
vários anos, e as funções de erro percentual, MAPE e classificação.

Cada caso precisa ser capaz de falhar. Um teste que passa com a regra desarmada
não é evidência de nada.

A interface tem testes de Compose para os fluxos principais.

O build publicado sai do R8 (CALANDR-26), e os testes acima rodam no debug, com
dublês: não exercitam o que o R8 pode quebrar em execução. Por isso o módulo
`:teste-release` instala o build `minificado` do `:app` (o release, assinado
com a chave de depuração) e o percorre de fora do processo dele com o UI
Automator, que é o caminho oficial para testar o build otimizado: a abertura
(o grafo do Hilt), a simulação com cotação ao vivo (Retrofit, OkHttp e JSON), o
modo cobrado em reais e a tela de licenças (os `assets`). Roda na CI, no mesmo
Pixel 2 com API 36 dos outros testes de aparelho.

O aplicativo mostra o mesmo aviso de cotações indisponíveis para a fonte fora do
ar e para uma falha dele, e o título do cartão aparece mesmo sem cotação. Por
isso o teste sonda cada fonte por conta própria, nos endereços que o aplicativo
consulta, e toda fonte que responde tem de aparecer pelo nome no resultado:
"PTAX do Banco Central" no cartão, AwesomeAPI ou Yahoo Finance na conta global.
Sem fonte alguma respondendo, o teste se declara pulado, e não verde. Os dados do
aplicativo são limpos antes de cada fluxo, para uma cotação guardada não se
passar por uma lida da rede. Foi conferido com dois controles: com o aplicativo
derrubado ao abrir, os três fluxos caem; com a leitura do JSON devolvendo nada,
cai a simulação.

## 9. Privacidade e publicação

Sem inteligência artificial e sem envio de e-mail, **o aplicativo não coleta
dado pessoal algum**. Não há analytics, fingerprinting nem identificador
persistente próprio, conforme as decisões já registradas no `README.md`.

A única permissão necessária é `INTERNET`. O formulário de segurança de dados da
Play Console declara ausência de coleta.

## 10. Riscos aceitos

**Yahoo Finance como contingência da taxa spot.** O endereço
`query1.finance.yahoo.com/v8/finance/chart` não é interface pública documentada
da Yahoo; é endpoint interno. Usá-lo num binário assinado e distribuído na Play
é risco de termos de uso, distinto do risco técnico. **Foi apontado e o operador
decidiu incluí-lo**, mantendo paridade com o produto web. Registrado aqui para
que apareça como escolha deliberada em qualquer auditoria ou revisão futura.

**Constantes de parâmetro**, conforme a seção 7.

**Série de backtest iniciando vazia**, conforme a seção 3.

**Taxa spot da camada cacheada da AwesomeAPI**, conforme a seção 5 — o mesmo que
o produto web recebe hoje, porque nenhum dos dois usa chave.

## 11. Pendências que esta especificação cria

O `README.md` afirma que, quando o scaffold Android real for introduzido, a
mesma mudança revisada deve acrescentar validação do Gradle Wrapper, lint,
testes, build e análise CodeQL adequada a Java/Kotlin. O scaffold entrou em
17/09/2026 sem essas peças, e esta seção registrou as quatro que faltavam. Os
itens não são apagados conforme se resolvem — são o registro do que esta
especificação criou —, mas cada um carrega seu estado atual, com data e
evidência.

### Resolvidas

1. **Workflow de integração contínua.** ~~Não existe workflow que compile,
   analise e teste.~~ **Resolvida em 17/09/2026** pela CALANDR-10: o
   `.github/workflows/ci.yml` valida o Gradle Wrapper e executa
   `:app:assembleDebug`, `:app:lintDebug` e os testes unitários em todo pull
   request e em todo push para `main`. É portão de todo PR desde então.
2. **CodeQL analisa Actions e o placeholder JavaScript**, ~~não Kotlin.~~
   **Resolvida em 24/09/2026** pela CALANDR-14: o CodeQL 2.27.1, primeira
   versão que suporta o Kotlin 2.4.20 deste projeto, analisa `java-kotlin` com
   *autobuild*, e a primeira análise na `main` (`95d8506`) terminou verde. A
   pré-condição de existir Kotlin no `main` fora satisfeita em 18/09/2026 pela
   CALANDR-13 (`:core:calc`, PR #40).

<!-- Os itens têm número fixo, citado no texto ("o item 2"); o 3 continua aberto, abaixo. Lista
     separada para o 4 ser exibido como 4: numa lista só, o Markdown o renumeraria para 3. -->

<!-- markdownlint-disable-next-line MD029 -->
4. **`README.md` desatualizado pelo scaffold de 17/09.** ~~Ainda afirma que não
   existe projeto Gradle nem código Android.~~ **Resolvida em 17/09/2026**, na
   mesma mudança: o `README.md` passou a descrever o scaffold real, a registrar
   a dívida que ele deixou em vez de apagá-la, e a acompanhar cada módulo
   entregue do porte.

### Abertas, bloqueadas por dependência externa

<!-- markdownlint-disable-next-line MD029 -->
3. **O placeholder inerte `quality/code-quality-probe.js`** existe, segundo o
   próprio `README.md`, "somente para fornecer ao GitHub Code Quality uma
   linguagem suportada antes do código Android real". ~~O motivo dele só deixa de
   existir quando o item 2 estiver feito.~~ O item 2 foi feito, e o motivo
   continua: a análise por regras do Code Quality não cobre Kotlin. A
   documentação oficial lista C#, Go, Java, JavaScript, Python, Ruby e
   TypeScript, e o modo `none` com que ele compila não extrai Kotlin. O
   placeholder é a única fonte que ele analisa aqui.

~~Os dois são um só trabalho, e nesta ordem: acrescentar `java-kotlin` antes de
remover o placeholder, para que o repositório não fique sem linguagem analisável
no intervalo. São carregados pela issue #42 (CALANDR-14).~~

~~**O que bloqueia não é a falta de Kotlin — é o CodeQL.** Ele não suporta o
Kotlin 2.4.20, que é a versão deste projeto, e por isso o operador retirou
`java-kotlin` do Default setup em 18/09/2026, às 20:27. Enquanto o suporte não
chegar, acrescentar a linguagem só produziria análise que falha. A reavaliação
está marcada para **25/09/2026** e é da issue, não deste documento.~~

**O que bloqueia agora é o Code Quality.** O CodeQL passou a cobrir o Kotlin em
24/09/2026, mas o placeholder não existe para ele. Por decisão do operador na
mesma data, o placeholder fica até o Code Quality cobrir Kotlin. A remoção
continua na issue #42 (CALANDR-14): tirar `javascript-typescript` das
configurações do CodeQL e do Code Quality antes de remover o arquivo, para
nenhum job ficar sem código.

---

<sub>Registro por Claude Code (Claude Opus 5), sob direção do operador.</sub>
