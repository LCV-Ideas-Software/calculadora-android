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

| Superfície | Recurso |
| --- | --- |
| GitHub | [`LCV-Ideas-Software/calculadora-android`](https://github.com/LCV-Ideas-Software/calculadora-android) |
| GitHub Project | [Project #18](https://github.com/orgs/LCV-Ideas-Software/projects/18) |
| Linear | Team e Project `calculadora-android` |
| Bootstrap | [CALANDR-1](https://linear.app/lcv-ideas-software/issue/CALANDR-1) ↔ [GitHub #1](https://github.com/LCV-Ideas-Software/calculadora-android/issues/1) |

Uma GitHub Issue só pode ser criada ou vinculada quando houver contraparte
Linear explícita e inequívoca. Os drafts históricos do Project #18 não são
convertidos em massa.

## Automação

- CodeQL analisa GitHub Actions e a sonda JavaScript inerte em pull requests,
  merge groups, `main` e execução agendada.
- Dependency Review avalia pull requests e o SHA sintético da merge queue.
- Zizmor audita a segurança dos workflows e publica SARIF.
- OpenSSF Scorecard observa a postura de supply chain do branch principal; não
  é gate por pull request.
- Dependabot verifica GitHub Actions diariamente. Gradle será incluído somente
  quando existir um projeto Gradle real.
- GitHub Pages publica exclusivamente o conteúdo estático de `site/` em
  <https://calculadora-android.lcv.dev>.
- Linear Release registra cada mudança bem-sucedida em `main` no pipeline
  contínuo correspondente.

Todas as Actions externas usam SHA completo imutável e aparecem em
[`THIRDPARTY.md`](THIRDPARTY.md) e no inventário verificável
`.github/workflows/actions.lock`.

## Contribuição e segurança

Leia [CONTRIBUTING.md](CONTRIBUTING.md) antes de propor mudanças. Vulnerabilidades
e dados sensíveis devem seguir o canal privado descrito em
[SECURITY.md](SECURITY.md), nunca uma Issue ou Discussion pública.

## Licença

Copyright © 2026 LCV Ideas & Software.

O conteúdo original deste repositório é licenciado sob
**GNU AGPL-3.0-or-later**. Consulte [LICENSE](LICENSE), [NOTICE](NOTICE) e
[THIRDPARTY.md](THIRDPARTY.md).
