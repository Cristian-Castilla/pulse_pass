-- Replace V4's surrogate identity without rebuilding rows or cascading FK drops.
ALTER TABLE user_roles
    DROP CONSTRAINT user_roles_pkey,
    ADD CONSTRAINT pk_user_roles PRIMARY KEY (user_id, role);

ALTER TABLE user_roles
    DROP CONSTRAINT uk_user_role,
    DROP COLUMN id;
