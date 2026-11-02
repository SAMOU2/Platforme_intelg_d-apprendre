package fr.ihm2026.reseau;

import java.io.IOException;
import java.nio.channels.*;
import java.nio.file.*;

/** Registre local de processus, non secret, partagé avec le serveur de découverte PC1. */
public final class InstanceHttpsLocale implements AutoCloseable {
    private static final Path DOSSIER=Path.of(".ihm-runtime");
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
        Path temporaire=dossier.resolve("https.port.tmp");Files.writeString(temporaire,texte);
        Files.move(temporaire,annonce,StandardCopyOption.REPLACE_EXISTING);
    }
    public static int portActif() { return portActif(DOSSIER); }
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
    @Override public void close() throws IOException {
        try { if(acquise()){Files.deleteIfExists(annonce);verrou.release();} }
        finally {canal.close();}
    }
}
