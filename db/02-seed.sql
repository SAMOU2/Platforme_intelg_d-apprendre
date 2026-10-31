-- =====================================================================
--  Jeu de données de démonstration — mêmes valeurs que la couche mock
--  du client (MockBackend), pour que l'écran et la base racontent la même histoire.
-- =====================================================================

BEGIN;

INSERT INTO langages (code, libelle, version, image_execution) VALUES
    ('python', 'Python', '3.12',      'python:3.12-slim'),
    ('java',   'Java',   'OpenJDK 21 LTS', 'eclipse-temurin:21-jre'),
    ('cpp',    'C++',    'C++20 (g++ 13)', 'gcc:13');

INSERT INTO parcours (langage_id, libelle)
SELECT id, 'Parcours ' || libelle FROM langages;

-- --------------------------------------------------------------- comptes
-- Mot de passe de démonstration : "demo" (haché par pgcrypto ; développement uniquement).
INSERT INTO utilisateurs (nom, email, mot_de_passe, role, actif) VALUES
    ('Nadia Cherif',  'nadia.cherif@univ.dz',  crypt('demo', gen_salt('bf', 12)), 'ADMINISTRATEUR', TRUE),
    ('Sofia Meziane', 'sofia.meziane@univ.dz', crypt('demo', gen_salt('bf', 12)), 'ENSEIGNANT',     TRUE),
    ('Amina Bouzid',  'amina.bouzid@univ.dz',  crypt('demo', gen_salt('bf', 12)), 'APPRENANT',      TRUE),
    ('Karim Haddad',  'karim.haddad@univ.dz',  crypt('demo', gen_salt('bf', 12)), 'APPRENANT',      TRUE),
    ('Omar Belkacem', 'omar.belkacem@univ.dz', crypt('demo', gen_salt('bf', 12)), 'APPRENANT',      TRUE),
    ('Lina Saadi',    'lina.saadi@univ.dz',    crypt('demo', gen_salt('bf', 12)), 'APPRENANT',      TRUE),
    ('Yacine Rami',   'yacine.rami@univ.dz',   crypt('demo', gen_salt('bf', 12)), 'APPRENANT',      FALSE);

-- ---------------------------------------------------------- niveaux Python
INSERT INTO niveaux (parcours_id, rang, titre, objectifs, seuil, tentatives_max, statut, cree_par)
SELECT p.id, v.rang, v.titre, v.objectifs, v.seuil, v.tentatives, 'PUBLIE',
       (SELECT id FROM utilisateurs WHERE email = 'sofia.meziane@univ.dz')
FROM parcours p
JOIN langages l ON l.id = p.langage_id AND l.code = 'python'
JOIN (VALUES
    (1, 'Variables et types',        'Déclarer, typer, convertir',            10, 3),
    (2, 'Conditions et boucles',     'if/elif/else, for, while',               10, 3),
    (3, 'Fonctions et modules',      'def, paramètres, import',                12, 3),
    (4, 'Structures de données',     'listes, dictionnaires, ensembles',       12, 3),
    (5, 'Fichiers et exceptions',    'open, with, try/except',                 12, 2)
) AS v(rang, titre, objectifs, seuil, tentatives) ON TRUE;

-- ------------------------------------------------------------ niveaux Java
INSERT INTO niveaux (parcours_id, rang, titre, objectifs, seuil, tentatives_max, statut, cree_par)
SELECT p.id, v.rang, v.titre, v.objectifs, v.seuil, v.tentatives, 'PUBLIE',
       (SELECT id FROM utilisateurs WHERE email = 'sofia.meziane@univ.dz')
FROM parcours p
JOIN langages l ON l.id = p.langage_id AND l.code = 'java'
JOIN (VALUES
    (1, 'Classes et objets',            'attributs, méthodes, constructeurs',  12, 3),
    (2, 'Héritage et interfaces',       'extends, implements, polymorphisme',  12, 3),
    (3, 'Collections et généricité',    'List, Map, <T>',                      12, 3),
    (4, 'Exceptions et flux',           'try/catch, I/O',                      12, 2),
    (5, 'Interface graphique et MVC',   'JavaFX, FXML, contrôleurs',           12, 2)
) AS v(rang, titre, objectifs, seuil, tentatives) ON TRUE;

-- ------------------------------------------------------------- niveaux C++
INSERT INTO niveaux (parcours_id, rang, titre, objectifs, seuil, tentatives_max, statut, cree_par)
SELECT p.id, v.rang, v.titre, v.objectifs, v.seuil, v.tentatives, 'PUBLIE',
       (SELECT id FROM utilisateurs WHERE email = 'sofia.meziane@univ.dz')
FROM parcours p
JOIN langages l ON l.id = p.langage_id AND l.code = 'cpp'
JOIN (VALUES
    (1, 'Compilation et types',      'préprocesseur, types, compilation',     10, 3),
    (2, 'Pointeurs et références',   'adressage, passage par référence',      10, 3),
    (3, 'Classes et surcharge',      'constructeurs, opérateurs',             10, 3),
    (4, 'STL et templates',          'vector, map, algorithm',                10, 2),
    (5, 'Gestion mémoire',           'new/delete, RAII, smart pointers',      10, 2)
) AS v(rang, titre, objectifs, seuil, tentatives) ON TRUE;

-- ------------------------------------------------- cours du niveau 3 Python
WITH niveau AS (
    SELECT n.id FROM niveaux n
    JOIN parcours p ON p.id = n.parcours_id
    JOIN langages l ON l.id = p.langage_id AND l.code = 'python'
    WHERE n.rang = 3
), cours_cree AS (
    INSERT INTO cours (niveau_id, titre, statut)
    SELECT id, 'Fonctions et modules', 'PUBLIE' FROM niveau
    RETURNING id
)
INSERT INTO blocs (cours_id, position, type, langage, contenu)
SELECT cours_cree.id, v.position, v.type, v.langage, v.contenu
FROM cours_cree
JOIN (VALUES
    (1, 'TEXTE', NULL, 'Une fonction regroupe des instructions réutilisables. En Python, elle se déclare avec le mot-clé def et son corps est indenté : l''indentation fait partie de la syntaxe.'),
    (2, 'CODE', 'python', E'def moyenne(notes):\n    """Retourne la moyenne d''une liste de notes."""\n    if not notes:\n        return 0\n    return sum(notes) / len(notes)\n\nprint(moyenne([12, 15, 9]))   # 12.0'),
    (3, 'TEXTE', NULL, 'Les paramètres peuvent avoir une valeur par défaut : def saluer(nom="monde"). Attention, la valeur par défaut est évaluée une seule fois, à la définition de la fonction.'),
    (4, 'CODE', 'python', E'def saluer(nom: str = "monde") -> str:\n    return f"Bonjour, {nom} !"\n\nprint(saluer())        # Bonjour, monde !\nprint(saluer("Amina")) # Bonjour, Amina !')
) AS v(position, type, langage, contenu) ON TRUE;

-- ------------------------------------------------------- test du niveau 3
WITH niveau AS (
    SELECT n.id FROM niveaux n
    JOIN parcours p ON p.id = n.parcours_id
    JOIN langages l ON l.id = p.langage_id AND l.code = 'python'
    WHERE n.rang = 3
), test_cree AS (
    INSERT INTO tests (niveau_id, duree_minutes, ordre_aleatoire)
    SELECT id, 45, FALSE FROM niveau
    RETURNING id
)
INSERT INTO questions (test_id, position, type, enonce, bareme)
SELECT test_cree.id, v.position, 'QCM', v.enonce, 1.00
FROM test_cree
JOIN (VALUES
    (1, 'Quel mot-clé déclare une fonction en Python ?'),
    (2, 'Que vaut 7 // 2 en Python ?'),
    (3, 'Quelle est la portée d''une variable déclarée dans un bloc en Java ?')
) AS v(position, enonce) ON TRUE;

INSERT INTO propositions (question_id, position, texte, correcte)
SELECT q.id, v.position, v.texte, v.correcte
FROM (VALUES
    ('Quel mot-clé déclare une fonction en Python ?', 1, 'func',     FALSE),
    ('Quel mot-clé déclare une fonction en Python ?', 2, 'def',      TRUE),
    ('Quel mot-clé déclare une fonction en Python ?', 3, 'function', FALSE),
    ('Quel mot-clé déclare une fonction en Python ?', 4, 'void',     FALSE),
    ('Que vaut 7 // 2 en Python ?',                    1, '3.5',      FALSE),
    ('Que vaut 7 // 2 en Python ?',                    2, '3',        TRUE),
    ('Que vaut 7 // 2 en Python ?',                    3, '4',        FALSE),
    ('Que vaut 7 // 2 en Python ?',                    4, 'Erreur',   FALSE),
    ('Quelle est la portée d''une variable déclarée dans un bloc en Java ?',
                                                       1, 'Tout le programme',                FALSE),
    ('Quelle est la portée d''une variable déclarée dans un bloc en Java ?',
                                                       2, 'La classe',                        FALSE),
    ('Quelle est la portée d''une variable déclarée dans un bloc en Java ?',
                                                       3, 'Le bloc où elle est déclarée',     TRUE),
    ('Quelle est la portée d''une variable déclarée dans un bloc en Java ?',
                                                       4, 'Le fichier',                       FALSE)
) AS v(enonce, position, texte, correcte)
JOIN questions q ON q.enonce = v.enonce;

-- --------------------------------------------------------- progressions
-- Amina : niveaux 1 et 2 validés (même état que la frise du client).
INSERT INTO progressions (apprenant_id, niveau_id, etat, meilleure_note, valide_le)
SELECT u.id, n.id, 'VALIDE', v.note, now() - INTERVAL '20 days'
FROM (VALUES ('amina.bouzid@univ.dz', 1, 16.0),
             ('amina.bouzid@univ.dz', 2, 13.0)) AS v(email, rang, note)
JOIN utilisateurs u ON u.email = v.email
JOIN parcours p    ON p.langage_id = (SELECT id FROM langages WHERE code = 'python')
JOIN niveaux  n    ON n.parcours_id = p.id AND n.rang = v.rang;

-- Amina : niveau 3 en cours.
INSERT INTO progressions (apprenant_id, niveau_id, etat)
SELECT u.id, n.id, 'EN_COURS'
FROM utilisateurs u
JOIN parcours p ON p.langage_id = (SELECT id FROM langages WHERE code = 'python')
JOIN niveaux  n ON n.parcours_id = p.id AND n.rang = 3
WHERE u.email = 'amina.bouzid@univ.dz';

-- Karim et Omar : niveau 3 en cours, une tentative insuffisante chacun.
INSERT INTO progressions (apprenant_id, niveau_id, etat, meilleure_note)
SELECT u.id, n.id, 'EN_COURS', v.note
FROM (VALUES ('karim.haddad@univ.dz', 9.0),
             ('omar.belkacem@univ.dz', 11.0)) AS v(email, note)
JOIN utilisateurs u ON u.email = v.email
JOIN parcours p ON p.langage_id = (SELECT id FROM langages WHERE code = 'python')
JOIN niveaux  n ON n.parcours_id = p.id AND n.rang = 3;

-- ------------------------------------------- agent d'IA (positionnements A / B)
INSERT INTO agent_ia (nom, actif, positionnement, quota_jour)
VALUES ('agent_ia', TRUE, 'A', 200);

-- --------------------------------------------------------- journal d'audit
INSERT INTO journal_audit (acteur_id, action, cible, details)
SELECT u.id, 'PUBLICATION_NIVEAU', 'niveaux/3',
       '{"seuil": 12, "tentatives": 3, "parcours": "python"}'::jsonb
FROM utilisateurs u WHERE u.email = 'sofia.meziane@univ.dz';

COMMIT;
