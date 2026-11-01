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
                        yield liste(c,"SELECT n.id,n.rang,n.titre,l.code AS langage FROM niveaux n JOIN parcours p ON p.id=n.parcours_id JOIN langages l ON l.id=p.langage_id WHERE n.statut='PUBLIE' AND (? OR n.cree_par=?) ORDER BY l.code,n.rang,n.id",role==Session.Role.ADMINISTRATEUR,acteur); }
                    case "preparer-test" -> { droitEdition(c,acteur,role,id(demande,"niveau")); yield preparerTest(c,acteur,demande); }
                    case "publier-contenus" -> { droitEdition(c,acteur,role,id(demande,"niveau")); yield publierContenus(c,acteur,demande); }
                    case "contenus" -> { exiger(role==Session.Role.APPRENANT); yield contenus(c,acteur,demande); }
                    case "affectations" -> { exiger(role == Session.Role.ADMINISTRATEUR); yield affectations(c); }
                    case "affecter" -> { exiger(role == Session.Role.ADMINISTRATEUR); yield affecter(c,acteur,demande); }
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
    private static void droitEdition(Connection c,long acteur,Session.Role role,long niveau) throws SQLException {
        exiger(role==Session.Role.ADMINISTRATEUR || role==Session.Role.ENSEIGNANT);
        try(var p=c.prepareStatement("SELECT id FROM niveaux WHERE id=? AND (? OR cree_par=?)")) {
            p.setLong(1,niveau);p.setBoolean(2,role==Session.Role.ADMINISTRATEUR);p.setLong(3,acteur);
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
        try(var p=c.prepareStatement("SELECT id FROM niveaux WHERE id=? AND statut='PUBLIE' FOR UPDATE")) {
            p.setLong(1,niveau);try(var r=p.executeQuery()){if(!r.next())throw new IllegalArgumentException("Niveau publié requis");}
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

    private static JsonNode affectations(Connection c) throws SQLException {
        return liste(c,"""
            SELECT u.id,u.nom,u.role::text,a.enseignant_id FROM utilisateurs u
            LEFT JOIN affectations_pedagogiques a ON a.apprenant_id=u.id
            WHERE u.actif AND u.statut_inscription='ACCEPTE' AND u.role IN ('APPRENANT','ENSEIGNANT')
            ORDER BY u.role,u.nom,u.id
            """);
    }
    private static JsonNode affecter(Connection c,long acteur,JsonNode d) throws SQLException {
        long apprenant=id(d,"apprenant"), enseignant=id(d,"enseignant");
        // Verrou commun : sérialise l'affectation avec la création d'une copie.
        try(var p=c.prepareStatement("SELECT id FROM utilisateurs WHERE id=? AND role='APPRENANT' AND actif AND statut_inscription='ACCEPTE' FOR UPDATE")) {
            p.setLong(1,apprenant); try(var r=p.executeQuery()) { if(!r.next()) throw new IllegalArgumentException("Apprenant actif requis"); }
        }
        try(var p=c.prepareStatement("SELECT id FROM utilisateurs WHERE id=? AND role='ENSEIGNANT' AND actif AND statut_inscription='ACCEPTE' FOR SHARE")) {
            p.setLong(1,enseignant); try(var r=p.executeQuery()) { if(!r.next()) throw new IllegalArgumentException("Enseignant actif requis"); }
        }
        try(var p=c.prepareStatement("SELECT id FROM tentatives WHERE apprenant_id=? AND enseignant_id<>? AND statut IN ('EN_COURS','SOUMISE')")) {
            p.setLong(1,apprenant);p.setLong(2,enseignant);
            try(var r=p.executeQuery()) { if(r.next()) throw new IllegalStateException("Une copie reste à terminer/corriger avant réaffectation."); }
        }
        try(var p=c.prepareStatement("""
            INSERT INTO affectations_pedagogiques(apprenant_id,enseignant_id,affecte_par) VALUES(?,?,?)
            ON CONFLICT(apprenant_id) DO UPDATE SET enseignant_id=excluded.enseignant_id,affecte_par=excluded.affecte_par,maj_le=now()
            """)) { p.setLong(1,apprenant);p.setLong(2,enseignant);p.setLong(3,acteur);p.executeUpdate(); }
        audit(c,acteur,"AFFECTATION_APPRENANT",apprenant); return JSON.createObjectNode().put("ok",true);
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
            WHERE l.code=? AND n.statut='PUBLIE' AND p.actif ORDER BY n.rang,n.id
            """,acteur,acteur,acteur,langage);
    }

    private static JsonNode commencer(Connection c,long acteur,JsonNode d) throws Exception {
        String langage=d.path("langage").asText();
        if(!List.of("java","python","cpp").contains(langage)) throw new IllegalArgumentException("Langage invalide");
        long niveauDemande=id(d,"niveau");
        int rang=d.path("rang").asInt(); if(rang<1) throw new IllegalArgumentException("Niveau invalide");
        JsonNode contenu=contenus(c,acteur,d);
        JsonNode infos=contenu.path("niveau");
        if(infos.path("rang").asInt()!=rang)throw new IllegalArgumentException("Le niveau sélectionné ne correspond pas au parcours. Revenez dans Parcours.");
        if(!infos.path("cours_pret").asBoolean() || !infos.path("exercice_pret").asBoolean())
            throw new IllegalStateException("Cours ou entraînement en préparation : le niveau doit être complet avant le test final.");
        if(!infos.path("test_pret").asBoolean())throw new IllegalStateException("Test en préparation : aucune question publiée pour ce niveau.");
        if(!infos.path("periode_ouverte").asBoolean())throw new IllegalStateException("Le test est en dehors de sa période d'ouverture.");
        try(var p=c.prepareStatement("SELECT id FROM utilisateurs WHERE id=? FOR UPDATE")) { p.setLong(1,acteur);p.executeQuery().close(); }
        long prof;
        try(var p=c.prepareStatement("SELECT a.enseignant_id FROM affectations_pedagogiques a JOIN utilisateurs u ON u.id=a.enseignant_id WHERE a.apprenant_id=? AND u.actif AND u.statut_inscription='ACCEPTE'")) {
            p.setLong(1,acteur); try(var r=p.executeQuery()) { if(!r.next()) throw new IllegalStateException("L'administrateur doit d'abord vous affecter à un enseignant actif."); prof=r.getLong(1); }
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
