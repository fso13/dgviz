package io.github.dgviz.sync.client;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import io.github.dgviz.vulnerability.Vulnerability;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Live OSV Maven lookups ({@code /v1/querybatch} + {@code /v1/vulns/{id}}).
 */
@Component
public class OsvMavenAdvisoryClient {

    private static final Logger log = LoggerFactory.getLogger(OsvMavenAdvisoryClient.class);
    private static final MediaType JSON = MediaType.get("application/json");
    private static final String DEFAULT_BASE = "https://api.osv.dev/v1";
    private static final int BATCH_SIZE = 80;

    private final OkHttpClient http = new OkHttpClient.Builder()
            .callTimeout(Duration.ofSeconds(60))
            .connectTimeout(Duration.ofSeconds(20))
            .build();
    private final ObjectMapper mapper;

    public OsvMavenAdvisoryClient(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public record MavenCoordinate(String groupArtifact, String version) {
        public String key() {
            return groupArtifact + "@" + (version == null ? "" : version);
        }
    }

    /**
     * @return vulnerabilities keyed by {@code group:artifact@version}
     */
    public Map<String, List<Vulnerability>> queryAffecting(List<MavenCoordinate> coordinates) {
        Map<String, List<Vulnerability>> byGav = new LinkedHashMap<>();
        if (coordinates == null || coordinates.isEmpty()) {
            return byGav;
        }
        List<MavenCoordinate> unique = dedupe(coordinates);
        Map<String, Set<String>> gavToVulnIds = new HashMap<>();
        Set<String> allVulnIds = new LinkedHashSet<>();

        for (int i = 0; i < unique.size(); i += BATCH_SIZE) {
            List<MavenCoordinate> batch = unique.subList(i, Math.min(i + BATCH_SIZE, unique.size()));
            queryBatchIds(batch, gavToVulnIds, allVulnIds);
        }

        Map<String, Vulnerability> details = fetchDetails(allVulnIds);
        for (Map.Entry<String, Set<String>> e : gavToVulnIds.entrySet()) {
            String gavKey = e.getKey();
            int at = gavKey.lastIndexOf('@');
            String ga = at > 0 ? gavKey.substring(0, at) : gavKey;
            String version = at > 0 ? gavKey.substring(at + 1) : "";
            List<Vulnerability> list = new ArrayList<>();
            for (String id : e.getValue()) {
                Vulnerability template = details.get(id);
                if (template != null) {
                    list.add(copyForPackage(template, ga, version));
                }
            }
            if (!list.isEmpty()) {
                byGav.put(gavKey, list);
            }
        }
        log.info("OSV live query: {} GAV → {} with vulns ({} unique advisories)",
                unique.size(), byGav.size(), allVulnIds.size());
        return byGav;
    }

    public List<Vulnerability> queryPackage(String ga) {
        ObjectNode body = mapper.createObjectNode();
        ObjectNode pkg = body.putObject("package");
        pkg.put("ecosystem", "Maven");
        pkg.put("name", ga);
        Request request = new Request.Builder()
                .url(DEFAULT_BASE + "/query")
                .post(RequestBody.create(body.toString(), JSON))
                .header("Accept", "application/json")
                .build();
        try (Response response = http.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                log.warn("OSV /query failed for {} HTTP {}", ga, response.code());
                return List.of();
            }
            JsonNode root = mapper.readTree(response.body().byteStream());
            JsonNode vulns = root.path("vulns");
            if (!vulns.isArray()) {
                return List.of();
            }
            List<Vulnerability> result = new ArrayList<>();
            for (JsonNode vuln : vulns) {
                result.add(mapVuln(vuln, ga, ""));
            }
            return result;
        } catch (Exception ex) {
            log.warn("OSV /query error for {}: {}", ga, ex.getMessage());
            return List.of();
        }
    }

    private List<MavenCoordinate> dedupe(List<MavenCoordinate> coordinates) {
        Map<String, MavenCoordinate> map = new LinkedHashMap<>();
        for (MavenCoordinate c : coordinates) {
            if (c.groupArtifact() == null || c.groupArtifact().isBlank()) {
                continue;
            }
            map.putIfAbsent(c.key(), c);
        }
        return new ArrayList<>(map.values());
    }

    private void queryBatchIds(
            List<MavenCoordinate> batch,
            Map<String, Set<String>> gavToVulnIds,
            Set<String> allVulnIds
    ) {
        ObjectNode root = mapper.createObjectNode();
        ArrayNode queries = root.putArray("queries");
        List<String> order = new ArrayList<>();
        for (MavenCoordinate c : batch) {
            ObjectNode q = queries.addObject();
            ObjectNode pkg = q.putObject("package");
            pkg.put("ecosystem", "Maven");
            pkg.put("name", c.groupArtifact());
            if (c.version() != null && !c.version().isBlank()) {
                q.put("version", c.version());
            }
            order.add(c.key());
        }
        Request request = new Request.Builder()
                .url(DEFAULT_BASE + "/querybatch")
                .post(RequestBody.create(root.toString(), JSON))
                .header("Accept", "application/json")
                .build();
        try (Response response = http.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                log.warn("OSV /querybatch failed HTTP {}", response.code());
                return;
            }
            JsonNode results = mapper.readTree(response.body().byteStream()).path("results");
            if (!results.isArray()) {
                return;
            }
            for (int i = 0; i < results.size() && i < order.size(); i++) {
                String gavKey = order.get(i);
                JsonNode vulns = results.get(i).path("vulns");
                if (!vulns.isArray()) {
                    continue;
                }
                Set<String> ids = gavToVulnIds.computeIfAbsent(gavKey, k -> new LinkedHashSet<>());
                for (JsonNode v : vulns) {
                    String id = v.path("id").asText("");
                    if (!id.isBlank()) {
                        ids.add(id);
                        allVulnIds.add(id);
                    }
                }
            }
        } catch (Exception ex) {
            log.warn("OSV /querybatch error: {}", ex.getMessage());
        }
    }

    private Map<String, Vulnerability> fetchDetails(Collection<String> ids) {
        Map<String, Vulnerability> map = new HashMap<>();
        for (String id : ids) {
            Request request = new Request.Builder()
                    .url(DEFAULT_BASE + "/vulns/" + id)
                    .header("Accept", "application/json")
                    .build();
            try (Response response = http.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) {
                    continue;
                }
                JsonNode vuln = mapper.readTree(response.body().byteStream());
                map.put(id, mapVuln(vuln, "", ""));
            } catch (Exception ex) {
                log.debug("OSV /vulns/{} failed: {}", id, ex.getMessage());
            }
        }
        return map;
    }

    private Vulnerability copyForPackage(Vulnerability template, String ga, String version) {
        Vulnerability v = new Vulnerability();
        v.setSource("OSV");
        v.setCveId(template.getCveId());
        v.setTitle(template.getTitle());
        v.setDescription(template.getDescription());
        v.setSeverity(template.getSeverity());
        v.setCvss(template.getCvss());
        v.setPackageEcosystem("Maven");
        v.setPackageName(ga);
        v.setAffectedVersion(version == null ? "" : version);
        v.setFixedVersion(template.getFixedVersion());
        v.setPublishedAt(template.getPublishedAt());
        v.setModifiedAt(template.getModifiedAt());
        v.setRawJson(template.getRawJson());
        v.setSyncedAt(Instant.now());
        return v;
    }

    private Vulnerability mapVuln(JsonNode vuln, String packageName, String version) {
        Vulnerability v = new Vulnerability();
        v.setSource("OSV");
        String id = vuln.path("id").asText("");
        String cve = id;
        JsonNode aliases = vuln.path("aliases");
        if (aliases.isArray()) {
            for (JsonNode a : aliases) {
                String alias = a.asText("");
                if (alias.startsWith("CVE-")) {
                    cve = alias;
                    break;
                }
            }
        }
        v.setCveId(cve);
        v.setTitle(vuln.path("summary").asText(id));
        v.setDescription(vuln.path("details").asText(""));
        v.setPackageEcosystem("Maven");
        v.setPackageName(packageName);
        v.setAffectedVersion(version == null ? "" : version);
        v.setSeverity(extractSeverity(vuln));
        v.setCvss(extractCvss(vuln));
        v.setFixedVersion(extractFixedVersions(vuln));
        v.setPublishedAt(parseInstant(vuln.path("published").asText(null)));
        v.setModifiedAt(parseInstant(vuln.path("modified").asText(null)));
        v.setRawJson(vuln.toString());
        v.setSyncedAt(Instant.now());
        return v;
    }

    /** Collects {@code fixed} events from OSV ranges (comma-separated). */
    static String extractFixedVersions(JsonNode vuln) {
        LinkedHashSet<String> fixed = new LinkedHashSet<>();
        JsonNode affected = vuln.path("affected");
        if (affected.isArray()) {
            for (JsonNode a : affected) {
                JsonNode ranges = a.path("ranges");
                if (!ranges.isArray()) {
                    continue;
                }
                for (JsonNode range : ranges) {
                    JsonNode events = range.path("events");
                    if (!events.isArray()) {
                        continue;
                    }
                    for (JsonNode event : events) {
                        String f = event.path("fixed").asText("");
                        if (!f.isBlank()) {
                            fixed.add(f);
                        }
                    }
                }
            }
        }
        return String.join(", ", fixed);
    }

    private String extractSeverity(JsonNode vuln) {
        JsonNode db = vuln.path("database_specific");
        if (db.has("severity")) {
            return db.path("severity").asText("UNKNOWN");
        }
        Double score = extractCvss(vuln);
        if (score != null) {
            if (score >= 9.0) {
                return "CRITICAL";
            }
            if (score >= 7.0) {
                return "HIGH";
            }
            if (score >= 4.0) {
                return "MEDIUM";
            }
            return "LOW";
        }
        return "UNKNOWN";
    }

    private Double extractCvss(JsonNode vuln) {
        JsonNode severity = vuln.path("severity");
        if (severity.isArray()) {
            for (JsonNode s : severity) {
                String score = s.path("score").asText("");
                if (score.matches("^[0-9]+(\\.[0-9]+)?$")) {
                    return Double.parseDouble(score);
                }
            }
        }
        JsonNode db = vuln.path("database_specific");
        if (db.has("cvss") && db.path("cvss").has("score")) {
            return db.path("cvss").path("score").asDouble();
        }
        return null;
    }

    private Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (Exception ex) {
            return null;
        }
    }
}
