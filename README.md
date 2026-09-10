# Calculadora Android

[![OpenSSF Best Practices](https://www.bestpractices.dev/projects/14229/badge)](https://www.bestpractices.dev/projects/14229)

Repositório público de entrega do aplicativo Android da Calculadora da
LCV Ideas & Software.

## Estado atual

Este repositório contém apenas o baseline de governança, segurança,
observabilidade e publicação. Ainda não existe projeto Gradle, código Android,
configuração de assinatura, pacote de aplicação ou dependência de produção.
Não adicione arquivos Gradle fictícios para satisfazer automações.

Quando o scaffold Android real for introduzido, ele deverá usar o package name
`dev.lcv.calculadora` e acrescentar, na mesma mudança revisada, validação do
Gradle Wrapper, lint, testes, build e análise CodeQL adequada a Java/Kotlin.

Decisões de produto vigentes:

- nenhuma funcionalidade de inteligência artificial;
- zero analytics, fingerprinting ou identificador persistente próprio;
- nenhuma migração da telemetria, do log de IP ou da integração de IA do
  produto web;
- coleta funcional mínima, explícita e justificada apenas quando o aplicativo
  real exigir.

O arquivo inerte
[`quality/code-quality-probe.js`](quality/code-quality-probe.js) existe somente
para fornecer ao GitHub Code Quality uma linguagem suportada antes do código
Android real. Ele não é carregado pela página, não integra o aplicativo e não
representa cobertura de Kotlin.

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

- CodeQL usa o Default setup nativo do GitHub para analisar GitHub Actions e
  a sonda JavaScript inerte. Code Quality também usa a configuração nativa.
- Dependency Review avalia as alterações de dependências nos pull requests.
- Zizmor audita a segurança dos workflows e publica SARIF.
- OpenSSF Scorecard observa a postura de supply chain do branch principal e
  mantém o SARIF no próprio repositório; não é gate por pull request.
- Dependabot verifica GitHub Actions todos os dias, inclusive fins de semana,
  às 05h no fuso fixo UTC−03:00, com grupo de versões minor/patch e majors separados.
  O cooldown de sete dias preserva as exceções para `actions/*` e `github/*`.
  Gradle será incluído somente quando existir um projeto Gradle real.
  Atualizações de segurança têm um grupo separado e não aguardam o agendamento
  de versões nem o cooldown. Se um membro falhar, diagnosticar e ajustar o
  agrupamento nativo para liberar as demais correções com os checks exigidos.
- O workflow local habilita o auto-merge squash nativo para os pull requests
  do Dependabot deste repositório, vinculado ao SHA exato e condicionado aos
  checks e regras aplicáveis. Não existe merge queue ou controlador central.
- GitHub Pages publica exclusivamente o conteúdo estático de `site/` em
  <https://calculadora-android.lcv.dev>.
- Linear Release registra os commits de `main` no pipeline contínuo
  correspondente, com a action e a CLI oficiais v0.17.2. A fila preserva runs
  pendentes e as falhas permanecem visíveis. O registro não representa a
  publicação de um aplicativo Android nem depende do deploy de Pages.

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
