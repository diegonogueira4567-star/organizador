# Organizador

Aplicativo Android de produtividade pessoal **100% offline**: tarefas, checklists recorrentes, rotinas,
notas em blocos, calendário, estatísticas, backup criptografado e bloqueio por PIN/biometria.
Inspirado na simplicidade de uso do Todoist (sem copiar marca ou identidade visual).

- Sem login, sem conta, sem servidor. O APK **não declara a permissão de INTERNET**.
- Dados no armazenamento privado do app (Room/SQLite + DataStore). Backup em nuvem do Android desativado.

## Entrega

| Item | Local |
|---|---|
| APK de release assinado | `entrega/Organizador-1.1.0-release.apk` (versionCode 2, versionName 1.1.0) |
| Código-fonte | `domain/`, `data/`, `app/` |
| Documentação | `docs/` |
| Relatório de testes | `docs/TESTING.md` |
| Bugs corrigidos e limitações conhecidas | `docs/BUG_REPORT.md` |

## Requisitos de build

- JDK 17+ (testado com Temurin 21 em `~/Android/jdk-21`)
- Android SDK com plataforma 35 e build-tools 35 (`local.properties` → `sdk.dir`)
- Gradle 8.10.2 (wrapper incluído), AGP 8.7.3, Kotlin 2.0.21

## Compilar e executar

```bash
export JAVA_HOME=$HOME/Android/jdk-21
./gradlew :app:assembleDebug          # APK de debug (app/build/outputs/apk/debug)
./gradlew :app:installDebug           # instala no aparelho/emulador conectado
./gradlew :app:assembleRelease        # APK de release minificado e assinado
```

### Assinatura de release

As credenciais **não ficam no repositório**. O build lê de `~/.gradle/gradle.properties` (ou variáveis de ambiente):

```
ORGANIZADOR_STORE_FILE=/caminho/organizador-release.jks
ORGANIZADOR_STORE_PASSWORD=...
ORGANIZADOR_KEY_ALIAS=organizador
ORGANIZADOR_KEY_PASSWORD=...
```

Nesta máquina o keystore foi criado em `~/.android-keystores/organizador-release.jks` (RSA 3072, validade 10.000 dias).
**Faça cópia de segurança desse arquivo e da senha**: sem eles não é possível publicar atualizações com a mesma assinatura.
Sem as propriedades, `assembleRelease` gera um APK não assinado.

## Testes

```bash
./gradlew :domain:test :data:testDebugUnitTest                         # JVM (rápidos)
./gradlew :data:connectedDebugAndroidTest :app:connectedDebugAndroidTest # aparelho/emulador
./gradlew :app:lintDebug
./gradlew :benchmark:connectedBenchmarkAndroidTest   # inicialização, fluidez e teste de processo morto
```

Resultados e cobertura por requisito em [`docs/TESTING.md`](docs/TESTING.md).

## Documentação

- [ARCHITECTURE.md](docs/ARCHITECTURE.md) — módulos, camadas, decisões
- [DATABASE.md](docs/DATABASE.md) — tabelas, relacionamentos, índices, migrações
- [RECURRENCE_SYSTEM.md](docs/RECURRENCE_SYSTEM.md) — regras, execuções, janelas, fuso/horário de verão
- [BACKUP_SYSTEM.md](docs/BACKUP_SYSTEM.md) — formato, integridade, criptografia, restauração
- [TESTING.md](docs/TESTING.md) — estratégia e relatório
- [CHANGELOG.md](docs/CHANGELOG.md)
- [BUG_REPORT.md](docs/BUG_REPORT.md)

## Licença

Código distribuído sob a [licença MIT](LICENSE): pode ser usado, copiado, modificado e redistribuído,
inclusive em outros projetos, desde que o aviso de copyright seja mantido. O software é fornecido sem garantia.
