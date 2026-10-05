(() => {
  const shell = document.getElementById("app-shell");
  const sidebar = document.getElementById("app-sidebar");
  const mobileToggle = document.getElementById("sidebar-toggle");
  const collapseToggle = document.getElementById("sidebar-collapse");
  const backdrop = document.getElementById("sidebar-backdrop");
  const STORAGE_KEY = "sidebar-collapsed";

  function setMobileOpen(open) {
    if (!sidebar) return;
    sidebar.classList.toggle("is-open", open);
    document.body.classList.toggle("sidebar-open", open);
    if (backdrop) {
      backdrop.hidden = !open;
    }
    if (mobileToggle) {
      mobileToggle.setAttribute("aria-expanded", open ? "true" : "false");
      mobileToggle.setAttribute("aria-label", open ? "Закрыть меню" : "Открыть меню");
    }
  }

  function setCollapsed(collapsed) {
    if (!shell || shell.classList.contains("app-shell--guest")) return;
    shell.classList.toggle("is-sidebar-collapsed", collapsed);
    localStorage.setItem(STORAGE_KEY, collapsed ? "1" : "0");
    if (collapseToggle) {
      collapseToggle.setAttribute("aria-expanded", collapsed ? "false" : "true");
      collapseToggle.setAttribute(
        "aria-label",
        collapsed ? "Развернуть навигацию" : "Свернуть навигацию"
      );
      collapseToggle.title = collapsed ? "Развернуть навигацию" : "Свернуть навигацию";
    }
  }

  if (collapseToggle && shell && !shell.classList.contains("app-shell--guest")) {
    const saved = localStorage.getItem(STORAGE_KEY) === "1";
    setCollapsed(saved);
    collapseToggle.addEventListener("click", () => {
      setCollapsed(!shell.classList.contains("is-sidebar-collapsed"));
    });
  }

  if (mobileToggle && sidebar) {
    mobileToggle.addEventListener("click", () => {
      setMobileOpen(!sidebar.classList.contains("is-open"));
    });
  }

  if (backdrop) {
    backdrop.addEventListener("click", () => setMobileOpen(false));
  }

  window.addEventListener("keydown", (event) => {
    if (event.key === "Escape") {
      setMobileOpen(false);
    }
  });
})();
