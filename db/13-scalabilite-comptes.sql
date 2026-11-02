-- Scalabilité de l'administration des comptes : index pour la file filtrée/paginée.
-- Migration non destructive : aucune table ni donnée existante n'est supprimée ni réécrite.
-- À appliquer après 12 sur une base sauvegardée, service HTTPS arrêté.

BEGIN;

-- File d'attente des inscriptions : statut puis ordre d'arrivée (FIFO).
CREATE INDEX IF NOT EXISTS ix_utilisateurs_admin_statut_date
    ON utilisateurs (statut_inscription, cree_le, id);

-- Filtres fréquents de l'administrateur : rôle + statut + date.
CREATE INDEX IF NOT EXISTS ix_utilisateurs_admin_role_statut_date
    ON utilisateurs (role, statut_inscription, cree_le, id);

-- Filtre par domaine e-mail (ex. univ.dz) sans scan complet de la table.
CREATE INDEX IF NOT EXISTS ix_utilisateurs_admin_domaine
    ON utilisateurs ((lower(split_part(email, '@', 2))), statut_inscription, cree_le, id);

COMMIT;
