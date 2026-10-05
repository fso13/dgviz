(() => {
  const tree = document.getElementById("dep-tree");
  if (!tree) {
    return;
  }

  const setCollapsed = (node, collapsed) => {
    node.classList.toggle("is-collapsed", collapsed);
    const btn = node.querySelector(":scope > .dep-tree__row > .dep-tree__toggle:not(.dep-tree__toggle--leaf)");
    if (btn) {
      btn.setAttribute("aria-expanded", collapsed ? "false" : "true");
    }
  };

  tree.addEventListener("click", (event) => {
    const btn = event.target.closest(".dep-tree__toggle:not(.dep-tree__toggle--leaf)");
    if (!btn || !tree.contains(btn)) {
      return;
    }
    const node = btn.closest(".dep-tree__node");
    if (!node) {
      return;
    }
    setCollapsed(node, !node.classList.contains("is-collapsed"));
  });

  document.querySelectorAll("[data-tree-action]").forEach((control) => {
    control.addEventListener("click", () => {
      const collapse = control.getAttribute("data-tree-action") === "collapse";
      tree.querySelectorAll(".dep-tree__node.has-children").forEach((node) => {
        setCollapsed(node, collapse);
      });
    });
  });
})();
