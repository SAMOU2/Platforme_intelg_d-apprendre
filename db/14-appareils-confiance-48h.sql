-- IHM 2026 — appareils de confiance limités à 48 heures.
--
-- Après une connexion complète (mot de passe + code e-mail), l'utilisateur peut
-- choisir de faire confiance à son poste. Le serveur ne conserve que l'empreinte
-- SHA-256 d'un jeton opaque de 256 bits. Ce jeton ne remplace jamais le mot de
-- passe : il permet seulement d'éviter un nouveau code e-mail pendant 48 heures.
-- Son expiration est absolue et n'est pas prolongée par les utilisations.

BEGIN;

CREATE TABLE IF NOT EXISTS appareils_confiance (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    utilisateur_id        BIGINT NOT NULL REFERENCES utilisateurs(id) ON DELETE CASCADE,
    empreinte_jeton       TEXT NOT NULL UNIQUE,
    libelle_poste         TEXT,
    cree_le               TIMESTAMPTZ NOT NULL DEFAULT now(),
    derniere_utilisation  TIMESTAMPTZ,
    expire_le             TIMESTAMPTZ NOT NULL,
    revoque_le            TIMESTAMPTZ,
    CONSTRAINT appareils_confiance_empreinte_valide
        CHECK (char_length(empreinte_jeton) = 64),
    CONSTRAINT appareils_confiance_expiration_valide
        CHECK (expire_le > cree_le)
);

CREATE INDEX IF NOT EXISTS idx_appareils_confiance_utilisateur_actifs
    ON appareils_confiance(utilisateur_id, expire_le)
    WHERE revoque_le IS NULL;

CREATE INDEX IF NOT EXISTS idx_appareils_confiance_expiration
    ON appareils_confiance(expire_le);

COMMIT;
