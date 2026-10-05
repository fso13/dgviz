package io.github.dgviz.security;

/**
 * Fine-grained authorities emitted for AppUser capability flags.
 * ADMIN role short-circuits permission checks in AccessControlService.
 */
public final class PermissionAuthorities {

    public static final String PROJECT_MANAGE = "PERM_PROJECT_MANAGE";
    public static final String REPO_MANAGE = "PERM_REPO_MANAGE";
    public static final String GROUP_MANAGE = "PERM_GROUP_MANAGE";

    private PermissionAuthorities() {
    }
}
