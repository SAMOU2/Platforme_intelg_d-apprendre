package fr.ihm2026.api.persistence;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import fr.ihm2026.core.Session;
import java.sql.*;
import java.util.*;

/** Les identités et autorisations viennent exclusivement de la session HTTPS. */
public final class PostgresPedagogieRepository {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final PostgresConfiguration config;
    public PostgresPedagogieRepository(PostgresConfiguration config) { this.config = config; }
    public JsonNode traiter(long acteur, Session.Role role, JsonNode demande) throws SQLException {
        if (demande == null || !demande.isObject() || !demande.path("action").isTextual())
            throw new IllegalArgumentException("Action requise");
        try (Connection c = DriverManager.getConnection(config.url(),config.utilisateur(),config.motDePasse())) {
            c.setAutoCommit(false);
            try {
                JsonNode resultat = switch (demande.path("action").asText()) {
                    case "catalogue-tests" -> { exiger(role == Session.Role.ADMINISTRATEUR || role == Session.Role.ENSEIGNANT);
                        yield liste(c,"SELECT n.id,n.rang,n.titre,n.statut::text,l.code AS langage FROM niveaux n JOIN parcours p ON p.id=n.parcours_id JOIN langages l ON l.id=p.langage_id WHERE (? OR n.cree_par=?) ORDER BY l.code,n.rang,n.id",role==Session.Role.ADMINISTRATEUR,acteur); }
                    case "preparer-test" -> { droitEdition(c,acteur,role,id(demande,"niveau")); yield preparerTest(c,acteur,demande); }
                    case "creer-niveau" -> { exiger(role==Session.Role.ENSEIGNANT); yield creerNiveau(c,acteur,demande); }
                    case "publier-niveau" -> { exiger(role==Session.Role.ENSEIGNANT); droitEdition(c,acteur,role,id(demande,"niveau")); yield publierNiveau(c,acteur,demande); }
                    case "publier-contenus" -> { droitEdition(c,acteur,role,id(demande,"niveau")); yield publierContenus(c,acteur,demande); }
                    case "contenus" -> { exiger(role==Session.Role.APPRENANT); yield contenus(c,acteur,demande); }
                    // L'affectation d'un apprenant n'est plus un acte d'administration : elle
                    // résulte de sa demande d'inscription acceptée par l'enseignant (ou par le
                    // bot de l'offre). Toute demande d'affectation directe est refusée.
                    case "affectations", "affecter" -> throw new AccesRefuse(
                            "L'administrateur n'affecte plus les apprenants : l'apprenant choisit un enseignant dans son interface et l'enseignant (ou le bot de son offre) accepte la demande.");
                    case "offres" -> { exiger(role==Session.Role.APPRENANT); yield offres(c,acteur,demande); }
                    case "inscrire-offre" -> { exiger(role==Session.Role.APPRENANT); yield inscrireOffre(c,acteur,demande); }
                    case "mes-demandes" -> { exiger(role==Session.Role.APPRENANT); yield mesDemandes(c,acteur); }
                    case "annuler-demande" -> { exiger(role==Session.Role.APPRENANT); yield annulerDemande(c,acteur,demande); }
                    case "mon-offre" -> { exiger(role==Session.Role.ENSEIGNANT); yield monOffre(c,acteur); }
                    case "publier-offre" -> { exiger(role==Session.Role.ENSEIGNANT); yield publierOffre(c,acteur,demande); }
                    case "demandes-recues" -> { exiger(role==Session.Role.ENSEIGNANT); yield demandesRecues(c,acteur); }
                    case "decider-demande" -> { exiger(role==Session.Role.ENSEIGNANT); yield deciderDemande(c,acteur,demande); }
                    case "copies" -> copies(c,acteur,role);
                    case "niveaux" -> { exiger(role == Session.Role.APPRENANT); yield niveaux(c,acteur,demande); }
                    case "commencer" -> { exiger(role == Session.Role.APPRENANT); yield commencer(c,acteur,demande); }
                    case "soumettre", "abandonner" -> { exiger(role == Session.Role.APPRENANT); yield soumettre(c,acteur,demande); }
                    case "corriger", "publier" -> { exiger(role == Session.Role.ENSEIGNANT); yield corriger(c,acteur,demande); }
                    case "mes-apprenants" -> { exiger(role == Session.Role.ENSEIGNANT); yield mesApprenants(c,acteur); }
                    default -> throw new IllegalArgumentException("Action inconnue");
                };
                c.commit(); return resultat;
            } catch (Exception ex) {
                c.rollback();
                if (ex instanceof SQLException sql) throw sql;
                if (ex instanceof RuntimeException runtime) throw runtime;
                throw new SQLException("Service pédagogique indisponible", ex);
            }
        }
    }
    private static void exiger(boolean condition) {
        if (!condition) throw new AccesRefuse();
    }
    public static final class AccesRefuse extends RuntimeException {
        public AccesRefuse() { super("Accès pédagogique refusé."); }
        public AccesRefuse(String motif) { super(motif == null || motif.isBlank() ? "Accès pédagogique refusé." : motif); }
    }
    private static long id(JsonNode j,String champ) {
        if (!j.path(champ).isIntegralNumber() || j.path(champ).asLong() < 1) throw new IllegalArgumentException("Identifiant invalide");
        return j.path(champ).asLong();
    }
    private static JsonNode liste(Connection c,String sql,Object... params) throws SQLException {
        ArrayNode liste = JSON.createArrayNode();
        try (PreparedStatement p = c.prepareStatement(sql)) {
            for(int i=0;i<params.length;i++) p.setObject(i+1,params[i]);
            try(ResultSet r=p.executeQuery()) {
                while(r.next()) {
                    ObjectNode ligne=liste.addObject();
                    for(int i=1;i<=r.getMetaData().getColumnCount();i++) {
                        Object v=r.getObject(i); String nom=r.getMetaData().getColumnLabel(i);
                        if(v==null) ligne.putNull(nom);
                        else if(v instanceof Number n) ligne.put(nom,new java.math.BigDecimal(n.toString()));
                        else if(v instanceof Boolean b) ligne.put(nom,b);
                        else ligne.put(nom,v.toString());
                    }
                }
            }
        }
        return liste;
    }
    /** Un parcours privé par enseignant et langage ; rang attribué sous verrou, jamais choisi par le client. */
    private static JsonNode creerNiveau(Connection c,long acteur,JsonNode d) throws SQLException {
        String langage=d.path("langage").asText(),titre=d.path("titre").asText().strip();
        int seuil=d.path("seuil").asInt(-1),max=d.path("tentatives").asInt(-1);
        if(!List.of("java","python","cpp").contains(langage) || titre.isEmpty() || titre.length()>180
                || seuil<0 || seuil>20 || max<1 || max>10)throw new IllegalArgumentException("Titre, langage, seuil (0–20) et tentatives (1–10) requis.");
        try(var p=c.prepareStatement("SELECT id FROM utilisateurs WHERE id=? AND role='ENSEIGNANT' AND actif AND statut_inscription='ACCEPTE' FOR UPDATE")){
            p.setLong(1,acteur);try(var r=p.executeQuery()){exiger(r.next());}
        }
        long langue;
        try(var p=c.prepareStatement("SELECT id FROM langages WHERE code=?")){p.setString(1,langage);try(var r=p.executeQuery()){if(!r.next())throw new IllegalArgumentException("Langage non initialisé");langue=r.getLong(1);}}
        long parcours=0;
        try(var p=c.prepareStatement("SELECT parcours_id FROM parcours_enseignants WHERE enseignant_id=? AND langage_id=?")){
            p.setLong(1,acteur);p.setLong(2,langue);try(var r=p.executeQuery()){if(r.next())parcours=r.getLong(1);}
        }
        if(parcours==0){
            try(var p=c.prepareStatement("INSERT INTO parcours(langage_id,libelle) VALUES(?,?) RETURNING id")){
                p.setLong(1,langue);p.setString(2,"Parcours enseignant "+acteur+" — "+langage);try(var r=p.executeQuery()){r.next();parcours=r.getLong(1);}
            }
            try(var p=c.prepareStatement("INSERT INTO parcours_enseignants(enseignant_id,langage_id,parcours_id) VALUES(?,?,?)")){
                p.setLong(1,acteur);p.setLong(2,langue);p.setLong(3,parcours);p.executeUpdate();
            }
        }
        ObjectNode resultat=JSON.createObjectNode();
        try(var p=c.prepareStatement("INSERT INTO niveaux(parcours_id,rang,titre,seuil,tentatives_max,cree_par) SELECT ?,coalesce(max(rang),0)+1,?,?,?,? FROM niveaux WHERE parcours_id=? RETURNING id,rang")){
            p.setLong(1,parcours);p.setString(2,titre);p.setInt(3,seuil);p.setInt(4,max);p.setLong(5,acteur);p.setLong(6,parcours);
            try(var r=p.executeQuery()){r.next();resultat.put("id",r.getLong(1)).put("rang",r.getInt(2));}
        }
        audit(c,acteur,"CREATION_NIVEAU",resultat.path("id").asLong());return resultat;
    }
    private static JsonNode publierNiveau(Connection c,long acteur,JsonNode d) throws SQLException {
        long niveau=id(d,"niveau"),parcours,langage;int rang;
        try(var p=c.prepareStatement("SELECT n.parcours_id,p.langage_id,n.rang FROM niveaux n JOIN parcours p ON p.id=n.parcours_id WHERE n.id=? AND n.cree_par=? FOR NO KEY UPDATE OF p")){
            p.setLong(1,niveau);p.setLong(2,acteur);try(var r=p.executeQuery()){exiger(r.next());parcours=r.getLong(1);langage=r.getLong(2);rang=r.getInt(3);}
        }
        try(var p=c.prepareStatement("SELECT id FROM niveaux WHERE id=? FOR UPDATE")){p.setLong(1,niveau);p.executeQuery().close();}
        try(var p=c.prepareStatement("""
            SELECT 1 FROM niveaux n WHERE n.id=?
            AND EXISTS(SELECT 1 FROM cours x JOIN blocs b ON b.cours_id=x.id WHERE x.niveau_id=n.id AND x.statut='PUBLIE')
            AND EXISTS(SELECT 1 FROM entrainements_niveaux e WHERE e.niveau_id=n.id AND e.publie)
            AND EXISTS(SELECT 1 FROM tests t JOIN questions q ON q.test_id=t.id WHERE t.niveau_id=n.id AND q.active_pedagogique)
            AND (n.rang=1 OR EXISTS(SELECT 1 FROM niveaux precedent WHERE precedent.parcours_id=n.parcours_id AND precedent.rang=n.rang-1 AND precedent.statut='PUBLIE'))
            """)){
            p.setLong(1,niveau);try(var r=p.executeQuery()){if(!r.next())throw new IllegalStateException("Publiez d'abord cours, entraînement, questions du test et niveau précédent.");}
        }
        // Même verrou apprenant que commencer/inscription : ne pas masquer une copie encore ouverte.
        try(var p=c.prepareStatement("SELECT u.id FROM utilisateurs u JOIN affectations_pedagogiques a ON a.apprenant_id=u.id WHERE a.enseignant_id=? ORDER BY u.id FOR UPDATE OF u")){
            p.setLong(1,acteur);p.executeQuery().close();
        }
        try(var p=c.prepareStatement("""
            SELECT 1 FROM tentatives t JOIN tests x ON x.id=t.test_id JOIN niveaux n ON n.id=x.niveau_id
            JOIN parcours p ON p.id=n.parcours_id JOIN affectations_pedagogiques a ON a.apprenant_id=t.apprenant_id
            WHERE a.enseignant_id=? AND p.langage_id=? AND p.id<>? AND t.enseignant_id IS NOT NULL AND t.statut IN ('EN_COURS','SOUMISE')
            """)){
            p.setLong(1,acteur);p.setLong(2,langage);p.setLong(3,parcours);try(var r=p.executeQuery()){if(r.next())throw new IllegalStateException("Une copie de l'ancien parcours est encore ouverte : terminer sa correction/publication avant de basculer.");}
        }
        try(var p=c.prepareStatement("UPDATE niveaux SET statut='PUBLIE',maj_le=now() WHERE id=?")){p.setLong(1,niveau);p.executeUpdate();}
        audit(c,acteur,"PUBLICATION_NIVEAU",niveau);return JSON.createObjectNode().put("ok",true).put("rang",rang);
    }

    /**
     * Droit d'édition d'un niveau : son auteur enseignant, ou l'administrateur pour un niveau
     * <b>sans auteur</b> (continuité du service sur les contenus créés avant la migration 12).
     *
     * <p>Publier un cours, un entraînement ou un sujet de test est un acte d'enseignant :
     * l'interface administrateur ne le propose plus et le serveur ne l'accepte plus sur le niveau
     * d'un enseignant. Un client modifié ne peut donc pas rétablir ces actes côté administration.</p>
     */
    private static void droitEdition(Connection c,long acteur,Session.Role role,long niveau) throws SQLException {
        exiger(role==Session.Role.ADMINISTRATEUR || role==Session.Role.ENSEIGNANT);
        try(var p=c.prepareStatement("SELECT id FROM niveaux WHERE id=? AND (cree_par=? OR (cree_par IS NULL AND ?))")) {
            p.setLong(1,niveau);p.setLong(2,acteur);p.setBoolean(3,role==Session.Role.ADMINISTRATEUR);
            try(var r=p.executeQuery()){exiger(r.next());}
        }
    }
    private static JsonNode publierContenus(Connection c,long acteur,JsonNode d) throws SQLException {
        long niveau=id(d,"niveau");
        for(String champ:List.of("cours","exercice","code")) if(!d.path(champ).isTextual())throw new IllegalArgumentException("Contenu invalide");
        String texte=d.path("cours").asText().strip(),exercice=d.path("exercice").asText().strip(),code=d.path("code").asText();
        if(texte.isEmpty() || texte.length()>20000 || exercice.isEmpty() || exercice.length()>12000 || code.length()>12000)throw new IllegalArgumentException("Cours et exercice requis, tailles limitées.");
        try(var p=c.prepareStatement("SELECT id FROM niveaux WHERE id=? FOR UPDATE")){p.setLong(1,niveau);p.executeQuery().close();}
        try(var p=c.prepareStatement("UPDATE cours SET statut='BROUILLON' WHERE niveau_id=? AND statut='PUBLIE'")){p.setLong(1,niveau);p.executeUpdate();}
        long cours;
        try(var p=c.prepareStatement("INSERT INTO cours(niveau_id,titre,statut) SELECT id,titre,'PUBLIE' FROM niveaux WHERE id=? RETURNING id")){
            p.setLong(1,niveau);try(var r=p.executeQuery()){exiger(r.next());cours=r.getLong(1);}
        }
        try(var p=c.prepareStatement("INSERT INTO blocs(cours_id,position,type,contenu) VALUES(?,1,'TEXTE',?)")){p.setLong(1,cours);p.setString(2,texte);p.executeUpdate();}
        try(var p=c.prepareStatement("UPDATE entrainements_niveaux SET publie=FALSE WHERE niveau_id=?")){p.setLong(1,niveau);p.executeUpdate();}
        try(var p=c.prepareStatement("INSERT INTO entrainements_niveaux(niveau_id,enonce,code_depart,publie,cree_par) VALUES(?,?,?,TRUE,?)")){
            p.setLong(1,niveau);p.setString(2,exercice);p.setString(3,code);p.setLong(4,acteur);p.executeUpdate();
        }
        audit(c,acteur,"PUBLICATION_COURS_ENTRAINEMENT",niveau);return JSON.createObjectNode().put("ok",true);
    }
    private static JsonNode contenus(Connection c,long acteur,JsonNode d) throws SQLException {
        long niveau=id(d,"niveau");
        JsonNode accessibles=niveaux(c,acteur,d); JsonNode selection=null;
        for(JsonNode n:accessibles)if(n.path("id").asLong()==niveau)selection=n;
        if(selection==null)throw new IllegalArgumentException("Choisissez un niveau de ce langage depuis Parcours.");
        if("VERROUILLE".equals(selection.path("etat").asText()))throw new IllegalStateException("Le niveau précédent doit être validé par l'enseignant.");
        ObjectNode resultat=JSON.createObjectNode();resultat.set("niveau",selection);
        resultat.set("blocs",liste(c,"SELECT b.type,b.langage,b.contenu FROM blocs b JOIN cours x ON x.id=b.cours_id WHERE x.niveau_id=? AND x.statut='PUBLIE' ORDER BY x.id,b.position",niveau));
        resultat.set("exercices",liste(c,"SELECT id,enonce,code_depart FROM entrainements_niveaux WHERE niveau_id=? AND publie ORDER BY id",niveau));
        return resultat;
    }

    private static JsonNode preparerTest(Connection c,long acteur,JsonNode d) throws SQLException {
        long niveau=id(d,"niveau");
        JsonNode questions=d.path("questions");
        if(!questions.isArray() || questions.isEmpty() || questions.size()>20)throw new IllegalArgumentException("De 1 à 20 questions requises");
        for(JsonNode q:questions)if(!q.isTextual() || q.asText().isBlank() || q.asText().length()>1500)throw new IllegalArgumentException("Énoncé invalide");
        try(var p=c.prepareStatement("SELECT id FROM niveaux WHERE id=? FOR UPDATE")) {
            p.setLong(1,niveau);try(var r=p.executeQuery()){if(!r.next())throw new IllegalArgumentException("Niveau introuvable");}
        }
        try(var p=c.prepareStatement("INSERT INTO tests(niveau_id) VALUES(?) ON CONFLICT(niveau_id) DO NOTHING")){p.setLong(1,niveau);p.executeUpdate();}
        long test;
        try(var p=c.prepareStatement("SELECT id FROM tests WHERE niveau_id=? FOR UPDATE")){p.setLong(1,niveau);try(var r=p.executeQuery()){r.next();test=r.getLong(1);}}
        try(var p=c.prepareStatement("SELECT 1 FROM tentatives WHERE test_id=? AND enseignant_id IS NOT NULL AND statut='EN_COURS'")) {
            p.setLong(1,test);try(var r=p.executeQuery()){if(r.next())throw new IllegalStateException("Un apprenant passe ce test. Attendez sa soumission avant de changer le sujet.");}
        }
        int position;
        try(var p=c.prepareStatement("SELECT coalesce(max(position),0) FROM questions WHERE test_id=?")){p.setLong(1,test);try(var r=p.executeQuery()){r.next();position=r.getInt(1);}}
        try(var p=c.prepareStatement("UPDATE questions SET active_pedagogique=FALSE WHERE test_id=?")){p.setLong(1,test);p.executeUpdate();}
        for(JsonNode q:questions)try(var p=c.prepareStatement("INSERT INTO questions(test_id,position,type,enonce) VALUES(?,?,'REPONSE_COURTE',?)")) {
            p.setLong(1,test);p.setInt(2,++position);p.setString(3,q.asText().strip());p.executeUpdate();
        }
        audit(c,acteur,"PREPARATION_TEST",test);return JSON.createObjectNode().put("ok",true);
    }

    // ------------------------------------------------------------------ offres et inscriptions
    /**
     * Catalogue lu par l'apprenant : les enseignants actifs qui proposent une formation
     * dans ce langage, avec les informations réellement publiées (offre, niveaux, accès)
     * et l'état de sa propre demande. Aucune donnée inventée : un enseignant sans niveau
     * publié n'apparaît pas.
     */
    private static JsonNode offres(Connection c,long acteur,JsonNode d) throws SQLException {
        String langage=d.path("langage").asText();
        if(!List.of("java","python","cpp").contains(langage))throw new IllegalArgumentException("Langage invalide");
        return liste(c,"""
            WITH candidats AS (
              SELECT o.enseignant_id,o.langage_id FROM offres_enseignants o
              UNION
              SELECT pe.enseignant_id,pe.langage_id FROM parcours_enseignants pe
               WHERE EXISTS(SELECT 1 FROM niveaux n WHERE n.parcours_id=pe.parcours_id AND n.statut='PUBLIE')
            )
            SELECT e.id AS enseignant_id,e.nom AS enseignant,l.code AS langage,
             coalesce(o.titre,'Formation '||l.libelle||' — '||e.nom) AS titre,
             coalesce(o.presentation,'') AS presentation,
             coalesce(o.auto_acceptation,FALSE) AS acceptation_bot,
             coalesce(o.ouverte,TRUE) AS ouverte,
             (SELECT count(*) FROM parcours_enseignants pe JOIN niveaux n ON n.parcours_id=pe.parcours_id
               WHERE pe.enseignant_id=e.id AND pe.langage_id=l.id AND n.statut='PUBLIE') AS niveaux_publies,
             (SELECT coalesce(string_agg('Niveau '||n.rang||' — '||n.titre,' · ' ORDER BY n.rang),'')
                FROM parcours_enseignants pe JOIN niveaux n ON n.parcours_id=pe.parcours_id
               WHERE pe.enseignant_id=e.id AND pe.langage_id=l.id AND n.statut='PUBLIE') AS niveaux_titres,
             EXISTS(SELECT 1 FROM affectations_pedagogiques a WHERE a.apprenant_id=? AND a.enseignant_id=e.id) AS inscrit,
             (SELECT dm.id FROM demandes_inscription dm WHERE dm.apprenant_id=? AND dm.enseignant_id=e.id ORDER BY dm.id DESC LIMIT 1) AS demande_id,
             (SELECT dm.statut FROM demandes_inscription dm WHERE dm.apprenant_id=? AND dm.enseignant_id=e.id ORDER BY dm.id DESC LIMIT 1) AS statut_demande,
             (SELECT dm.decide_par FROM demandes_inscription dm WHERE dm.apprenant_id=? AND dm.enseignant_id=e.id ORDER BY dm.id DESC LIMIT 1) AS decide_par,
             (SELECT dm.motif FROM demandes_inscription dm WHERE dm.apprenant_id=? AND dm.enseignant_id=e.id ORDER BY dm.id DESC LIMIT 1) AS motif_demande
            FROM candidats k
            JOIN utilisateurs e ON e.id=k.enseignant_id
            JOIN langages l ON l.id=k.langage_id
            LEFT JOIN offres_enseignants o ON o.enseignant_id=e.id AND o.langage_id=l.id
            WHERE l.code=? AND e.role='ENSEIGNANT' AND e.actif AND e.statut_inscription='ACCEPTE'
              AND coalesce(o.ouverte,TRUE)
            ORDER BY e.nom,e.id
            """,acteur,acteur,acteur,acteur,acteur,langage);
    }
    /**
     * Demande d'inscription envoyée par l'apprenant lui-même. Le nom saisi accompagne la
     * demande ; l'enseignant la reçoit. Le bot de l'offre peut l'accepter immédiatement
     * (politique PC1), sinon l'accès attend la décision de l'enseignant.
     */
    private static JsonNode inscrireOffre(Connection c,long acteur,JsonNode d) throws SQLException {
        long enseignant=id(d,"enseignant");
        String langage=d.path("langage").asText();
        if(!List.of("java","python","cpp").contains(langage))throw new IllegalArgumentException("Langage invalide");
        String nom=d.path("nom").asText("").strip();
        String message=d.path("message").asText("").strip();
        if(nom.length()<fr.ihm2026.api.service.BotDemandes.NOM_MIN || nom.length()>fr.ihm2026.api.service.BotDemandes.NOM_MAX)
            throw new IllegalArgumentException("Saisissez votre nom complet (2 à 120 caractères).");
        if(message.length()>600)throw new IllegalArgumentException("Message trop long : 600 caractères maximum.");
        long langue;
        try(var p=c.prepareStatement("SELECT id FROM langages WHERE code=?")){p.setString(1,langage);try(var r=p.executeQuery()){if(!r.next())throw new IllegalArgumentException("Langage non initialisé");langue=r.getLong(1);}}
        // Verrou commun avec la création d'une copie : l'accès et les tentatives restent cohérents.
        try(var p=c.prepareStatement("SELECT id FROM utilisateurs WHERE id=? AND role='APPRENANT' AND actif AND statut_inscription='ACCEPTE' FOR UPDATE")) {
            p.setLong(1,acteur); try(var r=p.executeQuery()) { if(!r.next()) throw new IllegalStateException("Votre compte apprenant n'est pas encore actif et accepté."); }
        }
        try(var p=c.prepareStatement("SELECT id FROM utilisateurs WHERE id=? AND role='ENSEIGNANT' AND actif AND statut_inscription='ACCEPTE' FOR SHARE")) {
            p.setLong(1,enseignant); try(var r=p.executeQuery()) { if(!r.next()) throw new IllegalStateException("Cet enseignant n'est pas disponible."); }
        }
        // L'offre peut être publiée avant les niveaux : ce qui compte est que l'enseignant
        // propose réellement quelque chose (une offre rédigée ou au moins un niveau publié).
        boolean ouverte=true,acceptationBot=false,offrePubliee=false;
        // En PostgreSQL, max() n'existe pas pour le type boolean (erreur 42883 « function
        // max(boolean) does not exist »). bool_or() agrege des booleens : « au moins une offre
        // ouverte / avec acceptation automatique », comme le voulait le max() initial.
        try(var p=c.prepareStatement("""
            SELECT count(*)>0,coalesce(bool_or(o.ouverte),TRUE),coalesce(bool_or(o.auto_acceptation),FALSE)
            FROM offres_enseignants o WHERE o.enseignant_id=? AND o.langage_id=?
            """)) {
            p.setLong(1,enseignant);p.setLong(2,langue);
            try(var r=p.executeQuery()) { r.next();offrePubliee=r.getBoolean(1);ouverte=r.getBoolean(2);acceptationBot=r.getBoolean(3); }
        }
        boolean coursPublies;
        try(var p=c.prepareStatement("""
            SELECT EXISTS(SELECT 1 FROM parcours_enseignants pe JOIN niveaux n ON n.parcours_id=pe.parcours_id
             WHERE pe.enseignant_id=? AND pe.langage_id=? AND n.statut='PUBLIE')
            """)) {
            p.setLong(1,enseignant);p.setLong(2,langue);
            try(var r=p.executeQuery()) { r.next();coursPublies=r.getBoolean(1); }
        }
        if(!offrePubliee && !coursPublies)throw new IllegalStateException("Cet enseignant ne propose encore aucune formation dans ce langage.");
        if(!ouverte)throw new IllegalStateException("Cet enseignant n'accepte pas de nouvelles demandes pour le moment.");
        try(var p=c.prepareStatement("SELECT id FROM demandes_inscription WHERE apprenant_id=? AND enseignant_id=? AND statut='EN_ATTENTE' FOR UPDATE")) {
            p.setLong(1,acteur);p.setLong(2,enseignant);
            try(var r=p.executeQuery()) { if(r.next()) return JSON.createObjectNode().put("id",r.getLong(1)).put("statut","EN_ATTENTE").put("decide_par",(String)null).put("dejaEnvoyee",true)
                    .put("message","Votre demande est déjà en attente chez cet enseignant."); }
        }
        long actuel=0;
        try(var p=c.prepareStatement("SELECT enseignant_id FROM affectations_pedagogiques WHERE apprenant_id=? FOR UPDATE")) {
            p.setLong(1,acteur); try(var r=p.executeQuery()) { if(r.next()) actuel=r.getLong(1); }
        }
        if(actuel==enseignant) return JSON.createObjectNode().put("statut","ACCEPTEE").put("decide_par","ENSEIGNANT").put("dejaEnvoyee",true)
                .put("message","Vous êtes déjà inscrit auprès de cet enseignant : vos cours sont accessibles.");
        if(actuel!=0) verifierCopiesOuvertes(c,acteur,enseignant);
        var decision=fr.ihm2026.api.service.BotDemandes.depuisEnvironnement().analyser(nom,ouverte,acceptationBot);
        ObjectNode resultat=JSON.createObjectNode();
        try(var p=c.prepareStatement("""
            INSERT INTO demandes_inscription(apprenant_id,enseignant_id,langage_id,nom_demandeur,message,statut,motif,decide_par,decide_le)
            VALUES(?,?,?,?,?,CASE WHEN ? THEN 'ACCEPTEE' ELSE 'EN_ATTENTE' END,?,CASE WHEN ? THEN 'BOT' ELSE NULL END,CASE WHEN ? THEN now() ELSE NULL END)
            RETURNING id,statut
            """)) {
            p.setLong(1,acteur);p.setLong(2,enseignant);p.setLong(3,langue);p.setString(4,nom);
            p.setString(5,message.isEmpty()?null:message);p.setBoolean(6,decision.autoAccepter());
            p.setString(7,decision.autoAccepter()?decision.motif():null);p.setBoolean(8,decision.autoAccepter());p.setBoolean(9,decision.autoAccepter());
            try(var r=p.executeQuery()) { r.next();resultat.put("id",r.getLong(1)).put("statut",r.getString(2)); }
        }
        if(decision.autoAccepter()) accorderAcces(c,acteur,enseignant);
        audit(c,acteur,decision.autoAccepter()?"DEMANDE_ACCES_ACCEPTEE_PAR_BOT":"DEMANDE_ACCES_TRANSMISE",enseignant);
        resultat.put("decide_par",decision.autoAccepter()?"BOT":null);
        resultat.put("motif",decision.motif());
        resultat.put("message",decision.autoAccepter()
                ? "Demande acceptée immédiatement par le bot : vos cours, exercices et tests sont accessibles."
                : "Demande transmise à l'enseignant. L'écran se met à jour dès son acceptation.");
        return resultat;
    }
    private static JsonNode mesDemandes(Connection c,long acteur) throws SQLException {
        return liste(c,"""
            SELECT dm.id,e.nom AS enseignant,l.code AS langage,dm.nom_demandeur,dm.statut,dm.decide_par,dm.motif,dm.cree_le,dm.decide_le,
             EXISTS(SELECT 1 FROM affectations_pedagogiques a WHERE a.apprenant_id=dm.apprenant_id AND a.enseignant_id=dm.enseignant_id) AS inscrit
            FROM demandes_inscription dm JOIN utilisateurs e ON e.id=dm.enseignant_id JOIN langages l ON l.id=dm.langage_id
            WHERE dm.apprenant_id=? ORDER BY dm.id DESC LIMIT 100
            """,acteur);
    }
    private static JsonNode annulerDemande(Connection c,long acteur,JsonNode d) throws SQLException {
        long demande=id(d,"demande");
        try(var p=c.prepareStatement("""
            UPDATE demandes_inscription SET statut='ANNULEE',decide_par='APPRENANT',decide_le=now(),
             motif='Demande retirée par l''apprenant.' WHERE id=? AND apprenant_id=? AND statut='EN_ATTENTE'
            """)) {
            p.setLong(1,demande);p.setLong(2,acteur);
            if(p.executeUpdate()==0)throw new IllegalStateException("Cette demande n'est plus en attente.");
        }
        audit(c,acteur,"DEMANDE_ACCES_RETIREE",demande);return JSON.createObjectNode().put("ok",true);
    }
    /** L'offre publiée par l'enseignant : ce que les apprenants voient avant de s'inscrire. */
    private static JsonNode monOffre(Connection c,long acteur) throws SQLException {
        return liste(c,"""
            SELECT l.code AS langage,o.titre,o.presentation,o.auto_acceptation,o.ouverte,o.maj_le,
             (SELECT count(*) FROM parcours_enseignants pe JOIN niveaux n ON n.parcours_id=pe.parcours_id
               WHERE pe.enseignant_id=? AND pe.langage_id=l.id AND n.statut='PUBLIE') AS niveaux_publies,
             (SELECT count(*) FROM demandes_inscription dm WHERE dm.enseignant_id=? AND dm.langage_id=l.id AND dm.statut='EN_ATTENTE') AS demandes_attente,
             (SELECT count(*) FROM affectations_pedagogiques a WHERE a.enseignant_id=?) AS apprenants_inscrits
            FROM langages l LEFT JOIN offres_enseignants o ON o.enseignant_id=? AND o.langage_id=l.id
            WHERE l.code IN ('java','python','cpp') ORDER BY l.code
            """,acteur,acteur,acteur,acteur);
    }
    private static JsonNode publierOffre(Connection c,long acteur,JsonNode d) throws SQLException {
        String langage=d.path("langage").asText();
        if(!List.of("java","python","cpp").contains(langage))throw new IllegalArgumentException("Langage invalide");
        String titre=d.path("titre").asText("").strip(),presentation=d.path("presentation").asText("").strip();
        if(titre.length()<3 || titre.length()>180)throw new IllegalArgumentException("Titre de 3 à 180 caractères requis.");
        if(presentation.length()<3 || presentation.length()>2000)throw new IllegalArgumentException("Présentation de 3 à 2000 caractères requise.");
        boolean auto=d.path("auto_acceptation").asBoolean(false),ouverte=d.path("ouverte").asBoolean(true);
        try(var p=c.prepareStatement("SELECT id FROM utilisateurs WHERE id=? AND role='ENSEIGNANT' AND actif AND statut_inscription='ACCEPTE' FOR UPDATE")) {
            p.setLong(1,acteur); try(var r=p.executeQuery()) { exiger(r.next()); }
        }
        long langue;
        try(var p=c.prepareStatement("SELECT id FROM langages WHERE code=?")) {
            p.setString(1,langage);
            try(var r=p.executeQuery()) { if(!r.next()) throw new IllegalArgumentException("Langage non initialisé"); langue=r.getLong(1); }
        }
        try(var p=c.prepareStatement("""
            INSERT INTO offres_enseignants(enseignant_id,langage_id,titre,presentation,auto_acceptation,ouverte) VALUES(?,?,?,?,?,?)
            ON CONFLICT(enseignant_id,langage_id) DO UPDATE SET titre=excluded.titre,presentation=excluded.presentation,
             auto_acceptation=excluded.auto_acceptation,ouverte=excluded.ouverte,maj_le=now()
            """)) {
            p.setLong(1,acteur);p.setLong(2,langue);p.setString(3,titre);p.setString(4,presentation);
            p.setBoolean(5,auto);p.setBoolean(6,ouverte);p.executeUpdate();
        }
        audit(c,acteur,"PUBLICATION_OFFRE",langue);return JSON.createObjectNode().put("ok",true).put("langage",langage).put("auto_acceptation",auto).put("ouverte",ouverte);
    }
    private static JsonNode demandesRecues(Connection c,long acteur) throws SQLException {
        return liste(c,"""
            SELECT dm.id,dm.nom_demandeur,dm.message,dm.statut,dm.decide_par,dm.motif,dm.cree_le,dm.decide_le,
             u.nom AS apprenant,u.email AS apprenant_email,l.code AS langage,
             EXISTS(SELECT 1 FROM affectations_pedagogiques a WHERE a.apprenant_id=dm.apprenant_id AND a.enseignant_id=dm.enseignant_id) AS inscrit
            FROM demandes_inscription dm JOIN utilisateurs u ON u.id=dm.apprenant_id JOIN langages l ON l.id=dm.langage_id
            WHERE dm.enseignant_id=? ORDER BY (dm.statut='EN_ATTENTE') DESC,dm.id DESC LIMIT 200
            """,acteur);
    }
    /** Décision de l'enseignant : l'acceptation ouvre l'accès aux cours publiés. */
    private static JsonNode deciderDemande(Connection c,long acteur,JsonNode d) throws SQLException {
        long demande=id(d,"demande");
        if(!d.path("accepter").isBoolean())throw new IllegalArgumentException("Décision invalide : accepter est requis.");
        boolean accepter=d.path("accepter").asBoolean();
        String motif=d.path("motif").asText("").strip();
        if(motif.length()>600)throw new IllegalArgumentException("Motif trop long : 600 caractères maximum.");
        long apprenant=0;String statut=null;
        try(var p=c.prepareStatement("SELECT apprenant_id,statut FROM demandes_inscription WHERE id=? AND enseignant_id=? FOR UPDATE")) {
            p.setLong(1,demande);p.setLong(2,acteur);
            try(var r=p.executeQuery()) { exiger(r.next());apprenant=r.getLong(1);statut=r.getString(2); }
        }
        if(!"EN_ATTENTE".equals(statut))throw new IllegalStateException("Cette demande est déjà traitée.");
        if(accepter) {
            try(var p=c.prepareStatement("SELECT id FROM utilisateurs WHERE id=? AND role='APPRENANT' AND actif AND statut_inscription='ACCEPTE' FOR SHARE")) {
                p.setLong(1,apprenant); try(var r=p.executeQuery()) { if(!r.next()) throw new IllegalStateException("Ce compte apprenant n'est plus actif et accepté."); }
            }
            verifierCopiesOuvertes(c,apprenant,acteur);
            accorderAcces(c,apprenant,acteur);
        }
        try(var p=c.prepareStatement("UPDATE demandes_inscription SET statut=?,motif=?,decide_par='ENSEIGNANT',decide_le=now() WHERE id=?")) {
            p.setString(1,accepter?"ACCEPTEE":"REFUSEE");p.setString(2,motif.isEmpty()?null:motif);p.setLong(3,demande);p.executeUpdate();
        }
        audit(c,acteur,accepter?"ACCORD_ACCES_APPRENANT":"REFUS_ACCES_APPRENANT",demande);
        return JSON.createObjectNode().put("ok",true).put("statut",accepter?"ACCEPTEE":"REFUSEE").put("demande",demande);
    }
    /** Accès aux cours publiés : écrit uniquement sur décision de l'enseignant ou de son bot. */
    private static void accorderAcces(Connection c,long apprenant,long enseignant) throws SQLException {
        try(var p=c.prepareStatement("""
            INSERT INTO affectations_pedagogiques(apprenant_id,enseignant_id,affecte_par) VALUES(?,?,?)
            ON CONFLICT(apprenant_id) DO UPDATE SET enseignant_id=excluded.enseignant_id,affecte_par=excluded.affecte_par,maj_le=now()
            """)) { p.setLong(1,apprenant);p.setLong(2,enseignant);p.setLong(3,enseignant);p.executeUpdate(); }
    }
    /** Un changement d'enseignant reste interdit tant qu'une copie n'est pas terminée et corrigée. */
    private static void verifierCopiesOuvertes(Connection c,long apprenant,long enseignant) throws SQLException {
        try(var p=c.prepareStatement("SELECT id FROM tentatives WHERE apprenant_id=? AND enseignant_id<>? AND statut IN ('EN_COURS','SOUMISE')")) {
            p.setLong(1,apprenant);p.setLong(2,enseignant);
            try(var r=p.executeQuery()) { if(r.next()) throw new IllegalStateException("Une copie reste à terminer ou à corriger avec l'enseignant actuel avant de changer d'enseignant."); }
        }
    }
    private static JsonNode mesApprenants(Connection c,long acteur) throws SQLException {
        return liste(c,"SELECT u.id,u.nom FROM affectations_pedagogiques a JOIN utilisateurs u ON u.id=a.apprenant_id WHERE a.enseignant_id=? ORDER BY u.nom,u.id",acteur);
    }
    private static JsonNode copies(Connection c,long acteur,Session.Role role) throws SQLException {
        exiger(role==Session.Role.APPRENANT || role==Session.Role.ENSEIGNANT);
        boolean enseignant=role==Session.Role.ENSEIGNANT;
        return liste(c,"""
            SELECT t.id,u.nom AS apprenant,e.nom AS enseignant,n.rang,l.code AS langage,t.statut::text,
              t.sujet_snapshot::text AS sujet,t.reponses_snapshot::text AS reponses,t.code_source,
              CASE WHEN t.publie_le IS NOT NULL THEN t.note END AS note,
              CASE WHEN t.publie_le IS NOT NULL THEN t.commentaire_brouillon END AS commentaire,
              CASE WHEN t.publie_le IS NOT NULL THEN t.decision_brouillon END AS valide,
              t.publie_le,t.soumise_le,t.seuil_snapshot AS seuil
            """ + (enseignant ? ",t.note_brouillon,t.commentaire_brouillon,t.decision_brouillon " : " ") + """
            FROM tentatives t JOIN utilisateurs u ON u.id=t.apprenant_id
            JOIN utilisateurs e ON e.id=t.enseignant_id JOIN tests x ON x.id=t.test_id
            JOIN niveaux n ON n.id=x.niveau_id JOIN parcours p ON p.id=n.parcours_id JOIN langages l ON l.id=p.langage_id
            WHERE t.enseignant_id IS NOT NULL AND
            """ + (enseignant ? "t.enseignant_id=?" : "t.apprenant_id=?") + " ORDER BY t.id DESC LIMIT 200",acteur);
    }
    private static JsonNode niveaux(Connection c,long acteur,JsonNode d) throws SQLException {
        String langage=d.path("langage").asText();
        if(!List.of("java","python","cpp").contains(langage))throw new IllegalArgumentException("Langage invalide");
        return liste(c,"""
            WITH garde AS (SELECT ?::bigint AS apprenant)
            SELECT n.id,n.rang,n.titre,n.seuil,n.tentatives_max,g.meilleure_note,
             EXISTS(SELECT 1 FROM cours x JOIN blocs b ON b.cours_id=x.id WHERE x.niveau_id=n.id AND x.statut='PUBLIE') AS cours_pret,
             EXISTS(SELECT 1 FROM entrainements_niveaux e WHERE e.niveau_id=n.id AND e.publie) AS exercice_pret,
             EXISTS(SELECT 1 FROM tests x JOIN questions q ON q.test_id=x.id WHERE x.niveau_id=n.id AND q.active_pedagogique) AS test_pret,
             (n.periode_debut IS NULL OR n.periode_debut<=current_date) AND (n.periode_fin IS NULL OR n.periode_fin>=current_date) AS periode_ouverte,
             EXISTS(SELECT 1 FROM affectations_pedagogiques a JOIN utilisateurs prof ON prof.id=a.enseignant_id WHERE a.apprenant_id=garde.apprenant AND prof.actif AND prof.statut_inscription='ACCEPTE') AS affecte,

             CASE WHEN g.etat='VALIDE' THEN 'VALIDE'
               WHEN n.rang=1 OR EXISTS(SELECT 1 FROM progressions avant JOIN niveaux precedent ON precedent.id=avant.niveau_id
                 WHERE avant.apprenant_id=? AND avant.etat='VALIDE' AND precedent.parcours_id=n.parcours_id AND precedent.rang=n.rang-1)
               THEN 'EN_COURS' ELSE 'VERROUILLE' END AS etat
            FROM niveaux n CROSS JOIN garde JOIN parcours p ON p.id=n.parcours_id JOIN langages l ON l.id=p.langage_id
            LEFT JOIN progressions g ON g.niveau_id=n.id AND g.apprenant_id=?
            WHERE l.code=? AND n.statut='PUBLIE' AND p.actif
            AND (
              EXISTS(SELECT 1 FROM parcours_enseignants pe JOIN affectations_pedagogiques a ON a.enseignant_id=pe.enseignant_id
                WHERE a.apprenant_id=garde.apprenant AND pe.parcours_id=p.id)
              OR (NOT EXISTS(SELECT 1 FROM parcours_enseignants pe WHERE pe.parcours_id=p.id)
                AND NOT EXISTS(SELECT 1 FROM parcours_enseignants pe JOIN affectations_pedagogiques a ON a.enseignant_id=pe.enseignant_id
                  JOIN niveaux prive ON prive.parcours_id=pe.parcours_id AND prive.statut='PUBLIE'
                  WHERE a.apprenant_id=garde.apprenant AND pe.langage_id=p.langage_id))
            ) ORDER BY n.rang,n.id
            """,acteur,acteur,acteur,langage);
    }

    private static JsonNode commencer(Connection c,long acteur,JsonNode d) throws Exception {
        String langage=d.path("langage").asText();
        if(!List.of("java","python","cpp").contains(langage)) throw new IllegalArgumentException("Langage invalide");
        long niveauDemande=id(d,"niveau");
        int rang=d.path("rang").asInt(); if(rang<1) throw new IllegalArgumentException("Niveau invalide");
        try(var p=c.prepareStatement("SELECT id FROM utilisateurs WHERE id=? FOR UPDATE")) { p.setLong(1,acteur);p.executeQuery().close(); }
        JsonNode contenu=contenus(c,acteur,d);
        JsonNode infos=contenu.path("niveau");
        if(infos.path("rang").asInt()!=rang)throw new IllegalArgumentException("Le niveau sélectionné ne correspond pas au parcours. Revenez dans Parcours.");
        if(!infos.path("cours_pret").asBoolean() || !infos.path("exercice_pret").asBoolean())
            throw new IllegalStateException("Cours ou entraînement en préparation : le niveau doit être complet avant le test final.");
        if(!infos.path("test_pret").asBoolean())throw new IllegalStateException("Test en préparation : aucune question publiée pour ce niveau.");
        if(!infos.path("periode_ouverte").asBoolean())throw new IllegalStateException("Le test est en dehors de sa période d'ouverture.");
        long prof;
        try(var p=c.prepareStatement("SELECT a.enseignant_id FROM affectations_pedagogiques a JOIN utilisateurs u ON u.id=a.enseignant_id WHERE a.apprenant_id=? AND u.actif AND u.statut_inscription='ACCEPTE'")) {
            p.setLong(1,acteur); try(var r=p.executeQuery()) { if(!r.next()) throw new IllegalStateException("Aucun enseignant ne vous a encore accepté : inscrivez-vous auprès d'un enseignant depuis le catalogue."); prof=r.getLong(1); }
        }
        long test,niveau; int seuil,max;
        try(var p=c.prepareStatement("""
            SELECT x.id,n.id,n.seuil,n.tentatives_max FROM tests x JOIN niveaux n ON n.id=x.niveau_id
            JOIN parcours p ON p.id=n.parcours_id JOIN langages l ON l.id=p.langage_id
            WHERE l.code=? AND n.rang=? AND n.id=? AND n.statut='PUBLIE' AND p.actif
            AND (n.periode_debut IS NULL OR n.periode_debut<=current_date)
            AND (n.periode_fin IS NULL OR n.periode_fin>=current_date)
            AND (n.rang=1 OR EXISTS(SELECT 1 FROM progressions g JOIN niveaux precedent ON precedent.id=g.niveau_id
              WHERE g.apprenant_id=? AND g.etat='VALIDE' AND precedent.parcours_id=n.parcours_id AND precedent.rang=n.rang-1))
            ORDER BY x.id LIMIT 1 FOR SHARE OF x
            """)) {
            p.setString(1,langage);p.setInt(2,rang);p.setLong(3,niveauDemande);p.setLong(4,acteur);
            try(var r=p.executeQuery()) { if(!r.next()) throw new IllegalStateException("Ce test n'est pas publié, est hors période ou le niveau précédent n'est pas validé."); test=r.getLong(1);niveau=r.getLong(2);seuil=r.getInt(3);max=r.getInt(4); }
        }
        try(var p=c.prepareStatement("SELECT id,sujet_snapshot::text,statut::text FROM tentatives WHERE apprenant_id=? AND test_id=? AND enseignant_id IS NOT NULL AND statut IN ('EN_COURS','SOUMISE')")) {
            p.setLong(1,acteur);p.setLong(2,test);
            try(var r=p.executeQuery()) { if(r.next()) {
                if(!"EN_COURS".equals(r.getString(3))) throw new IllegalStateException("Une copie est déjà en attente de correction.");
                return JSON.createObjectNode().put("id",r.getLong(1)).set("questions",JSON.readTree(r.getString(2)));
            } }
        }
        int numero;
        try(var p=c.prepareStatement("SELECT coalesce(max(numero),0)+1,count(*) FILTER(WHERE statut<>'ANNULEE') FROM tentatives WHERE apprenant_id=? AND test_id=?")) {
            p.setLong(1,acteur);p.setLong(2,test);try(var r=p.executeQuery()){r.next();numero=r.getInt(1);if(r.getInt(2)>=max) throw new IllegalStateException("Nombre maximal de tentatives atteint.");}
        }
        ArrayNode questions=JSON.createArrayNode();
        try(var p=c.prepareStatement("SELECT id,type::text,enonce FROM questions WHERE test_id=? AND active_pedagogique ORDER BY position")) {
            p.setLong(1,test);try(var r=p.executeQuery()){while(r.next()){
                ObjectNode q=questions.addObject().put("id",r.getLong(1)).put("type",r.getString(2)).put("enonce",r.getString(3));
                q.set("propositions",liste(c,"SELECT id,texte FROM propositions WHERE question_id=? ORDER BY position",r.getLong(1)));
            }}
        }
        if(questions.isEmpty()) throw new IllegalStateException("Ce test n'a pas de questions configurées.");
        try(var p=c.prepareStatement("INSERT INTO tentatives(apprenant_id,test_id,numero,enseignant_id,sujet_snapshot,seuil_snapshot) VALUES(?,?,?,?,?::jsonb,?) RETURNING id")) {
            p.setLong(1,acteur);p.setLong(2,test);p.setInt(3,numero);p.setLong(4,prof);p.setString(5,questions.toString());p.setInt(6,seuil);
            try(var r=p.executeQuery()){r.next();long copie=r.getLong(1);audit(c,acteur,"DEBUT_TEST",copie);return JSON.createObjectNode().put("id",copie).set("questions",questions);}
        }
    }
    private static JsonNode soumettre(Connection c,long acteur,JsonNode d) throws Exception {
        long copie=id(d,"id"); String sujet;
        try(var p=c.prepareStatement("SELECT sujet_snapshot::text FROM tentatives WHERE id=? AND apprenant_id=? AND enseignant_id IS NOT NULL AND statut='EN_COURS' FOR UPDATE")) {
            p.setLong(1,copie);p.setLong(2,acteur);try(var r=p.executeQuery()){exiger(r.next());sujet=r.getString(1);}
        }
        boolean abandon="abandonner".equals(d.path("action").asText());
        if(!abandon) {
            JsonNode reponses=d.path("reponses"),questions=JSON.readTree(sujet);
            if(!reponses.isObject()) throw new IllegalArgumentException("Réponses invalides");
            Set<String> ids=new HashSet<>();questions.forEach(q -> ids.add(q.path("id").asText()));
            var champs=reponses.fieldNames();while(champs.hasNext()) if(!ids.contains(champs.next())) throw new IllegalArgumentException("Question inconnue");
            for(JsonNode q:questions){
                JsonNode valeur=reponses.get(q.path("id").asText());
                if(valeur==null || valeur.isNull()) continue;
                if("QCM".equals(q.path("type").asText())) {
                    boolean trouve=false;for(JsonNode prop:q.path("propositions")) if(valeur.isIntegralNumber() && prop.path("id").asLong()==valeur.asLong()) trouve=true;
                    if(!trouve) throw new IllegalArgumentException("Proposition invalide");
                } else if(!valeur.isTextual() || valeur.asText().length()>8000) throw new IllegalArgumentException("Réponse texte invalide");
            }
            if(!d.path("code").isTextual() || d.path("code").asText().length()>16000) throw new IllegalArgumentException("Code trop long");
        }
        try(var p=c.prepareStatement("UPDATE tentatives SET statut=?::statut_tentative,reponses_snapshot=?::jsonb,code_source=?,soumise_le=now() WHERE id=?")) {
            p.setString(1,abandon?"ANNULEE":"SOUMISE");p.setString(2,abandon?"{}":d.path("reponses").toString());p.setString(3,abandon?"":d.path("code").asText());p.setLong(4,copie);p.executeUpdate();
        }
        audit(c,acteur,abandon?"ABANDON_TEST":"SOUMISSION_TEST",copie);return JSON.createObjectNode().put("ok",true);
    }
    private static JsonNode corriger(Connection c,long acteur,JsonNode d) throws SQLException {
        long copie=id(d,"id");long apprenant,niveau;int seuil;
        try(var p=c.prepareStatement("SELECT t.apprenant_id,x.niveau_id,t.seuil_snapshot FROM tentatives t JOIN tests x ON x.id=t.test_id WHERE t.id=? AND t.enseignant_id=? AND t.statut='SOUMISE' AND t.publie_le IS NULL FOR UPDATE OF t")) {
            p.setLong(1,copie);p.setLong(2,acteur);try(var r=p.executeQuery()){exiger(r.next());apprenant=r.getLong(1);niveau=r.getLong(2);seuil=r.getInt(3);}
        }
        if(!d.path("note").isNumber() || !d.path("valide").isBoolean() || !d.path("commentaire").isTextual()) throw new IllegalArgumentException("Correction incomplète");
        var note=d.path("note").decimalValue();String commentaire=d.path("commentaire").asText().strip();boolean valide=d.path("valide").asBoolean();
        if(note.signum()<0 || note.compareTo(java.math.BigDecimal.valueOf(20))>0 || note.scale()>2 || commentaire.isBlank() || commentaire.length()>4000 || (valide && note.compareTo(java.math.BigDecimal.valueOf(seuil))<0)) throw new IllegalArgumentException("Note, commentaire ou validation incohérents avec le seuil.");
        boolean publier="publier".equals(d.path("action").asText());
        try(var p=c.prepareStatement("UPDATE tentatives SET note_brouillon=?,commentaire_brouillon=?,decision_brouillon=?,note=CASE WHEN ? THEN ? ELSE NULL END,statut=CASE WHEN ? THEN 'CORRIGEE'::statut_tentative ELSE statut END,publie_le=CASE WHEN ? THEN now() ELSE NULL END WHERE id=?")) {
            p.setBigDecimal(1,note);p.setString(2,commentaire);p.setBoolean(3,valide);p.setBoolean(4,publier);p.setBigDecimal(5,note);p.setBoolean(6,publier);p.setBoolean(7,publier);p.setLong(8,copie);p.executeUpdate();
        }
        if(publier) {
            try(var p=c.prepareStatement("""
                INSERT INTO progressions(apprenant_id,niveau_id,etat,meilleure_note,valide_le)
                VALUES(?,?,?::etat_niveau,?,CASE WHEN ? THEN now() ELSE NULL END)
                ON CONFLICT(apprenant_id,niveau_id) DO UPDATE SET
                etat=CASE WHEN progressions.etat='VALIDE' THEN progressions.etat ELSE excluded.etat END,
                meilleure_note=greatest(progressions.meilleure_note,excluded.meilleure_note),
                valide_le=coalesce(progressions.valide_le,excluded.valide_le),maj_le=now()
                """)) {
                p.setLong(1,apprenant);p.setLong(2,niveau);p.setString(3,valide?"VALIDE":"EN_COURS");p.setBigDecimal(4,note);p.setBoolean(5,valide);p.executeUpdate();
            }
        }
        audit(c,acteur,publier?"PUBLICATION_CORRECTION":"BROUILLON_CORRECTION",copie);
        return JSON.createObjectNode().put("ok",true);
    }
    public boolean evaluationEnCours(long acteur) throws SQLException {
        try(var c=DriverManager.getConnection(config.url(),config.utilisateur(),config.motDePasse());var p=c.prepareStatement("SELECT 1 FROM tentatives WHERE apprenant_id=? AND enseignant_id IS NOT NULL AND statut='EN_COURS'")) {
            p.setLong(1,acteur);try(var r=p.executeQuery()){return r.next();}
        }
    }
    private static void audit(Connection c,long acteur,String action,long cible) throws SQLException {
        try(var p=c.prepareStatement("INSERT INTO journal_audit(acteur_id,action,cible) VALUES(?,?,?)")){p.setLong(1,acteur);p.setString(2,action);p.setString(3,Long.toString(cible));p.executeUpdate();}
    }
}
