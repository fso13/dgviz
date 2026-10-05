CREATE TABLE app_setting (
    id           BIGSERIAL PRIMARY KEY,
    setting_key  VARCHAR(200)  NOT NULL UNIQUE,
    setting_value TEXT,
    description  VARCHAR(1000),
    secret       BOOLEAN       NOT NULL DEFAULT FALSE,
    updated_at   TIMESTAMP     NOT NULL DEFAULT NOW()
);

INSERT INTO app_setting (setting_key, setting_value, description, secret) VALUES
 ('sync.default-cron',     '0 0 2 * * *', 'Default cron for new sync sources', FALSE),
 ('sync.nvd-api-key',      '',            'NVD API key (optional, raises rate limit)', TRUE),
 ('sync.snyk-api-token',   '',            'Snyk API token (required to enable Snyk source)', TRUE),
 ('sync.page-size',        '2000',        'Page size for vulnerability API fetches', FALSE),
 ('sync.max-pages-per-run','5',           'Max pages fetched per sync run', FALSE);

CREATE TABLE analysis_run (
    id              BIGSERIAL PRIMARY KEY,
    repository_id   BIGINT NOT NULL REFERENCES repository(id) ON DELETE CASCADE,
    analyzed_at     TIMESTAMP NOT NULL DEFAULT NOW(),
    project_name    VARCHAR(500),
    node_count      INTEGER NOT NULL DEFAULT 0,
    issue_count     INTEGER NOT NULL DEFAULT 0,
    conflict_count  INTEGER NOT NULL DEFAULT 0,
    vulnerability_count INTEGER NOT NULL DEFAULT 0,
    status          VARCHAR(50) NOT NULL,
    message         VARCHAR(2000),
    graph_json      TEXT,
    issues_json     TEXT
);

CREATE INDEX idx_analysis_run_repo ON analysis_run(repository_id, analyzed_at DESC);

CREATE TABLE analysis_dependency (
    id              BIGSERIAL PRIMARY KEY,
    analysis_run_id BIGINT NOT NULL REFERENCES analysis_run(id) ON DELETE CASCADE,
    node_id         VARCHAR(500) NOT NULL,
    parent_node_id  VARCHAR(500),
    group_id        VARCHAR(255),
    artifact_id     VARCHAR(255),
    version         VARCHAR(255),
    scope           VARCHAR(50),
    origin          VARCHAR(50),
    module_path     VARCHAR(500)
);

CREATE INDEX idx_analysis_dep_run ON analysis_dependency(analysis_run_id);

CREATE TABLE analysis_issue (
    id              BIGSERIAL PRIMARY KEY,
    analysis_run_id BIGINT NOT NULL REFERENCES analysis_run(id) ON DELETE CASCADE,
    issue_type      VARCHAR(50) NOT NULL,
    severity        VARCHAR(50) NOT NULL,
    title           VARCHAR(1000) NOT NULL,
    description     TEXT,
    recommendation  TEXT,
    cve             VARCHAR(64),
    cvss            DOUBLE PRECISION,
    artifact_keys   TEXT
);

CREATE INDEX idx_analysis_issue_run ON analysis_issue(analysis_run_id);
CREATE INDEX idx_analysis_issue_cve ON analysis_issue(cve);
