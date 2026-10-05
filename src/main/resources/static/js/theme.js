(() => {
  const themeToggle = document.getElementById("theme-toggle");
  const themeColorMeta = document.getElementById("theme-color-meta");

  function systemTheme() {
    return window.matchMedia("(prefers-color-scheme: light)").matches ? "light" : "dark";
  }

  function getTheme() {
    return document.documentElement.getAttribute("data-theme") === "light" ? "light" : "dark";
  }

  function applyTheme(theme, { persist = true } = {}) {
    const next = theme === "light" ? "light" : "dark";
    document.documentElement.setAttribute("data-theme", next);
    if (persist) {
      localStorage.setItem("theme", next);
    }
    if (themeColorMeta) {
      themeColorMeta.setAttribute("content", next === "light" ? "#f4f5fa" : "#282a36");
    }
    if (themeToggle) {
      themeToggle.setAttribute(
        "aria-label",
        next === "light" ? "Включить тёмную тему" : "Включить светлую тему"
      );
      themeToggle.title = next === "light" ? "Тёмная тема" : "Светлая тема";
    }
  }

  const savedTheme = localStorage.getItem("theme");
  applyTheme(savedTheme === "light" || savedTheme === "dark" ? savedTheme : systemTheme(), {
    persist: false,
  });

  if (themeToggle) {
    themeToggle.addEventListener("click", () => {
      applyTheme(getTheme() === "light" ? "dark" : "light");
    });
  }

  window.matchMedia("(prefers-color-scheme: light)").addEventListener("change", (event) => {
    if (!localStorage.getItem("theme")) {
      applyTheme(event.matches ? "light" : "dark", { persist: false });
    }
  });
})();
