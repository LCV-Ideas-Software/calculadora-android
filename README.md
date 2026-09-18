# Calculadora Android

[![OpenSSF Best Practices](https://www.bestpractices.dev/projects/14229/badge)](https://www.bestpractices.dev/projects/14229)

Repositório público de entrega do aplicativo Android da Calculadora da
LCV Ideas & Software.

## Estado atual

O repositório já contém o projeto Gradle e a esteira de publicação na Play,
introduzidos em 17/09/2026 pela [CALANDR-8](https://linear.app/lcv-ideas-software/issue/CALANDR-8):
Gradle 9.7.1 com a distribuição fixada por checksum, Android Gradle Plugin
9.4.0, `compileSdk` e `targetSdk` 36, `minSdk` 24 e o package name
`dev.lcv.calculadora`. Não há assinatura no repositório — o material de
assinatura é injetado em tempo de build pelo fluxo de publicação.

**Ainda não existe código Kotlin de aplicação.** O desenvolvimento do port
nativo está especificado em
[`docs/especificacao-v1.md`](docs/especificacao-v1.md) e começa pelo motor de
cálculo.

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
para fornecer ao GitHub Code Quality uma linguagem suportada enquanto não houver
código Kotlin de aplicação. Ele não é carregado pela página, não integra o
aplicativo e não representa cobertura de Kotlin. É a única fonte JavaScript do
repositório e, portanto, o que sustenta a análise hoje: quando o Kotlin entrar e
`java-kotlin` for acrescentado à configuração, esta sonda perde a finalidade e
sai — nessa ordem, para que o repositório não fique sem linguagem analisável no
intervalo.

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
  `lintDebug` e testes unitários, com o mesmo JDK usado na publicação.
- CodeQL usa o Default setup nativo do GitHub para analisar GitHub Actions e
  a sonda JavaScript inerte. Code Quality também usa a configuração nativa.
  A linguagem `java-kotlin` será acrescentada junto com o primeiro Kotlin real,
  porque a análise precisa de código para compilar.
- Dependency Review avalia as alterações de dependências nos pull requests.
- Zizmor audita a segurança dos workflows e publica SARIF.
- OpenSSF Scorecard observa a postura de supply chain do branch principal e
  mantém o SARIF no próprio repositório; não é gate por pull request.
- Dependabot verifica GitHub Actions todos os dias, inclusive fins de semana,
  às 05h no fuso fixo UTC−03:00, com grupo de versões minor/patch e majors separados.
  O cooldown de sete dias preserva as exceções para `actions/*` e `github/*`.
  O ecossistema Gradle foi declarado em 17/09/2026, junto com o projeto real.
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
