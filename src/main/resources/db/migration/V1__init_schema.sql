CREATE TABLE app_user (
    id              BIGSERIAL PRIMARY KEY,
    username        VARCHAR(100) NOT NULL UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,
    display_name    VARCHAR(255) NOT NULL,
    email           VARCHAR(255),
    role            VARCHAR(50)  NOT NULL,
    enabled         BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP  NOT NULL DEFAULT NOW()
);

CREATE TABLE app_group (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(150) NOT NULL UNIQUE,
    description VARCHAR(1000),
    created_at  TIMESTAMP  NOT NULL DEFAULT NOW()
);

CREATE TABLE app_group_member (
    group_id BIGINT NOT NULL REFERENCES app_group(id) ON DELETE CASCADE,
    user_id  BIGINT NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    PRIMARY KEY (group_id, user_id)
);

CREATE TABLE project (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(200) NOT NULL,
    description VARCHAR(2000),
    group_id    BIGINT REFERENCES app_group(id) ON DELETE SET NULL,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_project_name UNIQUE (name)
);

CREATE TABLE repository (
    id              BIGSERIAL PRIMARY KEY,
    project_id      BIGINT NOT NULL REFERENCES project(id) ON DELETE CASCADE,
    name            VARCHAR(200) NOT NULL,
    vcs_url         VARCHAR(1000),
    gitlab_path     VARCHAR(500),
    default_branch  VARCHAR(200) NOT NULL DEFAULT 'main',
    local_path      VARCHAR(1000),
    build_system    VARCHAR(50),
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_repo_project_name UNIQUE (project_id, name)
);

CREATE TABLE vulnerability (
    id               BIGSERIAL PRIMARY KEY,
    cve_id           VARCHAR(64)  NOT NULL,
    source           VARCHAR(50)  NOT NULL,
    package_ecosystem VARCHAR(50),
    package_name     VARCHAR(500),
    affected_version VARCHAR(200),
    severity         VARCHAR(50),
    cvss             DOUBLE PRECISION,
    title            VARCHAR(1000),
    description      TEXT,
    published_at     TIMESTAMP,
    modified_at      TIMESTAMP,
    raw_json         TEXT,
    synced_at        TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_vuln_source_cve_pkg UNIQUE (source, cve_id, package_name, affected_version)
);

CREATE INDEX idx_vulnerability_cve ON vulnerability(cve_id);
CREATE INDEX idx_vulnerability_pkg ON vulnerability(package_name);
CREATE INDEX idx_vulnerability_ga ON vulnerability(package_ecosystem, package_name);

CREATE TABLE sync_source_config (
    id              BIGSERIAL PRIMARY KEY,
    source_code     VARCHAR(50)  NOT NULL UNIQUE,
    display_name    VARCHAR(200) NOT NULL,
    enabled         BOOLEAN      NOT NULL DEFAULT TRUE,
    cron_expression VARCHAR(100) NOT NULL,
    api_base_url    VARCHAR(500),
    last_synced_at  TIMESTAMP,
    last_status     VARCHAR(50),
    last_message    VARCHAR(2000),
    updated_at      TIMESTAMP  NOT NULL DEFAULT NOW()
);

CREATE TABLE sync_run (
    id              BIGSERIAL PRIMARY KEY,
    source_code     VARCHAR(50)  NOT NULL,
    started_at      TIMESTAMP  NOT NULL,
    finished_at     TIMESTAMP,
    status          VARCHAR(50)  NOT NULL,
    records_upserted INTEGER     NOT NULL DEFAULT 0,
    message         VARCHAR(2000)
);

INSERT INTO sync_source_config (source_code, display_name, enabled, cron_expression, api_base_url)
VALUES
 ('NVD',  'NVD / CVE.org feed', TRUE,  '0 0 2 * * *', 'https://services.nvd.nist.gov/rest/json/cves/2.0'),
 ('OSV',  'OSV (Maven)',        TRUE,  '0 30 2 * * *', 'https://api.osv.dev/v1'),
 ('GHSA', 'GitHub Advisories',  TRUE,  '0 0 3 * * *', 'https://api.github.com/advisories'),
 ('SNYK', 'Snyk (optional token)', FALSE, '0 30 3 * * *', 'https://api.snyk.io');
