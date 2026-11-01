package fr.ihm2026.api.persistence;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import fr.ihm2026.core.Session;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import static org.junit.jupiter.api.Assertions.*;
import java.sql.*;
import java.util.UUID;

/** Nécessite migrations 01,07,08,09,10. Fixtures isolées et supprimées, pas de données utilisateur modifiées. */
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
                long niveau=inserer(c,"INSERT INTO niveaux(parcours_id,rang,titre,seuil,statut) VALUES(?,1,'Test isolé',12,'PUBLIE') RETURNING id",parcours);
                long niveauSuivant=inserer(c,"INSERT INTO niveaux(parcours_id,rang,titre,seuil,statut) VALUES(?,2,'Niveau suivant isolé',12,'PUBLIE') RETURNING id",parcours);
                var sujet=d("preparer-test").put("niveau",niveau);sujet.putArray("questions").add("Explique une variable.");
                repo.traiter(admin,Session.Role.ADMINISTRATEUR,sujet);
                final long e=eleve,p=prof,a=autre;
                assertThrows(IllegalStateException.class,() -> repo.traiter(e,Session.Role.APPRENANT,d("commencer").put("niveau",niveau).put("langage","python").put("rang",1)));
                var affectation=d("affecter").put("apprenant",eleve).put("enseignant",prof);
                assertThrows(PostgresPedagogieRepository.AccesRefuse.class,() -> repo.traiter(p,Session.Role.ENSEIGNANT,affectation));
                repo.traiter(admin,Session.Role.ADMINISTRATEUR,affectation);
                assertEquals(1,repo.traiter(prof,Session.Role.ENSEIGNANT,d("mes-apprenants")).size());
                JsonNode copie=repo.traiter(eleve,Session.Role.APPRENANT,d("commencer").put("niveau",niveau).put("langage","python").put("rang",1));
                long id=copie.path("id").asLong();assertTrue(repo.evaluationEnCours(eleve));
                assertFalse(copie.toString().contains("correcte"));
                assertEquals(id,repo.traiter(eleve,Session.Role.APPRENANT,d("commencer").put("niveau",niveau).put("langage","python").put("rang",1)).path("id").asLong());
                final long ad=admin;
                assertThrows(IllegalStateException.class,() -> repo.traiter(ad,Session.Role.ADMINISTRATEUR,d("affecter").put("apprenant",e).put("enseignant",a)));
                var soumission=d("soumettre").put("id",id).put("code","x = 1");
                soumission.putObject("reponses").put(copie.path("questions").path(0).path("id").asText(),"Une variable associe un nom à une valeur.");
                assertThrows(PostgresPedagogieRepository.AccesRefuse.class,() -> repo.traiter(p,Session.Role.ENSEIGNANT,soumission));
                repo.traiter(eleve,Session.Role.APPRENANT,soumission);assertFalse(repo.evaluationEnCours(eleve));
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
            } finally {
                // Ordre FK explicite : les fixtures ne touchent aucun compte existant.
                try(var p=c.prepareStatement("DELETE FROM tentatives WHERE apprenant_id=?")){p.setLong(1,eleve);p.executeUpdate();}
                try(var p=c.prepareStatement("DELETE FROM affectations_pedagogiques WHERE apprenant_id=?")){p.setLong(1,eleve);p.executeUpdate();}
                try(var p=c.prepareStatement("DELETE FROM parcours WHERE id=?")){p.setLong(1,parcours);p.executeUpdate();}
                for(long id:new long[]{admin,prof,autre,eleve}){
                    try(var p=c.prepareStatement("DELETE FROM journal_audit WHERE acteur_id=?")){p.setLong(1,id);p.executeUpdate();}
                    try(var p=c.prepareStatement("DELETE FROM utilisateurs WHERE id=?")){p.setLong(1,id);p.executeUpdate();}
                }
            }
        }
    }
}
