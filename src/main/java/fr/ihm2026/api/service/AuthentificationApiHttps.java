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
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.Optional;

/** Routes d'authentification exposées uniquement par le serveur HTTPS dédié. */
public final class AuthentificationApiHttps implements HttpHandler {

    private static final int TAILLE_MAX_CORPS = 16 * 1024;
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String REFUS = "Identifiants invalides ou compte indisponible.";
    private static final int LOT_DECISIONS_MAX = 20;

    private final PostgresAuthentificationService authentification;
    private final PostgresCompteRepository comptes;
    private final ServiceEmail email;
    private final BotInscription botInscription = BotInscription.depuisEnvironnement();
    private final java.util.concurrent.Semaphore placeIa = new java.util.concurrent.Semaphore(1);
    /**
     * Envoi du courriel de connexion : on attend sa fin au plus 10 s pour pouvoir annoncer
     * un echec SMTP, puis on repond sans attendre. Le serveur SMTP met parfois plusieurs
     * dizaines de secondes ; faire attendre la reponse HTTP declenchait des « service
     * inaccessible » cote poste, alors que l'authentification avait reussi.
     */
    private static final int ATTENTE_COURRIEL_SECONDES = 10;
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
        System.out.println("[inscription-bot] Mode du bot de tri des apprenants : "
                + botInscription.mode());
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        long debut = System.nanoTime();
        String chemin = exchange.getRequestURI() == null ? "?" : exchange.getRequestURI().getPath();
        String methode = exchange.getRequestMethod();
        String distant = exchange.getRemoteAddress() == null
                ? "?"
                : exchange.getRemoteAddress().getAddress().getHostAddress();
        try {
            if (!(exchange instanceof HttpsExchange)) {
                envoyerJson(exchange, 426, erreur("HTTPS est obligatoire."));
                return;
            }
            chemin = exchange.getRequestURI().getPath();
            switch (chemin) {
                case "/api/pedagogie" -> pedagogie(exchange);
                case "/api/ia/aide" -> aideIa(exchange);
                case "/api/auth/connexion" -> connexion(exchange);
                case "/api/auth/code" -> confirmerCodeConnexion(exchange);
                case "/api/auth/appareil-confiance/oublier" -> oublierAppareilConfiance(exchange);
                case "/api/auth/activation" -> activerCompte(exchange);
                case "/api/auth/inscription" -> inscrireApprenant(exchange);
                case "/api/auth/session" -> session(exchange);
                case "/api/auth/deconnexion" -> deconnexion(exchange);
                case "/api/admin/comptes" -> administrerComptes(exchange);
                case "/api/admin/comptes/etat" -> definirCompteActif(exchange);
                case "/api/admin/comptes/decision" -> deciderInscription(exchange);
                case "/api/admin/comptes/decisions" -> deciderInscriptions(exchange);
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
                envoyerJson(exchange, 503, erreur("Mise à jour de la base nécessaire : appliquer les migrations 10, 11 et 12 sur PC1."));
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
            long duree = java.time.Duration.ofNanos(System.nanoTime() - debut).toMillis();
            int statut = exchange.getResponseCode();
            System.out.println("[auth-https] " + methode + " " + chemin + " <- " + distant
                    + " -> " + (statut < 0 ? "sans reponse" : String.valueOf(statut))
                    + " (" + duree + " ms)");
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
        java.util.List<fr.ihm2026.api.ia.ContexteIa.Tour> historique=new java.util.ArrayList<>();
        if(corps.has("historique")) {
            var tours=corps.path("historique");
            if(!tours.isArray() || tours.size()>fr.ihm2026.api.ia.ContexteIa.TOURS_MAX) {
                envoyerJson(exchange,400,erreur("Historique IA invalide."));return;
            }
            for(var tour:tours) {
                if(!tour.path("question").isTextual() || !tour.path("reponse").isTextual()
                        || tour.path("question").asText().length()>2000 || tour.path("reponse").asText().length()>2400) {
                    envoyerJson(exchange,400,erreur("Échange IA invalide."));return;
                }
                historique.add(new fr.ihm2026.api.ia.ContexteIa.Tour(tour.path("question").asText(),tour.path("reponse").asText()));
            }
        }
        var contexte = new fr.ihm2026.api.ia.ContexteIa(corps.path("rangNiveau").asInt(),
                Session.Langage.valueOf(corps.path("langage").asText()),
                corps.path("question").asText(), corps.path("code").asText(""),historique);
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

    /**
     * Envoie un courriel sans faire attendre la réponse HTTP. Le SMTP met parfois plusieurs
     * dizaines de secondes : répondre après l'envoi déclenchait des « service inaccessible »
     * côté poste alors que l'authentification avait réussi. On attend au plus
     * {@link #ATTENTE_COURRIEL_SECONDES} pour pouvoir signaler un échec franc, puis on répond.
     *
     * @return l'échec s'il est connu à temps, sinon vide (envoi terminé ou encore en cours).
     */
    private java.util.Optional<RuntimeException> courriel(String destinataire, String sujet,
            String contenu, Runnable siEchec) {
        long debut = System.nanoTime();
        CountDownLatch fin = new CountDownLatch(1);
        AtomicReference<RuntimeException> echec = new AtomicReference<>();
        Thread.ofVirtual().name("envoi-courriel").start(() -> {
            try {
                email.envoyer(destinataire, sujet, contenu);
            } catch (RuntimeException ex) {
                echec.set(ex);
                if (siEchec != null) siEchec.run();
            } finally {
                fin.countDown();
            }
        });
        boolean termine;
        try {
            termine = fin.await(ATTENTE_COURRIEL_SECONDES, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            termine = false;
        }
        long duree = java.time.Duration.ofNanos(System.nanoTime() - debut).toMillis();
        if (!termine) {
            System.out.println("[auth-https] courriel encore en envoi apres "
                    + ATTENTE_COURRIEL_SECONDES + " s : reponse renvoyee sans attendre (destinataire non journalise).");
            return java.util.Optional.empty();
        }
        if (echec.get() != null) {
            System.err.println("[auth-https] courriel non envoye (" + duree + " ms) : "
                    + echec.get().getMessage());
            return java.util.Optional.of(echec.get());
        }
        System.out.println("[auth-https] courriel envoye en " + duree + " ms.");
        return java.util.Optional.empty();
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

        JsonNode jetonConfianceNode = corps.get("jetonConfiance");
        if (jetonConfianceNode != null && !jetonConfianceNode.isNull()
                && !jetonConfianceNode.isTextual()) {
            envoyerJson(exchange, 400, erreur("Requête invalide."));
            return;
        }
        String jetonConfiance = jetonConfianceNode == null || jetonConfianceNode.isNull()
                ? null : jetonConfianceNode.textValue();

        var debut = authentification.commencerConnexion(email.textValue(),
                motDePasse.textValue().toCharArray(),
                exchange.getRemoteAddress().getAddress().getHostAddress(), jetonConfiance);
        if (!debut.codeRequis()) {
            envoyerJson(exchange, 200, versReponseConnexion(debut.session()));
            return;
        }
        var challenge = debut.challenge();
        var echec = courriel(challenge.email(), "Votre code de connexion IHM 2026",
                "Bonjour " + challenge.nom() + ",\n\n"
                        + "Votre code de connexion est : " + challenge.code()
                        + "\nIl expire dans 10 minutes. Ne le communiquez à personne.",
                () -> authentification.annulerCodeConnexion(challenge.identifiant()));
        if (echec.isPresent()) {
            envoyerJson(exchange, 503, erreur(echec.get().getMessage()));
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
        String message = "Votre demande a été enregistrée et attend une décision de l'administration.";
        var analyseBot = botInscription.analyser(compte.email(), compte.role(),
                compte.statutInscription());
        if (analyseBot.autoAccepter()) {
            try {
                var decision = comptes.accepterInscriptionParBot(compte.email());
                compte = decision.compte();
                try {
                    envoyerEmailDecision(compte, true, decision.activation());
                    message = "Votre demande apprenant a été acceptée automatiquement. "
                            + "Consultez votre e-mail pour le PIN d'activation (48 heures), "
                            + "puis choisissez « Activer mon compte avec le PIN reçu ».";
                } catch (IllegalStateException ex) {
                    message = "Votre demande apprenant a été acceptée automatiquement, "
                            + "mais l'e-mail contenant le PIN n'a pas pu être envoyé. "
                            + "Contactez l'administration pour demander un renvoi du PIN.";
                }
            } catch (IllegalArgumentException | SQLException ex) {
                // Repli sûr : la demande reste dans la file administrateur, sans donnée personnelle au journal console.
                System.err.println("[inscription-bot] Pré-acceptation impossible ; file administrateur conservée.");
            }
        }
        envoyerJson(exchange, 201, new ReponseInscription(compte.id(), compte.nom(), compte.email(),
                compte.role().name(), compte.actif(), compte.statutInscription(), message));
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
        var echec = courriel(invitation.email(), "Votre PIN d’activation IHM 2026",
                "Bonjour,\n\n"
                        + "Votre demande a été acceptée. Activez votre compte IHM 2026.\n"
                        + "Adresse e-mail : " + invitation.email() + "\n"
                        + "Rôle autorisé : " + invitation.role().label() + "\n"
                        + "PIN d'activation : " + invitation.pin() + "\n\n"
                        + "Ce PIN est valable jusqu'au " + invitation.expireLe()
                        + ", ne peut être utilisé qu'une fois et est limité à cinq essais.\n"
                        + "Ne le communiquez à personne.",
                null);
        if (echec.isPresent()) {
            envoyerJson(exchange, 503, erreur(
                    "Le PIN a été créé, mais l'e-mail n'a pas pu être envoyé : "
                            + echec.get().getMessage()
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
                || !texte(corps, "identifiant") || !texte(corps, "code")
                || (corps.has("memoriserAppareil") && !corps.path("memoriserAppareil").isBoolean())
                || (corps.hasNonNull("libelleAppareil") && !texte(corps, "libelleAppareil"))) {
            envoyerJson(exchange, 400, erreur("Requête invalide."));
            return;
        }
        ResultatConnexion resultat = authentification.confirmerCodeConnexion(
                corps.path("identifiant").asText(), corps.path("code").asText(),
                corps.path("memoriserAppareil").asBoolean(false),
                corps.path("libelleAppareil").asText(null));
        envoyerJson(exchange, 200, versReponseConnexion(resultat));
    }

    private ReponseConnexion versReponseConnexion(ResultatConnexion resultat) {
        var session = resultat.session();
        var appareil = resultat.appareilConfiance();
        return new ReponseConnexion(resultat.jeton(), session.utilisateurId(), session.nom(),
                session.email(), session.role().name(), session.expireLe().toString(),
                appareil == null ? null : appareil.jeton(),
                appareil == null ? null : appareil.expireLe().toString());
    }

    private void oublierAppareilConfiance(HttpExchange exchange) throws IOException, SQLException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            envoyerJson(exchange, 405, erreur("Méthode non autorisée."));
            return;
        }
        if (!typeJson(exchange.getRequestHeaders())) {
            envoyerJson(exchange, 415, erreur("Type de contenu non pris en charge."));
            return;
        }
        JsonNode corps = lireCorps(exchange);
        if (corps == null || !corps.isObject() || !texte(corps, "jetonConfiance")) {
            envoyerJson(exchange, 400, erreur("Requête invalide."));
            return;
        }
        authentification.revoquerAppareilConfiance(corps.path("jetonConfiance").asText());
        envoyerJson(exchange, 200, Map.of("message", "Appareil de confiance oublié."));
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
            var page = comptes.lister(lireFiltreComptes(exchange));
            envoyerJson(exchange, 200, new ReponsePageComptes(
                    page.comptes().stream().map(this::versReponseCompte).toList(),
                    page.total(), page.page(), page.taille(), page.enAttente(), page.acceptes(),
                    page.refuses()));
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
        envoyerJson(exchange, 201, versReponseCompte(compte));
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
        envoyerJson(exchange, 200, versReponseCompte(compte));
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
            envoyerEmailDecision(compte, acceptee, resultat.activation());
        } catch (IllegalStateException ex) {
            envoyerJson(exchange, 503, erreur("Décision enregistrée, mais e-mail non envoyé. "
                    + "Actualisez les comptes. Après acceptation, utilisez Renvoyer le PIN d'activation."));
            return;
        }
        envoyerJson(exchange, 200, versReponseCompte(compte));
    }

    private void deciderInscriptions(HttpExchange exchange) throws IOException, SQLException {
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
        if (corps == null || !corps.isObject() || !corps.path("emails").isArray()
                || !corps.path("acceptee").isBoolean()) {
            envoyerJson(exchange, 400, erreur("Requête invalide."));
            return;
        }
        if (corps.path("emails").size() == 0 || corps.path("emails").size() > LOT_DECISIONS_MAX) {
            envoyerJson(exchange, 400, erreur(
                    "Un lot doit contenir entre 1 et " + LOT_DECISIONS_MAX + " demandes."));
            return;
        }
        SessionAuthentifiee acteur = sessionAdministrateur(exchange);
        if (acteur == null) {
            return;
        }
        LinkedHashSet<String> emails = new LinkedHashSet<>();
        for (JsonNode element : corps.path("emails")) {
            if (!element.isTextual() || element.asText().isBlank()) {
                envoyerJson(exchange, 400, erreur("Requête invalide."));
                return;
            }
            emails.add(element.asText().strip().toLowerCase(Locale.ROOT));
        }

        boolean acceptee = corps.path("acceptee").asBoolean();
        List<ReponseDecision> resultats = new ArrayList<>();
        for (String adresse : emails) {
            try {
                var resultat = comptes.deciderInscription(adresse, acceptee, acteur.utilisateurId());
                boolean emailEnvoye;
                String message;
                try {
                    envoyerEmailDecision(resultat.compte(), acceptee, resultat.activation());
                    emailEnvoye = true;
                    message = acceptee ? "Décision enregistrée ; PIN envoyé par e-mail."
                            : "Décision enregistrée ; e-mail envoyé.";
                } catch (IllegalStateException ex) {
                    emailEnvoye = false;
                    message = "Décision enregistrée, mais e-mail non envoyé.";
                }
                resultats.add(new ReponseDecision(adresse, true, emailEnvoye, message,
                        versReponseCompte(resultat.compte())));
            } catch (IllegalArgumentException ex) {
                resultats.add(new ReponseDecision(adresse, false, false,
                        "Demande introuvable ou déjà traitée.", null));
            } catch (SQLException ex) {
                resultats.add(new ReponseDecision(adresse, false, false,
                        "Service de comptes indisponible pour cette demande.", null));
            }
        }
        int succes = (int) resultats.stream().filter(ReponseDecision::traite).count();
        envoyerJson(exchange, 200, new ReponseDecisions(resultats, succes,
                resultats.size() - succes));
    }

    private void envoyerEmailDecision(PostgresCompteRepository.Compte compte, boolean acceptee,
            PostgresCompteRepository.Invitation activation) {
        var echec = courriel(compte.email(), "Décision sur votre compte IHM 2026",
                "Bonjour " + compte.nom() + ",\n\nVotre demande a été "
                        + (acceptee ? "acceptée." : "refusée.")
                        + (acceptee ? "\nVotre PIN d'activation : " + activation.pin()
                                + "\nValable 48 heures et une seule fois. Cliquez sur Activer mon compte."
                                : "\nContactez l'administration pour plus d'informations."),
                null);
        // Meme comportement qu'avant pour les appelants : echec = IllegalStateException.
        if (echec.isPresent()) throw echec.get();
    }

    private PostgresCompteRepository.FiltreComptes lireFiltreComptes(HttpExchange exchange) {
        Map<String, String> parametres = parametresRequete(exchange.getRequestURI().getRawQuery());
        Session.Role role = null;
        String roleBrut = parametres.get("role");
        if (roleBrut != null && !roleBrut.isBlank()) {
            role = Session.Role.valueOf(roleBrut.strip().toUpperCase(Locale.ROOT));
        }
        Boolean actif = null;
        String actifBrut = parametres.get("actif");
        if (actifBrut != null && !actifBrut.isBlank()) {
            if ("true".equalsIgnoreCase(actifBrut.strip())) {
                actif = Boolean.TRUE;
            } else if ("false".equalsIgnoreCase(actifBrut.strip())) {
                actif = Boolean.FALSE;
            } else {
                throw new IllegalArgumentException("Paramètre actif invalide.");
            }
        }
        return new PostgresCompteRepository.FiltreComptes(
                parametres.get("recherche"), role, parametres.get("statut"), actif,
                parametres.get("domaine"), entierRequete(parametres.get("page"), 1),
                entierRequete(parametres.get("taille"), 50));
    }

    private ReponseCompte versReponseCompte(PostgresCompteRepository.Compte compte) {
        var analyse = botInscription.analyser(compte.email(), compte.role(),
                compte.statutInscription());
        return new ReponseCompte(compte.id(), compte.nom(), compte.email(), compte.role().name(),
                compte.actif(), compte.statutInscription(),
                compte.creeLe() == null ? null : compte.creeLe().toString(),
                analyse.recommandation().name(), analyse.motif());
    }

    private static Map<String, String> parametresRequete(String requete) {
        Map<String, String> parametres = new LinkedHashMap<>();
        if (requete == null || requete.isBlank()) {
            return parametres;
        }
        for (String paire : requete.split("&")) {
            if (paire.isBlank()) {
                continue;
            }
            String[] morceaux = paire.split("=", 2);
            String cle = URLDecoder.decode(morceaux[0], StandardCharsets.UTF_8);
            String valeur = morceaux.length > 1
                    ? URLDecoder.decode(morceaux[1], StandardCharsets.UTF_8)
                    : "";
            parametres.put(cle, valeur);
        }
        return parametres;
    }

    private static int entierRequete(String valeur, int parDefaut) {
        if (valeur == null || valeur.isBlank()) {
            return parDefaut;
        }
        try {
            return Integer.parseInt(valeur.strip());
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Paramètre de pagination invalide.", ex);
        }
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
            int limite = ("/api/pedagogie".equals(exchange.getRequestURI().getPath()) || "/api/ia/aide".equals(exchange.getRequestURI().getPath())) ? 64 * 1024 : TAILLE_MAX_CORPS;
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
            String role, String expireLe, String jetonConfiance, String confianceExpireLe) {
    }

    public record ReponseSession(long utilisateurId, String nom, String email, String role,
            String expireLe) {
    }

    public record ReponseChallenge(String identifiant, String message) {
    }

    public record ReponseInvitation(String email, String role, String expireLe, String message) {
    }

    public record ReponseInscription(long id, String nom, String email, String role, boolean actif,
            String statutInscription, String message) {
    }

    public record ReponseCompte(long id, String nom, String email, String role, boolean actif,
            String statutInscription, String creeLe, String recommandationBot, String motifBot) {
    }

    public record ReponsePageComptes(List<ReponseCompte> comptes, long total, int page, int taille,
            long enAttente, long acceptes, long refuses) {
    }

    public record ReponseDecision(String email, boolean traite, boolean emailEnvoye, String message,
            ReponseCompte compte) {
    }

    public record ReponseDecisions(List<ReponseDecision> resultats, int succes, int echecs) {
    }
}
