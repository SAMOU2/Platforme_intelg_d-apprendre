@echo off
REM Copier ce modele vers configuration-PC1.local.cmd dans le meme dossier.
REM Fichier CMD de confiance : ne pas executer une configuration recue d'un inconnu.
REM Ne jamais committer le fichier local ni partager ses mots de passe.
REM Pas de setlocal : les variables doivent rester visibles du lanceur appelant.
REM En CMD, un caractere pourcent literal dans un secret doit etre ecrit deux fois.

REM Valeurs de la base de demonstration docker-compose.yml, port par defaut.
REM Adapter si vous avez personnalise le port, le compte ou une base existante.
set "IHM_DB_URL=jdbc:postgresql://localhost:5433/ihm2026"
set "IHM_DB_USER=ihm2026"
REM Decommenter pour la base de demonstration uniquement :
REM set "IHM_DB_PASSWORD=ihm2026"

REM Magasin PKCS12 contenant la cle privee ET le certificat serveur.
REM Le certificat doit correspondre a l'IP/nom utilise par les clients et etre approuve.
REM Ranger le magasin dans postes\secrets ou hors du depot, jamais dans Git.
REM set "IHM_TLS_KEYSTORE=%~dp0secrets\serveur.p12"
REM set "IHM_TLS_PASSWORD=REMPLACER_PAR_VOTRE_SECRET"
set "IHM_AUTH_PORT=8443"

REM Serveur SMTP reel : STARTTLS obligatoire, port 587 par defaut.
REM set "IHM_SMTP_HOST=smtp.votre-fournisseur.example"
REM set "IHM_SMTP_FROM=adresse-expeditrice@example.org"
REM set "IHM_SMTP_USER=utilisateur-smtp"
REM set "IHM_SMTP_PASSWORD=REMPLACER_PAR_VOTRE_SECRET"
set "IHM_SMTP_PORT=587"

REM Seulement si aucun administrateur n'existe dans la base :
REM set "IHM_BOOTSTRAP_ADMIN_NAME=Votre nom"
REM set "IHM_BOOTSTRAP_ADMIN_EMAIL=votre-adresse@example.org"
REM Mot de passe : au moins 12 caracteres et au plus 72 octets UTF-8.
REM set "IHM_BOOTSTRAP_ADMIN_PASSWORD=REMPLACER_PAR_VOTRE_SECRET"
REM IA NVIDIA : uniquement dans configuration-PC1.local.cmd sur PC1.
REM Ne jamais envoyer cette cle aux postes clients ni la publier dans Git.
REM set "NVIDIA_API_KEY=REMPLACER_PAR_VOTRE_CLE_PRIVEE"
exit /b 0
