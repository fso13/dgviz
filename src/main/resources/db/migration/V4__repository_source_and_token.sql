-- Repository source metadata for local upload / GitLab / GitHub
ALTER TABLE repository ADD COLUMN source_type VARCHAR(20) NOT NULL DEFAULT 'LOCAL';
ALTER TABLE repository ADD COLUMN access_token VARCHAR(2000);
ALTER TABLE repository ADD COLUMN remote_host VARCHAR(500);

UPDATE repository
SET source_type = 'GITLAB'
WHERE (gitlab_path IS NOT NULL AND TRIM(gitlab_path) <> '')
   OR LOWER(COALESCE(vcs_url, '')) LIKE '%gitlab%';

UPDATE repository
SET source_type = 'GITHUB'
WHERE LOWER(COALESCE(vcs_url, '')) LIKE '%github.com%';

CREATE INDEX idx_vulnerability_source ON vulnerability(source);
CREATE INDEX idx_vulnerability_severity ON vulnerability(severity);
