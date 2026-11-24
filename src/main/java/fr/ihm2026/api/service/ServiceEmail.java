package fr.ihm2026.api.service;

import jakarta.mail.Authenticator;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

/**
 * Envoi des notifications sans conserver de secret SMTP dans le dépôt.
 *
 * <p>Deux modes de chiffrement sont pris en charge, car les hébergeurs grand public n'utilisent
 * pas tous le même :</p>
 * <ul>
 *   <li><b>STARTTLS</b> (port 587 en général) : la connexion démarre en clair puis monte en
 *       TLS ; {@code mail.smtp.starttls.required=true} refuse de continuer si le serveur ne
 *       propose pas STARTTLS.</li>
 *   <li><b>SMTPS</b> (port 465) : TLS implicite dès la connexion. Ce mode est déduit du port 465
 *       et peut être forcé avec {@code IHM_SMTP_SSL=1} (ou désactivé avec {@code IHM_SMTP_SSL=0}).</li>
 * </ul>
 *
 * <p>Un envoi qui échoue lève une {@link IllegalStateException} explicite : les codes de
 * vérification ne doivent jamais disparaître en silence.</p>
 */
public final class ServiceEmail {

    private final String hote;
    private final int port;
    private final String utilisateur;
    private final String motDePasse;
    private final String expediteur;
    private final boolean sslImplicite;

    public ServiceEmail() {
        this(System.getenv());
    }

    ServiceEmail(Map<String, String> environnement) {
        hote = environnement.getOrDefault("IHM_SMTP_HOST", "").strip();
        utilisateur = environnement.getOrDefault("IHM_SMTP_USER", "").strip();
        motDePasse = environnement.getOrDefault("IHM_SMTP_PASSWORD", "");
        expediteur = environnement.getOrDefault("IHM_SMTP_FROM", "").strip();
        try {
            port = Integer.parseInt(environnement.getOrDefault("IHM_SMTP_PORT", "587").strip());
        } catch (NumberFormatException ex) {
            throw new IllegalStateException("IHM_SMTP_PORT doit être un port valide.", ex);
        }
        if (port < 1 || port > 65535) {
            throw new IllegalStateException("IHM_SMTP_PORT doit être compris entre 1 et 65535.");
        }
        sslImplicite = sslImplicite(environnement, port);
        if (!utilisateur.isBlank() && motDePasse.isBlank()) {
            // mail.smtp.auth=true avec un mot de passe vide échoue sur le serveur SMTP, souvent
            // après plusieurs secondes et sans explication utile côté interface.
            throw new IllegalStateException("IHM_SMTP_USER est défini sans IHM_SMTP_PASSWORD : "
                    + "renseigner les deux, ou aucun des deux si le serveur SMTP n'exige pas d'authentification.");
        }
    }

    private static boolean sslImplicite(Map<String, String> environnement, int port) {
        String valeur = environnement.getOrDefault("IHM_SMTP_SSL", "").strip().toLowerCase(Locale.ROOT);
        if (valeur.isEmpty()) {
            return port == 465;
        }
        return switch (valeur) {
            case "1", "true", "oui", "yes" -> true;
            case "0", "false", "non", "no" -> false;
            default -> throw new IllegalStateException("IHM_SMTP_SSL doit valoir 1 ou 0 (true ou false).");
        };
    }

    /**
     * Description affichable au démarrage du service : jamais le mot de passe ni l'adresse
     * d'expédition complète, seulement de quoi vérifier que la configuration est cohérente.
     */
    public String resume() {
        if (hote.isBlank() || expediteur.isBlank()) {
            return "non configuré (IHM_SMTP_HOST ou IHM_SMTP_FROM absent) : "
                    + "aucun code d'activation, de connexion ou de réinitialisation ne peut être envoyé";
        }
        return hote + ":" + port
                + (sslImplicite ? " SMTPS (TLS implicite)" : " STARTTLS obligatoire")
                + (utilisateur.isBlank() ? ", sans authentification" : ", authentification activée");
    }

    /** Propriétés Jakarta Mail utilisées pour la session SMTP. */
    Properties proprietes() {
        Properties proprietes = new Properties();
        proprietes.put("mail.smtp.host", hote);
        proprietes.put("mail.smtp.port", Integer.toString(port));
        proprietes.put("mail.smtp.auth", Boolean.toString(!utilisateur.isBlank()));
        proprietes.put("mail.smtp.ssl.enable", Boolean.toString(sslImplicite));
        // En SMTPS la session est déjà chiffrée : STARTTLS n'a plus de sens et le rendre
        // obligatoire ferait échouer la négociation.
        proprietes.put("mail.smtp.starttls.enable", Boolean.toString(!sslImplicite));
        proprietes.put("mail.smtp.starttls.required", Boolean.toString(!sslImplicite));
        proprietes.put("mail.smtp.ssl.checkserveridentity", Boolean.toString(sslImplicite));
        proprietes.put("mail.smtp.connectiontimeout", "8000");
        proprietes.put("mail.smtp.timeout", "8000");
        proprietes.put("mail.smtp.writetimeout", "8000");
        return proprietes;
    }

    /** Validation locale, sans réseau et sans rechercher un compte. */
    void verifierConfiguration() {
        if (hote.isBlank() || expediteur.isBlank()) {
            throw new IllegalStateException(
                    "L'envoi d'e-mails est indisponible : configurer IHM_SMTP_HOST et IHM_SMTP_FROM.");
        }
    }

    public void envoyer(String destinataire, String sujet, String contenu) {
        Objects.requireNonNull(destinataire, "destinataire");
        Objects.requireNonNull(sujet, "sujet");
        Objects.requireNonNull(contenu, "contenu");
        verifierConfiguration();

        Authenticator authentificateur = utilisateur.isBlank() ? null : new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(utilisateur, motDePasse);
            }
        };
        Session session = Session.getInstance(proprietes(), authentificateur);
        try {
            MimeMessage message = new MimeMessage(session);
            message.setFrom(new InternetAddress(expediteur, true));
            message.setRecipient(Message.RecipientType.TO, new InternetAddress(destinataire, true));
            message.setSubject(sujet, java.nio.charset.StandardCharsets.UTF_8.name());
            message.setText(contenu, java.nio.charset.StandardCharsets.UTF_8.name());
            Transport.send(message);
        } catch (MessagingException ex) {
            // L'hôte et le port aident au diagnostic ; le mot de passe n'apparaît jamais ici.
            throw new IllegalStateException("Impossible d'envoyer l'e-mail de notification ("
                    + hote + ":" + port + ") : " + ex.getMessage(), ex);
        }
    }
}
