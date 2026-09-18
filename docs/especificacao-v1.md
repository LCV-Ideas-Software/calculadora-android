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

Aproximadamente **820 linhas de lógica** a reimplementar.

**Fora do escopo, por decisão do operador:**

| Unidade | Motivo |
| -- | -- |
| `oraculo.ts` (327 linhas) e `oraculo-observabilidade.js` (80) | sem funcionalidade de inteligência artificial |
| `enviar-email.js` (80) e `contato.js` | sem envio de e-mail |

De `functions/api/_shared/security.js` interessa apenas a sanitização de
entrada. O *rate limiting* é preocupação de servidor e não tem contraparte num
aplicativo que fala direto com as fontes.

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
série do backtest, DataStore para preferências, Hilt para injeção de dependência
e Retrofit/OkHttp para rede. Tudo AndroidX oficial.

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

Duas conclusões dessa medição:

1. **Nenhuma fonte filtra por User-Agent.** O `Mozilla/5.0` presente no código do
   produto web é hábito herdado, não exigência. O aplicativo se identifica
   honestamente.
2. **Nenhuma fonte exige chave.** O limite da AwesomeAPI é por endereço IP; num
   aplicativo, cada aparelho usa o seu, o que distribui a carga em vez de
   concentrá-la num único servidor.

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

## 8. Testes

O motor em `:core:calc` é testado na JVM, sem emulador.

Os casos verificam a **regra de negócio**, com valores conhecidos e resultados
conferidos à mão: incidência de IOF, spread por modalidade, aplicação da
calibragem, os três cenários do modo "cobrado em reais", a seleção de spread por
contexto operacional, e as funções de erro percentual, MAPE e classificação.

Cada caso precisa ser capaz de falhar. Um teste que passa com a regra desarmada
não é evidência de nada.

A interface tem testes de Compose para os fluxos principais.

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

## 11. Pendências que esta especificação cria

O `README.md` afirma que, quando o scaffold Android real for introduzido, a
mesma mudança revisada deve acrescentar validação do Gradle Wrapper, lint,
testes, build e análise CodeQL adequada a Java/Kotlin. O scaffold entrou em
17/09/2026, e essas peças **ainda não existem**:

1. **Não há workflow de integração contínua** que compile, analise e teste. Os
   workflows atuais cobrem publicação, Pages, Scorecard, Zizmor, revisão de
   dependências, auto-merge do Dependabot e Linear Release — nenhum executa
   `build` ou `test`.
2. **CodeQL analisa Actions e a sonda JavaScript**, não Kotlin. Com código
   Kotlin real, a configuração precisa cobri-lo.
3. **A sonda inerte `quality/code-quality-probe.js`** existe, segundo o próprio
   `README.md`, "somente para fornecer ao GitHub Code Quality uma linguagem
   suportada antes do código Android real". Com Kotlin no repositório, o motivo
   dela deixa de existir.
4. **O `README.md` ficou desatualizado** pelo scaffold de 17/09: ainda afirma que
   não existe projeto Gradle nem código Android, e que "Gradle será incluído
   somente quando existir um projeto Gradle real" — quando o ecossistema Gradle
   já foi declarado no Dependabot.

Cada uma vira issue própria, com contraparte nos dois rastreadores.

---

<sub>Registro por Claude Code (Claude Opus 5), sob direção do operador.</sub>
