package fr.ihm2026.api.service;

import jakarta.mail.Authenticator;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.util.Objects;
import java.util.Properties;

/** Envoi des notifications sans conserver de secret SMTP dans le dépôt. */
public final class ServiceEmail {

    private final String hote;
    private final int port;
    private final String utilisateur;
    private final String motDePasse;
    private final String expediteur;

    public ServiceEmail() {
        this(System.getenv());
    }

    ServiceEmail(java.util.Map<String, String> environnement) {
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
    }

    public void envoyer(String destinataire, String sujet, String contenu) {
        Objects.requireNonNull(destinataire, "destinataire");
        Objects.requireNonNull(sujet, "sujet");
        Objects.requireNonNull(contenu, "contenu");
        if (hote.isBlank() || expediteur.isBlank()) {
            throw new IllegalStateException(
                    "L'envoi d'e-mails est indisponible : configurer IHM_SMTP_HOST et IHM_SMTP_FROM.");
        }

        Properties proprietes = new Properties();
        proprietes.put("mail.smtp.host", hote);
        proprietes.put("mail.smtp.port", Integer.toString(port));
        proprietes.put("mail.smtp.auth", Boolean.toString(!utilisateur.isBlank()));
        proprietes.put("mail.smtp.starttls.enable", "true");
        proprietes.put("mail.smtp.starttls.required", "true");
        proprietes.put("mail.smtp.connectiontimeout", "8000");
        proprietes.put("mail.smtp.timeout", "8000");
        proprietes.put("mail.smtp.writetimeout", "8000");

        Authenticator authentificateur = utilisateur.isBlank() ? null : new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(utilisateur, motDePasse);
            }
        };
        Session session = Session.getInstance(proprietes, authentificateur);
        try {
            MimeMessage message = new MimeMessage(session);
            message.setFrom(new InternetAddress(expediteur, true));
            message.setRecipient(Message.RecipientType.TO, new InternetAddress(destinataire, true));
            message.setSubject(sujet, java.nio.charset.StandardCharsets.UTF_8.name());
            message.setText(contenu, java.nio.charset.StandardCharsets.UTF_8.name());
            Transport.send(message);
        } catch (MessagingException ex) {
            throw new IllegalStateException("Impossible d'envoyer l'e-mail de notification.", ex);
        }
    }
}
