package fr.ihm2026.api.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpsExchange;
import fr.ihm2026.api.persistence.PostgresCompteRepository;
import fr.ihm2026.api.persistence.PostgresAuthentificationService;
import fr.ihm2026.api.persistence.PostgresAuthentificationService.ResultatConnexion;
import fr.ihm2026.api.persistence.PostgresAuthentificationService.SessionAuthentifiee;
import fr.ihm2026.core.Session;
import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;
import java.util.Optional;

/** Routes d'authentification exposées uniquement par le serveur HTTPS dédié. */
public final class AuthentificationApiHttps implements HttpHandler {

    private static final int TAILLE_MAX_CORPS = 16 * 1024;
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String REFUS = "Identifiants invalides ou compte indisponible.";

    private final PostgresAuthentificationService authentification;
    private final PostgresCompteRepository comptes;
    private final ServiceEmail email;
    private final java.util.concurrent.Semaphore placeIa = new java.util.concurrent.Semaphore(1);
    private final java.util.Map<Long, Integer> quotasIa = new java.util.HashMap<>();
    private java.time.LocalDate jourIa = java.time.LocalDate.now();

    public AuthentificationApiHttps(PostgresAuthentificationService authentification,
            PostgresCompteRepository comptes) {
        this(authentification, comptes, new ServiceEmail());
    }

    public AuthentificationApiHttps(PostgresAuthentificationService authentification,
            PostgresCompteRepository comptes, ServiceEmail email) {
        this.authentification = java.util.Objects.requireNonNull(authentification, "authentification");
        this.comptes = java.util.Objects.requireNonNull(comptes, "comptes");
        this.email = java.util.Objects.requireNonNull(email, "email");
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        try {
            if (!(exchange instanceof HttpsExchange)) {
                envoyerJson(exchange, 426, erreur("HTTPS est obligatoire."));
                return;
            }
            String chemin = exchange.getRequestURI().getPath();
            switch (chemin) {
                case "/api/pedagogie" -> pedagogie(exchange);
                case "/api/ia/aide" -> aideIa(exchange);
                case "/api/auth/connexion" -> connexion(exchange);
                case "/api/auth/code" -> confirmerCodeConnexion(exchange);
                case "/api/auth/activation" -> activerCompte(exchange);
                case "/api/auth/inscription" -> inscrireApprenant(exchange);
                case "/api/auth/session" -> session(exchange);
                case "/api/auth/deconnexion" -> deconnexion(exchange);
                case "/api/admin/comptes" -> administrerComptes(exchange);
                case "/api/admin/comptes/etat" -> definirCompteActif(exchange);
                case "/api/admin/comptes/decision" -> deciderInscription(exchange);
                case "/api/admin/invitations" -> creerInvitation(exchange);
                default -> envoyerJson(exchange, 404, erreur("Route inconnue."));
            }
        } catch (fr.ihm2026.api.persistence.PostgresPedagogieRepository.AccesRefuse ex) {
            envoyerJson(exchange, 403, erreur("Accès pédagogique refusé."));
        } catch (PostgresAuthentificationService.AuthentificationRefuseeException ex) {
            envoyerJson(exchange, 401, erreur(REFUS));
        } catch (SQLException ex) {
            if (("/api/pedagogie".equals(exchange.getRequestURI().getPath()) || "/api/ia/aide".equals(exchange.getRequestURI().getPath()))
                    && ("42P01".equals(ex.getSQLState()) || "42703".equals(ex.getSQLState()))) {
                envoyerJson(exchange, 503, erreur("Mise à jour de la base nécessaire : appliquer les migrations 10 et 11 sur PC1."));
            } else if ("23505".equals(ex.getSQLState())) {
                envoyerJson(exchange, 409, erreur("Un compte utilise déjà cette adresse e-mail."));
            } else {
                envoyerJson(exchange, 503, erreur("Service d'authentification indisponible."));
            }
        } catch (IllegalStateException ex) {
            envoyerJson(exchange, 409, erreur(ex.getMessage()));
        } catch (IllegalArgumentException ex) {
            envoyerJson(exchange, 400, erreur("Requête invalide."));
        } finally {
            exchange.close();
        }
    }

    private fr.ihm2026.api.persistence.PostgresPedagogieRepository pedagogieRepository() {
        return new fr.ihm2026.api.persistence.PostgresPedagogieRepository(
                fr.ihm2026.api.persistence.PostgresConfiguration.depuisEnvironnement());
    }
    private void pedagogie(HttpExchange exchange) throws IOException, SQLException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            envoyerJson(exchange, 405, erreur("Méthode non autorisée.")); return;
        }
        var acteur = authentification.valider(jeton(exchange));
        if (acteur.isEmpty()) { envoyerJson(exchange, 401, erreur(REFUS)); return; }
        if (!typeJson(exchange.getRequestHeaders())) { envoyerJson(exchange, 415, erreur("JSON requis.")); return; }
        JsonNode demande;
        try { demande=lireCorps(exchange); }
        catch(IOException ex) { envoyerJson(exchange,400,erreur("JSON invalide.")); return; }
        envoyerJson(exchange,200,pedagogieRepository().traiter(acteur.get().utilisateurId(),acteur.get().role(),demande));
    }

    private void aideIa(HttpExchange exchange) throws IOException, SQLException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            envoyerJson(exchange, 405, erreur("Méthode non autorisée.")); return;
        }
        var acteur = authentification.valider(jeton(exchange));
        if (acteur.isEmpty()) { envoyerJson(exchange, 401, erreur(REFUS)); return; }
        if (!typeJson(exchange.getRequestHeaders())) {
            envoyerJson(exchange, 415, erreur("JSON obligatoire.")); return;
        }
        JsonNode corps;
        try { corps = lireCorps(exchange); }
        catch (IOException ex) { envoyerJson(exchange, 400, erreur("JSON invalide.")); return; }
        if (corps == null || !corps.path("question").isTextual()
                || corps.path("question").asText().isBlank() || corps.path("question").asText().length() > 2000
                || !corps.path("rangNiveau").canConvertToInt() || corps.path("rangNiveau").asInt() < 1
                || corps.path("rangNiveau").asInt() > 100 || !corps.path("langage").isTextual()
                || (corps.has("code") && !corps.path("code").isTextual())
                || corps.path("code").asText("").length() > 8000) {
            envoyerJson(exchange, 400, erreur("Contexte IA invalide.")); return;
        }
        var contexte = new fr.ihm2026.api.ia.ContexteIa(corps.path("rangNiveau").asInt(),
                Session.Langage.valueOf(corps.path("langage").asText()),
                corps.path("question").asText(), corps.path("code").asText(""));
        if (acteur.get().role() == Session.Role.APPRENANT
                && pedagogieRepository().evaluationEnCours(acteur.get().utilisateurId())) {
            envoyerJson(exchange, 200, MAPPER.readTree(fr.ihm2026.api.ia.SortieIa.enJson(
                    fr.ihm2026.api.ia.ReponseIa.refus(fr.ihm2026.api.ia.MotifRefus.TEST_EN_COURS,
                            fr.ihm2026.api.ia.ModeleSimule.VERSION_PROMPT)))); return;
        }
        if (!placeIa.tryAcquire()) { envoyerJson(exchange, 429, erreur("IA occupée, réessayez plus tard.")); return; }
        try {
            if (!jourIa.equals(java.time.LocalDate.now())) {
                quotasIa.clear(); jourIa = java.time.LocalDate.now(); AideIaApi.reinitialiserQuota();
            }
            long id = acteur.get().utilisateurId();
            if (quotasIa.getOrDefault(id, 0) >= 30) {
                envoyerJson(exchange, 429, erreur("Quota IA quotidien atteint.")); return;
            }
            quotasIa.merge(id, 1, Integer::sum);
            var sortie = fr.ihm2026.api.ia.SortieIa.enJson(AideIaApi.demander(contexte));
            envoyerJson(exchange, 200, MAPPER.readTree(sortie));
        } finally { placeIa.release(); }
    }

    private void connexion(HttpExchange exchange) throws IOException, SQLException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            envoyerJson(exchange, 405, erreur("Méthode non autorisée."));
            return;
        }
        if (!typeJson(exchange.getRequestHeaders())) {
            envoyerJson(exchange, 415, erreur("Type de contenu non pris en charge."));
            return;
        }
        JsonNode corps;
        try {
            corps = lireCorps(exchange);
        } catch (IOException ex) {
            envoyerJson(exchange, 400, erreur("Requête invalide."));
            return;
        }
        if (corps == null || !corps.isObject()) {
            envoyerJson(exchange, 400, erreur("Requête invalide."));
            return;
        }
        JsonNode email = corps.get("email");
        JsonNode motDePasse = corps.get("motDePasse");
        if (email == null || !email.isTextual() || motDePasse == null || !motDePasse.isTextual()) {
            envoyerJson(exchange, 400, erreur("Requête invalide."));
            return;
        }

        var challenge = authentification.demanderCodeConnexion(email.textValue(),
                motDePasse.textValue().toCharArray(),
                exchange.getRemoteAddress().getAddress().getHostAddress());
        try {
            this.email.envoyer(challenge.email(), "Votre code de connexion IHM 2026",
                    "Bonjour " + challenge.nom() + ",\n\n"
                            + "Votre code de connexion est : " + challenge.code()
                            + "\nIl expire dans 10 minutes. Ne le communiquez à personne.");
        } catch (RuntimeException ex) {
            authentification.annulerCodeConnexion(challenge.identifiant());
            envoyerJson(exchange, 503, erreur(ex.getMessage()));
            return;
        }
        envoyerJson(exchange, 202, new ReponseChallenge(challenge.identifiant(),
                "Un code à usage unique a été envoyé par e-mail."));
    }

    private void inscrireApprenant(HttpExchange exchange) throws IOException, SQLException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            envoyerJson(exchange, 405, erreur("Méthode non autorisée."));
            return;
        }
        if (!typeJson(exchange.getRequestHeaders())) {
            envoyerJson(exchange, 415, erreur("Type de contenu non pris en charge."));
            return;
        }
        JsonNode corps;
        try {
            corps = lireCorps(exchange);
        } catch (IOException ex) {
            envoyerJson(exchange, 400, erreur("Requête invalide."));
            return;
        }
        if (corps == null || !corps.isObject()
                || !texte(corps, "nom") || !texte(corps, "email")
                || !texte(corps, "motDePasse") || !texte(corps, "role")) {
            envoyerJson(exchange, 400, erreur("Requête invalide."));
            return;
        }
        Session.Role role = Session.Role.valueOf(corps.path("role").asText());
        var compte = comptes.inscrireCompte(corps.path("nom").asText(),
                corps.path("email").asText(), corps.path("motDePasse").asText().toCharArray(),
                role);
        envoyerJson(exchange, 201, ReponseCompte.depuis(compte));
    }

    private void activerCompte(HttpExchange exchange) throws IOException, SQLException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            envoyerJson(exchange, 405, erreur("Méthode non autorisée."));
            return;
        }
        if (!typeJson(exchange.getRequestHeaders())) {
            envoyerJson(exchange, 415, erreur("Type de contenu non pris en charge."));
            return;
        }
        JsonNode corps = lireCorps(exchange);
        if (corps == null || !corps.isObject() || !texte(corps, "email")
                || !texte(corps, "pin") || !texte(corps, "role")) {
            envoyerJson(exchange, 400, erreur("Requête invalide."));
            return;
        }
        comptes.activerCompte(corps.path("email").asText(), corps.path("pin").asText(),
                Session.Role.valueOf(corps.path("role").asText()));
        envoyerJson(exchange, 200, java.util.Map.of("message", "Compte activé. Vous pouvez vous connecter."));
    }

    private void creerInvitation(HttpExchange exchange) throws IOException, SQLException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            envoyerJson(exchange, 405, erreur("Méthode non autorisée."));
            return;
        }
        if (!typeJson(exchange.getRequestHeaders())) {
            envoyerJson(exchange, 415, erreur("Type de contenu non pris en charge."));
            return;
        }
        JsonNode corps = lireCorps(exchange);
        if (corps == null || !corps.isObject()
                || !texte(corps, "email") || !texte(corps, "role")) {
            envoyerJson(exchange, 400, erreur("Requête invalide."));
            return;
        }
        SessionAuthentifiee acteur = sessionAdministrateur(exchange);
        if (acteur == null) {
            return;
        }
        Session.Role role = Session.Role.valueOf(corps.path("role").asText());
        var invitation = comptes.creerInvitation(corps.path("email").asText(),
                role, acteur.utilisateurId());
        try {
            email.envoyer(invitation.email(), "Votre PIN d’activation IHM 2026",
                    "Bonjour,\n\n"
                            + "Votre demande a été acceptée. Activez votre compte IHM 2026.\n"
                            + "Adresse e-mail : " + invitation.email() + "\n"
                            + "Rôle autorisé : " + invitation.role().label() + "\n"
                            + "PIN d'activation : " + invitation.pin() + "\n\n"
                            + "Ce PIN est valable jusqu'au " + invitation.expireLe()
                            + ", ne peut être utilisé qu'une fois et est limité à cinq essais.\n"
                            + "Ne le communiquez à personne.");
        } catch (IllegalStateException ex) {
            envoyerJson(exchange, 503, erreur(
                    "Le PIN a été créé, mais l'e-mail n'a pas pu être envoyé : "
                            + ex.getMessage()
                            + " Corrigez la configuration SMTP puis générez une nouvelle invitation ; "
                            + "cette invitation sera alors invalidée."));
            return;
        }
        envoyerJson(exchange, 201, new ReponseInvitation(invitation.email(),
                invitation.role().name(), invitation.expireLe().toString(),
                "PIN envoyé par e-mail."));
    }

    private void confirmerCodeConnexion(HttpExchange exchange) throws IOException, SQLException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            envoyerJson(exchange, 405, erreur("Méthode non autorisée."));
            return;
        }
        if (!typeJson(exchange.getRequestHeaders())) {
            envoyerJson(exchange, 415, erreur("Type de contenu non pris en charge."));
            return;
        }
        JsonNode corps = lireCorps(exchange);
        if (corps == null || !corps.isObject()
                || !texte(corps, "identifiant") || !texte(corps, "code")) {
            envoyerJson(exchange, 400, erreur("Requête invalide."));
            return;
        }
        ResultatConnexion resultat = authentification.confirmerCodeConnexion(
                corps.path("identifiant").asText(), corps.path("code").asText());
        var session = resultat.session();
        envoyerJson(exchange, 200, new ReponseConnexion(resultat.jeton(), session.utilisateurId(),
                session.nom(), session.email(), session.role().name(), session.expireLe().toString()));
    }

    private void session(HttpExchange exchange) throws IOException, SQLException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            envoyerJson(exchange, 405, erreur("Méthode non autorisée."));
            return;
        }
        Optional<SessionAuthentifiee> session = authentification.valider(jeton(exchange));
        if (session.isEmpty()) {
            envoyerJson(exchange, 401, erreur("Session invalide ou expirée."));
            return;
        }
        SessionAuthentifiee valide = session.get();
        envoyerJson(exchange, 200, new ReponseSession(valide.utilisateurId(), valide.nom(),
                valide.email(), valide.role().name(), valide.expireLe().toString()));
    }

    private void deconnexion(HttpExchange exchange) throws IOException, SQLException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            envoyerJson(exchange, 405, erreur("Méthode non autorisée."));
            return;
        }
        String jeton = jeton(exchange);
        if (authentification.valider(jeton).isEmpty()) {
            envoyerJson(exchange, 401, erreur("Session invalide ou expirée."));
            return;
        }
        authentification.deconnecter(jeton);
        exchange.sendResponseHeaders(204, -1);
    }

    private void administrerComptes(HttpExchange exchange) throws IOException, SQLException {
        if (!verifierAdministrateur(exchange)) {
            return;
        }
        if ("GET".equals(exchange.getRequestMethod())) {
            envoyerJson(exchange, 200, comptes.lister().stream()
                    .map(ReponseCompte::depuis).toList());
            return;
        }
        if (!"POST".equals(exchange.getRequestMethod())) {
            envoyerJson(exchange, 405, erreur("Méthode non autorisée."));
            return;
        }
        if (!typeJson(exchange.getRequestHeaders())) {
            envoyerJson(exchange, 415, erreur("Type de contenu non pris en charge."));
            return;
        }
        JsonNode corps = lireCorps(exchange);
        if (corps == null || !corps.isObject()
                || !texte(corps, "nom") || !texte(corps, "email")
                || !texte(corps, "motDePasse") || !texte(corps, "role")
                || !corps.path("actif").isBoolean()) {
            envoyerJson(exchange, 400, erreur("Requête invalide."));
            return;
        }
        SessionAuthentifiee acteur = sessionAdministrateur(exchange);
        if (acteur == null) {
            return;
        }
        Session.Role role = Session.Role.valueOf(corps.path("role").asText());
        var compte = comptes.creer(corps.path("nom").asText(), corps.path("email").asText(),
                corps.path("motDePasse").asText().toCharArray(), role,
                corps.path("actif").asBoolean(), acteur.utilisateurId());
        envoyerJson(exchange, 201, ReponseCompte.depuis(compte));
    }

    private void definirCompteActif(HttpExchange exchange) throws IOException, SQLException {
        if (!verifierAdministrateur(exchange)) {
            return;
        }
        if (!"PATCH".equals(exchange.getRequestMethod())) {
            envoyerJson(exchange, 405, erreur("Méthode non autorisée."));
            return;
        }
        if (!typeJson(exchange.getRequestHeaders())) {
            envoyerJson(exchange, 415, erreur("Type de contenu non pris en charge."));
            return;
        }
        JsonNode corps = lireCorps(exchange);
        if (corps == null || !corps.isObject()
                || !texte(corps, "email") || !corps.path("actif").isBoolean()) {
            envoyerJson(exchange, 400, erreur("Requête invalide."));
            return;
        }
        SessionAuthentifiee acteur = sessionAdministrateur(exchange);
        if (acteur == null) {
            return;
        }
        var compte = comptes.definirActif(corps.path("email").asText(),
                corps.path("actif").asBoolean(), acteur.utilisateurId());
        envoyerJson(exchange, 200, ReponseCompte.depuis(compte));
    }

    private void deciderInscription(HttpExchange exchange) throws IOException, SQLException {
        if (!verifierAdministrateur(exchange)) {
            return;
        }
        if (!"PATCH".equals(exchange.getRequestMethod())) {
            envoyerJson(exchange, 405, erreur("Méthode non autorisée."));
            return;
        }
        if (!typeJson(exchange.getRequestHeaders())) {
            envoyerJson(exchange, 415, erreur("Type de contenu non pris en charge."));
            return;
        }
        JsonNode corps = lireCorps(exchange);
        if (corps == null || !corps.isObject() || !texte(corps, "email")
                || !corps.path("acceptee").isBoolean()) {
            envoyerJson(exchange, 400, erreur("Requête invalide."));
            return;
        }
        SessionAuthentifiee acteur = sessionAdministrateur(exchange);
        if (acteur == null) {
            return;
        }
        boolean acceptee = corps.path("acceptee").asBoolean();
        var resultat = comptes.deciderInscription(corps.path("email").asText(),
                acceptee, acteur.utilisateurId());
        var compte = resultat.compte();
        try {
            email.envoyer(compte.email(), "Décision sur votre compte IHM 2026",
                    "Bonjour " + compte.nom() + ",\n\nVotre demande a été "
                            + (acceptee ? "acceptée." : "refusée.")
                            + (acceptee ? "\nVotre PIN d'activation : " + resultat.activation().pin()
                                    + "\nValable 48 heures et une seule fois. Cliquez sur Activer mon compte."
                                    : "\nContactez l'administration pour plus d'informations."));
        } catch (IllegalStateException ex) {
            envoyerJson(exchange, 503, erreur("Décision enregistrée, mais e-mail non envoyé. "
                    + "Actualisez les comptes. Après acceptation, utilisez Renvoyer le PIN d'activation."));
            return;
        }
        envoyerJson(exchange, 200, ReponseCompte.depuis(compte));
    }

    private boolean verifierAdministrateur(HttpExchange exchange) throws SQLException, IOException {
        SessionAuthentifiee session = sessionAdministrateur(exchange);
        if (session == null) {
            return false;
        }
        if (session.role() != Session.Role.ADMINISTRATEUR) {
            envoyerJson(exchange, 403, erreur("Cette action est réservée à l'administrateur."));
            return false;
        }
        return true;
    }

    private SessionAuthentifiee sessionAdministrateur(HttpExchange exchange)
            throws SQLException, IOException {
        Optional<SessionAuthentifiee> session = authentification.valider(jeton(exchange));
        if (session.isEmpty()) {
            envoyerJson(exchange, 401, erreur("Session invalide ou expirée."));
            return null;
        }
        if (session.get().role() != Session.Role.ADMINISTRATEUR) {
            envoyerJson(exchange, 403, erreur("Cette action est réservée à l'administrateur."));
            return null;
        }
        return session.get();
    }

    private static boolean texte(JsonNode objet, String champ) {
        JsonNode valeur = objet.get(champ);
        return valeur != null && valeur.isTextual();
    }

    private static String jeton(HttpExchange exchange) {
        String authorization = exchange.getRequestHeaders().getFirst("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")
                || authorization.length() <= 7) {
            return "";
        }
        return authorization.substring(7).strip();
    }

    private static boolean typeJson(Headers headers) {
        String type = headers.getFirst("Content-Type");
        return type != null && type.split(";", 2)[0].strip().equalsIgnoreCase("application/json");
    }

    private static JsonNode lireCorps(HttpExchange exchange) throws IOException {
        try (InputStream corps = exchange.getRequestBody()) {
            int limite = "/api/pedagogie".equals(exchange.getRequestURI().getPath()) ? 64 * 1024 : TAILLE_MAX_CORPS;
            byte[] donnees = corps.readNBytes(limite + 1);
            if (donnees.length > limite) {
                throw new IllegalArgumentException("La requête est trop volumineuse.");
            }
            return MAPPER.readTree(donnees);
        }
    }

    private static ObjectNode erreur(String message) {
        ObjectNode resultat = MAPPER.createObjectNode();
        resultat.put("erreur", message);
        return resultat;
    }

    private static void envoyerJson(HttpExchange exchange, int status, Object reponse)
            throws IOException {
        byte[] corps = MAPPER.writeValueAsBytes(reponse);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, corps.length);
        exchange.getResponseBody().write(corps);
    }

    public record ReponseConnexion(String jeton, long utilisateurId, String nom, String email,
            String role, String expireLe) {
    }

    public record ReponseSession(long utilisateurId, String nom, String email, String role,
            String expireLe) {
    }

    public record ReponseChallenge(String identifiant, String message) {
    }

    public record ReponseInvitation(String email, String role, String expireLe, String message) {
    }

    public record ReponseCompte(long id, String nom, String email, String role, boolean actif,
            String statutInscription) {

        private static ReponseCompte depuis(PostgresCompteRepository.Compte compte) {
            return new ReponseCompte(compte.id(), compte.nom(), compte.email(),
                    compte.role().name(), compte.actif(), compte.statutInscription());
        }
    }
}
