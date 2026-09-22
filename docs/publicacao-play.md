# Publicação e revisão na Google Play

O caminho de publicação deste repositório é **GitHub Actions com Workload Identity
Federation (WIF)**. Autenticação local pode ser usada para consultas e verificação;
não substitui o build, assinatura e publicação do workflow.

## Enviar a versão

1. Integrar o PR com `versionName` e `versionCode` novos, notas em
   `play/release-notes/pt-BR.txt` e checks aprovados. Não reutilizar código já enviado.
2. Em Actions, abrir **Publish to Google Play** e executar no commit integrado,
   com `track=production` e `release_status=completed` para um aplicativo habilitado.
   `draft` prepara uma versão, mas não a distribui.
3. Conferir o run: o workflow compila o AAB, compara seu SHA-256 com o recebido pela
   Play, atualiza a trilha e confirma a edição. O commit solicita envio para análise
   com `changesNotSentForReview=false`. Por padrão, uma revisão já em andamento
   deve ser preservada (`ERROR_IF_IN_REVIEW`); substituí-la exige decisão explícita
   e usa o comportamento nativo `CANCEL_IN_REVIEW_AND_SUBMIT`, selecionado no input
   `changes_in_review_behavior`. Essa opção cancela a revisão anterior e reenvia
   o conjunto de alterações; o prazo de análise pode recomeçar.
4. Ler o resumo do run e o lifecycle da versão. `edits.tracks.status=completed`
   descreve a configuração de rollout; sozinho não prova aprovação ou disponibilidade.
   A fonte para revisão é
   `GET /androidpublisher/v3/applications/dev.lcv.calculadora/tracks/production/releases`.

| Lifecycle | Próximo passo |
| --- | --- |
| `DRAFT` | Concluir preparação da versão e declarações exigidas. |
| `NOT_SENT_FOR_REVIEW` | Enviar as alterações pela Visão geral da publicação. |
| `IN_REVIEW` | Já foi enviada; acompanhar a revisão, sem reenviar repetidamente. |
| `APPROVED_NOT_PUBLISHED` | Aprovação obtida; publicação gerenciada aguarda comando no Console. |
| `NOT_APPROVED` | Ler a decisão da Google e corrigir o motivo indicado. |
| `PUBLISHED` | Disponível na trilha; conferir se o rollout não está suspenso. |

## Se for necessário enviar pelo Console

1. Entrar no [Google Play Console](https://play.google.com/console/) com a conta
   autorizada e selecionar **Calculadora**, pacote `dev.lcv.calculadora`.
2. Abrir **Testar e lançar → Produção**. Conferir a versão desejada, o código,
   notas em português e os países/regiões. Para esta correção, o alvo é **1.0.1 (3)**.
   Não fazer novo upload se o workflow já enviou esse bundle.
3. Abrir **Visão geral da publicação**. Examinar **Alterações ainda não enviadas
   para revisão** (o texto pode aparecer como alterações prontas para envio).
4. Conferir também ficha da loja, declarações de conteúdo, segurança de dados,
   acesso ao app e demais pendências apontadas pelo Console. Corrigir bloqueios;
   não marcar declarações sem correspondência com o aplicativo.
5. Conferir o conjunto que será enviado. Alterações não relacionadas podem ser
   guardadas com **Salvar para depois**, quando o Console oferecer essa opção.
6. Clicar **Enviar alterações para revisão** e confirmar. Verificar que a versão
   aparece em **Alterações em revisão**. Esse é o recibo; salvar uma versão ou
   vê-la como `completed` na API de trilhas não substitui essa confirmação.
7. Acompanhar a mesma página e as notificações da Google. Não há prazo garantido.
   Com publicação gerenciada ligada, após aprovação a versão fica pronta para
   publicar: conferir e acionar **Publicar alterações**. Sem ela, a publicação
   segue o fluxo automático da Google após aprovação.
8. Verificar lifecycle `PUBLISHED`, o código e a trilha antes de anunciar
   disponibilidade. Se o run original terminou durante a revisão, executar
   **Record a Play release on GitHub**, no commit exato da versão, com o código
   correspondente. Ele valida estado, pacote, versão, certificado e cria a tag
   nesse SHA; não recompila nem envia novamente o bundle.

## Referências oficiais

- [Preparar e lançar uma versão](https://support.google.com/googleplay/android-developer/answer/9859348?hl=pt-BR).
- [Controlar revisão e publicação](https://support.google.com/googleplay/android-developer/answer/9859654?hl=pt-BR).
- [Publicar seu app e estados de atualização](https://support.google.com/googleplay/android-developer/answer/9859751?hl=pt-BR).
- [Commit da edição e tratamento de revisão em curso](https://developers.google.com/android-publisher/api-ref/rest/v3/edits/commit).
- [Estados de lifecycle](https://developers.google.com/android-publisher/api-ref/rest/v3/applications.tracks.releases).
- [Consulta de releases por trilha](https://developers.google.com/android-publisher/api-ref/rest/v3/applications.tracks.releases/list).

A API `appstoreappsreview` destina-se a lojas de terceiros participantes do programa
da Google; não é um atalho para enviar este aplicativo comum à revisão.
