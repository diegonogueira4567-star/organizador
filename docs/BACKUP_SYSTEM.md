# Backup e restauração

Código: `data/backup/BackupArchive.kt` (formato, validação, criptografia — JVM puro) e `BackupManager.kt` (banco,
anexos, preferências). Tela: Configurações → Backup e restauração.

## Formato do pacote (.zip)

| Entrada | Conteúdo |
|---|---|
| `data.json` | Exportação lógica de todas as tabelas selecionadas (entidades Room serializadas) |
| `settings.json` | Preferências (opcional; **PIN e biometria não são incluídos**) |
| `attachments/<uuid>.<ext>` | Arquivos anexados (opcional) |
| `manifest.json` | formato/versão, versão do app e do esquema, data, seções, contagens, SHA-256 e tamanho de cada entrada, parâmetros de criptografia |

**Por que lógico e não o arquivo SQLite?** Independe do modo WAL e de cópia em uso, permite backup seletivo,
mesclagem por id e leitura por versões futuras do app (campos desconhecidos são ignorados).

Seções selecionáveis: tarefas e projetos; notas e pastas; checklists/rotinas com histórico de execuções; histórico
de atividades; configurações; anexos. Projetos, categorias e etiquetas acompanham qualquer seção de conteúdo.

## Criptografia opcional

- Chave: PBKDF2-HMAC-SHA256, 150.000 iterações, sal aleatório de 16 bytes.
- Cifra: AES-256-GCM (autenticada), IV aleatório de 12 bytes por entrada, **AAD = caminho da entrada** (impede
  trocar conteúdos entre entradas).
- O manifesto guarda um *verificador* cifrado: a senha é validada antes de qualquer restauração.
- Senha mínima de 8 caracteres na interface; não há recuperação de senha esquecida.

## Validação na importação (antes de tocar em qualquer dado)

1. Somente nomes de entrada permitidos (`manifest.json`, `data.json`, `settings.json`, `attachments/<uuid>.<ext>`):
   impede *path traversal*/zip-slip, diretórios e arquivos inesperados.
2. Limites: 20.000 entradas, 4 GB no total, 256 MB por JSON, 51 MB por anexo — medidos no conteúdo
   **descompactado** (proteção contra zip bomb). Entradas duplicadas são recusadas.
3. Cada entrada confere tamanho e SHA-256 com o manifesto; nada a mais, nada a menos.
4. Formato mais novo que o app → recusado como incompatível.
5. Senha conferida pelo verificador; conteúdo adulterado falha na autenticação GCM.

A interface mostra data, versão, se é criptografado e **as contagens reais** (tarefas, notas, checklists,
execuções, anexos…) e pede o modo.

## Restauração

- **Mesclar**: mantém os dados atuais e insere apenas registros cujos ids não existem.
- **Substituir** (confirmação adicional): apaga os dados atuais e importa o backup.
- Sempre: **backup preventivo** completo (sem senha) em `files/backups/preventivo-AAAAMMDD-HHMMSS.zip` (os 3 mais
  recentes ficam; podem ser restaurados pela mesma tela).
- Anexos são extraídos para uma área temporária já validada, copiados para o armazenamento privado e então o banco é
  atualizado em **uma única transação**. Referências que violariam chaves estrangeiras (ex.: tarefa ligada a uma nota
  de seção não importada) são anuladas antes da inserção.
- Após restaurar: reconciliação de execuções e reagendamento de todos os alarmes.
- A área temporária é apagada ao final (com sucesso ou erro); "Limpar arquivos temporários" em Segurança remove sobras.
