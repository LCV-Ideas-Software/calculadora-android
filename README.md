# Calculadora Android

[![OpenSSF Best Practices](https://www.bestpractices.dev/projects/14229/badge)](https://www.bestpractices.dev/projects/14229)

Repositório público de entrega do aplicativo Android da Calculadora da
LCV Ideas & Software.

## Estado atual

O repositório já contém o projeto Gradle e a esteira de publicação na Play,
introduzidos em 17/09/2026 pela [CALANDR-8](https://linear.app/lcv-ideas-software/issue/CALANDR-8):
Gradle 9.7.1 com a distribuição fixada por checksum, Android Gradle Plugin
9.4.x, `compileSdk` e `targetSdk` 37 (Android 17), `minSdk` 34 (Android 14,
decisão do operador de 19/09/2026) e o package name `dev.lcv.calculadora`. Não há assinatura no repositório — o material de
assinatura é injetado em tempo de build pelo fluxo de publicação.

A versão 1.0.1 corrige a auditoria pós-porte: [contratos e evidências](docs/correcoes-v1.0.1.md).
O fluxo de [publicação e revisão na Google Play](docs/publicacao-play.md) distingue
envio, análise e distribuição efetiva. A CI também testa Room/Compose na API mínima 34.

O desenvolvimento do port nativo está especificado em
[`docs/especificacao-v1.md`](docs/especificacao-v1.md) e começou pelo motor de
cálculo: o módulo `:core:calc`, entregue pela
[CALANDR-13](https://linear.app/lcv-ideas-software/issue/CALANDR-13), é Kotlin
puro, sem dependência de Android, com toda a regra de negócio do produto web —
custo cartão × conta global, modo cobrado em reais, sensibilidade, contexto
operacional com feriados móveis calculados pela Páscoa, backtest, leitura do CSV
do BCB, parsing e formatação — em `BigDecimal`, testada na JVM. A camada de
dados veio em seguida, pela
[CALANDR-15](https://linear.app/lcv-ideas-software/issue/CALANDR-15): o módulo
`:core:data`, Android library com as quatro fontes de cotação (BCB Olinda, CSV
de fechamento do BCB, AwesomeAPI e Yahoo Finance) atrás de Retrofit/OkHttp com
User-Agent honesto e sem chave, cache local e série do backtest em Room, Hilt
para a injeção e o `Simulador`, que orquestra cotações, motor e persistência e
é o único ponto de entrada da interface. A interface fecha o port pela
[CALANDR-16](https://linear.app/lcv-ideas-software/issue/CALANDR-16): o módulo
`:app`, Jetpack Compose com Material 3, com as mesmas seções, os mesmos rótulos
e a mesma marca do produto web, na moldura do Android.

```
:core:calc    Kotlin puro — motor e regras (entregue)
:core:data    fontes de cotação, cache local, persistência do backtest (entregue)
:app          interface Compose e ViewModels (entregue)
```

A aparência preserva a identidade do port. Na 1.0.1, rótulos, contraste e avisos
foram ajustados para acessibilidade e precisão; o ícone e a marca continuam os
da LCV. A interface usa recursos nativos — barra superior e conteúdo de
largura cheia no lugar do painel centralizado do navegador, diálogo de data
nativo no lugar do `<input type="date">`, folha de compartilhamento do Android
no lugar do botão de copiar. Dois afastamentos deliberados estão declarados em
[`docs/especificacao-v1.md`](docs/especificacao-v1.md): não há a tela de
partículas animada do fundo, porque animação permanente custa bateria sem
entregar informação, e não há paleta escura, porque a marca ainda não definiu
tons escuros e inventá-los seria criar identidade, não portá-la.

Aquele scaffold devia ter trazido, na mesma mudança revisada, validação do
Gradle Wrapper, lint, testes, build e análise CodeQL adequada a Java/Kotlin.
Trouxe apenas duas dessas peças — validação do wrapper e build — e ambas dentro
do `publish-play.yml`, que roda por `workflow_dispatch`. A dívida é registrada e
tratada na [CALANDR-10](https://linear.app/lcv-ideas-software/issue/CALANDR-10):
o workflow `ci.yml` passa a compilar, analisar e testar todo pull request, e a
análise CodeQL de Java/Kotlin entra junto com o primeiro Kotlin real.

Decisões de produto vigentes:

- nenhuma funcionalidade de inteligência artificial;
- zero analytics, fingerprinting ou identificador persistente próprio;
- nenhuma migração da telemetria, do log de IP ou da integração de IA do
  produto web;
- coleta funcional mínima, explícita e justificada apenas quando o aplicativo
  real exigir.

O arquivo inerte
[`quality/code-quality-probe.js`](quality/code-quality-probe.js) existe somente
para fornecer ao GitHub Code Quality uma linguagem que a análise por regras
dele cobre. Ele não é carregado pela página, não integra o aplicativo e não
representa cobertura de Kotlin. O Kotlin é coberto pelo code scanning do
CodeQL: desde 24/09/2026, o Default setup analisa `java-kotlin`, compilado com
o *autobuild*, no CodeQL 2.27.1, a primeira versão que suporta o Kotlin 2.4.20
deste projeto. O Code Quality não o cobre: a análise por regras dele suporta
C#, Go, Java, JavaScript, Python, Ruby e TypeScript, e o modo `none` com que
ele compila não extrai Kotlin. Por isso o placeholder continua sendo a única
fonte que o Code Quality analisa aqui e, por decisão do operador em
24/09/2026, fica até o Code Quality cobrir Kotlin. Rastreado pela
[CALANDR-14](https://linear.app/lcv-ideas-software/issue/CALANDR-14).

## Tracking canônico

| Superfície     | Recurso                                                                                                                                              |
| -------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------- |
| GitHub         | [`LCV-Ideas-Software/calculadora-android`](https://github.com/LCV-Ideas-Software/calculadora-android)                                                |
| GitHub Project | [Project #18](https://github.com/orgs/LCV-Ideas-Software/projects/18)                                                                                |
| Linear         | Team e Project `calculadora-android`                                                                                                                 |
| Bootstrap      | [CALANDR-1](https://linear.app/lcv-ideas-software/issue/CALANDR-1) ↔ [GitHub #1](https://github.com/LCV-Ideas-Software/calculadora-android/issues/1) |

Uma GitHub Issue só pode ser criada ou vinculada quando houver contraparte
Linear explícita e inequívoca. Os drafts históricos do Project #18 não são
convertidos em massa.

## Automação

- O workflow `CI` compila, analisa e testa o projeto em cada pull request e em
  cada push para `main`: validação do Gradle Wrapper, `assembleDebug`,
  `lintDebug` e testes unitários — inclusive os do motor `:core:calc`, que
  rodam na JVM — com o mesmo JDK usado na publicação.
- CodeQL usa o Default setup nativo do GitHub para analisar GitHub Actions, o
  Kotlin (`java-kotlin`, compilado com o *autobuild* do CodeQL) e o placeholder
  JavaScript inerte. Code Quality também usa a configuração nativa; como a
  análise por regras dele não cobre Kotlin, ele analisa só o placeholder — ver
  CALANDR-14.
- Dependency Review avalia as alterações de dependências nos pull requests.
- Zizmor audita a segurança dos workflows e publica SARIF.
- OpenSSF Scorecard observa a postura de supply chain do branch principal e
  mantém o SARIF no próprio repositório; não é gate por pull request.
- Dependabot verifica GitHub Actions todos os dias, inclusive fins de semana,
  às 05h no fuso fixo UTC−03:00, com grupo de versões minor/patch e majors separados.
  O cooldown de sete dias preserva as exceções para `actions/*` e `github/*`.
  O ecossistema Gradle foi declarado em 17/09/2026, junto com o projeto real, e
  lê o catálogo `gradle/libs.versions.toml`, onde ficam as versões do Android
  Gradle Plugin, do Kotlin Gradle Plugin e do JUnit.
  Atualizações de segurança têm um grupo separado e não aguardam o agendamento
  de versões nem o cooldown. Se um membro falhar, diagnosticar e ajustar o
  agrupamento nativo para liberar as demais correções com os checks exigidos.
- O workflow local habilita o auto-merge squash nativo para os pull requests
  do Dependabot deste repositório, vinculado ao SHA exato e condicionado aos
  checks e regras aplicáveis. Não existe merge queue ou controlador central.
- GitHub Pages publica exclusivamente o conteúdo estático de `site/` em
  <https://calculadora-android.lcv.dev>.
- Linear Release registra os commits de `main` no pipeline contínuo
  correspondente, com a action e a CLI oficiais v0.18.0. A fila preserva runs
  pendentes e as falhas permanecem visíveis. O registro não representa a
  publicação de um aplicativo Android nem depende do deploy de Pages.
- O `publish-play.yml`, disparado manualmente, constrói o App Bundle, envia à
  trilha escolhida da Google Play e recusa a publicação se o digest recebido
  pela Play não for o do artefato construído. Quando a trilha é `production`,
  o mesmo run registra uma **GitHub Release** com tag `vXX.XX.XX` derivada do
  `versionName`, anexando o APK universal que a própria Play gerou e assinou
  com a chave de assinatura do app — o mesmo binário da loja, portanto
  instalável e atualizável em conjunto com ela — mais `SHA256SUMS` e uma
  attestation de proveniência verificável com `gh attestation verify`. As
  trilhas `internal`, `alpha` e `beta` ficam só na Play. Uma segunda
  publicação em produção exige `versionName` novo: tag repetida falha o run.
  As notas da versão não são digitadas no Console: vivem em
  `play/release-notes/pt-BR.txt` e sobem na mesma atualização de trilha, como
  `releases[].releaseNotes[]` da API Android Publisher v3. Um passo anterior ao
  build recusa arquivo ausente, vazio ou acima dos 500 caracteres por idioma que
  o Console aceita. O workflow `Record a Play release on GitHub` grava a Release
  de uma versão que já está na loja, dado o `versionCode`, sem reconstruir nem
  reenviar — é o caminho quando a publicação foi concluída no Console e o
  `publish-play.yml` já terminou. O run de publicação recebe também um `release_status`: um aplicativo que
  nunca foi publicado é um *draft app*, e a API só aceita `draft` dele na trilha
  pública — a primeira publicação se conclui no Play Console, cujo botão de
  liberar publica o aplicativo junto. Um release em rascunho não grava GitHub
  Release, porque a loja ainda não entrega aquele binário.

Todas as Actions externas usam SHA completo imutável diretamente nos workflows.
O inventário de terceiros está em [`THIRDPARTY.md`](THIRDPARTY.md).

## Contribuição e segurança

Leia [CONTRIBUTING.md](CONTRIBUTING.md) e [INBOUND.md](INBOUND.md) antes de propor mudanças. Vulnerabilidades
e dados sensíveis devem seguir o canal privado descrito em
[SECURITY.md](SECURITY.md), nunca uma Issue ou Discussion pública.

## Licença

Copyright © 2026 LCV Ideas & Software.

O conteúdo original deste repositório é licenciado sob
**GNU AGPL-3.0-or-later**. Consulte [LICENSE](LICENSE), [NOTICE](NOTICE) e
[THIRDPARTY.md](THIRDPARTY.md).
