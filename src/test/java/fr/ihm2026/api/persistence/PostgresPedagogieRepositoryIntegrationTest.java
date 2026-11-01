package fr.ihm2026.api.persistence;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import fr.ihm2026.core.Session;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import static org.junit.jupiter.api.Assertions.*;
import java.sql.*;
import java.util.UUID;

/** Nécessite migrations 01,07,08,09,10,11,12. Fixtures isolées et supprimées, pas de données utilisateur modifiées. */
@EnabledIfEnvironmentVariable(named="IHM_DB_URL",matches="jdbc:postgresql://.+")
class PostgresPedagogieRepositoryIntegrationTest {
    private static final ObjectMapper JSON=new ObjectMapper();
    private static ObjectNode d(String action){return JSON.createObjectNode().put("action",action);}
    private static long inserer(Connection c,String sql,Object... valeurs)throws SQLException {
        try(var p=c.prepareStatement(sql)){for(int i=0;i<valeurs.length;i++)p.setObject(i+1,valeurs[i]);try(var r=p.executeQuery()){r.next();return r.getLong(1);}}
    }
    @Test void affectationSoumissionCorrectionPublicationEtIsolation()throws Exception {
        var config=PostgresConfiguration.depuisEnvironnement();
        var repo=new PostgresPedagogieRepository(config);
        String suffixe=UUID.randomUUID().toString();
        long admin=0,prof=0,autre=0,eleve=0,parcours=0;
        try(var c=DriverManager.getConnection(config.url(),config.utilisateur(),config.motDePasse())) {
            try {
                String utilisateur="INSERT INTO utilisateurs(nom,email,mot_de_passe,role,actif,statut_inscription) VALUES(?,?,'fixture-non-connectable',?::role_utilisateur,TRUE,'ACCEPTE') RETURNING id";
                admin=inserer(c,utilisateur,"Admin test","admin-"+suffixe+"@example.org","ADMINISTRATEUR");
                prof=inserer(c,utilisateur,"Prof test","prof-"+suffixe+"@example.org","ENSEIGNANT");
                autre=inserer(c,utilisateur,"Autre prof","autre-"+suffixe+"@example.org","ENSEIGNANT");
                eleve=inserer(c,utilisateur,"Élève test","eleve-"+suffixe+"@example.org","APPRENANT");
                long langage=inserer(c,"SELECT id FROM langages WHERE code='python'");
                parcours=inserer(c,"INSERT INTO parcours(langage_id,libelle) VALUES(?,?) RETURNING id",langage,"fixture-"+suffixe);
                long niveau=inserer(c,"INSERT INTO niveaux(parcours_id,rang,titre,seuil,statut,cree_par) VALUES(?,1,'Test isolé',12,'PUBLIE',?) RETURNING id",parcours,prof);
                long niveauSuivant=inserer(c,"INSERT INTO niveaux(parcours_id,rang,titre,seuil,statut) VALUES(?,2,'Niveau suivant isolé',12,'PUBLIE') RETURNING id",parcours);
                final long responsable=prof,intrus=autre;
                var contenu=d("publier-contenus").put("niveau",niveau).put("cours","Une variable associe un nom à une valeur.")
                        .put("exercice","Déclare une variable entière.").put("code","nombre = 1");
                assertThrows(PostgresPedagogieRepository.AccesRefuse.class,() -> repo.traiter(intrus,Session.Role.ENSEIGNANT,contenu));
                repo.traiter(responsable,Session.Role.ENSEIGNANT,contenu);
                var lu=repo.traiter(eleve,Session.Role.APPRENANT,d("contenus").put("niveau",niveau).put("langage","python"));
                assertEquals(1,lu.path("blocs").size());assertEquals(1,lu.path("exercices").size());
                assertTrue(lu.path("niveau").path("cours_pret").asBoolean());
                assertFalse(lu.path("niveau").path("test_pret").asBoolean());
                final long apprenantTest=eleve;
                assertThrows(IllegalStateException.class,() -> repo.traiter(apprenantTest,Session.Role.APPRENANT,
                        d("contenus").put("niveau",niveauSuivant).put("langage","python")));
                var sujet=d("preparer-test").put("niveau",niveau);sujet.putArray("questions").add("Explique une variable.");
                repo.traiter(admin,Session.Role.ADMINISTRATEUR,sujet);
                final long e=eleve,p=prof,a=autre;
                assertThrows(IllegalStateException.class,() -> repo.traiter(e,Session.Role.APPRENANT,d("commencer").put("niveau",niveau).put("langage","python").put("rang",1)));
                var affectation=d("affecter").put("apprenant",eleve).put("enseignant",prof);
                assertThrows(PostgresPedagogieRepository.AccesRefuse.class,() -> repo.traiter(p,Session.Role.ENSEIGNANT,affectation));
                repo.traiter(admin,Session.Role.ADMINISTRATEUR,affectation);
                assertEquals(1,repo.traiter(prof,Session.Role.ENSEIGNANT,d("mes-apprenants")).size());
                // Création autonome : brouillons privés, sans intervention de l'administrateur.
                var creation=d("creer-niveau").put("langage","python").put("titre","Mon niveau").put("seuil",12).put("tentatives",3);
                assertThrows(PostgresPedagogieRepository.AccesRefuse.class,() -> repo.traiter(e,Session.Role.APPRENANT,creation));
                long premier=repo.traiter(prof,Session.Role.ENSEIGNANT,creation).path("id").asLong();
                var second=repo.traiter(prof,Session.Role.ENSEIGNANT,creation.deepCopy().put("titre","Mon deuxième niveau"));
                assertEquals(2,second.path("rang").asInt());long deuxieme=second.path("id").asLong();
                long etranger=repo.traiter(autre,Session.Role.ENSEIGNANT,creation).path("id").asLong();
                assertThrows(PostgresPedagogieRepository.AccesRefuse.class,() -> repo.traiter(p,Session.Role.ENSEIGNANT,sujet.deepCopy().put("niveau",etranger)));
                assertThrows(PostgresPedagogieRepository.AccesRefuse.class,() -> repo.traiter(p,Session.Role.ENSEIGNANT,contenu.deepCopy().put("niveau",etranger)));
                assertFalse(repo.traiter(prof,Session.Role.ENSEIGNANT,d("catalogue-tests")).findValues("id").stream().anyMatch(n -> n.asLong()==etranger));
                assertThrows(PostgresPedagogieRepository.AccesRefuse.class,() -> repo.traiter(a,Session.Role.ENSEIGNANT,d("publier-niveau").put("niveau",premier)));
                assertThrows(IllegalStateException.class,() -> repo.traiter(p,Session.Role.ENSEIGNANT,d("publier-niveau").put("niveau",premier)));
                for(long n:new long[]{premier,deuxieme,etranger}){
                    long auteur=n==etranger?autre:prof;
                    repo.traiter(auteur,Session.Role.ENSEIGNANT,contenu.deepCopy().put("niveau",n));
                    repo.traiter(auteur,Session.Role.ENSEIGNANT,sujet.deepCopy().put("niveau",n));
                }
                assertThrows(IllegalStateException.class,() -> repo.traiter(p,Session.Role.ENSEIGNANT,d("publier-niveau").put("niveau",deuxieme)));
                repo.traiter(autre,Session.Role.ENSEIGNANT,d("publier-niveau").put("niveau",etranger));
                var avant=repo.traiter(eleve,Session.Role.APPRENANT,d("niveaux").put("langage","python"));
                assertTrue(avant.findValues("id").stream().anyMatch(n -> n.asLong()==niveau));
                assertFalse(avant.findValues("id").stream().anyMatch(n -> n.asLong()==premier || n.asLong()==etranger));
                JsonNode copie=repo.traiter(eleve,Session.Role.APPRENANT,d("commencer").put("niveau",niveau).put("langage","python").put("rang",1));
                long id=copie.path("id").asLong();assertTrue(repo.evaluationEnCours(eleve));
                assertThrows(IllegalStateException.class,() -> repo.traiter(p,Session.Role.ENSEIGNANT,d("publier-niveau").put("niveau",premier)));
                assertFalse(copie.toString().contains("correcte"));
                assertEquals(id,repo.traiter(eleve,Session.Role.APPRENANT,d("commencer").put("niveau",niveau).put("langage","python").put("rang",1)).path("id").asLong());
                final long ad=admin;
                assertThrows(IllegalStateException.class,() -> repo.traiter(ad,Session.Role.ADMINISTRATEUR,d("affecter").put("apprenant",e).put("enseignant",a)));
                var soumission=d("soumettre").put("id",id).put("code","x = 1");
                soumission.putObject("reponses").put(copie.path("questions").path(0).path("id").asText(),"Une variable associe un nom à une valeur.");
                assertThrows(PostgresPedagogieRepository.AccesRefuse.class,() -> repo.traiter(p,Session.Role.ENSEIGNANT,soumission));
                repo.traiter(eleve,Session.Role.APPRENANT,soumission);assertFalse(repo.evaluationEnCours(eleve));
                assertThrows(IllegalStateException.class,() -> repo.traiter(p,Session.Role.ENSEIGNANT,d("publier-niveau").put("niveau",premier)));
                assertThrows(PostgresPedagogieRepository.AccesRefuse.class,() -> repo.traiter(e,Session.Role.APPRENANT,soumission));
                var brouillon=d("corriger").put("id",id).put("note",15).put("commentaire","Bonne explication.").put("valide",true);
                assertThrows(PostgresPedagogieRepository.AccesRefuse.class,() -> repo.traiter(a,Session.Role.ENSEIGNANT,brouillon));
                assertThrows(IllegalArgumentException.class,() -> repo.traiter(p,Session.Role.ENSEIGNANT,
                        brouillon.deepCopy().put("note",11)));
                repo.traiter(prof,Session.Role.ENSEIGNANT,brouillon);
                JsonNode attente=repo.traiter(eleve,Session.Role.APPRENANT,d("copies")).path(0);
                assertTrue(attente.path("note").isNull());assertFalse(attente.has("note_brouillon"));assertTrue(attente.path("commentaire").isNull());
                assertEquals(0,repo.traiter(autre,Session.Role.ENSEIGNANT,d("copies")).size());
                var publication=brouillon.deepCopy().put("action","publier");
                repo.traiter(prof,Session.Role.ENSEIGNANT,publication);
                JsonNode decision=repo.traiter(eleve,Session.Role.APPRENANT,d("copies")).path(0);
                assertEquals(15,decision.path("note").asInt());assertTrue(decision.path("valide").asBoolean());
                assertEquals("CORRIGEE",decision.path("statut").asText());
                JsonNode progression=repo.traiter(eleve,Session.Role.APPRENANT,d("niveaux").put("langage","python"));
                boolean suivantOuvert=false;
                for(JsonNode n:progression)if(n.path("id").asLong()==niveauSuivant)suivantOuvert="EN_COURS".equals(n.path("etat").asText());
                assertTrue(suivantOuvert);
                assertThrows(PostgresPedagogieRepository.AccesRefuse.class,() -> repo.traiter(p,Session.Role.ENSEIGNANT,publication));
                assertEquals("VALIDE",repo.traiter(eleve,Session.Role.APPRENANT,d("niveaux").put("langage","python"))
                        .findParents("etat").stream().filter(n -> n.path("id").asLong()==niveau).findFirst().orElseThrow().path("etat").asText());
                // Après correction de l'ancienne copie, bascule vers les seuls niveaux publiés du professeur.
                repo.traiter(prof,Session.Role.ENSEIGNANT,d("publier-niveau").put("niveau",premier));
                repo.traiter(prof,Session.Role.ENSEIGNANT,d("publier-niveau").put("niveau",deuxieme));
                var visibles=repo.traiter(eleve,Session.Role.APPRENANT,d("niveaux").put("langage","python"));
                assertEquals(2,visibles.size());assertEquals(premier,visibles.path(0).path("id").asLong());
                assertEquals("VERROUILLE",visibles.path(1).path("etat").asText());
                assertEquals(15,repo.traiter(eleve,Session.Role.APPRENANT,d("copies")).path(0).path("note").asInt());
                assertThrows(IllegalArgumentException.class,() -> repo.traiter(e,Session.Role.APPRENANT,d("contenus").put("niveau",etranger).put("langage","python")));
                assertThrows(IllegalArgumentException.class,() -> repo.traiter(e,Session.Role.APPRENANT,d("commencer").put("niveau",niveau).put("langage","python").put("rang",1)));
                var nouvelle=repo.traiter(eleve,Session.Role.APPRENANT,d("commencer").put("niveau",premier).put("langage","python").put("rang",1));
                var reponse=d("soumettre").put("id",nouvelle.path("id").asLong()).put("code","x=1");
                reponse.putObject("reponses").put(nouvelle.path("questions").path(0).path("id").asText(),"Une valeur nommée.");
                repo.traiter(eleve,Session.Role.APPRENANT,reponse);
                repo.traiter(prof,Session.Role.ENSEIGNANT,d("publier").put("id",nouvelle.path("id").asLong()).put("note",16).put("commentaire","Validé").put("valide",true));
                assertEquals("EN_COURS",repo.traiter(eleve,Session.Role.APPRENANT,d("niveaux").put("langage","python")).path(1).path("etat").asText());
            } finally {
                // Ordre FK explicite : les fixtures ne touchent aucun compte existant.
                try(var p=c.prepareStatement("DELETE FROM tentatives WHERE apprenant_id=?")){p.setLong(1,eleve);p.executeUpdate();}
                try(var p=c.prepareStatement("DELETE FROM affectations_pedagogiques WHERE apprenant_id=?")){p.setLong(1,eleve);p.executeUpdate();}
                try(var p=c.prepareStatement("DELETE FROM parcours WHERE id=?")){p.setLong(1,parcours);p.executeUpdate();}
                try(var p=c.prepareStatement("DELETE FROM parcours WHERE id IN (SELECT parcours_id FROM parcours_enseignants WHERE enseignant_id IN (?,?))")){p.setLong(1,prof);p.setLong(2,autre);p.executeUpdate();}
                for(long id:new long[]{admin,prof,autre,eleve}){
                    try(var p=c.prepareStatement("DELETE FROM journal_audit WHERE acteur_id=?")){p.setLong(1,id);p.executeUpdate();}
                    try(var p=c.prepareStatement("DELETE FROM utilisateurs WHERE id=?")){p.setLong(1,id);p.executeUpdate();}
                }
            }
        }
    }
}
