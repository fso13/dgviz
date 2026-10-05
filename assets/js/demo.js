(() => {
  const body = document.getElementById("demo-body");
  const titleEl = document.getElementById("demo-title");
  const subtitleEl = document.getElementById("demo-subtitle");
  const tabs = Array.from(document.querySelectorAll(".demo-tab"));
  let data = null;
  let active = "overview";
  let severityFilter = "";

  async function load() {
    const res = await fetch("../assets/data/demo-repo.json");
    data = await res.json();
    titleEl.textContent = data.repository.name;
    subtitleEl.textContent = `${data.repository.project} · ${data.repository.buildSystem} · ${data.repository.branch}`;
    render();
  }

  function esc(s) {
    return String(s ?? "")
      .replaceAll("&", "&amp;")
      .replaceAll("<", "&lt;")
      .replaceAll(">", "&gt;")
      .replaceAll('"', "&quot;");
  }

  function renderTree(nodes) {
    if (!nodes?.length) return "";
    return `<ul>${nodes.map((n) => `
      <li>
        <span class="pkg">${esc(n.name)}</span>
        <span class="ver">:${esc(n.version)}</span>
        ${n.flag === "vuln" ? '<span class="flag">CVE</span>' : ""}
        ${n.flag === "conflict" ? '<span class="flag">CONFLICT</span>' : ""}
        ${renderTree(n.children)}
      </li>`).join("")}</ul>`;
  }

  function renderOverview() {
    const s = data.scan;
    return `
      <p class="demo-meta">Последний скан · ${esc(s.analyzedAt)} · status <strong>${esc(s.status)}</strong></p>
      <div class="stats">
        <div class="stat"><strong>${s.nodeCount}</strong><span>nodes</span></div>
        <div class="stat"><strong>${s.vulnerabilityCount}</strong><span>vulnerabilities</span></div>
        <div class="stat"><strong>${s.conflictCount}</strong><span>conflicts</span></div>
      </div>
      <p class="muted">Выберите вкладку «Дерево» или «Уязвимости», чтобы посмотреть детали мок-скана.</p>`;
  }

  function renderTreeTab() {
    return `<p class="demo-meta">Dependency tree (mock)</p><div class="tree">${renderTree(data.tree)}</div>`;
  }

  function renderVulns() {
    const items = data.vulnerabilities.filter((v) =>
      !severityFilter || v.severity === severityFilter
    );
    const severities = [...new Set(data.vulnerabilities.map((v) => v.severity))];
    return `
      <div style="display:flex;flex-wrap:wrap;gap:0.5rem;margin-bottom:1rem;align-items:center">
        <span class="muted">Severity:</span>
        <button type="button" class="demo-tab ${severityFilter === "" ? "is-active" : ""}" data-sev="">All</button>
        ${severities.map((s) => `
          <button type="button" class="demo-tab ${severityFilter === s ? "is-active" : ""}" data-sev="${esc(s)}">${esc(s)}</button>
        `).join("")}
      </div>
      <div class="table-wrap">
        <table style="width:100%;border-collapse:collapse;font-size:0.9rem">
          <thead>
            <tr>
              <th style="text-align:left;padding:0.5rem;border-bottom:1px solid var(--line)">Severity</th>
              <th style="text-align:left;padding:0.5rem;border-bottom:1px solid var(--line)">Package</th>
              <th style="text-align:left;padding:0.5rem;border-bottom:1px solid var(--line)">CVE</th>
              <th style="text-align:left;padding:0.5rem;border-bottom:1px solid var(--line)">CVSS</th>
              <th style="text-align:left;padding:0.5rem;border-bottom:1px solid var(--line)">Title</th>
            </tr>
          </thead>
          <tbody>
            ${items.map((v) => `
              <tr>
                <td style="padding:0.55rem 0.5rem;border-bottom:1px solid var(--line)">
                  <span class="sev sev--${esc(v.severity.toLowerCase())}">${esc(v.severity)}</span>
                </td>
                <td class="mono" style="padding:0.55rem 0.5rem;border-bottom:1px solid var(--line)">
                  ${esc(v.packageName)}@${esc(v.packageVersion)}
                  <div class="muted" style="font-size:0.8rem">fix → ${esc(v.fixedVersion)}</div>
                </td>
                <td class="mono" style="padding:0.55rem 0.5rem;border-bottom:1px solid var(--line)">${esc(v.cve)}</td>
                <td style="padding:0.55rem 0.5rem;border-bottom:1px solid var(--line)">${esc(v.cvss)}</td>
                <td style="padding:0.55rem 0.5rem;border-bottom:1px solid var(--line)">${esc(v.title)}</td>
              </tr>`).join("")}
          </tbody>
        </table>
      </div>`;
  }

  function renderConflicts() {
    return `
      <p class="demo-meta">Version conflicts (mock)</p>
      <ul>
        ${data.conflicts.map((c) => `
          <li style="margin:0.5rem 0">
            <code>${esc(c.artifact)}</code>
            <span class="muted"> → ${esc(c.versions.join(" / "))}</span>
          </li>`).join("")}
      </ul>`;
  }

  function render() {
    if (!data) return;
    const map = {
      overview: renderOverview,
      tree: renderTreeTab,
      vulns: renderVulns,
      conflicts: renderConflicts
    };
    body.innerHTML = (map[active] || renderOverview)();
    body.querySelectorAll("[data-sev]").forEach((btn) => {
      btn.addEventListener("click", () => {
        severityFilter = btn.getAttribute("data-sev") || "";
        render();
      });
    });
  }

  tabs.forEach((tab) => {
    tab.addEventListener("click", () => {
      active = tab.dataset.tab;
      tabs.forEach((t) => t.classList.toggle("is-active", t === tab));
      if (active !== "vulns") severityFilter = "";
      render();
    });
  });

  load().catch((err) => {
    body.innerHTML = `<p class="muted">Не удалось загрузить демо: ${esc(err.message)}</p>`;
  });
})();
