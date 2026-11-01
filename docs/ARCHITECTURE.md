# Arquitetura

## Módulos

```
Organizador/
├── domain/   Kotlin puro (JVM). Modelos, motor de recorrência, formato de notas, contratos de repositório, casos de uso.
├── data/     Android library. Room/SQLite, DataStore, repositórios, backup, criptografia, armazenamento de anexos.
└── app/      Aplicação. Compose/Material 3, ViewModels, navegação, Hilt, notificações, alarmes, WorkManager, bloqueio.
benchmark/    Testes Macrobenchmark/UiAutomator contra a variante "benchmark" do app (R8 ligado, assinatura de debug).
```

A variante `benchmark` inclui apenas em `app/src/benchmark` uma tela de carga de dados (600 tarefas, 300 notas)
usada pelas medições; ela não existe no APK de release.

Dependências: `app → data → domain`. Não há dependência circular; `domain` não conhece Android.

**Decisão:** a estrutura de referência do prompt (core/feature/… em dezenas de módulos) foi condensada em 3 módulos
Gradle com pacotes por funcionalidade dentro de `app/ui/*`. Motivos: a máquina de build tem 8 GB de RAM e roda
emulador; módulos vazios ou de uma classe só aumentariam o tempo de build sem ganho real. A separação que importa
(UI × regras × persistência) é imposta pelo compilador: a UI não enxerga DAOs nem entidades Room porque depende
apenas das interfaces de `domain` (os pacotes `data.backup`/`data.security` são usados diretamente pelas telas de
backup e bloqueio, de propósito).

## Camadas

| Camada | Onde | Responsabilidade |
|---|---|---|
| Modelo | `domain/model` | Entidades de negócio imutáveis (`Task`, `ChecklistTemplate`, `Note`…) e preferências (`AppSettings`). |
| Regras | `domain/recurrence`, `domain/note`, `domain/usecase` | Cálculo de ocorrências, plano de reconciliação, edição de spans, conclusão de tarefas recorrentes, conversões nota→tarefa. |
| Contratos | `domain/repository` | Interfaces de repositório e `TransactionRunner`. |
| Persistência | `data/local`, `data/repository` | Room (entidades, DAOs, consultas dinâmicas), mapeadores, implementações dos repositórios. |
| Infra | `data/backup`, `data/security`, `data/storage`, `data/prefs` | ZIP de backup, AES-GCM/PBKDF2, PIN, anexos, DataStore. |
| Apresentação | `app/ui/**` | Telas Compose + `@HiltViewModel`. Estado exposto como `StateFlow`. |
| Sistema | `app/core/**` | Alarmes, notificações, receivers, `SyncCoordinator`, `ReconcileWorker`, `LockManager`. |

Regras complexas nunca ficam nas telas: por exemplo, concluir uma tarefa recorrente passa por
`CompleteTaskUseCase`, salvar um checklist por `SaveChecklistUseCase` (que recalcula o acompanhamento da regra) e
a geração de execuções por `ReconcileChecklistsUseCase` + `OccurrencePlanner` (puro, testado na JVM).

## Fluxo de dados

- Room é a **fonte única da verdade**. Telas observam `Flow`s do Room; qualquer gravação reflete automaticamente.
- Consultas que dependem de "hoje" usam `TimeProvider.todayFlow()`, que reemite na virada do dia/fuso.
- Agregados de várias tabelas usam `invalidationFlow(...)` (observador do `InvalidationTracker`).

## Sincronização e tarefas em segundo plano

`SyncCoordinator.fullSync()` = reconciliar execuções de checklists + reconstruir todos os alarmes a partir do banco.
É chamado:

1. na criação do processo e a cada `onResume` da Activity;
2. pelo `ReconcileWorker` periódico (6 h, WorkManager) — garante a geração mesmo sem abrir o app;
3. pelo alarme de "virada de janela" (próximo início de ocorrência, inexato);
4. pelo `SystemEventsReceiver` (BOOT_COMPLETED, MY_PACKAGE_REPLACED, TIME_SET, TIMEZONE_CHANGED, DATE_CHANGED,
   mudança da permissão de alarme exato) via WorkManager;
5. a cada alteração da tabela de lembretes (observador com debounce).

`AlarmScheduler.syncAll()` é idempotente: calcula o conjunto desejado de alarmes, cancela os que sobraram
(registro de chaves em SharedPreferences) e (re)agenda os demais. Com permissão de alarme exato usa
`setExactAndAllowWhileIdle`; sem ela, `setAndAllowWhileIdle` (o Android pode atrasar — a tela de
notificações informa isso ao usuário; o app não promete pontualidade que o sistema não garante).

## Injeção de dependências

Hilt. `DatabaseModule` (banco, `TimeProvider`) e `RepositoryModule` (bindings) em `data/di`; `AppModule`
(`@ApplicationScope` para gravações que precisam terminar após a tela fechar). Workers via `HiltWorkerFactory`
(o inicializador automático do WorkManager foi removido do manifesto). Nos testes instrumentados o banco é
substituído por um em memória (`TestDatabaseModule`).

## Editor de notas

Documento de blocos (`NoteDocument`, JSON versionado, **não HTML**). Cada bloco tem tipo (parágrafo, títulos,
listas, tarefa, citação, código, divisória, imagem, anexo, tabela, referência a checklist/nota) e spans de estilo
(negrito, itálico, sublinhado, tachado, destaque colorido, link). O editor usa `BasicTextField` com uma
`VisualTransformation` que desenha os spans sobre o texto puro; `SpanEditing.adjust` desloca os spans a cada edição.

Persistência: rascunho com debounce de 400 ms (`note_drafts`) e gravação com debounce de 1,5 s, além de gravação
imediata em `ON_STOP`, ao voltar e em `onCleared` (escopo de aplicação). Concorrência: bloqueio otimista por
`version`; se outra gravação mudou a nota, nada é sobrescrito — o conteúdo local vira uma "cópia de conflito".
Ao reabrir, um rascunho mais novo que a versão gravada oferece recuperação.

## Segurança

- PIN: PBKDF2-HMAC-SHA256 (60.000 iterações, sal aleatório de 16 bytes) em DataStore separado (`security`),
  comparação em tempo constante, bloqueio progressivo após 5 tentativas (30 s → até 1 h). Nunca em texto puro.
- Biometria: `BiometricPrompt` (BIOMETRIC_WEAK) como atalho para o PIN.
- Backups: AES-256-GCM com chave PBKDF2 (150.000 iterações). **Android Keystore não é usado** para essas chaves
  porque o backup precisa ser restaurável em outro aparelho (chaves do Keystore não são exportáveis) e o PIN só
  precisa de verificação (hash), não de cifragem reversível. Decisão documentada para revisão futura.
- Logs não contêm conteúdo de notas/tarefas (apenas contagens e tipos de evento).
- `allowBackup=false` + regras de extração vazias: nada sai do aparelho sem ação explícita do usuário.

## Desempenho

- Listas: consultas indexadas com agregados no próprio SQL (sem N+1); `LazyColumn` virtualiza a renderização.
- Paginação: concluídas, arquivadas, lixeira e histórico de execuções carregam páginas de 100 (`LIMIT` no SQL) com
  botão "Carregar mais" quando a página veio cheia.
- Prévias de notas usam `substr(plainText, 1, 200)` — o documento completo só é carregado no editor.
- Miniaturas de imagens decodificadas com `inSampleSize` em `Dispatchers.IO`.
- Medições em `docs/TESTING.md`.
