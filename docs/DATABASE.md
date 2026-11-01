# Banco de dados

Room 2.6.1 sobre SQLite, arquivo `organizador.db` (WAL), versão de esquema **1**. Esquema exportado em
`data/schemas/com.organizador.data.local.OrganizadorDatabase/1.json` (versionado junto com o código).

## Convenções

- Ids: UUID v4 em texto — estáveis entre aparelhos e backups.
- Instantes: epoch millis UTC. Datas locais: `epochDay`. Horários: minuto do dia.
- Exclusão do usuário é **lógica** (`deletedAt`); remoção física apenas pela lixeira, explicitamente.
- `PRAGMA foreign_keys = ON` em toda abertura.
- Pais são gravados com `@Upsert` (nunca `REPLACE`, que apagaria filhos em cascata).

## Tabelas

| Tabela | Conteúdo | Chaves estrangeiras | Índices |
|---|---|---|---|
| `projects` | nome, descrição, cor, ícone, ordem, arquivado | — | archived, sortOrder |
| `categories` | nome, cor, ordem | — | sortOrder |
| `tags` | nome (único), cor | — | name UNIQUE |
| `recurrence_rules` | frequência, intervalo, dias da semana/mês, mês/dia anual, início/fim, horário, reinício, fuso, ativa | — | — |
| `tasks` | título, descrição, observações, prazo (data+hora), prioridade, status, arquivada, `deletedAt`, ordem, datas | project SET NULL, category SET NULL, rule SET NULL, linkedNote SET NULL | projectId, categoryId, recurrenceRuleId, linkedNoteId, (status, dueDate), deletedAt, completedAt |
| `subtasks` | título, feita, ordem | task CASCADE | taskId |
| `task_tags` | (taskId, tagId) PK | task CASCADE, tag CASCADE | tagId |
| `task_attachments` | nome, tipo, tamanho, caminho relativo | task CASCADE | taskId |
| `note_folders` | nome, pasta-mãe, cor | parent CASCADE (subpastas) | parentId |
| `notes` | título, `content` (JSON de blocos), `plainText`, pasta, projeto, favorita, fixada, arquivada, `deletedAt`, **`version`** | folder SET NULL, project SET NULL | folderId, projectId, updatedAt, deletedAt |
| `note_tags` | (noteId, tagId) PK | note CASCADE, tag CASCADE | tagId |
| `note_attachments` | idem anexos de tarefa | note CASCADE | noteId |
| `note_drafts` | rascunho por nota (título, conteúdo, versão base) | note CASCADE | PK noteId |
| `entity_links` | vínculos nota↔tarefa/nota/checklist | — (polimórfico; limpo na exclusão física) | (type, fromId, toId) UNIQUE, toId |
| `checklist_templates` | **A. modelo**: título, tipo, rotina?, projeto, cor, regra, `trackingFrom`, `generatedThrough`, arquivado, `deletedAt` | project SET NULL, rule SET NULL | projectId, recurrenceRuleId, (kind, isRoutine), deletedAt |
| `checklist_items` | **B. itens do modelo** | template CASCADE | templateId |
| `checklist_occurrences` | **D. execução**: data, status, totais, `dueAt`, `closedAt`, `completedAt` | template CASCADE | **(templateId, date) UNIQUE**, date, closedAt, status |
| `checklist_item_states` | **E. estado do item na execução** (título copiado, marcado, quando) | occurrence CASCADE, item SET NULL | (occurrenceId, itemId) UNIQUE, itemId |
| `reminders` | disparo, antecedência, disparado, adiado até | task CASCADE, checklist CASCADE | taskId, checklistId, (fired, triggerAt) |
| `activity_history` | tipo, entidade, título resumido, projeto, data | — | createdAt, (entityType, entityId) |

(C. regra de recorrência = `recurrence_rules`.)

### Pontos de integridade

- **Unicidade das execuções:** índice único `(templateId, date)` + `INSERT OR IGNORE` ⇒ geração idempotente mesmo
  com execuções concorrentes (worker + tela + alarme).
- **Histórico imutável:** o estado de cada item guarda uma cópia do título. Remover/renomear um item do modelo não
  altera execuções fechadas (`itemId` vira NULL por `SET NULL`; o título permanece).
- **Projetos/categorias/pastas excluídos** não apagam tarefas, notas ou checklists (`SET NULL`).
- Regras de recorrência órfãs são removidas por `deleteIfOrphan` somente quando nenhuma tarefa/checklist as referencia.

## Consultas

- Listas de tarefas: `TaskQueryBuilder` gera SQL parametrizado (visão + filtros + ordenação) com agregados de
  subtarefas, etiquetas (`group_concat`) e lembretes em uma única consulta. Pesquisa com `LIKE … ESCAPE '\'`
  (curingas do usuário são escapados).
- Notas: `NoteQueryBuilder` com prévia `substr(plainText,1,200)`.
- Estatísticas: contagens agregadas no SQL; agrupamento por dia feito em Kotlin com o fuso real (correto no horário de verão).

## Migrações

- `OrganizadorDatabase.MIGRATIONS` (vazio na versão 1). **`fallbackToDestructiveMigration` não é usado**: uma
  atualização sem migração falha de forma visível em vez de apagar dados.
- Processo para a versão 2: incrementar `VERSION`, adicionar `Migration(1, 2)` em `MIGRATIONS`, compilar (gera
  `2.json`) e o `MigrationTest` passa a validar 1→2 automaticamente com dados preservados.
- O backup é lógico (JSON das entidades) e tolerante a campos novos, então backups da v1 continuam restauráveis.
