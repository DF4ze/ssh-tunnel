package fr.ses10doigts.service;

import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpException;
import java.io.File;
import fr.ses10doigts.config.SshProperties;
import fr.ses10doigts.model.TunnelConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.function.Consumer;

/**
 * Service Spring gérant la session SSH et les tunnels port-forwarding.
 * Remplace SshTunnelManager + la boucle principale de SshTunnelDaemon.
 */
@Service
public class SshTunnelService {

    private static final Logger log = LoggerFactory.getLogger(SshTunnelService.class);

    private final SshProperties props;
    private Session session;
    private boolean manualDisconnect = false;

    /** Callback vers le systray/LogWindow pour les messages de log */
    private Consumer<String> logCallback;

    public SshTunnelService(SshProperties props) {
        this.props = props;
    }

    public void setLogCallback(Consumer<String> logCallback) {
        this.logCallback = logCallback;
    }

    // ─── Connexion / Déconnexion ──────────────────────────────────────────────

    public synchronized void connect() throws Exception {
        if (session != null && session.isConnected()) {
            return;
        }
        JSch jsch = new JSch();
        session = jsch.getSession(props.getUser(), props.getHost(), props.getPort());
        session.setPassword(props.getPassword());
        session.setConfig("StrictHostKeyChecking", "no");
        session.connect();

        for (TunnelConfig tunnel : props.getTunnels()) {
            session.setPortForwardingL(tunnel.getLocalPort(), tunnel.getRemoteHost(), tunnel.getRemotePort());
            log("Tunnel actif : " + tunnel);
        }
        log("Connecté à " + props.getHost() + ":" + props.getPort());
    }

    public synchronized void disconnect() {
        manualDisconnect = true;
        closeSession();
    }

    public synchronized void reconnect() {
        manualDisconnect = false;
        closeSession();
        // La prochaine itération du scheduler reconnectera
    }

    public synchronized boolean isConnected() {
        return session != null && session.isConnected();
    }

    public boolean isManualDisconnect() {
        return manualDisconnect;
    }

    public List<TunnelConfig> getTunnels() {
        return props.getTunnels();
    }

    // ─── Boucle de reconnexion automatique (@Scheduled) ───────────────────────

    /**
     * Vérifie l'état de la connexion toutes les N secondes (ssh.check-interval).
     * Reconnecte automatiquement si déconnecté et pas en mode déconnexion manuelle.
     * Utilise fixedDelayString pour lire l'intervalle depuis les propriétés.
     */
    @Scheduled(fixedDelayString = "#{sshProperties.checkInterval * 1000}")
    public void checkConnection() {
        if (!isConnected() && !manualDisconnect) {
            try {
                log("Tentative de connexion...");
                connect();
            } catch (Exception e) {
                log("Erreur de connexion : " + e.getMessage());
                closeSession();
            }
        }
    }

    // ─── Exécution de commandes SSH ───────────────────────────────────────────

    /**
     * Exécute une commande sur le serveur distant et retourne la sortie (stdout + stderr).
     *
     * @param command  La commande shell à exécuter
     * @param sudoPassword  Si non null, envoyé sur stdin (pour les commandes sudo)
     * @return La sortie combinée de la commande
     */
    public String executeCommand(String command, String sudoPassword) throws Exception {
        if (!isConnected()) {
            throw new IllegalStateException("Non connecté au serveur SSH");
        }

        ChannelExec channel = (ChannelExec) session.openChannel("exec");

        // Pour sudo sans TTY, on utilise -S pour lire le password sur stdin
        if (sudoPassword != null) {
            channel.setCommand("sudo -S " + command);
        } else {
            channel.setCommand(command);
        }

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ByteArrayOutputStream errorStream = new ByteArrayOutputStream();
        channel.setOutputStream(outputStream);
        channel.setErrStream(errorStream);

        // Récupérer stdin AVANT connect() — contrainte JSch
        OutputStream stdin = channel.getOutputStream();

        channel.connect();

        if (sudoPassword != null) {
            stdin.write((sudoPassword + "\n").getBytes());
            stdin.flush();
        }

        // Attendre la fin de la commande
        while (!channel.isClosed()) {
            Thread.sleep(100);
        }

        int exitStatus = channel.getExitStatus();
        channel.disconnect();

        String output = outputStream.toString();
        String error  = errorStream.toString();

        // Filtrer le prompt sudo du stderr
        if (sudoPassword != null && error.contains("[sudo]")) {
            error = error.replaceAll("\\[sudo\\].*?\\n", "").trim();
        }

        String combined = (output + (error.isEmpty() ? "" : "\n[stderr] " + error)).trim();
        return exitStatus != 0
                ? "⚠️ [exit: " + exitStatus + "]\n" + combined
                : combined;
    }

    /**
     * Exécute une commande simple sans sudo.
     */
    public String executeCommand(String command) throws Exception {
        return executeCommand(command, null);
    }

    // ─── Transfert de fichiers (SFTP) ────────────────────────────────────────

    /**
     * Transfère un fichier local vers le serveur distant via SFTP.
     * Réutilise la session SSH existante — pas de nouvelle connexion.
     * Crée les répertoires distants parents si nécessaire.
     *
     * @param localPath   Chemin absolu du fichier local source
     * @param remotePath  Chemin absolu de destination sur le serveur
     * @return Message de résultat structuré (✅ ou ❌)
     */
    public String uploadFile(String localPath, String remotePath) throws Exception {
        if (!isConnected()) {
            throw new IllegalStateException("Non connecté au serveur SSH");
        }

        File localFile = new File(localPath);
        if (!localFile.exists()) {
            throw new IllegalArgumentException("Fichier source introuvable : " + localPath);
        }
        if (!localFile.isFile()) {
            throw new IllegalArgumentException("Le chemin source n'est pas un fichier : " + localPath);
        }

        long fileSize  = localFile.length();
        long startTime = System.currentTimeMillis();

        ChannelSftp sftp = null;
        try {
            sftp = (ChannelSftp) session.openChannel("sftp");
            sftp.connect();

            // Créer les répertoires distants parents si nécessaire
            int lastSlash = remotePath.lastIndexOf('/');
            if (lastSlash > 0) {
                mkdirRemote(sftp, remotePath.substring(0, lastSlash));
            }

            sftp.put(localPath, remotePath, ChannelSftp.OVERWRITE);

            long   elapsed = System.currentTimeMillis() - startTime;
            double speed   = elapsed > 0 ? (fileSize / 1024.0 / 1024.0) / (elapsed / 1000.0) : 0;

            String result = String.format("✅ Upload réussi : %s → %s  (%s en %.1fs à %.1f MB/s)",
                    localFile.getName(), remotePath,
                    formatSize(fileSize), elapsed / 1000.0, speed);
            log(result);
            return result;

        } finally {
            if (sftp != null && sftp.isConnected()) {
                sftp.disconnect();
            }
        }
    }

    /**
     * Crée récursivement les répertoires distants (ignore si déjà existants).
     */
    private void mkdirRemote(ChannelSftp sftp, String remoteDir) {
        String[] parts = remoteDir.split("/");
        StringBuilder current = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) { current.append("/"); continue; }
            if (current.length() > 0 && current.charAt(current.length() - 1) != '/') {
                current.append("/");
            }
            current.append(part);
            try { sftp.mkdir(current.toString()); }
            catch (SftpException ignored) { /* répertoire déjà existant — normal */ }
        }
    }

    private String formatSize(long bytes) {
        if (bytes < 1_024L)                   return bytes + " B";
        if (bytes < 1_024L * 1_024)           return String.format("%.1f KB", bytes / 1_024.0);
        if (bytes < 1_024L * 1_024 * 1_024)   return String.format("%.1f MB", bytes / (1_024.0 * 1_024));
        return String.format("%.1f GB", bytes / (1_024.0 * 1_024 * 1_024));
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private synchronized void closeSession() {
        if (session != null && session.isConnected()) {
            session.disconnect();
            log("Connexion SSH fermée.");
        }
        session = null;
    }

    private void log(String message) {
        log.info(message);
        if (logCallback != null) {
            logCallback.accept(message);
        }
    }
}
