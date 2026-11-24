-- =====================================================================
--  Démonstration : offres de formation publiées par les enseignants.
--
--  À appliquer APRÈS 16-offres-et-demandes-inscription.sql, une seule
--  fois, sur une base de démonstration. Idempotent : relancer ne crée
--  pas de doublon et ne réécrit pas une offre déjà rédigée par un
--  enseignant sur PC1.
--
--  Pourquoi ce fichier : sans offre ni niveau publié, le catalogue de
--  l'apprenant est vide (« Aucun enseignant ne propose… »), et rien ne
--  permet d'essayer le parcours d'inscription sans créer d'abord des
--  comptes, des parcours et des niveaux à la main.
--
--  Ce fichier ne supprime ni ne modifie aucune donnée existante :
--  uniquement des INSERT ... ON CONFLICT DO NOTHING.
-- =====================================================================
BEGIN;

-- 1) Rattacher chaque enseignant de démonstration à son parcours par langage.
--    C'est cette ligne (parcours_enseignants, migration 12) qui rend un
--    enseignant visible dans le catalogue de l'apprenant.
INSERT INTO parcours_enseignants (enseignant_id, langage_id, parcours_id)
SELECT u.id, l.id, p.id
FROM (VALUES ('sofia.meziane@univ.dz', 'python')) AS v(email, langage)
JOIN utilisateurs u ON u.email = v.email AND u.role = 'ENSEIGNANT'
JOIN langages l ON l.code = v.langage
JOIN parcours p ON p.langage_id = l.id
ON CONFLICT (enseignant_id, langage_id) DO NOTHING;

-- 2) Les offres visibles par les apprenants.
INSERT INTO offres_enseignants
    (enseignant_id, langage_id, titre, presentation, auto_acceptation, ouverte)
SELECT u.id, l.id, v.titre, v.presentation, v.auto_acceptation, TRUE
FROM (VALUES
    ('sofia.meziane@univ.dz', 'python',
     'Python pas à pas — de zéro à autonome',
     'Cinq niveaux progressifs : variables et types, conditions et boucles, fonctions et modules, '
     || 'structures de données, fichiers et exceptions. Chaque niveau se termine par un test final '
     || 'corrigé personnellement. Les exercices sont corrigés automatiquement, l''aide IA est '
     || 'disponible pendant l''entraînement uniquement.',
     FALSE),
    ('sofia.meziane@univ.dz', 'java',
     'Java : les fondations solides',
     'Parcours Java pour débuter proprement : types, contrôle de flux, méthodes, collections, '
     || 'puis gestion des exceptions. Le test final reste sans aide IA et je corrige chaque copie.',
     FALSE),
    ('sofia.meziane@univ.dz', 'cpp',
     'C++ moderne : du premier programme à la STL',
     'Du premier « Hello world » aux conteneurs de la STL, avec compilation commentée et '
     || 'exercices courts. Rythme adapté à un premier contact avec un langage compilé.',
     TRUE)
) AS v(email, langage, titre, presentation, auto_acceptation)
JOIN utilisateurs u ON u.email = v.email AND u.role = 'ENSEIGNANT' AND u.actif
JOIN langages l ON l.code = v.langage
ON CONFLICT (enseignant_id, langage_id) DO NOTHING;

COMMIT;
