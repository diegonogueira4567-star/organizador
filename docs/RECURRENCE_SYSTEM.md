# Sistema de recorrência

Código: `domain/recurrence/` (puro, sem Android) + `ReconcileChecklistsUseCase` + `ChecklistRepositoryImpl.applyPlan`.

## Separação obrigatória

| | Entidade | Observação |
|---|---|---|
| A | `ChecklistTemplate` | tipo: SIMPLE, RECURRING, PERMANENT, TEMPLATE; `isRoutine` decide se aparece em Rotinas |
| B | `ChecklistItem` | itens atuais do modelo |
| C | `RecurrenceRule` | regra declarativa (abaixo) |
| D | `ChecklistOccurrence` | uma execução por (modelo, data) |
| E | `ChecklistItemState` | marcação de cada item **naquela** execução, com título copiado |

## Regra (`RecurrenceRule`)

- `frequency`: NONE (data única), DAILY, WEEKLY, MONTHLY, YEARLY; `interval` ≥ 1.
- `daysOfWeek` (semanal), `daysOfMonth` (1–31 e −1 = último dia; dia inexistente é ajustado ao último dia do mês),
  `monthOfYear`/`dayOfMonth` (anual; 29/02 vira 28/02 em anos comuns).
- `startDate`, `endDate` opcional, `timeOfDay` (horário de execução/aviso), `resetTime` (início do "dia" da
  rotina; padrão nas preferências), `zoneId` opcional (null = acompanha o fuso do aparelho), `enabled`.

Presets na interface: todos os dias, seg–sex, fins de semana, sábados, domingos, dias escolhidos, datas do mês,
anual, a cada N dias, e "personalizado" (frequência + intervalo livres).

`RecurrenceEngine`: `matches`, `nextOnOrAfter`, `nextAfter`, `previousOnOrBefore`, `occurrencesBetween`.
Toda busca é limitada ao período máximo da regra (custo constante; nada é materializado para o futuro).

## Identidade e janelas

- Uma execução é identificada por **data local**. Instantes só são derivados quando necessários (aviso, janela).
  Assim, mudar de fuso ou passar pelo horário de verão não cria nem perde execuções.
- **Data efetiva** = data atual no fuso da regra, recuada um dia se o horário for anterior a `resetTime`.
- **Janela** da execução de data D: de `D@resetTime` até o início da próxima ocorrência (ou até o dia seguinte ao
  `endDate`). Ex.: rotina de sábado pode ser concluída até a véspera do sábado seguinte; checklist diário vale o dia.
- `ZonedDateTime.of(...)` ajusta horários inexistentes (lacuna do horário de verão) para frente — testado com
  São Paulo, 04/11/2018, quando 00:00 não existiu.

## Reconciliação (`OccurrencePlanner.plan`)

Entrada: regra, `trackingFrom`, `generatedThrough`, execuções abertas, agora (no fuso da regra), `resetTime`.
Saída (`ReconcilePlan`):

1. **Fechar** execuções abertas cuja janela terminou → COMPLETED (todos marcados), PARTIAL (alguns) ou MISSED (nenhum).
2. **Criar como MISSED** (sem estados de itens) as datas devidas cuja janela já terminou sem registro — distingue
   execução perdida de futura; futuras nunca são gravadas.
3. **Criar a execução ativa** (com todos os itens pendentes) para a janela atual.
4. Atualizar `generatedThrough` (otimização; a garantia real é o índice único).

Limites e proteções:

- Recuperação retroativa limitada a **400 dias** (`truncated = true` quando excedido).
- `trackingFrom` evita registrar dezenas de perdas quando a data inicial escolhida está no passado: ao criar,
  alterar ou reativar a regra, o acompanhamento começa na ocorrência cuja janela está aberta agora (ou hoje).
- Regra desativada: não gera novas execuções; a aberta é fechada normalmente quando a janela acaba.
- Regra alterada: execuções fechadas ficam intactas; a execução aberta que não pertence mais à nova regra e não
  teve progresso é descartada; com progresso, é mantida.
- Itens editados no modelo: só a execução aberta é sincronizada (novos itens entram pendentes, marcações são
  preservadas); execuções fechadas mantêm títulos e marcações originais.
- Histórico só muda por edição explícita (tela da execução → "Editar esta execução do histórico"); o status final
  é recalculado.

## Quando a reconciliação roda (não depende de abrir o app)

WorkManager periódico (6 h), alarme na próxima virada de janela, BOOT_COMPLETED, MY_PACKAGE_REPLACED, TIME_SET,
TIMEZONE_CHANGED, DATE_CHANGED, abertura/retorno ao app e após salvar um checklist. Todas as chamadas passam por um
`Mutex` e são idempotentes.

## Tarefas recorrentes

Ao concluir (`CompleteTaskUseCase`): a instância atual fica concluída (histórico/estatísticas) e uma nova é criada na
próxima data a partir do prazo; se a tarefa estava muito atrasada e a próxima data ainda ficaria no passado, usa a
próxima a partir de hoje. Subtarefas da nova instância começam pendentes; lembretes relativos são recalculados.
"Desfazer" reabre a original e remove a instância criada (ver BUG-002).

## Exemplo do enunciado (coberto por teste no banco real)

"Rotina de sábado" com 5 itens; no sábado 3 são marcados → no sábado seguinte a execução anterior fecha como
PARTIAL 3/5 com as marcações preservadas, e a nova começa com 5 pendentes
(`ChecklistRecurrenceDbTest.rotinaDeSabado_registraParcialEReiniciaNaProximaExecucao`).
