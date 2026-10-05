-- Scan API token for Gradle plugin; issue-creation defaults
ALTER TABLE repository ADD COLUMN scan_token VARCHAR(64);
ALTER TABLE repository ADD COLUMN create_issues_default BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE repository SET source_type = 'PLUGIN' WHERE source_type = 'LOCAL';

CREATE UNIQUE INDEX uq_repository_scan_token ON repository(scan_token);
