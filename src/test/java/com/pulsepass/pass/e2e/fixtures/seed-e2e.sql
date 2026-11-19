CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- 1) Usuarios (dominio reservado example.test)
INSERT INTO users (username, email, active) VALUES
                                                ('e2e_admin',    'e2e_admin@example.test',    TRUE),
                                                ('e2e_staff',    'e2e_staff@example.test',    TRUE),
                                                ('e2e_inactive', 'e2e_inactive@example.test', FALSE);

-- 2) Perfiles
INSERT INTO user_profiles (first_name, last_name, birth_date, user_id)
SELECT 'Admin', 'E2E', DATE '1990-01-01', id FROM users WHERE username = 'e2e_admin';
INSERT INTO user_profiles (first_name, last_name, birth_date, user_id)
SELECT 'Staff', 'E2E', DATE '1990-01-01', id FROM users WHERE username = 'e2e_staff';
INSERT INTO user_profiles (first_name, last_name, birth_date, user_id)
SELECT 'Inactivo', 'E2E', DATE '1990-01-01', id FROM users WHERE username = 'e2e_inactive';

-- 3) Credenciales (hash BCrypt generado por PostgreSQL)
INSERT INTO user_credentials (user_id, password_hash, password_changed_at)
SELECT id, crypt('ADMIN_PASSWORD_AQUI', gen_salt('bf', 10)), now()
FROM users WHERE username = 'e2e_admin';
INSERT INTO user_credentials (user_id, password_hash, password_changed_at)
SELECT id, crypt('STAFF_PASSWORD_AQUI', gen_salt('bf', 10)), now()
FROM users WHERE username = 'e2e_staff';
INSERT INTO user_credentials (user_id, password_hash, password_changed_at)
SELECT id, crypt('INACTIVE_PASSWORD_AQUI', gen_salt('bf', 10)), now()
FROM users WHERE username = 'e2e_inactive';

-- 4) Roles
INSERT INTO user_roles (user_id, role)
SELECT id, 'ADMIN' FROM users WHERE username = 'e2e_admin';
INSERT INTO user_roles (user_id, role)
SELECT id, 'STAFF' FROM users WHERE username = 'e2e_staff';
INSERT INTO user_roles (user_id, role)
SELECT id, 'USER' FROM users WHERE username = 'e2e_inactive';

INSERT INTO venues (code, name, city, address, capacity, active) VALUES
    ('VEN-E2E-01', 'Recinto E2E', 'Santa Marta', 'Calle de Pruebas 1', 100, TRUE);

INSERT INTO artists (stage_name, country, genre, active) VALUES
    ('Artista E2E', 'Colombia', 'Pop', TRUE);