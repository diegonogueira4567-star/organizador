# Registro técnico de bugs

Classificação: CRÍTICO (perda de dados/falha geral), ALTO (recorrência/notificação/função central), MÉDIO, BAIXO.

## Corrigidos durante o desenvolvimento

### BUG-001 — CRÍTICO — Nota salva era lida de volta vazia
- **Reprodução:** `NoteDocumentTest.codifica e decodifica sem perda` falhou: o documento decodificado tinha um único parágrafo vazio.
- **Causa raiz:** o JSON usava `encodeDefaults = false`; o campo de versão `v` (igual ao padrão) não era gravado, e a
  decodificação tratava documentos sem `v` como formato legado `{ "text": … }`, descartando os blocos.
- **Correção:** `@EncodeDefault(ALWAYS)` no campo de versão + documentos sem `v` mas com `blocks` são lidos como v1
  (`domain/note/NoteDocument.kt`). Conteúdo ilegível nunca é descartado: vira parágrafo com o texto bruto.
- **Regressão:** `NoteDocumentTest` (5 testes) e todos os testes de notas no banco.

### BUG-002 — ALTO — "Desfazer" conclusão de tarefa recorrente falhava (chave estrangeira)
- **Reprodução:** `TaskNoteDbTest.tarefaRecorrenteConcluidaGeraProximaEPodeSerDesfeita` → `SQLiteConstraintException: FOREIGN KEY constraint failed`.
- **Causa raiz:** `UndoCompletionUseCase` apagava a próxima instância antes de devolver a regra à tarefa original;
  a regra ficava órfã e era removida, e a atualização seguinte apontava para uma regra inexistente.
- **Correção:** devolver a regra à tarefa original primeiro, depois apagar a instância (`domain/usecase/TaskUseCases.kt`).
- **Regressão:** o mesmo teste.

### BUG-003 — CRÍTICO — App fechava ao abrir a adição rápida de tarefa
- **Reprodução:** `NavigationUiTest.adicionarTarefaRapidaApareceEmHoje` → `IllegalStateException: FocusRequester is not initialized`.
- **Causa raiz:** o foco era pedido em `LaunchedEffect` antes de a `ModalBottomSheet` compor/posicionar o campo.
- **Correção:** pedir o foco uma única vez em `onGloballyPositioned` (`ui/quick/QuickSheets.kt`; mesmo padrão no editor de checklist).
- **Regressão:** o mesmo teste de interface.

### BUG-004 — CRÍTICO — Alarmes, ações de notificação e reagendamento após reinicialização quebrados
- **Reprodução:** instalação do APK de teste → processo caiu com `ClassNotFoundException: com.organizador.app.notifications.SystemEventsReceiver`.
- **Causa raiz:** o manifesto declarava os receivers em `.notifications.*`, mas as classes estão em `.core.notifications.*`.
- **Correção:** nomes corrigidos em `app/src/main/AndroidManifest.xml`.
- **Regressão:** `ReminderDeliveryTest` (exige o `AlarmReceiver` real para a notificação aparecer) e a execução da suíte
  instrumentada (que instala o app e recebe `MY_PACKAGE_REPLACED`).

### BUG-005 — MÉDIO — Enter no campo "Novo item" quebrava linha em vez de adicionar
- **Reprodução:** teste manual no release (criar rotina digitando itens com Enter).
- **Correção:** quebra de linha no campo dispara a adição (uma linha = um item); colar várias linhas continua funcionando.

### BUG-006 — MÉDIO — Avisos do Lint com risco funcional
- `StateFlow.value` lido durante a composição (data de "Hoje" e ordenação de notas não atualizavam) → passaram a ser
  coletados como estado. `produceState` da miniatura de imagem substituído por `LaunchedEffect` + estado.

### BUG-007 — MÉDIO — `profileinstaller` transitivo não suportava Android 15
- **Reprodução:** Macrobenchmark com `CompilationMode.Partial` → `The device SDK version (35) isn't supported by the target app's copy of profileinstaller`.
- **Efeito no usuário:** em Android 15 os perfis de base das bibliotecas (Compose) não eram instalados fora da Play Store, deixando a inicialização e a rolagem iniciais mais lentas.
- **Correção:** dependência explícita `androidx.profileinstaller:1.4.1`.
- **Regressão:** `StartupBenchmark.inicializacaoFrioComPerfil` e os `ScrollBenchmark` usam esse caminho.

### BUILD-001 — Falhas intermitentes de compilação
- **Sintoma:** `compileKotlin` falhava às vezes com `InvocationTargetException` e passava ao repetir.
- **Causa raiz (confirmada no log):** `Not enough memory to run compilation` — o compilador Kotlin roda dentro do
  daemon do Gradle (2 GB) e vários módulos compilavam em paralelo com o emulador ocupando a RAM.
- **Correção:** `org.gradle.workers.max=2` em `gradle.properties`. Numa execução longa (todas as suítes no mesmo
  daemon) apareceu também `AssertionError: Metaspace` (limite de 512 MB do metaspace, consumido pelo compilador
  Kotlin em processo) → `-XX:MaxMetaspaceSize=1g`. O metaspace só ocupa memória quando realmente usado.

### Observação de build (não é defeito do app)
Após renomear `mipmap-anydpi-v26` → `mipmap-anydpi`, um build incremental reaproveitou saída antiga e o recurso do
ícone não foi encontrado. Resolvido com `./gradlew clean` (o APK entregue foi gerado com `clean` e `--no-build-cache`
e o ícone foi conferido com `aapt2 dump badging`). A falha transitória de `compileReleaseKotlin` vista
na versão 1.0.0 era o BUILD-001.

## Limitações conhecidas (não são erros críticos)

| Severidade | Descrição |
|---|---|
| MÉDIO | "Eventos pessoais" do calendário são tarefas com data/hora; não há entidade separada de evento. |
| MÉDIO | Reordenação manual por botões subir/descer (projetos, categorias, itens, cartões, menus); arrastar e soltar não implementado. |
| BAIXO | Notas novas abrem com o foco no corpo, não no título. |
| BAIXO | Biometria não testada em aparelho com digital cadastrada (emulador sem biometria); fluxo de PIN sem teste de interface automatizado. |
| BAIXO | Anexos ficam no armazenamento privado sem cifragem adicional em repouso (o Android já isola e, em aparelhos modernos, cifra o armazenamento). |
| — | 60 FPS não confirmado: o emulador desta máquina usa renderização por software e nem o app Configurações passa de ~22 FPS nele. A medição automatizada existe (`:benchmark`), falta rodá-la em aparelho físico de entrada. |
