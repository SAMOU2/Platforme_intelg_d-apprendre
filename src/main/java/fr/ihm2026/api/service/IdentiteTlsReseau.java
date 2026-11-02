package fr.ihm2026.api.service;

import java.net.*;
import java.nio.file.*;
import java.security.*;
import java.security.cert.X509Certificate;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import javax.net.ssl.*;

/** CA locale approuvée explicitement : certificats SAN renouvelés sans désactiver TLS. */
public final class IdentiteTlsReseau implements AutoCloseable {
    private final Path autorite;
    private final String secretCa, secretServeur;
    @FunctionalInterface interface Adresses { Set<String> lire() throws Exception; }
    private final Adresses fournisseurAdresses;
    private final ClesTournantes cles=new ClesTournantes();
    private final SSLContext contexte;
    private final ScheduledExecutorService surveillance=Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t=new Thread(r,"certificat-reseau-PC1");t.setDaemon(true);return t;
    });
    private Set<String> dernieres=Set.of();
    private Instant renouveler=Instant.MIN;
    public IdentiteTlsReseau() throws Exception {
        this(Path.of(obligatoire("IHM_TLS_CA_KEYSTORE")).toAbsolutePath(),obligatoire("IHM_TLS_CA_PASSWORD"),
                obligatoire("IHM_TLS_PASSWORD"),IdentiteTlsReseau::adressesReseau,true);
    }
    IdentiteTlsReseau(Path autorite,String secretCa,String secretServeur,Adresses fournisseur,boolean surveiller) throws Exception {
        this.autorite=autorite;this.secretCa=secretCa;this.secretServeur=secretServeur;this.fournisseurAdresses=fournisseur;
        if(secretCa.length()<12 || secretServeur.length()<12)throw new IllegalArgumentException("Les secrets CA et TLS reseau doivent avoir au moins 12 caracteres.");
        renouvelerSiNecessaire();
        contexte=SSLContext.getInstance("TLS");contexte.init(new KeyManager[]{cles},null,null);
        if(surveiller)surveillance.scheduleWithFixedDelay(() -> {
            try {renouvelerSiNecessaire();}
            catch(Exception ex){System.err.println("[tls-reseau] Renouvellement impossible ("+ex.getClass().getSimpleName()+"). Ancien certificat conserve ; verifier CA, droits et connexion reseau.");}
        },10,10,TimeUnit.SECONDS);
    }
    public SSLContext contexte(){return contexte;}
    X509Certificate certificatCourant(){return cles.getCertificateChain(cles.chooseServerAlias("RSA",null,null))[0];}
    static Set<String> adressesReseau() throws SocketException {
        Set<String> resultat=new TreeSet<>();
        for(var carte:Collections.list(NetworkInterface.getNetworkInterfaces())) {
            String nom=(carte.getName()+" "+carte.getDisplayName()).toLowerCase(Locale.ROOT);
            if(!carte.isUp() || carte.isLoopback() || carte.isVirtual() || nom.contains("vethernet")
                    || nom.contains("docker") || nom.contains("virtualbox") || nom.contains("vmware"))continue;
            for(var ip:Collections.list(carte.getInetAddresses()))
                if(ip instanceof Inet4Address && !ip.isLoopbackAddress() && !ip.isLinkLocalAddress() && !ip.isAnyLocalAddress())resultat.add(ip.getHostAddress());
        }
        return Set.copyOf(resultat);
    }
    synchronized void renouvelerSiNecessaire() throws Exception {
        Set<String> adresses=Set.copyOf(fournisseurAdresses.lire());
        if(adresses.isEmpty())throw new IllegalStateException("Aucune adresse IPv4 reseau active sur PC1.");
        if(adresses.equals(dernieres) && Instant.now().isBefore(renouveler))return;
        KeyStore ca=charger(autorite,secretCa);
        X509Certificate racine=(X509Certificate)ca.getCertificate("autorite-ihm");
        if(racine==null || racine.getBasicConstraints()<0 || !ca.isKeyEntry("autorite-ihm"))throw new IllegalStateException("Autorite privee invalide.");
        racine.checkValidity();
        if(racine.getNotAfter().toInstant().isBefore(Instant.now().plus(Duration.ofDays(31))))throw new IllegalStateException("Autorite proche de son expiration : renouvellement manuel requis.");
        Path travail=Files.createTempDirectory(autorite.getParent(),"certificat-en-cours-");
        try {
            Path magasin=travail.resolve("serveur.p12"),demande=travail.resolve("demande.csr"),certificat=travail.resolve("serveur.crt"),publicCa=travail.resolve("autorite.crt");
            var env=Map.of("IHM_TLS_CA_PASSWORD",secretCa,"IHM_TLS_PASSWORD",secretServeur);
            String san="SAN="+String.join(",",adresses.stream().sorted().map(a -> "ip:"+a).toList());
            outil(env,"-genkeypair","-alias","serveur-reseau","-keyalg","RSA","-keysize","3072","-dname","CN=Plateforme PC1,OU=IHM,O=Plateforme,C=DZ","-validity","30","-storetype","PKCS12","-keystore",magasin.toString(),"-storepass:env","IHM_TLS_PASSWORD");
            outil(env,"-certreq","-alias","serveur-reseau","-keystore",magasin.toString(),"-storepass:env","IHM_TLS_PASSWORD","-file",demande.toString());
            outil(env,"-gencert","-alias","autorite-ihm","-keystore",autorite.toString(),"-storepass:env","IHM_TLS_CA_PASSWORD","-infile",demande.toString(),"-outfile",certificat.toString(),"-validity","30","-ext",san,"-ext","EKU=serverAuth","-ext","KU=digitalSignature,keyEncipherment","-ext","BC=ca:false");
            outil(env,"-exportcert","-rfc","-alias","autorite-ihm","-keystore",autorite.toString(),"-storepass:env","IHM_TLS_CA_PASSWORD","-file",publicCa.toString());
            outil(env,"-importcert","-noprompt","-alias","autorite-ihm","-keystore",magasin.toString(),"-storepass:env","IHM_TLS_PASSWORD","-file",publicCa.toString());
            outil(env,"-importcert","-noprompt","-alias","serveur-reseau","-keystore",magasin.toString(),"-storepass:env","IHM_TLS_PASSWORD","-file",certificat.toString());
            KeyStore nouveau=charger(magasin,secretServeur);
            X509Certificate feuille=(X509Certificate)nouveau.getCertificate("serveur-reseau");
            feuille.checkValidity();feuille.verify(racine.getPublicKey());
            char[] secret=secretServeur.toCharArray();
            try {
                KeyManagerFactory usine=KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());usine.init(nouveau,secret);
                var gestionnaire=Arrays.stream(usine.getKeyManagers()).filter(k -> k instanceof X509ExtendedKeyManager).map(k -> (X509ExtendedKeyManager)k).findFirst().orElseThrow();
                cles.installer(gestionnaire);
            } finally {Arrays.fill(secret,'\0');}
            dernieres=adresses;renouveler=Instant.now().plus(Duration.ofDays(7));
            System.out.println("[tls-reseau] Certificat actualise pour "+String.join(", ",new TreeSet<>(adresses))+". Confiance CA inchangee.");
        } finally {
            try(var fichiers=Files.list(travail)){for(Path f:fichiers.toList())Files.deleteIfExists(f);}
            Files.deleteIfExists(travail);
        }
    }
    private static KeyStore charger(Path chemin,String motDePasse) throws Exception {
        char[] secret=motDePasse.toCharArray();
        try(var in=Files.newInputStream(chemin)) {KeyStore ks=KeyStore.getInstance("PKCS12");ks.load(in,secret);return ks;}
        finally {Arrays.fill(secret,'\0');}
    }
    private static void outil(Map<String,String> env,String... arguments) throws Exception {
        Path executable=Path.of(System.getProperty("java.home"),"bin",System.getProperty("os.name").startsWith("Windows")?"keytool.exe":"keytool");
        List<String> commande=new ArrayList<>();commande.add(executable.toString());commande.addAll(List.of(arguments));
        ProcessBuilder constructeur=new ProcessBuilder(commande);constructeur.environment().putAll(env);
        constructeur.redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD);
        Process processus=constructeur.start();
        try {
            if(!processus.waitFor(60,TimeUnit.SECONDS))throw new IllegalStateException("Delai keytool depasse.");
            if(processus.exitValue()!=0)throw new IllegalStateException("Echec keytool : verifier les magasins, secrets et permissions (aucun secret affiche).");
        } finally {if(processus.isAlive())processus.destroyForcibly();}
    }
    static String obligatoire(String nom) {
        String valeur=System.getenv(nom);if(valeur==null || valeur.isBlank())throw new IllegalStateException("Variable requise : "+nom);return valeur;
    }
    /** Commande explicite, jamais exécutée automatiquement : ne remplace aucune CA existante. */
    public static void main(String[] args) throws Exception {
        if(args.length!=1 || !args[0].equals("initialiser"))throw new IllegalArgumentException("Utiliser initialiser pour creer l'autorite privee une seule fois.");
        Path ca=Path.of(obligatoire("IHM_TLS_CA_KEYSTORE")).toAbsolutePath();
        String secret=obligatoire("IHM_TLS_CA_PASSWORD");if(secret.length()<12)throw new IllegalArgumentException("Secret CA : au moins 12 caracteres.");
        Files.createDirectories(ca.getParent());
        if(Files.exists(ca))throw new IllegalStateException("Autorite existante : aucune modification. Ne pas la recreer a chaque Wi-Fi.");
        Path reservation=ca.resolveSibling(ca.getFileName()+".creation.lock");
        var verrou=Files.newByteChannel(reservation,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE);
        try(verrou) {
            outil(Map.of("IHM_TLS_CA_PASSWORD",secret),"-genkeypair","-alias","autorite-ihm","-keyalg","RSA","-keysize","3072","-dname","CN=Autorite privee Plateforme PC1,OU=IHM,O=Plateforme,C=DZ","-validity","3650","-ext","BC=ca:true,pathlen:0","-ext","KU=keyCertSign,cRLSign","-storetype","PKCS12","-keystore",ca.toString(),"-storepass:env","IHM_TLS_CA_PASSWORD");
            outil(Map.of("IHM_TLS_CA_PASSWORD",secret),"-exportcert","-rfc","-alias","autorite-ihm","-keystore",ca.toString(),"-storepass:env","IHM_TLS_CA_PASSWORD","-file",ca.resolveSibling("autorite-ihm.crt").toString());
        } finally {Files.deleteIfExists(reservation);}
        System.out.println("Autorite creee. Faire verifier et approuver uniquement autorite-ihm.crt sur chaque poste. Garder le .p12 prive sur PC1.");
    }
    @Override public void close(){surveillance.shutdownNow();}

    /** Alias versionnés : une rotation ne change pas la clé d'une négociation déjà commencée. */
    private static final class ClesTournantes extends X509ExtendedKeyManager {
        private final Map<String,X509ExtendedKeyManager> versions=new ConcurrentHashMap<>();
        private volatile String courante;
        void installer(X509ExtendedKeyManager gestionnaire){String id=UUID.randomUUID().toString();versions.put(id,gestionnaire);courante=id;}
        private String choisir(String type,java.security.Principal[] autorites,java.net.Socket socket,SSLEngine moteur){
            String id=courante;var g=versions.get(id);String alias=moteur==null?g.chooseServerAlias(type,autorites,socket):g.chooseEngineServerAlias(type,autorites,moteur);
            return alias==null?null:id+"|"+alias;
        }
        public String chooseServerAlias(String t,java.security.Principal[] a,java.net.Socket s){return choisir(t,a,s,null);}
        public String chooseEngineServerAlias(String t,java.security.Principal[] a,SSLEngine e){return choisir(t,a,null,e);}
        public X509Certificate[] getCertificateChain(String alias){if(alias==null || !alias.contains("|"))return null;String[] p=alias.split("\\|",2);var g=versions.get(p[0]);return g==null?null:g.getCertificateChain(p[1]);}
        public PrivateKey getPrivateKey(String alias){if(alias==null || !alias.contains("|"))return null;String[] p=alias.split("\\|",2);var g=versions.get(p[0]);return g==null?null:g.getPrivateKey(p[1]);}
        public String[] getServerAliases(String t,java.security.Principal[] a){String id=courante;String[] noms=versions.get(id).getServerAliases(t,a);return noms==null?null:Arrays.stream(noms).map(n -> id+"|"+n).toArray(String[]::new);}
        public String[] getClientAliases(String t,java.security.Principal[] a){return null;}
        public String chooseClientAlias(String[] t,java.security.Principal[] a,java.net.Socket s){return null;}
    }
}
