# Correções pós-auditoria — 1.0.1

Auditoria de 21/09/2026 sobre `2449811470e9b347aaf4da186c4026782edcaad3`.
[CALANDR-24](https://linear.app/lcv-ideas-software/issue/CALANDR-24) ↔
[GitHub #63](https://github.com/LCV-Ideas-Software/calculadora-android/issues/63).
Este documento atualiza os contratos abaixo; a especificação original preserva
as decisões históricas do porte. A autoria do código não determina sua qualidade:
os achados foram classificados por comportamento reproduzido e impacto.

| Achado | Correção e prova |
| --- | --- |
| A01 | Edição cancela a requisição e incrementa sua revisão; respostas só alteram a revisão correspondente. Regressões de edição, modo e segunda requisição em `AuditViewModelTest`. |
| A02 | EUR/GBP exigem boletim de fechamento. Provisório não entra no cache. Regressão de abertura seguida de fechamento em `AuditDataTest`. |
| A03/A05 | Decimal limitado e sem expoente; opcionais inválidos não viram padrão. IOF/spreads entre 0 e 100%; VET/fatura positivos. Regressões da interface e do parser. |
| A04 | Download usa a saída da função HTTP redirecionada ao APK, sem segundo `curl -o`. Erros ficam em stderr. |
| A06 | Spot tem instante fornecido pela fonte; idade máxima de 24 h, incluindo cache persistido. Idade negativa é inválida. Uma nova consulta não renova artificialmente a idade. |
| A07 | Só compara spot com instante conhecido e PTAX final do mesmo dia; exclui contingências. Índice único moeda/dia impede que toques repetidos alterem o peso estatístico. |
| A08 | Rejeita data futura; informa data da PTAX efetiva, instante e origem da spot na tela e no compartilhamento. |
| A09/A10 | Spread expresso em reais; CSV exige taxa positiva, limitada e da data solicitada. |
| A11 | `SavedStateHandle` guarda apenas entradas primitivas; resultado/carregamento não são restaurados. |
| A12/A13 | Textos secundários usam `#475569`; campos têm `label` nativo, sem rótulo externo duplicado. Checkbox possui ação única na linha. |
| A14 | Exibe separadamente as sensibilidades do cartão e da conta global, identificadas. |
| A15/A16 | Tag usa o SHA verificado; APK tem identidade e certificado conferidos. GitHub Release exige trilha ativa e lifecycle `PUBLISHED`, não apenas APK gerado ou track `completed`. |

## Contratos numéricos e temporais

- Entradas: até 12 dígitos inteiros e 8 decimais; até 40 caracteres com espaços e
  agrupamento. `1.234,56` e `1,234.56` são aceitos com grupos completos de três.
  Um único separador é decimal: `1.234` significa 1,234. Expoentes e agrupamentos
  incompletos são rejeitados. Valores não são arredondados silenciosamente na entrada.
- Percentuais opcionais vazios usam os padrões; qualquer preenchimento inválido
  produz erro. A faixa 0–100% é um limite do formulário, não aconselhamento fiscal.
- PTAX: fechamento da data informada ou anterior, recuo máximo de sete dias corridos.
  Não se apresenta boletim intradiário como fechamento. O cache de fechamento é diário.
- Spot: prazo conservador de 24 h contado da fonte, inclusive em fim de semana.
  Expirada, tenta Yahoo; se não houver spot recente, usa PTAX como contingência explícita.
  A conta global representa uma conversão com cotação disponível agora, não uma
  reconstrução histórica da compra. Data e hora são exibidas em pt-BR, UTC−03:00.
- A comparação diária é desvio entre duas referências do mesmo dia, não precisão
  de uma previsão, garantia de preço bancário ou série histórica de mercado.
  É mantida a primeira observação elegível de cada moeda/dia. A janela é de sete
  dias, com retenção de trinta; as bandas de sensibilidade não são garantias.

## Persistência e rede

A migração explícita Room 1→2 invalida as três tabelas de dados derivados: a V1
podia conter PTAX provisória, spot com idade incorreta e comparações entre períodos
diferentes. Nenhum valor de compra digitado é armazenado nessas tabelas. O esquema
versionado e a migração são testados no SQLite real do Android; não há fallback de
migração destrutiva. Backup e transferência excluem os dados locais do aplicativo.

Respostas HTTP, inclusive chunked e erros, têm teto de 1 MiB antes do buffering do
Retrofit. Leitura de corpo ocorre em IO. PTAX e spot são consultadas em paralelo,
com orçamento de 15 s e 10 s, respectivamente, além dos 4 s por chamada HTTP.
Cancelamento continua sendo cancelamento, sem disparar fontes de contingência.

## Verificação e limites

As dez regressões do relatório original falharam antes da correção. A entrega
inclui testes JVM, Compose/Room em dispositivo, migração do schema V1, deduplicação,
recriação do estado, expiração de cotações e limites de rede. A CI executa testes
instrumentados com dispositivo gerenciado pelo AGP na API mínima 34, além de
lint/build debug e release. Os recibos de execução e revisão são vinculados à issue.

O risco CodeQL/Kotlin permanece em [#42](https://github.com/LCV-Ideas-Software/calculadora-android/issues/42)
/ CALANDR-14, conforme decisão já registrada; não se declara cobertura que o
extrator não fornece. Dependency Verification/locking do Gradle permanece avaliação
de hardening: checksum do wrapper e pins de Actions já existentes foram preservados.
Nenhum SDK de analytics, conta, backend ou coleta funcional foi acrescentado.

A verificação do APK da Play usa Build Tools 37 e o JDK 25 nativo do runner,
necessário para verificar a assinatura híbrida ML-DSA do Android 17. A compilação
continua com JDK 17. O hash informado pela Play é normalizado quanto a separadores
e caixa e comparado aos certificados dos signatários verificados; o certificado
do carimbo de origem não conta como certificado de assinatura do aplicativo.

## Fontes oficiais

- [Estado salvo de ViewModel](https://developer.android.com/topic/libraries/architecture/viewmodel/viewmodel-savedstate).
- [Migrações Room](https://developer.android.com/training/data-storage/room/migrating-db-versions).
- [Acessibilidade Compose](https://developer.android.com/develop/ui/compose/accessibility/api-defaults).
- [Dispositivos gerenciados pelo build](https://developer.android.com/studio/test/managed-devices).
- [Boletins do BCB](https://www.bcb.gov.br/conteudo/dadosabertos/BCBDepin/gnastportal-dados-abertostaxas-de-cambio---todos-os-boletins-diarios.pdf).
- [API de moedas AwesomeAPI](https://docs.awesomeapi.com.br/api-de-moedas).
- [Lifecycle de releases na Play](https://developers.google.com/android-publisher/api-ref/rest/v3/applications.tracks.releases).
- [Criação de GitHub Release com alvo explícito](https://cli.github.com/manual/gh_release_create).
