(() => {
  const openModal = (id) => {
    const modal = document.getElementById(id);
    if (!modal) return;
    modal.hidden = false;
    document.body.classList.add("modal-open");
    const focusable = modal.querySelector("input:not([disabled]), select, button.btn:not([data-close-modal])");
    focusable?.focus();
  };

  const closeModals = () => {
    document.querySelectorAll(".ui-modal:not([hidden])").forEach((m) => {
      m.hidden = true;
    });
    document.body.classList.remove("modal-open");
  };

  document.addEventListener("click", (event) => {
    const openBtn = event.target.closest("[data-open-modal]");
    if (openBtn) {
      const modalId = openBtn.getAttribute("data-open-modal");
      if (modalId === "edit-user-modal") {
        const form = document.getElementById("edit-user-form");
        const userId = openBtn.getAttribute("data-user-id");
        form.action = `/admin/users/${userId}`;
        document.getElementById("edit-username").value = openBtn.getAttribute("data-username") || "";
        document.getElementById("edit-display-name").value = openBtn.getAttribute("data-display-name") || "";
        document.getElementById("edit-email").value = openBtn.getAttribute("data-email") || "";
        document.getElementById("edit-role").value = openBtn.getAttribute("data-role") || "USER";
        document.getElementById("edit-enabled").checked = openBtn.getAttribute("data-enabled") === "true";
        document.getElementById("edit-can-manage-projects").checked =
            openBtn.getAttribute("data-can-manage-projects") === "true";
        document.getElementById("edit-can-manage-repositories").checked =
            openBtn.getAttribute("data-can-manage-repositories") === "true";
        document.getElementById("edit-can-manage-groups").checked =
            openBtn.getAttribute("data-can-manage-groups") === "true";
        document.getElementById("edit-password").value = "";
      }
      openModal(modalId);
      return;
    }
    if (event.target.closest("[data-close-modal]")) {
      closeModals();
    }
  });

  document.addEventListener("keydown", (event) => {
    if (event.key === "Escape") {
      closeModals();
    }
  });
})();
