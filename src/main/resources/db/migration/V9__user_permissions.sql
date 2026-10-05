ALTER TABLE app_user
    ADD COLUMN can_manage_projects BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN can_manage_repositories BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN can_manage_groups BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN app_user.can_manage_projects IS 'Create/edit/delete projects (scoped to accessible groups for non-admins)';
COMMENT ON COLUMN app_user.can_manage_repositories IS 'Create/edit/delete repositories only in projects the user can access';
COMMENT ON COLUMN app_user.can_manage_groups IS 'Manage membership of groups the user already belongs to';
