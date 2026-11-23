import fr.ihm2026.api.service.ServiceEmail;

/**
 * Envoi réel d'un message de test avec la configuration SMTP de PC1.
 *
 * <p>Lancé par {@code postes\tester-smtp.cmd}, jamais par l'application. Il affiche la
 * configuration lue (sans mot de passe), puis la cause exacte de l'échec si le serveur SMTP
 * refuse : c'est la même cause que celle journalisée par le service lors d'un code de
 * réinitialisation non envoyé.</p>
 */
public class TesterSmtp {

    public static void main(String[] arguments) {
        // Sans angus-mail/jakarta.mail sur le classpath, la JVM echoue en NoClassDefFoundError
        // sans explication : on precede l'envoi d'un controle explicite.
        try {
            Class.forName("jakarta.mail.MessagingException");
        } catch (ClassNotFoundException ex) {
            System.err.println("[ERREUR] La bibliotheque jakarta.mail est absente du classpath : "
                    + "le script n'a pas charge les dependances Maven.");
            System.err.println("         Relancer postes\\tester-smtp.cmd depuis la racine du projet.");
            System.exit(1);
            return;
        }
        if (arguments.length != 1 || arguments[0].isBlank()) {
            System.err.println("[ERREUR] Usage : TesterSmtp.java destinataire@exemple.org");
            System.exit(2);
        }
        ServiceEmail service;
        try {
            service = new ServiceEmail();
        } catch (RuntimeException ex) {
            System.err.println("[ERREUR] Configuration SMTP invalide : " + ex.getMessage());
            System.exit(1);
            return;
        }
        System.out.println("[smtp] " + service.resume());
        String destinataire = arguments[0].strip();
        try {
            service.envoyer(destinataire, "Test SMTP IHM 2026",
                    "Message de test envoye par postes\\tester-smtp.cmd.\n"
                            + "Si vous le recevez, les codes PIN, de connexion et de "
                            + "reinitialisation peuvent aussi partir.");
        } catch (RuntimeException ex) {
            System.err.println("[ERREUR] " + cause(ex));
            System.exit(1);
            return;
        }
        System.out.println("[smtp] message accepte pour " + destinataire);
    }

    /** Chaîne des messages de cause : la dernière est celle qui explique réellement l'échec. */
    private static String cause(Throwable erreur) {
        StringBuilder message = new StringBuilder();
        for (Throwable cause = erreur; cause != null; cause = cause.getCause()) {
            if (cause.getMessage() != null && !cause.getMessage().isBlank()) {
                if (message.length() > 0) message.append(" / ");
                message.append(cause.getMessage());
            }
            if (cause.getCause() == cause) break;
        }
        return message.length() == 0 ? erreur.getClass().getSimpleName() : message.toString();
    }
}
