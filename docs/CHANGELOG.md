# Changelog

## 1.1.0 (versionCode 2) — 01/11/2026

- Paginação com "Carregar mais" em concluídas, arquivadas, lixeira e histórico de execuções (páginas de 100).
- Animações discretas: entrada/saída/reordenação de itens nas listas, círculo de conclusão animado, barras de
  progresso animadas.
- Calendário projeta as próximas instâncias de tarefas recorrentes (marcadas como "prevista").
- Medições com Macrobenchmark (módulo `:benchmark`): inicialização a frio e fluidez de rolagem; teste de processo
  morto antes de salvar uma nota (recuperação de rascunho); teste de interface do salvamento automático.

## 1.0.0 (versionCode 1) — 01/11/2026

Primeira versão completa.

### Tarefas
- Criar, editar, duplicar, arquivar, excluir (lixeira), restaurar e excluir definitivamente; concluir/reabrir com "desfazer".
- Prazo com data e hora, prioridades P1–P4, projetos, categorias, etiquetas, subtarefas, observações, anexos.
- Recorrência completa (mesmo editor dos checklists); lembretes relativos ao prazo.
- Visões: Hoje (com atrasadas), Próximos dias, Caixa de entrada, Atrasadas, Concluídas, Arquivadas, Lixeira, por projeto,
  categoria, etiqueta e prioridade. Filtros, pesquisa e 6 ordenações. Adição rápida.

### Checklists e rotinas
- Tipos simples, recorrente (diário, dias úteis, fins de semana, sábados, domingos, dias escolhidos, datas do mês,
  anual, a cada N dias, personalizado), permanente e modelo reutilizável.
- Execuções separadas por data com histórico imutável, perdidas × futuras, horário de execução e de reinicialização,
  fuso fixo opcional, ativação/pausa, pular execução, edição explícita do histórico, lixeira de checklists.

### Notas
- Editor em blocos: títulos, negrito, itálico, sublinhado, tachado, destaque colorido, links, listas, caixas de seleção,
  citação, código, divisória, tabelas, imagens, anexos, checklists embutidos e referências entre notas.
- Pastas e subpastas, etiquetas, favoritas, fixadas, arquivadas, lixeira, pesquisa no conteúdo, ordenação.
- Salvamento automático, rascunhos com recuperação, detecção de conflito com cópia.
- Converter nota em tarefa e itens da nota em tarefas, vínculo com projetos.

### Demais
- Painel personalizável, calendário (mês, semana, dia, agenda), estatísticas por período/projeto, pesquisa global.
- Notificações: lembretes com concluir/adiar/amanhã, avisos de checklists e rotinas, resumo diário (atrasadas e prazos),
  horário silencioso, reagendamento após reinicialização e mudança de data/fuso.
- Aparência (tema claro/escuro/automático, cor de destaque, Material You, tamanho do texto, densidade), ordem dos
  menus, tela inicial, formatos de data/hora, primeiro dia da semana.
- Backup ZIP completo/seletivo com manifesto, SHA-256 e AES-256-GCM opcional; restauração com prévia, mesclar ou
  substituir e backup preventivo.
- Bloqueio por PIN (hash PBKDF2) e biometria.
