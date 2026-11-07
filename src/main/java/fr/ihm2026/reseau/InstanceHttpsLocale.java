package fr.ihm2026.reseau;

import java.io.IOException;
import java.nio.channels.*;
import java.nio.file.*;

/**
 * Registre local de processus, non secret, partagé avec le serveur de découverte PC1.
 *
 * <p>Le port HTTPS est éphémère : il change à chaque démarrage. Il est publié dans le dossier
 * de lancement {@code .ihm-runtime} et, en plus, dans un registre partagé par utilisateur
 * ({@code ~/.ihm2026-reseau}). Ce second emplacement évite qu'un poste lancé depuis un autre
 * dossier (raccourci, double-clic, terminal mal placé) annonce un serveur sans port HTTPS :
 * le client retomberait alors sur une valeur par défaut et la connexion échouerait.</p>
 */
public final class InstanceHttpsLocale implements AutoCloseable {
    private static final Path DOSSIER=Path.of(".ihm-runtime");
    private static final Path DOSSIER_PARTAGE=Path.of(System.getProperty("user.home","."),".ihm2026-reseau");
    private final Path dossier;
    private final Path annonce;
    private final FileChannel canal;
    private final FileLock verrou;
    public InstanceHttpsLocale() throws IOException { this(DOSSIER); }
    InstanceHttpsLocale(Path dossier) throws IOException {
        this.dossier=dossier;this.annonce=dossier.resolve("https.port");
        Files.createDirectories(dossier);
        canal=FileChannel.open(dossier.resolve("https.lock"),StandardOpenOption.CREATE,StandardOpenOption.WRITE);
        FileLock obtenu;
        try { obtenu=canal.tryLock(); } catch(OverlappingFileLockException ex) { obtenu=null; }
        verrou=obtenu;
    }
    public boolean acquise(){return verrou!=null;}
    public void publier(int port) throws IOException {
        if(!acquise())throw new IllegalStateException("Verrou HTTPS requis");
        var processus=ProcessHandle.current();
        String texte=port+" "+processus.pid()+" "+processus.info().startInstant().orElseThrow().toEpochMilli();
        ecrire(annonce,texte);
        partager(texte);
    }
    /** Port publié, quel que soit le dossier de lancement du processus qui l'a écrit. */
    public static int portActif() {
        int local=portActif(DOSSIER);
        return local>0 ? local : portActif(DOSSIER_PARTAGE);
    }
    static int portActif(Path dossier) {
        try {
            String[] champs=Files.readString(dossier.resolve("https.port")).strip().split(" ");
            if(champs.length!=3)return 0;
            int port=Integer.parseInt(champs[0]);long pid=Long.parseLong(champs[1]),debut=Long.parseLong(champs[2]);
            var processus=ProcessHandle.of(pid);
            if(port<1 || port>65535 || processus.isEmpty() || !processus.get().isAlive())return 0;
            return processus.get().info().startInstant().map(t -> t.toEpochMilli()==debut?port:0).orElse(0);
        } catch(IOException | RuntimeException ex){return 0;}
    }
    /** Emplacements inspectés, pour un message de diagnostic honnête. */
    public static String emplacements(){
        return DOSSIER.toAbsolutePath()+" et "+DOSSIER_PARTAGE.toAbsolutePath();
    }
    @Override public void close() throws IOException {
        try { if(acquise()){Files.deleteIfExists(annonce);partageSupprime();verrou.release();} }
        finally {canal.close();}
    }
    private static void ecrire(Path cible,String texte) throws IOException {
        Path temporaire=cible.resolveSibling(cible.getFileName()+".tmp");
        Files.writeString(temporaire,texte);
        Files.move(temporaire,cible,StandardCopyOption.REPLACE_EXISTING);
    }
    private void partager(String texte) {
        try { Files.createDirectories(DOSSIER_PARTAGE); ecrire(DOSSIER_PARTAGE.resolve("https.port"),texte); }
        catch(IOException ex) { /* Le registre du dossier de lancement suffit. */ }
    }
    private void partageSupprime() {
        try { Files.deleteIfExists(DOSSIER_PARTAGE.resolve("https.port")); }
        catch(IOException ex) { /* Rien à signaler : le port est validé par son processus. */ }
    }
}
