package fr.ihm2026.api.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import fr.ihm2026.core.Session;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Client HTTPS pour les ouvertures et fermetures de session de compte. */
public final class ClientAuthentificationHttps implements AutoCloseable {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Logger LOGGER =
            Logger.getLogger(ClientAuthentificationHttps.class.getName());
    private static final Duration DELAI_CONNEXION = Duration.ofSeconds(3);
    private static final Duration DELAI_REQUETE = Duration.ofSeconds(8);
    // Les routes qui envoient un e-mail attendent aussi les échanges SMTP/TLS.
    private static final Duration DELAI_ENVOI_EMAIL = Duration.ofSeconds(60);
    private static final Duration DELAI_DECISIONS_LOT = Duration.ofMinutes(3);
    private static final int LOT_DECISIONS_MAX = 20;

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(DELAI_CONNEXION)
            .build();
    private final URI base;
    private final Compte compte;
    private String jeton;

    private ClientAuthentificationHttps(URI base, String jeton, Compte compte) {
        this.base = base;
        this.jeton = jeton;
        this.compte = compte;
    }

    public static CompteInscrit inscrireCompte(String hote, int port,
            String nom, String email, char[] motDePasse, Session.Role role) {
        if (motDePasse == null) {
            throw new ErreurAuthentification("Saisir un mot de passe.");
        }
        try {
            URI base = base(hote, port);
            String corps = MAPPER.writeValueAsString(Map.of(
                    "nom", nom == null ? "" : nom,
                    "email", email == null ? "" : email,
                    "motDePasse", new String(motDePasse),
                    "role", role == null ? "" : role.name()));
            JsonNode reponse = envoyer(HttpRequest.newBuilder(base.resolve("/api/auth/inscription"))
                    .timeout(DELAI_ENVOI_EMAIL)
                    .header("Content-Type", "application/json; charset=utf-8")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(corps))
                    .build());
            String statut = texteObligatoire(reponse, "statutInscription");
            if (role == null || !role.name().equals(texteObligatoire(reponse, "role"))
                    || reponse.path("actif").asBoolean(true)
                    || (!"EN_ATTENTE".equals(statut) && !"ACCEPTE".equals(statut))) {
                throw new ErreurAuthentification(
                        "Réponse invalide : l'inscription doit rester inactive avant validation ou activation par PIN.");
            }
            return new CompteInscrit(reponse.path("id").asLong(),
                    texteObligatoire(reponse, "nom"), texteObligatoire(reponse, "email"),
                    statut, reponse.path("message").asText(""));
        } catch (ErreurAuthentification ex) {
            throw ex;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ErreurAuthentification("Inscription interrompue.", ex);
        } catch (javax.net.ssl.SSLHandshakeException ex) {
            throw new ErreurAuthentification(
                    "Certificat HTTPS du serveur non reconnu ou nom du serveur non concordant.", ex);
        } catch (IOException ex) {
            throw new ErreurAuthentification("Service HTTPS d'authentification inaccessible.", ex);
        } catch (IllegalArgumentException ex) {
            throw new ErreurAuthentification("Réponse invalide du service d'authentification.", ex);
        } finally {
            Arrays.fill(motDePasse, '\0');
        }
    }

    public static void activerCompte(String hote, int port, String email, String pin, Session.Role role) {
        try {
            String corps = MAPPER.writeValueAsString(Map.of("email", email, "pin", pin, "role", role.name()));
            envoyer(HttpRequest.newBuilder(base(hote, port).resolve("/api/auth/activation"))
                    .timeout(DELAI_REQUETE).header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(corps)).build());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ErreurAuthentification("Activation interrompue.", ex);
        } catch (IOException ex) {
            throw new ErreurAuthentification("Activation impossible : vérifier HTTPS et le serveur.", ex);
        }
    }

    public static ChallengeConnexion demanderCodeConnexion(String hote, int port, String email,
            char[] motDePasse) {
        if (motDePasse == null) {
            throw new ErreurAuthentification("Saisir le mot de passe du compte.");
        }
        try {
            URI base = base(hote, port);
            String secret = new String(motDePasse);
            String corps = MAPPER.writeValueAsString(
                    Map.of("email", email == null ? "" : email, "motDePasse", secret));
            JsonNode reponse = envoyer(HttpRequest.newBuilder(base.resolve("/api/auth/connexion"))
                    .timeout(DELAI_ENVOI_EMAIL)
                    .header("Content-Type", "application/json; charset=utf-8")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(corps))
                    .build());
            return new ChallengeConnexion(texteObligatoire(reponse, "identifiant"),
                    texteObligatoire(reponse, "message"));
        } catch (ErreurAuthentification ex) {
            throw ex;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ErreurAuthentification("Connexion au service d'authentification annulée.", ex);
        } catch (javax.net.ssl.SSLHandshakeException ex) {
            throw new ErreurAuthentification(
                    "Certificat HTTPS du serveur non reconnu ou nom du serveur non concordant.", ex);
        } catch (IOException ex) {
            throw new ErreurAuthentification("Service HTTPS d'authentification inaccessible.", ex);
        } catch (IllegalArgumentException ex) {
            throw new ErreurAuthentification("Réponse invalide du service d'authentification.", ex);
        } finally {
            Arrays.fill(motDePasse, '\0');
        }
    }

    public static ClientAuthentificationHttps confirmerCodeConnexion(String hote, int port,
            String identifiant, String code) {
        try {
            URI base = base(hote, port);
            String corps = MAPPER.writeValueAsString(Map.of(
                    "identifiant", identifiant == null ? "" : identifiant,
                    "code", code == null ? "" : code));
            JsonNode reponse = envoyer(HttpRequest.newBuilder(base.resolve("/api/auth/code"))
                    .timeout(DELAI_REQUETE)
                    .header("Content-Type", "application/json; charset=utf-8")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(corps))
                    .build());
            String jeton = texteObligatoire(reponse, "jeton");
            Compte compte = new Compte(
                    reponse.path("utilisateurId").asLong(),
                    texteObligatoire(reponse, "nom"),
                    texteObligatoire(reponse, "email"),
                    Session.Role.valueOf(texteObligatoire(reponse, "role")),
                    Instant.parse(texteObligatoire(reponse, "expireLe")));
            return new ClientAuthentificationHttps(base, jeton, compte);
        } catch (ErreurAuthentification ex) {
            throw ex;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ErreurAuthentification("Connexion au service d'authentification annulée.", ex);
        } catch (javax.net.ssl.SSLHandshakeException ex) {
            throw new ErreurAuthentification(
                    "Certificat HTTPS du serveur non reconnu ou nom du serveur non concordant.", ex);
        } catch (IOException ex) {
            throw new ErreurAuthentification("Service HTTPS d'authentification inaccessible.", ex);
        } catch (IllegalArgumentException ex) {
            throw new ErreurAuthentification("Réponse invalide du service d'authentification.", ex);
        }
    }

    public JsonNode pedagogie(JsonNode demande) {
        if (!actif()) throw new ErreurAuthentification("Session expirée.");
        try {
            return envoyer(HttpRequest.newBuilder(base.resolve("/api/pedagogie"))
                    .timeout(Duration.ofSeconds(20)).header("Authorization", "Bearer " + jeton)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(demande.toString())).build());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt(); throw new ErreurAuthentification("Opération interrompue.", ex);
        } catch (IOException ex) { throw new ErreurAuthentification("Service pédagogique inaccessible.", ex); }
    }

    /** Seul le serveur PC1 possède la clé du fournisseur. */
    public fr.ihm2026.api.ia.ReponseIa demanderIa(fr.ihm2026.api.ia.ContexteIa contexte) {
        try {
            if (!actif()) throw new ErreurAuthentification("Session expirée.");
            JsonNode reponse = envoyer(HttpRequest.newBuilder(base.resolve("/api/ia/aide"))
                    .timeout(Duration.ofSeconds(55))
                    .header("Authorization", "Bearer " + jeton)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(contexte))).build());
            return fr.ihm2026.api.ia.SortieIa.lire(reponse.toString())
                    .map(fr.ihm2026.api.ia.SortieIa::versReponse)
                    .orElseThrow(() -> new ErreurAuthentification("Réponse IA invalide."));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ErreurAuthentification("Demande IA interrompue.", ex);
        } catch (IOException ex) {
            throw new ErreurAuthentification("Service IA indisponible.", ex);
        }
    }

    public Compte compte() {
        return compte;
    }

    public List<CompteAdmin> comptesAdministrateur() {
        return comptesAdministrateur(FiltreComptesAdmin.parDefaut()).comptes();
    }

    public PageComptesAdmin comptesAdministrateur(FiltreComptesAdmin filtre) {
        verifierAdministrateur();
        var filtreEffectif = filtre == null ? FiltreComptesAdmin.parDefaut() : filtre;
        try {
            JsonNode reponse = envoyer(HttpRequest.newBuilder(
                            base.resolve("/api/admin/comptes" + requeteAdmin(filtreEffectif)))
                    .timeout(DELAI_REQUETE)
                    .header("Authorization", "Bearer " + jeton)
                    .header("Accept", "application/json")
                    .GET()
                    .build());
            List<CompteAdmin> resultat = new ArrayList<>();
            if (reponse.isArray()) {
                // Compatibilité avec un serveur PC1 pas encore mis à jour.
                for (JsonNode element : reponse) {
                    resultat.add(lireCompteAdmin(element));
                }
                long enAttente = resultat.stream()
                        .filter(compte -> "EN_ATTENTE".equals(compte.statutInscription())).count();
                long acceptes = resultat.stream()
                        .filter(compte -> "ACCEPTE".equals(compte.statutInscription())).count();
                long refuses = resultat.stream()
                        .filter(compte -> "REFUSE".equals(compte.statutInscription())).count();
                return new PageComptesAdmin(List.copyOf(resultat), resultat.size(), 1,
                        Math.max(1, resultat.size()), enAttente, acceptes, refuses);
            }
            if (reponse == null || !reponse.path("comptes").isArray()) {
                throw new ErreurAuthentification("Réponse invalide pour la liste des comptes.");
            }
            for (JsonNode element : reponse.path("comptes")) {
                resultat.add(lireCompteAdmin(element));
            }
            return new PageComptesAdmin(List.copyOf(resultat),
                    reponse.path("total").asLong(resultat.size()),
                    reponse.path("page").asInt(filtreEffectif.page()),
                    reponse.path("taille").asInt(filtreEffectif.taille()),
                    reponse.path("enAttente").asLong(),
                    reponse.path("acceptes").asLong(),
                    reponse.path("refuses").asLong());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ErreurAuthentification("Chargement des comptes interrompu.", ex);
        } catch (IOException ex) {
            throw new ErreurAuthentification("Service HTTPS d'authentification inaccessible.", ex);
        }
    }

    public InvitationInscription creerInvitation(String email, Session.Role role) {
        verifierAdministrateur();
        try {
            String corps = MAPPER.writeValueAsString(Map.of(
                    "email", email == null ? "" : email,
                    "role", role == null ? "" : role.name()));
            JsonNode reponse = envoyer(HttpRequest.newBuilder(base.resolve("/api/admin/invitations"))
                    .timeout(DELAI_ENVOI_EMAIL)
                    .header("Authorization", "Bearer " + jeton)
                    .header("Content-Type", "application/json; charset=utf-8")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(corps))
                    .build());
            return new InvitationInscription(
                    texteObligatoire(reponse, "email"),
                    Session.Role.valueOf(texteObligatoire(reponse, "role")),
                    Instant.parse(texteObligatoire(reponse, "expireLe")),
                    texteObligatoire(reponse, "message"));
        } catch (ErreurAuthentification ex) {
            throw ex;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ErreurAuthentification("Création du PIN d'inscription interrompue.", ex);
        } catch (IOException | IllegalArgumentException ex) {
            throw new ErreurAuthentification("Impossible de créer le PIN d'inscription.", ex);
        }
    }

    public CompteAdmin creerCompte(String nom, String email, char[] motDePasse,
            Session.Role role, boolean actif) {
        if (motDePasse == null) {
            throw new ErreurAuthentification("Saisir un mot de passe temporaire.");
        }
        try {
            verifierAdministrateur();
            String corps = MAPPER.writeValueAsString(Map.of(
                    "nom", nom == null ? "" : nom,
                    "email", email == null ? "" : email,
                    "motDePasse", new String(motDePasse),
                    "role", role == null ? "" : role.name(),
                    "actif", actif));
            JsonNode reponse = envoyer(HttpRequest.newBuilder(base.resolve("/api/admin/comptes"))
                    .timeout(DELAI_REQUETE)
                    .header("Authorization", "Bearer " + jeton)
                    .header("Content-Type", "application/json; charset=utf-8")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(corps))
                    .build());
            return lireCompteAdmin(reponse);
        } catch (ErreurAuthentification ex) {
            throw ex;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ErreurAuthentification("Création du compte interrompue.", ex);
        } catch (IOException ex) {
            throw new ErreurAuthentification("Service HTTPS d'authentification inaccessible.", ex);
        } finally {
            Arrays.fill(motDePasse, '\0');
        }
    }

    public CompteAdmin definirCompteActif(String email, boolean actif) {
        verifierAdministrateur();
        try {
            String corps = MAPPER.writeValueAsString(Map.of(
                    "email", email == null ? "" : email,
                    "actif", actif));
            JsonNode reponse = envoyer(HttpRequest.newBuilder(base.resolve("/api/admin/comptes/etat"))
                    .timeout(DELAI_REQUETE)
                    .header("Authorization", "Bearer " + jeton)
                    .header("Content-Type", "application/json; charset=utf-8")
                    .header("Accept", "application/json")
                    .method("PATCH", HttpRequest.BodyPublishers.ofString(corps))
                    .build());
            return lireCompteAdmin(reponse);
        } catch (ErreurAuthentification ex) {
            throw ex;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ErreurAuthentification("Modification du compte interrompue.", ex);
        } catch (IOException ex) {
            throw new ErreurAuthentification("Service HTTPS d'authentification inaccessible.", ex);
        }
    }

    public CompteAdmin deciderInscription(String email, boolean acceptee) {
        verifierAdministrateur();
        try {
            String corps = MAPPER.writeValueAsString(Map.of(
                    "email", email == null ? "" : email,
                    "acceptee", acceptee));
            JsonNode reponse = envoyer(HttpRequest.newBuilder(
                            base.resolve("/api/admin/comptes/decision"))
                    .timeout(DELAI_ENVOI_EMAIL)
                    .header("Authorization", "Bearer " + jeton)
                    .header("Content-Type", "application/json; charset=utf-8")
                    .header("Accept", "application/json")
                    .method("PATCH", HttpRequest.BodyPublishers.ofString(corps))
                    .build());
            return lireCompteAdmin(reponse);
        } catch (ErreurAuthentification ex) {
            throw ex;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ErreurAuthentification("Décision d'inscription interrompue.", ex);
        } catch (IOException ex) {
            throw new ErreurAuthentification("Service HTTPS d'authentification inaccessible.", ex);
        }
    }

    public ResultatDecisionsAdmin deciderInscriptions(List<String> emails, boolean acceptee) {
        verifierAdministrateur();
        if (emails == null || emails.isEmpty()) {
            throw new ErreurAuthentification("Sélectionnez au moins une demande à traiter.");
        }
        List<String> uniques = new ArrayList<>(new LinkedHashSet<>(emails));
        List<CompteAdmin> traites = new ArrayList<>();
        List<String> erreurs = new ArrayList<>();
        int emailsNonEnvoyes = 0;
        try {
            for (int debut = 0; debut < uniques.size(); debut += LOT_DECISIONS_MAX) {
                List<String> lot = uniques.subList(debut,
                        Math.min(uniques.size(), debut + LOT_DECISIONS_MAX));
                String corps = MAPPER.writeValueAsString(Map.of(
                        "emails", lot,
                        "acceptee", acceptee));
                JsonNode reponse = envoyer(HttpRequest.newBuilder(
                                base.resolve("/api/admin/comptes/decisions"))
                        .timeout(DELAI_DECISIONS_LOT)
                        .header("Authorization", "Bearer " + jeton)
                        .header("Content-Type", "application/json; charset=utf-8")
                        .header("Accept", "application/json")
                        .method("PATCH", HttpRequest.BodyPublishers.ofString(corps))
                        .build());
                if (reponse == null || !reponse.path("resultats").isArray()) {
                    throw new ErreurAuthentification("Réponse invalide pour le traitement par lot.");
                }
                for (JsonNode resultat : reponse.path("resultats")) {
                    String adresse = resultat.path("email").asText("");
                    String message = resultat.path("message").asText("");
                    if (resultat.path("traite").asBoolean()) {
                        JsonNode compte = resultat.get("compte");
                        if (compte != null && !compte.isNull()) {
                            traites.add(lireCompteAdmin(compte));
                        }
                        if (!resultat.path("emailEnvoye").asBoolean()) {
                            emailsNonEnvoyes++;
                        }
                    } else {
                        erreurs.add(adresse + (message.isBlank() ? "" : " : " + message));
                    }
                }
            }
            return new ResultatDecisionsAdmin(List.copyOf(traites), List.copyOf(erreurs),
                    emailsNonEnvoyes);
        } catch (ErreurAuthentification ex) {
            throw ex;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ErreurAuthentification("Décisions d'inscription par lot interrompues.", ex);
        } catch (IOException ex) {
            throw new ErreurAuthentification("Service HTTPS d'authentification inaccessible.", ex);
        }
    }

    public boolean actif() {
        return jeton != null && Instant.now().isBefore(compte.expireLe());
    }

    public static int portConfigure() {
        String valeur = System.getProperty("ihm.auth.port", "8443").strip();
        try {
            int port = Integer.parseInt(valeur);
            if (port > 0 && port < 65536) {
                return port;
            }
        } catch (NumberFormatException ex) {
            throw new ErreurAuthentification(
                    "La propriété ihm.auth.port doit être un port compris entre 1 et 65535.", ex);
        }
        throw new ErreurAuthentification(
                "La propriété ihm.auth.port doit être un port compris entre 1 et 65535.");
    }

    @Override
    public void close() {
        String jetonARevoquer = jeton;
        jeton = null;
        if (jetonARevoquer == null) {
            return;
        }
        try {
            envoyer(HttpRequest.newBuilder(base.resolve("/api/auth/deconnexion"))
                    .timeout(DELAI_REQUETE)
                    .header("Authorization", "Bearer " + jetonARevoquer)
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            LOGGER.log(Level.WARNING, "Déconnexion HTTPS interrompue ; le jeton expirera côté serveur.", ex);
        } catch (IOException | RuntimeException ex) {
            LOGGER.log(Level.WARNING, "Le serveur HTTPS n'a pas confirmé la déconnexion du compte.", ex);
        }
    }

    private static JsonNode envoyer(HttpRequest requete) throws IOException, InterruptedException {
        HttpResponse<String> reponse = HTTP.send(requete, HttpResponse.BodyHandlers.ofString());
        if ("/api/ia/aide".equals(requete.uri().getPath()) && reponse.statusCode() != 200) {
            LOGGER.warning("[ia-pc1] HTTP " + reponse.statusCode()
                    + " : consulter le service HTTPS (session, contexte, quota ou version serveur).");
        }
        JsonNode resultat;
        try {
            resultat = MAPPER.readTree(reponse.body());
        } catch (IOException ex) {
            throw new ErreurAuthentification("Réponse illisible du service d'authentification.", ex);
        }
        if (reponse.statusCode() < 200 || reponse.statusCode() >= 300) {
            JsonNode erreur = resultat == null ? null : resultat.get("erreur");
            String message = erreur != null && erreur.isTextual()
                    ? erreur.asText()
                    : "Le service d'authentification a refusé la demande (HTTP "
                            + reponse.statusCode() + ").";
            throw new ErreurAuthentification(message);
        }
        return resultat;
    }

    private static URI base(String hote, int port) {
        if (hote == null || hote.isBlank() || hote.contains("/") || hote.contains("@")
                || port < 1 || port > 65535) {
            throw new ErreurAuthentification("Adresse du service HTTPS d'authentification invalide.");
        }
        try {
            URI uri = URI.create("https://" + hote.strip() + ":" + port);
            if (uri.getHost() == null || uri.getUserInfo() != null) {
                throw new IllegalArgumentException("Hôte invalide.");
            }
            return uri;
        } catch (IllegalArgumentException ex) {
            throw new ErreurAuthentification(
                    "Adresse du service HTTPS d'authentification invalide.", ex);
        }
    }

    private static String texteObligatoire(JsonNode reponse, String champ) {
        JsonNode valeur = reponse == null ? null : reponse.get(champ);
        if (valeur == null || !valeur.isTextual() || valeur.asText().isBlank()) {
            throw new ErreurAuthentification("Réponse incomplète du service d'authentification.");
        }
        return valeur.asText();
    }

    private void verifierAdministrateur() {
        if (!actif()) {
            throw new ErreurAuthentification("La session est expirée. Reconnectez-vous.");
        }
        if (compte.role() != Session.Role.ADMINISTRATEUR) {
            throw new ErreurAuthentification("Cette action est réservée à l'administrateur.");
        }
    }

    private static String requeteAdmin(FiltreComptesAdmin filtre) {
        StringBuilder requete = new StringBuilder("?");
        ajouterParametre(requete, "recherche", filtre.recherche());
        ajouterParametre(requete, "role", filtre.role() == null ? null : filtre.role().name());
        ajouterParametre(requete, "statut", filtre.statut());
        ajouterParametre(requete, "actif", filtre.actif() == null ? null : filtre.actif().toString());
        ajouterParametre(requete, "domaine", filtre.domaine());
        ajouterParametre(requete, "page", Integer.toString(filtre.page()));
        ajouterParametre(requete, "taille", Integer.toString(filtre.taille()));
        return requete.toString();
    }

    private static void ajouterParametre(StringBuilder requete, String nom, String valeur) {
        if (valeur == null || valeur.isBlank()) {
            return;
        }
        if (requete.length() > 1) {
            requete.append('&');
        }
        requete.append(URLEncoder.encode(nom, StandardCharsets.UTF_8))
                .append('=')
                .append(URLEncoder.encode(valeur.strip(), StandardCharsets.UTF_8));
    }

    private static CompteAdmin lireCompteAdmin(JsonNode reponse) {
        if (reponse == null || !reponse.path("id").canConvertToLong()
                || !reponse.path("nom").isTextual() || !reponse.path("email").isTextual()
                || !reponse.path("role").isTextual() || !reponse.path("actif").isBoolean()
                || !reponse.path("statutInscription").isTextual()) {
            throw new ErreurAuthentification("Réponse incomplète du service de gestion des comptes.");
        }
        try {
            Instant creeLe = reponse.path("creeLe").isTextual()
                    ? Instant.parse(reponse.path("creeLe").asText())
                    : null;
            return new CompteAdmin(reponse.path("id").asLong(),
                    reponse.path("nom").asText(), reponse.path("email").asText(),
                    Session.Role.valueOf(reponse.path("role").asText()),
                    reponse.path("actif").asBoolean(),
                    reponse.path("statutInscription").asText(), creeLe,
                    reponse.path("recommandationBot").asText("NON_EVALUEE"),
                    reponse.path("motifBot").asText(""));
        } catch (IllegalArgumentException | java.time.format.DateTimeParseException ex) {
            throw new ErreurAuthentification("Compte invalide dans la réponse du service.", ex);
        }
    }

    public record Compte(long id, String nom, String email, Session.Role role, Instant expireLe) {
    }

    public record ChallengeConnexion(String identifiant, String message) {
    }

    public record CompteInscrit(long id, String nom, String email, String statutInscription,
            String message) {
    }

    public record InvitationInscription(String email, Session.Role role, Instant expireLe,
            String message) {
    }

    public record FiltreComptesAdmin(String recherche, Session.Role role, String statut, Boolean actif,
            String domaine, int page, int taille) {

        public FiltreComptesAdmin {
            recherche = texteOuNull(recherche);
            statut = texteOuNull(statut);
            domaine = texteOuNull(domaine);
            page = Math.max(1, page);
            taille = Math.max(1, Math.min(200, taille));
        }

        public static FiltreComptesAdmin parDefaut() {
            return new FiltreComptesAdmin(null, null, null, null, null, 1, 200);
        }

        private static String texteOuNull(String valeur) {
            return valeur == null || valeur.isBlank() ? null : valeur.strip();
        }
    }

    public record PageComptesAdmin(List<CompteAdmin> comptes, long total, int page, int taille,
            long enAttente, long acceptes, long refuses) {
    }

    public record ResultatDecisionsAdmin(List<CompteAdmin> traites, List<String> erreurs,
            int emailsNonEnvoyes) {
    }

    public record CompteAdmin(long id, String nom, String email, Session.Role role, boolean actif,
            String statutInscription, Instant creeLe, String recommandationBot, String motifBot) {
    }

    public static final class ErreurAuthentification extends RuntimeException {

        private static final long serialVersionUID = 1L;

        public ErreurAuthentification(String message) {
            super(message);
        }

        public ErreurAuthentification(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
