-- =====================================================================
--  Corpus pédagogique v1 — généré par ia/corpus/construire.py (corpus-v1)
--  6 notions x 3 langages = 18 cours, 36 fragments citables.
--  Idempotent : relancer le script ne duplique ni un cours ni un bloc.
--  Aucun corrigé n'est inséré : seuls les blocs role = COURS entrent dans ce fichier.
--
--  Ordre d'application : 01-schema.sql, 02-seed.sql, 03-corpus-ia.sql,
--                        puis CE FICHIER, puis 04-ia-index.sql.
-- =====================================================================

BEGIN;

-- VAR / python — Variables et types en Python
WITH niveau AS (
    SELECT n.id FROM niveaux n
    JOIN parcours p ON p.id = n.parcours_id
    JOIN langages l ON l.id = p.langage_id AND l.code = 'python'
    WHERE n.rang = 1
), c AS (
    INSERT INTO cours (niveau_id, titre, statut)
    SELECT id, 'Variables et types en Python', 'PUBLIE' FROM niveau
    WHERE NOT EXISTS (SELECT 1 FROM cours WHERE titre = 'Variables et types en Python')
    RETURNING id
), cible AS (
    SELECT id FROM c
    UNION ALL
    SELECT id FROM cours WHERE titre = 'Variables et types en Python'
), insere AS (
    INSERT INTO blocs (cours_id, position, type, langage, contenu)
    SELECT cible.id, v.position, v.type, v.langage, v.contenu
    FROM cible
    JOIN (VALUES
    (11, 'TEXTE', NULL, 'En Python, une variable est un nom associé à un objet : aucune déclaration de type n''est nécessaire, le type est porté par la valeur. Les types de base sont int pour les entiers, float pour les réels, str pour les chaînes et bool pour les booléens. Attention à la division : 7 / 2 donne 3.5, alors que 7 // 2 donne 3.'),
    (21, 'CODE', 'python', 'age = 20            # int
moyenne = 12.5      # float
nom = "Amina"       # str
reussi = moyenne >= 10   # bool

print(type(age))      # <class ''int''>
print(7 / 2)          # 3.5
print(7 // 2)         # 3
print(int("12") + 1)  # conversion explicite : 13')
    ) AS v(position, type, langage, contenu) ON TRUE
    WHERE NOT EXISTS (
        SELECT 1 FROM blocs b WHERE b.cours_id = cible.id AND b.position = v.position)
    RETURNING 1
)
SELECT count(*) AS blocs_inseres FROM insere;

-- VAR / java — Variables et types en Java
WITH niveau AS (
    SELECT n.id FROM niveaux n
    JOIN parcours p ON p.id = n.parcours_id
    JOIN langages l ON l.id = p.langage_id AND l.code = 'java'
    WHERE n.rang = 1
), c AS (
    INSERT INTO cours (niveau_id, titre, statut)
    SELECT id, 'Variables et types en Java', 'PUBLIE' FROM niveau
    WHERE NOT EXISTS (SELECT 1 FROM cours WHERE titre = 'Variables et types en Java')
    RETURNING id
), cible AS (
    SELECT id FROM c
    UNION ALL
    SELECT id FROM cours WHERE titre = 'Variables et types en Java'
), insere AS (
    INSERT INTO blocs (cours_id, position, type, langage, contenu)
    SELECT cible.id, v.position, v.type, v.langage, v.contenu
    FROM cible
    JOIN (VALUES
    (11, 'TEXTE', NULL, 'En Java, toute variable a un type déclaré avant son nom : int pour les entiers, double pour les réels, String pour les chaînes, boolean pour les booléens. Le type est fixé à la compilation et ne change jamais. La division de deux int est entière : 7 / 2 vaut 3 ; pour obtenir un réel, l''un des deux opérandes doit être de type double.'),
    (21, 'CODE', 'java', 'int age = 20;
double moyenne = 12.5;
String nom = "Amina";
boolean reussi = moyenne >= 10;

System.out.println(7 / 2);            // 3
System.out.println(7 / 2.0);          // 3.5
System.out.println(Integer.parseInt("12") + 1);  // 13')
    ) AS v(position, type, langage, contenu) ON TRUE
    WHERE NOT EXISTS (
        SELECT 1 FROM blocs b WHERE b.cours_id = cible.id AND b.position = v.position)
    RETURNING 1
)
SELECT count(*) AS blocs_inseres FROM insere;

-- VAR / cpp — Variables et types en C++
WITH niveau AS (
    SELECT n.id FROM niveaux n
    JOIN parcours p ON p.id = n.parcours_id
    JOIN langages l ON l.id = p.langage_id AND l.code = 'cpp'
    WHERE n.rang = 1
), c AS (
    INSERT INTO cours (niveau_id, titre, statut)
    SELECT id, 'Variables et types en C++', 'PUBLIE' FROM niveau
    WHERE NOT EXISTS (SELECT 1 FROM cours WHERE titre = 'Variables et types en C++')
    RETURNING id
), cible AS (
    SELECT id FROM c
    UNION ALL
    SELECT id FROM cours WHERE titre = 'Variables et types en C++'
), insere AS (
    INSERT INTO blocs (cours_id, position, type, langage, contenu)
    SELECT cible.id, v.position, v.type, v.langage, v.contenu
    FROM cible
    JOIN (VALUES
    (11, 'TEXTE', NULL, 'En C++, le type précède le nom de la variable et sa taille dépend de la plateforme. Les types usuels sont int, double, bool et std::string, qui exige #include <string>. La division entre deux entiers est entière : 7 / 2 vaut 3. Une conversion explicite s''écrit avec static_cast<double>, plus sûre qu''un transtypage à la C.'),
    (21, 'CODE', 'cpp', '#include <string>

int age = 20;
double moyenne = 12.5;
std::string nom = "Amina";
bool reussi = moyenne >= 10;

std::cout << 7 / 2 << std::endl;                       // 3
std::cout << static_cast<double>(7) / 2 << std::endl;  // 3.5')
    ) AS v(position, type, langage, contenu) ON TRUE
    WHERE NOT EXISTS (
        SELECT 1 FROM blocs b WHERE b.cours_id = cible.id AND b.position = v.position)
    RETURNING 1
)
SELECT count(*) AS blocs_inseres FROM insere;

-- COND / python — Conditions en Python
WITH niveau AS (
    SELECT n.id FROM niveaux n
    JOIN parcours p ON p.id = n.parcours_id
    JOIN langages l ON l.id = p.langage_id AND l.code = 'python'
    WHERE n.rang = 1
), c AS (
    INSERT INTO cours (niveau_id, titre, statut)
    SELECT id, 'Conditions en Python', 'PUBLIE' FROM niveau
    WHERE NOT EXISTS (SELECT 1 FROM cours WHERE titre = 'Conditions en Python')
    RETURNING id
), cible AS (
    SELECT id FROM c
    UNION ALL
    SELECT id FROM cours WHERE titre = 'Conditions en Python'
), insere AS (
    INSERT INTO blocs (cours_id, position, type, langage, contenu)
    SELECT cible.id, v.position, v.type, v.langage, v.contenu
    FROM cible
    JOIN (VALUES
    (11, 'TEXTE', NULL, 'Une condition s''écrit avec if, elif et else, suivie de deux-points. Contrairement à Java et C++, les parenthèses ne sont pas nécessaires et le bloc est délimité par l''indentation : mélanger tabulations et espaces provoque une IndentationError. La comparaison d''égalité utilise deux signes égaux, l''affectation un seul.'),
    (21, 'CODE', 'python', 'note = 13

if note >= 16:
    mention = "très bien"
elif note >= 12:
    mention = "bien"
else:
    mention = "à revoir"

print(mention)   # bien')
    ) AS v(position, type, langage, contenu) ON TRUE
    WHERE NOT EXISTS (
        SELECT 1 FROM blocs b WHERE b.cours_id = cible.id AND b.position = v.position)
    RETURNING 1
)
SELECT count(*) AS blocs_inseres FROM insere;

-- COND / java — Conditions en Java
WITH niveau AS (
    SELECT n.id FROM niveaux n
    JOIN parcours p ON p.id = n.parcours_id
    JOIN langages l ON l.id = p.langage_id AND l.code = 'java'
    WHERE n.rang = 1
), c AS (
    INSERT INTO cours (niveau_id, titre, statut)
    SELECT id, 'Conditions en Java', 'PUBLIE' FROM niveau
    WHERE NOT EXISTS (SELECT 1 FROM cours WHERE titre = 'Conditions en Java')
    RETURNING id
), cible AS (
    SELECT id FROM c
    UNION ALL
    SELECT id FROM cours WHERE titre = 'Conditions en Java'
), insere AS (
    INSERT INTO blocs (cours_id, position, type, langage, contenu)
    SELECT cible.id, v.position, v.type, v.langage, v.contenu
    FROM cible
    JOIN (VALUES
    (11, 'TEXTE', NULL, 'En Java, la condition d''un if est une expression booléenne entre parenthèses : il n''existe pas de conversion automatique d''un entier vers un booléen, écrire if (note) ne compile pas. Les blocs sont délimités par des accolades ; sans accolades, seule l''instruction suivante appartient au if, ce qui est une source classique d''erreur.'),
    (21, 'CODE', 'java', 'int note = 13;
String mention;

if (note >= 16) {
    mention = "très bien";
} else if (note >= 12) {
    mention = "bien";
} else {
    mention = "à revoir";
}

System.out.println(mention);   // bien')
    ) AS v(position, type, langage, contenu) ON TRUE
    WHERE NOT EXISTS (
        SELECT 1 FROM blocs b WHERE b.cours_id = cible.id AND b.position = v.position)
    RETURNING 1
)
SELECT count(*) AS blocs_inseres FROM insere;

-- COND / cpp — Conditions en C++
WITH niveau AS (
    SELECT n.id FROM niveaux n
    JOIN parcours p ON p.id = n.parcours_id
    JOIN langages l ON l.id = p.langage_id AND l.code = 'cpp'
    WHERE n.rang = 1
), c AS (
    INSERT INTO cours (niveau_id, titre, statut)
    SELECT id, 'Conditions en C++', 'PUBLIE' FROM niveau
    WHERE NOT EXISTS (SELECT 1 FROM cours WHERE titre = 'Conditions en C++')
    RETURNING id
), cible AS (
    SELECT id FROM c
    UNION ALL
    SELECT id FROM cours WHERE titre = 'Conditions en C++'
), insere AS (
    INSERT INTO blocs (cours_id, position, type, langage, contenu)
    SELECT cible.id, v.position, v.type, v.langage, v.contenu
    FROM cible
    JOIN (VALUES
    (11, 'TEXTE', NULL, 'En C++, la condition d''un if est convertie en booléen : un entier nul vaut faux, tout autre entier vaut vrai. Cette souplesse est un piège : la faute de frappe if (x = 5), qui affecte au lieu de comparer, compile sans avertissement et rend la condition toujours vraie. Les compilateurs modernes la signalent avec l''option -Wall.'),
    (21, 'CODE', 'cpp', 'int note = 13;
std::string mention;

if (note >= 16) {
    mention = "très bien";
} else if (note >= 12) {
    mention = "bien";
} else {
    mention = "à revoir";
}

std::cout << mention << std::endl;   // bien')
    ) AS v(position, type, langage, contenu) ON TRUE
    WHERE NOT EXISTS (
        SELECT 1 FROM blocs b WHERE b.cours_id = cible.id AND b.position = v.position)
    RETURNING 1
)
SELECT count(*) AS blocs_inseres FROM insere;

-- BOUCLE / python — Boucles en Python
WITH niveau AS (
    SELECT n.id FROM niveaux n
    JOIN parcours p ON p.id = n.parcours_id
    JOIN langages l ON l.id = p.langage_id AND l.code = 'python'
    WHERE n.rang = 1
), c AS (
    INSERT INTO cours (niveau_id, titre, statut)
    SELECT id, 'Boucles en Python', 'PUBLIE' FROM niveau
    WHERE NOT EXISTS (SELECT 1 FROM cours WHERE titre = 'Boucles en Python')
    RETURNING id
), cible AS (
    SELECT id FROM c
    UNION ALL
    SELECT id FROM cours WHERE titre = 'Boucles en Python'
), insere AS (
    INSERT INTO blocs (cours_id, position, type, langage, contenu)
    SELECT cible.id, v.position, v.type, v.langage, v.contenu
    FROM cible
    JOIN (VALUES
    (11, 'TEXTE', NULL, 'La boucle for parcourt directement les éléments d''une séquence, sans compteur ni condition d''arrêt, comme dans for note in notes. Pour répéter un nombre connu de fois, on utilise range(n), qui produit les entiers de 0 à n-1. La boucle while répète tant qu''une condition reste vraie ; si cette condition ne devient jamais fausse, le programme ne se termine pas.'),
    (21, 'CODE', 'python', 'notes = [12, 15, 9]
somme = 0
for note in notes:
    somme += note
print(somme / len(notes))   # 12.0

i = 0
while i < 3:
    i += 1          # sans cette ligne : boucle infinie
print(i)            # 3')
    ) AS v(position, type, langage, contenu) ON TRUE
    WHERE NOT EXISTS (
        SELECT 1 FROM blocs b WHERE b.cours_id = cible.id AND b.position = v.position)
    RETURNING 1
)
SELECT count(*) AS blocs_inseres FROM insere;

-- BOUCLE / java — Boucles en Java
WITH niveau AS (
    SELECT n.id FROM niveaux n
    JOIN parcours p ON p.id = n.parcours_id
    JOIN langages l ON l.id = p.langage_id AND l.code = 'java'
    WHERE n.rang = 1
), c AS (
    INSERT INTO cours (niveau_id, titre, statut)
    SELECT id, 'Boucles en Java', 'PUBLIE' FROM niveau
    WHERE NOT EXISTS (SELECT 1 FROM cours WHERE titre = 'Boucles en Java')
    RETURNING id
), cible AS (
    SELECT id FROM c
    UNION ALL
    SELECT id FROM cours WHERE titre = 'Boucles en Java'
), insere AS (
    INSERT INTO blocs (cours_id, position, type, langage, contenu)
    SELECT cible.id, v.position, v.type, v.langage, v.contenu
    FROM cible
    JOIN (VALUES
    (11, 'TEXTE', NULL, 'Java propose trois formes : le for classique avec initialisation, condition et incrément ; le for-each pour parcourir un tableau sans indice ; et le while pour une condition inconnue à l''avance. Le parcours par indice impose de rester dans les bornes : un tableau de taille n se parcourt de 0 à n-1, sinon une ArrayIndexOutOfBoundsException est levée.'),
    (21, 'CODE', 'java', 'int[] notes = {12, 15, 9};
int somme = 0;

for (int note : notes) {
    somme += note;
}
System.out.println((double) somme / notes.length);   // 12.0

for (int i = 0; i < notes.length; i++) {
    System.out.print(notes[i] + " ");
}')
    ) AS v(position, type, langage, contenu) ON TRUE
    WHERE NOT EXISTS (
        SELECT 1 FROM blocs b WHERE b.cours_id = cible.id AND b.position = v.position)
    RETURNING 1
)
SELECT count(*) AS blocs_inseres FROM insere;

-- BOUCLE / cpp — Boucles en C++
WITH niveau AS (
    SELECT n.id FROM niveaux n
    JOIN parcours p ON p.id = n.parcours_id
    JOIN langages l ON l.id = p.langage_id AND l.code = 'cpp'
    WHERE n.rang = 1
), c AS (
    INSERT INTO cours (niveau_id, titre, statut)
    SELECT id, 'Boucles en C++', 'PUBLIE' FROM niveau
    WHERE NOT EXISTS (SELECT 1 FROM cours WHERE titre = 'Boucles en C++')
    RETURNING id
), cible AS (
    SELECT id FROM c
    UNION ALL
    SELECT id FROM cours WHERE titre = 'Boucles en C++'
), insere AS (
    INSERT INTO blocs (cours_id, position, type, langage, contenu)
    SELECT cible.id, v.position, v.type, v.langage, v.contenu
    FROM cible
    JOIN (VALUES
    (11, 'TEXTE', NULL, 'C++ reprend les trois formes de Java : for classique, for sur intervalle et while. Le for sur intervalle s''écrit for (int n : notes) et parcourt une copie de chaque élément ; pour modifier le contenu, on écrit for (int& n : notes). Comme en Java, un indice hors bornes n''est pas vérifié à l''exécution : le comportement est indéfini.'),
    (21, 'CODE', 'cpp', '#include <vector>

std::vector<int> notes{12, 15, 9};
int somme = 0;

for (int n : notes) {
    somme += n;
}
std::cout << static_cast<double>(somme) / notes.size() << std::endl;  // 12

for (int& n : notes) {
    n += 1;      // modifie le vecteur : passage par référence
}')
    ) AS v(position, type, langage, contenu) ON TRUE
    WHERE NOT EXISTS (
        SELECT 1 FROM blocs b WHERE b.cours_id = cible.id AND b.position = v.position)
    RETURNING 1
)
SELECT count(*) AS blocs_inseres FROM insere;

-- FONC / python — Fonctions et valeur de retour en Python
WITH niveau AS (
    SELECT n.id FROM niveaux n
    JOIN parcours p ON p.id = n.parcours_id
    JOIN langages l ON l.id = p.langage_id AND l.code = 'python'
    WHERE n.rang = 1
), c AS (
    INSERT INTO cours (niveau_id, titre, statut)
    SELECT id, 'Fonctions et valeur de retour en Python', 'PUBLIE' FROM niveau
    WHERE NOT EXISTS (SELECT 1 FROM cours WHERE titre = 'Fonctions et valeur de retour en Python')
    RETURNING id
), cible AS (
    SELECT id FROM c
    UNION ALL
    SELECT id FROM cours WHERE titre = 'Fonctions et valeur de retour en Python'
), insere AS (
    INSERT INTO blocs (cours_id, position, type, langage, contenu)
    SELECT cible.id, v.position, v.type, v.langage, v.contenu
    FROM cible
    JOIN (VALUES
    (11, 'TEXTE', NULL, 'Une fonction se déclare avec le mot-clé def et son corps est indenté. Une fonction qui ne contient aucun return, ou dont le return est sans valeur, renvoie None : c''est la valeur de retour par défaut en Python. Pour renvoyer un résultat, écrivez return suivi de la valeur, et veillez à ce que tous les chemins d''exécution atteignent un return.'),
    (21, 'CODE', 'python', 'def moyenne(notes):
    """Retourne la moyenne d''une liste de notes."""
    if not notes:
        return 0
    return sum(notes) / len(notes)

def sans_retour(notes):
    total = sum(notes)          # aucun return

print(moyenne([12, 15, 9]))     # 12.0
print(sans_retour([12, 15, 9])) # None')
    ) AS v(position, type, langage, contenu) ON TRUE
    WHERE NOT EXISTS (
        SELECT 1 FROM blocs b WHERE b.cours_id = cible.id AND b.position = v.position)
    RETURNING 1
)
SELECT count(*) AS blocs_inseres FROM insere;

-- FONC / java — Méthodes et valeur de retour en Java
WITH niveau AS (
    SELECT n.id FROM niveaux n
    JOIN parcours p ON p.id = n.parcours_id
    JOIN langages l ON l.id = p.langage_id AND l.code = 'java'
    WHERE n.rang = 1
), c AS (
    INSERT INTO cours (niveau_id, titre, statut)
    SELECT id, 'Méthodes et valeur de retour en Java', 'PUBLIE' FROM niveau
    WHERE NOT EXISTS (SELECT 1 FROM cours WHERE titre = 'Méthodes et valeur de retour en Java')
    RETURNING id
), cible AS (
    SELECT id FROM c
    UNION ALL
    SELECT id FROM cours WHERE titre = 'Méthodes et valeur de retour en Java'
), insere AS (
    INSERT INTO blocs (cours_id, position, type, langage, contenu)
    SELECT cible.id, v.position, v.type, v.langage, v.contenu
    FROM cible
    JOIN (VALUES
    (11, 'TEXTE', NULL, 'Une méthode déclare son type de retour avant son nom. Si ce type est void, la méthode ne renvoie rien ; sinon, tous les chemins d''exécution doivent atteindre un return, faute de quoi le compilateur refuse la compilation. Une méthode peut aussi lever une exception plutôt que renvoyer une valeur en cas d''erreur.'),
    (21, 'CODE', 'java', 'public static double moyenne(int[] notes) {
    if (notes.length == 0) {
        return 0;
    }
    int somme = 0;
    for (int n : notes) {
        somme += n;
    }
    return (double) somme / notes.length;
}

// moyenne(new int[]{12, 15, 9}) vaut 12.0')
    ) AS v(position, type, langage, contenu) ON TRUE
    WHERE NOT EXISTS (
        SELECT 1 FROM blocs b WHERE b.cours_id = cible.id AND b.position = v.position)
    RETURNING 1
)
SELECT count(*) AS blocs_inseres FROM insere;

-- FONC / cpp — Fonctions, retour et surcharge en C++
WITH niveau AS (
    SELECT n.id FROM niveaux n
    JOIN parcours p ON p.id = n.parcours_id
    JOIN langages l ON l.id = p.langage_id AND l.code = 'cpp'
    WHERE n.rang = 1
), c AS (
    INSERT INTO cours (niveau_id, titre, statut)
    SELECT id, 'Fonctions, retour et surcharge en C++', 'PUBLIE' FROM niveau
    WHERE NOT EXISTS (SELECT 1 FROM cours WHERE titre = 'Fonctions, retour et surcharge en C++')
    RETURNING id
), cible AS (
    SELECT id FROM c
    UNION ALL
    SELECT id FROM cours WHERE titre = 'Fonctions, retour et surcharge en C++'
), insere AS (
    INSERT INTO blocs (cours_id, position, type, langage, contenu)
    SELECT cible.id, v.position, v.type, v.langage, v.contenu
    FROM cible
    JOIN (VALUES
    (11, 'TEXTE', NULL, 'En C++, le type de retour est déclaré avant le nom de la fonction. Toute fonction non void doit retourner une valeur sur chaque chemin, sinon le comportement est indéfini. C++ autorise la surcharge : deux fonctions peuvent porter le même nom si leurs paramètres diffèrent, ce qui n''est pas possible en Python. Enfin, un paramètre volumineux se passe par référence constante (const std::vector<int>&) pour éviter une copie à chaque appel.'),
    (21, 'CODE', 'cpp', '#include <vector>

double moyenne(const std::vector<int>& notes) {
    if (notes.empty()) {
        return 0.0;
    }
    int somme = 0;
    for (int n : notes) {
        somme += n;
    }
    return static_cast<double>(somme) / notes.size();
}

// surcharge : même nom, paramètres différents
int moyenne(int a, int b) {
    return (a + b) / 2;
}')
    ) AS v(position, type, langage, contenu) ON TRUE
    WHERE NOT EXISTS (
        SELECT 1 FROM blocs b WHERE b.cours_id = cible.id AND b.position = v.position)
    RETURNING 1
)
SELECT count(*) AS blocs_inseres FROM insere;

-- COLL / python — Listes et dictionnaires en Python
WITH niveau AS (
    SELECT n.id FROM niveaux n
    JOIN parcours p ON p.id = n.parcours_id
    JOIN langages l ON l.id = p.langage_id AND l.code = 'python'
    WHERE n.rang = 1
), c AS (
    INSERT INTO cours (niveau_id, titre, statut)
    SELECT id, 'Listes et dictionnaires en Python', 'PUBLIE' FROM niveau
    WHERE NOT EXISTS (SELECT 1 FROM cours WHERE titre = 'Listes et dictionnaires en Python')
    RETURNING id
), cible AS (
    SELECT id FROM c
    UNION ALL
    SELECT id FROM cours WHERE titre = 'Listes et dictionnaires en Python'
), insere AS (
    INSERT INTO blocs (cours_id, position, type, langage, contenu)
    SELECT cible.id, v.position, v.type, v.langage, v.contenu
    FROM cible
    JOIN (VALUES
    (11, 'TEXTE', NULL, 'La liste est la collection ordonnée et modifiable de Python : elle s''écrit entre crochets et accepte des éléments de types différents. Sa taille s''obtient avec len(liste) et l''accès par indice commence à 0. Le dictionnaire associe des clés à des valeurs avec la syntaxe {clé: valeur} ; l''accès à une clé absente avec crochets lève une KeyError, alors que get renvoie None.'),
    (21, 'CODE', 'python', 'notes = [12, 15, 9]
notes.append(11)          # ajout en fin
print(len(notes))         # 4
print(notes[0])           # 12

bulletin = {"Amina": 16, "Karim": 9}
print(bulletin.get("Lina"))   # None, pas de KeyError
for nom, note in bulletin.items():
    print(nom, note)')
    ) AS v(position, type, langage, contenu) ON TRUE
    WHERE NOT EXISTS (
        SELECT 1 FROM blocs b WHERE b.cours_id = cible.id AND b.position = v.position)
    RETURNING 1
)
SELECT count(*) AS blocs_inseres FROM insere;

-- COLL / java — Collections en Java
WITH niveau AS (
    SELECT n.id FROM niveaux n
    JOIN parcours p ON p.id = n.parcours_id
    JOIN langages l ON l.id = p.langage_id AND l.code = 'java'
    WHERE n.rang = 1
), c AS (
    INSERT INTO cours (niveau_id, titre, statut)
    SELECT id, 'Collections en Java', 'PUBLIE' FROM niveau
    WHERE NOT EXISTS (SELECT 1 FROM cours WHERE titre = 'Collections en Java')
    RETURNING id
), cible AS (
    SELECT id FROM c
    UNION ALL
    SELECT id FROM cours WHERE titre = 'Collections en Java'
), insere AS (
    INSERT INTO blocs (cours_id, position, type, langage, contenu)
    SELECT cible.id, v.position, v.type, v.langage, v.contenu
    FROM cible
    JOIN (VALUES
    (11, 'TEXTE', NULL, 'En Java, la taille d''un tableau est fixée à sa création et s''obtient avec length, sans parenthèses. Pour une taille variable, on utilise les collections : ArrayList pour une liste modifiable, HashMap pour un dictionnaire. Leur taille s''obtient avec size(), avec des parenthèses. Confondre length et size() est l''erreur de compilation la plus fréquente.'),
    (21, 'CODE', 'java', 'import java.util.*;

List<Integer> notes = new ArrayList<>(List.of(12, 15, 9));
notes.add(11);
System.out.println(notes.size());      // 4
System.out.println(notes.get(0));      // 12

Map<String, Integer> bulletin = new HashMap<>();
bulletin.put("Amina", 16);
System.out.println(bulletin.getOrDefault("Lina", 0));   // 0

int[] tableau = {12, 15, 9};
System.out.println(tableau.length);    // 3, sans parenthèses')
    ) AS v(position, type, langage, contenu) ON TRUE
    WHERE NOT EXISTS (
        SELECT 1 FROM blocs b WHERE b.cours_id = cible.id AND b.position = v.position)
    RETURNING 1
)
SELECT count(*) AS blocs_inseres FROM insere;

-- COLL / cpp — Conteneurs STL en C++
WITH niveau AS (
    SELECT n.id FROM niveaux n
    JOIN parcours p ON p.id = n.parcours_id
    JOIN langages l ON l.id = p.langage_id AND l.code = 'cpp'
    WHERE n.rang = 1
), c AS (
    INSERT INTO cours (niveau_id, titre, statut)
    SELECT id, 'Conteneurs STL en C++', 'PUBLIE' FROM niveau
    WHERE NOT EXISTS (SELECT 1 FROM cours WHERE titre = 'Conteneurs STL en C++')
    RETURNING id
), cible AS (
    SELECT id FROM c
    UNION ALL
    SELECT id FROM cours WHERE titre = 'Conteneurs STL en C++'
), insere AS (
    INSERT INTO blocs (cours_id, position, type, langage, contenu)
    SELECT cible.id, v.position, v.type, v.langage, v.contenu
    FROM cible
    JOIN (VALUES
    (11, 'TEXTE', NULL, 'La bibliothèque standard de C++ fournit std::vector pour un tableau redimensionnable et std::map pour un dictionnaire ordonné. Leur taille s''obtient avec size(). L''accès par crochets ne vérifie pas les bornes : utiliser at() lève une exception si l''indice est hors limites. Un std::vector se passe par référence constante pour éviter une copie coûteuse.'),
    (21, 'CODE', 'cpp', '#include <vector>
#include <map>

std::vector<int> notes{12, 15, 9};
notes.push_back(11);
std::cout << notes.size() << std::endl;   // 4
std::cout << notes.at(0) << std::endl;    // 12, avec vérification

std::map<std::string, int> bulletin;
bulletin["Amina"] = 16;
std::cout << bulletin.size() << std::endl;  // 1')
    ) AS v(position, type, langage, contenu) ON TRUE
    WHERE NOT EXISTS (
        SELECT 1 FROM blocs b WHERE b.cours_id = cible.id AND b.position = v.position)
    RETURNING 1
)
SELECT count(*) AS blocs_inseres FROM insere;

-- ERREUR / python — Erreurs et exceptions en Python
WITH niveau AS (
    SELECT n.id FROM niveaux n
    JOIN parcours p ON p.id = n.parcours_id
    JOIN langages l ON l.id = p.langage_id AND l.code = 'python'
    WHERE n.rang = 1
), c AS (
    INSERT INTO cours (niveau_id, titre, statut)
    SELECT id, 'Erreurs et exceptions en Python', 'PUBLIE' FROM niveau
    WHERE NOT EXISTS (SELECT 1 FROM cours WHERE titre = 'Erreurs et exceptions en Python')
    RETURNING id
), cible AS (
    SELECT id FROM c
    UNION ALL
    SELECT id FROM cours WHERE titre = 'Erreurs et exceptions en Python'
), insere AS (
    INSERT INTO blocs (cours_id, position, type, langage, contenu)
    SELECT cible.id, v.position, v.type, v.langage, v.contenu
    FROM cible
    JOIN (VALUES
    (11, 'TEXTE', NULL, 'Python distingue les erreurs de syntaxe, détectées avant l''exécution, des exceptions, déclenchées pendant l''exécution. Une exception se capture avec try et except ; capturer une exception trop large masque les fautes de programmation. La division par zéro lève ZeroDivisionError, un indice hors bornes lève IndexError.'),
    (21, 'CODE', 'python', 'notes = []

try:
    moyenne = sum(notes) / len(notes)
except ZeroDivisionError:
    moyenne = 0     # on traite le cas, on ne masque pas l''erreur

print(moyenne)      # 0

# IndexError : notes[0] lèverait une exception')
    ) AS v(position, type, langage, contenu) ON TRUE
    WHERE NOT EXISTS (
        SELECT 1 FROM blocs b WHERE b.cours_id = cible.id AND b.position = v.position)
    RETURNING 1
)
SELECT count(*) AS blocs_inseres FROM insere;

-- ERREUR / java — Exceptions en Java
WITH niveau AS (
    SELECT n.id FROM niveaux n
    JOIN parcours p ON p.id = n.parcours_id
    JOIN langages l ON l.id = p.langage_id AND l.code = 'java'
    WHERE n.rang = 1
), c AS (
    INSERT INTO cours (niveau_id, titre, statut)
    SELECT id, 'Exceptions en Java', 'PUBLIE' FROM niveau
    WHERE NOT EXISTS (SELECT 1 FROM cours WHERE titre = 'Exceptions en Java')
    RETURNING id
), cible AS (
    SELECT id FROM c
    UNION ALL
    SELECT id FROM cours WHERE titre = 'Exceptions en Java'
), insere AS (
    INSERT INTO blocs (cours_id, position, type, langage, contenu)
    SELECT cible.id, v.position, v.type, v.langage, v.contenu
    FROM cible
    JOIN (VALUES
    (11, 'TEXTE', NULL, 'En Java, une exception se capture avec try et catch, et le bloc finally s''exécute dans tous les cas. Les exceptions vérifiées, comme IOException, doivent être déclarées ou capturées : le compilateur l''impose. Les exceptions non vérifiées, comme NullPointerException ou ArrayIndexOutOfBoundsException, traduisent une faute de programmation et ne devraient pas être capturées à la légère.'),
    (21, 'CODE', 'java', 'int[] notes = new int[0];

try {
    System.out.println(notes[0]);
} catch (ArrayIndexOutOfBoundsException e) {
    System.out.println("indice hors bornes : " + e.getMessage());
} finally {
    System.out.println("fin du traitement");
}')
    ) AS v(position, type, langage, contenu) ON TRUE
    WHERE NOT EXISTS (
        SELECT 1 FROM blocs b WHERE b.cours_id = cible.id AND b.position = v.position)
    RETURNING 1
)
SELECT count(*) AS blocs_inseres FROM insere;

-- ERREUR / cpp — Exceptions en C++
WITH niveau AS (
    SELECT n.id FROM niveaux n
    JOIN parcours p ON p.id = n.parcours_id
    JOIN langages l ON l.id = p.langage_id AND l.code = 'cpp'
    WHERE n.rang = 1
), c AS (
    INSERT INTO cours (niveau_id, titre, statut)
    SELECT id, 'Exceptions en C++', 'PUBLIE' FROM niveau
    WHERE NOT EXISTS (SELECT 1 FROM cours WHERE titre = 'Exceptions en C++')
    RETURNING id
), cible AS (
    SELECT id FROM c
    UNION ALL
    SELECT id FROM cours WHERE titre = 'Exceptions en C++'
), insere AS (
    INSERT INTO blocs (cours_id, position, type, langage, contenu)
    SELECT cible.id, v.position, v.type, v.langage, v.contenu
    FROM cible
    JOIN (VALUES
    (11, 'TEXTE', NULL, 'C++ utilise try et catch, mais rien n''oblige à traiter une exception : une exception non capturée termine le programme. Le conteneur std::vector lève std::out_of_range si l''on utilise at() avec un indice invalide, alors que l''opérateur crochets ne vérifie rien. Le RAII garantit que les ressources sont libérées même en cas d''exception.'),
    (21, 'CODE', 'cpp', '#include <stdexcept>
#include <vector>

std::vector<int> notes;

try {
    std::cout << notes.at(0) << std::endl;
} catch (const std::out_of_range& e) {
    std::cout << "indice hors bornes : " << e.what() << std::endl;
}')
    ) AS v(position, type, langage, contenu) ON TRUE
    WHERE NOT EXISTS (
        SELECT 1 FROM blocs b WHERE b.cours_id = cible.id AND b.position = v.position)
    RETURNING 1
)
SELECT count(*) AS blocs_inseres FROM insere;

-- ---------------------------------------------------------------- retrait
-- Les cours de démonstration antérieurs redisent une partie de la grille
-- (fonctions et valeur de retour). Les laisser PUBLIE ferait citer deux fois
-- la même notion par l'agent : ils repassent en BROUILLON, sans être supprimés.
UPDATE cours
   SET statut = 'BROUILLON', maj_le = now()
 WHERE statut = 'PUBLIE'
   AND titre IN ('Fonctions et modules',
                 'Méthodes et valeur de retour',
                 'Fonctions, retour et surcharge');

-- ------------------------------------------------ fragments indexables
-- Même formule de section que db/03-corpus-ia.sql : « cours — niveau ».
-- Rejoué ici pour que l'index reflète la grille dès l'application de ce fichier.
DO $$
BEGIN
    IF to_regclass('public.ia_fragments') IS NOT NULL THEN
        DELETE FROM ia_fragments;
        INSERT INTO ia_fragments (niveau_id, cours_id, langage, section, ordre, contenu, caracteres)
        SELECT n.id, c.id, COALESCE(b.langage, l.code),
               c.titre || ' — ' || n.titre, b.position, b.contenu, length(b.contenu)
          FROM blocs b
          JOIN cours c    ON c.id = b.cours_id
          JOIN niveaux n  ON n.id = c.niveau_id
          JOIN parcours p ON p.id = n.parcours_id
          JOIN langages l ON l.id = p.langage_id
         WHERE c.statut = 'PUBLIE';
    END IF;
END $$;

COMMIT;
