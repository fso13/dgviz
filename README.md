# DGViz — Dependency Graph Visualizer with GitLab Integration

Java 21 platform: Spring Boot admin + CLI analyzer with PostgreSQL, scheduled CVE sync, and Docker Compose.

**License:** Apache License 2.0

| | |
|---|---|
| Site / docs | https://fso13.github.io/dgviz/ |
| Demo (static) | https://fso13.github.io/dgviz/demo/ |
| Docker images | [`ghcr.io/fso13/dgviz`](https://github.com/fso13/dgviz/pkgs/container/dgviz) · [`dgviz-notifications`](https://github.com/fso13/dgviz/pkgs/container/dgviz-notifications) |

## Features

- Gradle plugin (`io.github.dgviz.scan`): sends **dependency tree + CycloneDX SBOM** to DGViz
- Server-side CVE / conflict analysis from the graph (no source upload)
- Optional GitLab/GitHub issue creation from scan findings
- CLI: `analyze`, `visualize`, `report`, `ci-check` (local project scan)
- Web admin: users, groups, projects, repositories, settings
- Async CVE sync from **NVD (CVE.org feed)**, **OSV**, **GitHub Advisories**, optional **Snyk**
- Per-source cron editable in admin UI (reloads schedule live)
- Docker Compose: app + PostgreSQL + Adminer`

## Quick start (Docker)

```bash
docker compose up --build
```

- Admin UI: http://localhost:8080 (default `admin` / `admin`)
- Adminer: http://localhost:8081 (System: PostgreSQL, server `db`, user/pass/db `dgviz`)

Or pull release images from GHCR:

```bash
docker pull ghcr.io/fso13/dgviz:latest
docker pull ghcr.io/fso13/dgviz-notifications:latest
```

Optional env:

```bash
export NVD_API_KEY=...     # higher NVD rate limit
export SNYK_TOKEN=...      # enable Snyk source in admin
docker compose up --build
```

## Documentation site (GitHub Pages)

Static site lives in [`docs/`](docs/): product overview, user/admin guides, mock demo.

Deployed automatically by [`.github/workflows/pages.yml`](.github/workflows/pages.yml) on pushes to `docs/**`.

Enable in the repo: **Settings → Pages → Source: GitHub Actions**.

## Release pipeline (Docker → GHCR)

Tag a version to build and publish images + GitHub Release (jar attached):

```bash
git tag v0.3.1
git push origin v0.3.1
```

Workflow: [`.github/workflows/release.yml`](.github/workflows/release.yml)

- `ghcr.io/<owner>/dgviz:<version>` (+ `latest` on tags)
- `ghcr.io/<owner>/dgviz-notifications:<version>` (+ `latest` on tags)

Packages are private by default for private repos — set visibility in **Packages** settings if needed.

## Requirements

- JVM **21+** (Gradle toolchain)
- Gradle **9.8+** (wrapper included)
- GitLab token with `read_api` (optional, for remote analysis)

## Build

```bash
./gradlew bootJar
java -jar build/libs/dgviz-0.3.0.jar
```

Stack: **Spring Boot 4.1.1** · Spring Framework 7 · Spring Security 7 · Jackson 3 · Jetty 12 · OkHttp 5 · Flyway 12

### CLI mode

If the first argument is a CLI command, Spring Boot is skipped:

```bash
java -jar build/libs/dgviz-0.3.0.jar analyze .
java -jar build/libs/dgviz-0.3.0.jar ci-check . --fail-on-conflict --fail-on-cvss 7
```

## Admin: configuration settings

Path: **Settings** (`/admin/settings`) — CRUD for runtime keys stored in DB:

- `sync.default-cron`
- `sync.nvd-api-key` (secret)
- `sync.snyk-api-token` (secret)
- `sync.page-size`
- `sync.max-pages-per-run`

Env vars still seed blank secrets on startup. Synced CVE clients read values from DB first.

## Gradle plugin scan (recommended)

1. Create a repository in Admin → Repositories — copy `repositoryId` and scan token.
2. Publish the plugin: `./gradlew :dgviz-gradle-plugin:publishToMavenLocal`
3. In the target project apply `id("io.github.dgviz.scan")` and set `host`, `repositoryId`, `token` (see `dgviz-gradle-plugin/README.md`).
4. Run `./gradlew dgvizScan` — writes `build/reports/dgviz/report.md` for MR comments.

API: `POST /api/v1/repositories/{id}/scan` with header `X-DGViz-Token`.

## Workspace (authenticated users)

Access is granted via **group membership** on a project (admins see everything):

- `/workspace/projects` — accessible projects
- `/workspace/repositories` — repos + last plugin scan status
- `/workspace/analysis/{id}/tree` — dependency tree
- `/workspace/analysis/{id}/issues` — conflicts / vulns for that run
- `/workspace/vulnerabilities` — vulnerability findings across accessible analyses

## Admin: CVE sync

Path: **CVE Sync** (`/admin/sync`)

| Source | Notes |
|--------|--------|
| NVD | Official CVE JSON API 2.0 (CVE.org catalogue) |
| OSV | Open Source Vulnerabilities, Maven packages |
| GHSA | GitHub Security Advisories (maven) |
| Snyk | Requires `SNYK_TOKEN`; disabled by default |

Each source has **enabled**, **cron** (`0 0 2 * * *` = 02:00 UTC daily), **API URL**, and **Run now**.

## Data model

- Users (ADMIN / USER) + Groups (many-to-many)
- Projects (optional group) → Repositories (PLUGIN target: scan token, optional VCS for issues)
- Vulnerabilities + sync configs / run history (Flyway `V1__init_schema.sql`)

## Tests

```bash
./gradlew test
```

## Security notes

- Change `DGVIZ_ADMIN_PASSWORD` in production
- Do not put GitLab/Snyk/NVD tokens into reports or git
- Prefer env vars / Compose secrets
