# Testes e relatório

Ambiente da execução registrada abaixo (01/11/2026, versão 1.1.0): emulador Android 15 (API 35, x86_64) em Linux, 8 GB de RAM no
host; JDK Temurin 21; Gradle 8.10.2.

## Como executar

```bash
export JAVA_HOME=$HOME/Android/jdk-21
./gradlew :domain:test :data:testDebugUnitTest                           # JVM
./gradlew :data:connectedDebugAndroidTest :app:connectedDebugAndroidTest   # aparelho/emulador conectado
./gradlew :app:lintDebug
./gradlew :benchmark:connectedBenchmarkAndroidTest                         # Macrobenchmark + teste de processo morto
```

Os testes do app usam banco **em memória** (`TestDatabaseModule`) e não tocam nos dados reais do aparelho.

## Resultado

| Suíte | Onde | Testes | Resultado |
|---|---|---|---|
| `RecurrenceEngineTest` | JVM | 13 | ✅ |
| `OccurrencePlannerTest` | JVM | 12 | ✅ |
| `NoteDocumentTest` + `SpanEditingTest` | JVM | 12 | ✅ |
| `CompleteTaskRulesTest` | JVM | 4 | ✅ |
| `BackupArchiveTest` | JVM | 9 | ✅ |
| `QueryBuilderTest` | JVM | 4 | ✅ |
| `ChecklistRecurrenceDbTest` | emulador (Room real) | 11 | ✅ |
| `TaskNoteDbTest` | emulador | 8 | ✅ |
| `BackupRestoreTest` | emulador | 5 | ✅ |
| `MigrationTest` | emulador | 1 | ✅ |
| `PerformanceTest` | emulador | 1 | ✅ |
| `AlarmSchedulerTest` | emulador (Hilt) | 2 | ✅ |
| `ReminderDeliveryTest` | emulador (Hilt, alarme real) | 1 | ✅ |
| `NavigationUiTest` | emulador (Compose UI) | 3 | ✅ |
| `NoteAutosaveUiTest` | emulador (Compose UI) | 1 | ✅ |
| `KillRecoveryTest` | emulador (UiAutomator, processo morto) | 1 | ✅ |
| `StartupBenchmark` | emulador (Macrobenchmark) | 2 | ✅ |
| `ScrollBenchmark` | emulador (Macrobenchmark) | 3 | ✅ (executa e mede; ver ressalva de 60 FPS) |
| **Total** | | **93** | **93 aprovados, 0 falhas** |

Android Lint (`lintDebug`): **0 erros**. Avisos restantes: versões mais novas de dependências disponíveis
(mantidas de propósito na toolchain já validada nesta máquina), `targetSdk 35` (36 disponível) e 2 informativos.

## Testes exigidos pelo enunciado

| # | Requisito | Coberto por |
|---|---|---|
| 1 | Checklist diário | `ChecklistRecurrenceDbTest.checklistDiario_*`, `OccurrencePlannerTest.checklist diario…` |
| 2 | Checklist semanal | `ChecklistRecurrenceDbTest.checklistSemanal_*`, planner |
| 3 | Checklist de sábado | `rotinaDeSabado_registraParcialEReiniciaNaProximaExecucao` (cenário exato do enunciado) |
| 4 | Dias personalizados | `diasPersonalizados` (banco) e planner |
| 5 | Reinicialização do celular | `reinicializacaoDoAparelho_*` (banco em arquivo fechado/reaberto + reconciliação após 2 semanas); `AlarmSchedulerTest` (ressincronização idempotente). *O broadcast BOOT_COMPLETED real não é disparado no teste.* |
| 6 | Mudança de fuso horário | `mudancaDeFusoNaoDuplica` (banco), planner (São Paulo ↔ Tóquio) e horário de verão (lacuna à meia-noite, 2018) |
| 7 | Encerramento inesperado | `KillRecoveryTest` (processo morto com `am force-stop` entre o rascunho e a gravação; ao reabrir, o app oferece e recupera o texto) + rascunho no repositório + backup corrompido não altera dados |
| 8 | Salvamento automático de notas | `NoteAutosaveUiTest` (digita no editor real, sem salvar; o texto chega ao banco após o debounce e o rascunho é limpo) + controle de versão/conflito no repositório |
| 9 | Recuperação de rascunhos | `salvamentoDeNotaComControleDeVersaoERascunho` |
| 10 | Migração do banco | `MigrationTest` (valida o esquema v1 exportado e o caminho de migrações com dados preservados) |
| 11 | Backup e restauração | `BackupRestoreTest` (completo, mesclagem, seletivo, senha, arquivo corrompido) + `BackupArchiveTest` (zip-slip, adulteração, ausência de arquivo, formato futuro) |
| 12 | Exclusão e recuperação de tarefas | `exclusaoERecuperacaoDeTarefas` (lixeira, restauração, exclusão definitiva em cascata) |

Outras categorias: notificações (`AlarmSchedulerTest`, `ReminderDeliveryTest` — a notificação é efetivamente exibida
pelo sistema), interface/navegação (`NavigationUiTest`), persistência (todas as suítes de banco), regressão (BUG-001 a BUG-004 de `BUG_REPORT.md` têm teste que falhava
antes da correção; BUG-005 e BUG-006 foram verificados manualmente/pelo Lint).

## Metas de desempenho (medidas)

Conjunto: 5.000 tarefas e 2.000 notas de 20 parágrafos (`PerformanceTest`, média de 3 execuções após aquecimento):

| Operação | Tempo | Meta |
|---|---|---|
| Lista Hoje (1.673 itens) | 50 ms | 500 ms |
| Próximos 7 dias | 24 ms | 500 ms |
| Concluídas (paginada, 300) | 25 ms | 500 ms |
| Pesquisa em tarefas | 19 ms | 500 ms |
| Lista de notas (2.000) | 29 ms | 500 ms |
| Pesquisa no conteúdo das notas | 48 ms | 500 ms |

Inicialização a frio do APK de release (`am start -W`, TotalTime): 0,61–0,90 s sem perfil compilado;
0,30–0,43 s após `cmd package compile -m speed-profile` (equivalente à otimização pós-instalação). Meta: 2 s.

### Macrobenchmark (módulo `:benchmark`, variante `benchmark` = release com R8)

Inicialização a frio (`StartupTimingMetric`, 5 iterações, timeToInitialDisplay): **771–928 ms** sem compilação
prévia e 879–1.063 ms com compilação por perfil (diferença dentro do ruído do emulador). Meta de 2 s atendida.

Fluidez (`FrameTimingMetric`, 5 iterações de 6 arremessos, com 600 tarefas e 300 notas carregadas):

| Tela | frameDurationCpuMs P50 / P90 / P99 |
|---|---|
| Lista Hoje | 44,9 / 52,4 / 73,6 ms |
| Lista de notas | 56,8 / 64,2 / 83,1 ms |
| Painel | 66,0 / 83,1 / 90,4 ms |

**Os 60 FPS (16,7 ms) NÃO foram confirmados nesta máquina.** O emulador renderiza com SwiftShader (GPU emulada
por software): como referência, o app Configurações do próprio Android, rolado da mesma forma, mediu P50 44 ms e
P90 73 ms — mesma faixa do Organizador. Os números servem para comparar versões, não como valor absoluto. Para
validar a meta é preciso rodar `:benchmark:connectedBenchmarkAndroidTest` em um aparelho físico (ou emulador com
`-gpu host`).

## Teste manual (APK de release)

Com R8/minificação ativos: criar nota com Enter dividindo blocos, sair e reabrir (conteúdo persistido); criar
"Rotina de domingo" com 3 itens recorrente aos domingos, marcar 2 (execução de 01/11, 2/3, progresso salvo);
painel, menus e navegação; nenhuma exceção no Logcat.
