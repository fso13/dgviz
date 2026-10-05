# DGViz Gradle Plugin

Плагин `id("io.github.dgviz.scan")` отправляет **дерево зависимостей** и **CycloneDX SBOM** в DGViz и сохраняет отчёт для MR/CI.

## Подключение

1. В DGViz (Admin → Репозитории) создайте репозиторий — получите `repositoryId` и `scan token`.
2. Опубликуйте плагин локально из репозитория DGViz:

```bash
./gradlew :dgviz-gradle-plugin:publishToMavenLocal
```

3. В целевом Gradle-проекте:

```kotlin
// settings.gradle.kts
pluginManagement {
    repositories {
        mavenLocal()
        gradlePluginPortal()
        mavenCentral()
    }
}

// build.gradle.kts
plugins {
    java
    id("io.github.dgviz.scan") version "0.3.0"
}

dgviz {
    host = System.getenv("DGVIZ_HOST") ?: "http://localhost:8080"
    repositoryId = (System.getenv("DGVIZ_REPO_ID") ?: "1").toLong()
    token = System.getenv("DGVIZ_TOKEN") ?: ""
    createIssues = (System.getenv("DGVIZ_CREATE_ISSUES") ?: "false").toBoolean()
    failOnIssues = true
    configurationName = "runtimeClasspath"
}
```

## Задача

| Свойство | Значение |
|----------|----------|
| Имя | `dgvizScan` |
| Group | `verification` |
| Выход | `build/reports/dgviz/report.md`, `report.json` |

```bash
export DGVIZ_HOST=http://localhost:8080
export DGVIZ_REPO_ID=1
export DGVIZ_TOKEN=dgviz_...
./gradlew dgvizScan
```

## Параметры блока `dgviz { }`

| Параметр | Тип | Обязательный | По умолчанию | Описание |
|----------|-----|--------------|--------------|----------|
| `host` | `String` | да* | `http://localhost:8080` | Базовый URL сервиса DGViz. Без завершающего `/`. |
| `repositoryId` | `Long` | **да** | — | Числовой ID репозитория в DGViz. |
| `token` | `String` | **да** | — | Scan token; передаётся в заголовке `X-DGViz-Token`. |
| `createIssues` | `Boolean` | нет | `false` | Если `true`, DGViz создаёт issues в GitLab/GitHub для critical/high уязвимостей (нужен access token у репозитория в DGViz). |
| `failOnIssues` | `Boolean` | нет | `true` | Если `true`, задача падает, когда API вернул `status: FAILED` (есть conflicts/vulns). |
| `configurationName` | `String` | нет | `runtimeClasspath` | Имя Gradle configuration для резолва дерева. Нужен плагин `java` / `java-library`. |

\* `host` имеет convention, но для production его нужно задать явно или через `DGVIZ_HOST`.

### Переменные окружения (удобно для CI)

| Переменная | Аналог в `dgviz` |
|------------|------------------|
| `DGVIZ_HOST` | `host` |
| `DGVIZ_REPO_ID` | `repositoryId` |
| `DGVIZ_TOKEN` | `token` |
| `DGVIZ_CREATE_ISSUES` | `createIssues` (`true` / `false`) |

### Настройка задачи напрямую (Groovy/Kotlin DSL)

Свойства задачи `dgvizScan` наследуют extension, но их можно переопределить:

```kotlin
tasks.named<DgvizScanTask>("dgvizScan") {
    configurationName.set("compileClasspath")
    failOnIssues.set(false)
}
```

| Свойство задачи | Описание |
|-----------------|----------|
| `outputDir` | Каталог отчётов (по умолчанию `build/reports/dgviz`). Только output, не настраивается в extension. |

## API DGViz

`POST /api/v1/repositories/{id}/scan`  
Header: `X-DGViz-Token: <scan token>`

```json
{
  "projectName": "my-app",
  "buildSystem": "gradle",
  "createIssues": false,
  "dependencyTree": { "group": "...", "name": "...", "dependencies": [] },
  "sbom": { "bomFormat": "CycloneDX", "specVersion": "1.5", "components": [] }
}
```

После scan SBOM доступен в UI: **Скачать SBOM** (`GET /workspace/repositories/{id}/sbom.json`).
