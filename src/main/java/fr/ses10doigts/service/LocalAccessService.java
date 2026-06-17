package fr.ses10doigts.service;

import fr.ses10doigts.ui.LogWindow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;

/**
 * Service d'accès à la machine locale.
 *
 * - readFile, listDir, getSystemInfo : lecture seule, toujours disponibles
 * - execCommand, writeFile            : écriture/exécution, appelés après validation par LocalPermissionService
 */
@Service
public class LocalAccessService {

    private static final Logger log = LoggerFactory.getLogger(LocalAccessService.class);

    private static final int    MAX_FILE_SIZE_KB        = 512;
    private static final int    COMMAND_TIMEOUT_SECONDS = 30;
    private static final int    MAX_OUTPUT_CHARS        = 50_000;

    private static final java.time.format.DateTimeFormatter TIMESTAMP_FMT =
            java.time.format.DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm");

    private final boolean   isWindows =
            System.getProperty("os.name", "").toLowerCase().contains("win");
    private final LogWindow logWindow;

    public LocalAccessService(LogWindow logWindow) {
        this.logWindow = logWindow;
    }

    /** Loggue dans SLF4J ET dans la LogWindow du systray. */
    private void uiLog(String message) {
        log.info(message);
        logWindow.log(java.time.LocalDateTime.now().format(TIMESTAMP_FMT) + " [local] " + message);
    }

    // ─── Lecture ─────────────────────────────────────────────────────────────

    public String readFile(String path) throws IOException {
        Path p = Path.of(path);
        if (!Files.exists(p))      return "❌ Fichier introuvable : " + path;
        if (Files.isDirectory(p))  return "❌ C'est un répertoire — utilisez listLocalDir";

        long sizeBytes = Files.size(p);
        if (sizeBytes > (long) MAX_FILE_SIZE_KB * 1024) {
            return "⚠️ Fichier trop volumineux (" + sizeBytes / 1024 + " KB, max " + MAX_FILE_SIZE_KB + " KB).\n"
                 + "Lisez une portion via execLocalCommand (head, tail, grep…).";
        }
        try {
            return Files.readString(p, StandardCharsets.UTF_8);
        } catch (MalformedInputException e) {
            return "⚠️ Fichier binaire ou encodage non UTF-8 : " + path;
        }
    }

    public String listDir(String path) throws IOException {
        Path p = Path.of(path);
        if (!Files.exists(p))     return "❌ Répertoire introuvable : " + path;
        if (!Files.isDirectory(p)) return "❌ Ce n'est pas un répertoire";

        StringBuilder sb = new StringBuilder();
        sb.append("📁 ").append(path).append("\n\n");

        try (var stream = Files.list(p)) {
            stream.sorted().forEach(entry -> {
                boolean isDir = Files.isDirectory(entry);
                String sizeStr = "";
                if (!isDir) {
                    try { sizeStr = " (" + formatSize(Files.size(entry)) + ")"; }
                    catch (IOException ignored) {}
                }
                sb.append(isDir ? "📁 " : "📄 ")
                  .append(entry.getFileName())
                  .append(isDir ? "/" : "")
                  .append(sizeStr)
                  .append("\n");
            });
        }
        return sb.toString();
    }

    public String getSystemInfo() {
        OperatingSystemMXBean os = ManagementFactory.getOperatingSystemMXBean();
        Runtime rt = Runtime.getRuntime();

        long total = rt.totalMemory();
        long free  = rt.freeMemory();
        long used  = total - free;
        double loadAvg = os.getSystemLoadAverage();

        StringBuilder sb = new StringBuilder();
        sb.append("💻 Infos système locales\n");
        sb.append("════════════════════════\n");
        sb.append("OS        : ").append(System.getProperty("os.name")).append(" ").append(System.getProperty("os.version")).append("\n");
        sb.append("Arch      : ").append(System.getProperty("os.arch")).append("\n");
        sb.append("JVM       : Java ").append(System.getProperty("java.version")).append("\n");
        sb.append("CPU cores : ").append(os.getAvailableProcessors()).append("\n");
        sb.append("CPU load  : ").append(loadAvg >= 0 ? String.format("%.1f%%", loadAvg * 100) : "N/A (Windows)").append("\n");
        sb.append("JVM RAM   : ").append(formatSize(used)).append(" utilisés / ").append(formatSize(total)).append(" alloués\n");
        sb.append("Utilisateur : ").append(System.getProperty("user.name")).append("\n");
        sb.append("Home      : ").append(System.getProperty("user.home")).append("\n");
        sb.append("Working   : ").append(System.getProperty("user.dir")).append("\n");

        sb.append("\nDisques :\n");
        for (File root : File.listRoots()) {
            long totalDisk = root.getTotalSpace();
            long freeDisk  = root.getFreeSpace();
            if (totalDisk == 0) continue;
            int pctUsed = (int) ((totalDisk - freeDisk) * 100L / totalDisk);
            sb.append("  ").append(root.getPath())
              .append("  total=").append(formatSize(totalDisk))
              .append("  libre=").append(formatSize(freeDisk))
              .append("  (").append(pctUsed).append("% utilisé)")
              .append("\n");
        }
        return sb.toString().trim();
    }

    // ─── Exécution locale ────────────────────────────────────────────────────

    public String execCommand(String command) throws IOException, InterruptedException {
        uiLog("Exécution commande locale : " + command);

        ProcessBuilder pb = isWindows
                ? new ProcessBuilder("cmd.exe", "/c", command)
                : new ProcessBuilder("/bin/sh", "-c", command);
        pb.redirectErrorStream(true);

        Process process = pb.start();
        boolean finished = process.waitFor(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        if (!finished) {
            process.destroyForcibly();
            uiLog("⏱ Timeout commande locale : " + command);
            return "❌ Timeout : la commande n'a pas terminé en " + COMMAND_TIMEOUT_SECONDS + "s.";
        }

        byte[] raw    = process.getInputStream().readAllBytes();
        String output = new String(raw, isWindows ? "Cp1252" : "UTF-8");
        int exitCode  = process.exitValue();

        if (output.length() > MAX_OUTPUT_CHARS) {
            output = output.substring(0, MAX_OUTPUT_CHARS)
                   + "\n… [sortie tronquée à " + MAX_OUTPUT_CHARS + " caractères]";
        }

        if (exitCode != 0) {
            uiLog("⚠️ Exit code " + exitCode + " pour : " + command);
            return "⚠️ Exit code : " + exitCode + "\n" + output;
        }
        return output;
    }

    // ─── Écriture ────────────────────────────────────────────────────────────

    public String writeFile(String path, String content) throws IOException {
        Path p = Path.of(path);
        if (p.getParent() != null) Files.createDirectories(p.getParent());
        Files.writeString(p, content, StandardCharsets.UTF_8);
        uiLog("Fichier local écrit : " + path + " (" + content.length() + " caractères)");
        return "✅ Fichier écrit : " + path + " (" + content.length() + " caractères)";
    }

    // ─── Utilitaire ──────────────────────────────────────────────────────────

    private String formatSize(long bytes) {
        if (bytes < 1_024L)             return bytes + " B";
        if (bytes < 1_024L * 1_024)     return String.format("%.1f KB", bytes / 1_024.0);
        if (bytes < 1_024L * 1_024 * 1_024) return String.format("%.1f MB", bytes / (1_024.0 * 1_024));
        return String.format("%.1f GB", bytes / (1_024.0 * 1_024 * 1_024));
    }
}
