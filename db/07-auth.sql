-- Authentification : adresses insensibles à la casse et migration des secrets existants vers
-- bcrypt (pgcrypto). Le script est idempotent et convient aux bases déjà initialisées.
BEGIN;

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM utilisateurs
        GROUP BY lower(email)
        HAVING count(*) > 1
    ) THEN
        RAISE EXCEPTION 'La migration d''authentification a détecté des e-mails dupliqués sans distinction de casse.'
            USING HINT = 'Corriger les comptes concernés, puis relancer db/07-auth.sql.';
    END IF;
END
$$;

UPDATE utilisateurs
SET mot_de_passe = crypt(mot_de_passe, gen_salt('bf', 12))
WHERE mot_de_passe !~ '^\$2[aby]\$[0-9]{2}\$[./A-Za-z0-9]{53}$';

CREATE UNIQUE INDEX IF NOT EXISTS uq_utilisateurs_email_insensible
    ON utilisateurs (lower(email));

COMMIT;
