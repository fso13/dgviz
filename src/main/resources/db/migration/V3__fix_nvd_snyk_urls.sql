-- Ensure NVD points at the official CVE API 2.0 endpoint (fixes bad edits / stale URLs)
UPDATE sync_source_config
SET api_base_url = 'https://services.nvd.nist.gov/rest/json/cves/2.0',
    updated_at = NOW()
WHERE source_code = 'NVD'
  AND (api_base_url IS NULL
    OR api_base_url = ''
    OR api_base_url NOT LIKE '%nvd.nist.gov/rest/json/cves/2.0%');

UPDATE sync_source_config
SET api_base_url = 'https://api.snyk.io',
    updated_at = NOW()
WHERE source_code = 'SNYK'
  AND (api_base_url IS NULL OR api_base_url = '' OR api_base_url LIKE '%/rest/vulnerability%');
