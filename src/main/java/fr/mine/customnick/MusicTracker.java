package fr.mine.customnick;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Suit la musique en cours via MPRIS (Linux), en interrogeant `playerctl` une fois par seconde
 * sur un thread à part. Fonctionne avec YouTube Music dans Firefox / Chromium, l'appli desktop,
 * Spotify desktop... tout lecteur qui expose MPRIS.
 * Les lecteurs de la liste noire sont ignorés (--ignore-player) : le HUD passe alors au lecteur suivant.
 */
public final class MusicTracker {
    private MusicTracker() {}

    /** Morceau lu au dernier sondage. Positions et durée en microsecondes. */
    public record Track(String player, boolean playing, String title, String artist,
                        long positionUs, long lengthUs, String artUrl, long polledAtMs) {

        /** Position estimée maintenant (avance entre deux sondages si le morceau joue). */
        public long currentPositionUs() {
            long pos = positionUs;
            if (playing) pos += (System.currentTimeMillis() - polledAtMs) * 1000L;
            return lengthUs > 0 ? Math.min(pos, lengthUs) : pos;
        }
    }

    private static final String SEP = "\u001F"; // séparateur improbable dans un titre
    private static final String FORMAT = String.join(SEP,
        "{{playerName}}", "{{status}}", "{{title}}", "{{artist}}", "{{position}}", "{{mpris:length}}", "{{mpris:artUrl}}");

    private static volatile Track current = null;
    /** Message d'erreur à afficher dans le menu (ex : playerctl non installé), sinon null. */
    private static volatile String problem = null;

    /** Mis à true par l'écran de réglages : on liste alors aussi les lecteurs présents. */
    public static volatile boolean listPlayers = false;
    private static volatile List<String> availablePlayers = List.of();

    private static ScheduledExecutorService executor;

    public static Track current() {
        return current;
    }

    public static String problem() {
        return problem;
    }

    /** Noms des lecteurs détectés (sans le suffixe d'instance), ex : "firefox", "spotify". */
    public static List<String> availablePlayers() {
        return availablePlayers;
    }

    /** "firefox.instance_1_42" -> "firefox" */
    public static String baseName(String player) {
        if (player == null) return "";
        int dot = player.indexOf('.');
        return dot > 0 ? player.substring(0, dot) : player;
    }

    public static synchronized void start() {
        if (executor != null) return;
        executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "whatif-music");
            t.setDaemon(true);
            return t;
        });
        executor.scheduleWithFixedDelay(MusicTracker::poll, 0, 1, TimeUnit.SECONDS);
    }

    private static void poll() {
        try {
            if (listPlayers) pollPlayerList();

            if (!NickConfig.musicHudEnabled) {
                current = null;
                return;
            }
            pollTrack();
        } catch (IOException e) {
            // playerctl absent du système
            current = null;
            availablePlayers = List.of();
            problem = "playerctl manquant : sudo pacman -S playerctl";
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            current = null;
        }
    }

    private static void pollTrack() throws IOException, InterruptedException {
        List<String> command = new ArrayList<>(List.of("playerctl"));
        if (!NickConfig.musicPlayer.isBlank()) {
            command.add("--player=" + NickConfig.musicPlayer.trim());
        }
        if (!NickConfig.musicBlacklist.isEmpty()) {
            command.add("--ignore-player=" + String.join(",", NickConfig.musicBlacklist));
        }
        command.addAll(List.of("metadata", "--format", FORMAT));

        Result result = run(command);
        if (result == null) return; // délai dépassé
        problem = null;

        // code de sortie non nul = aucun lecteur actif (hors liste noire)
        if (result.exitCode() != 0 || result.lines().isEmpty() || result.lines().get(0).isBlank()) {
            current = null;
            return;
        }

        String[] parts = result.lines().get(0).split(SEP, -1);
        if (parts.length < 6) {
            current = null;
            return;
        }

        current = new Track(
            parts[0],
            "Playing".equalsIgnoreCase(parts[1].trim()),
            parts[2].trim(),
            parts[3].trim(),
            parseLong(parts[4]),
            parseLong(parts[5]),
            parts.length >= 7 ? parts[6].trim() : "",
            System.currentTimeMillis()
        );
    }

    private static void pollPlayerList() throws IOException, InterruptedException {
        Result result = run(List.of("playerctl", "--list-all"));
        if (result == null) return;
        problem = null;

        Set<String> names = new LinkedHashSet<>();
        if (result.exitCode() == 0) {
            for (String line : result.lines()) {
                String name = baseName(line.trim());
                if (!name.isEmpty()) names.add(name);
            }
        }
        availablePlayers = List.copyOf(names);
    }

    private record Result(int exitCode, List<String> lines) {}

    private static Result run(List<String> command) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(command).redirectErrorStream(false).start();
        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
            new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) lines.add(line);
        }
        if (!process.waitFor(2, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            return null;
        }
        return new Result(process.exitValue(), lines);
    }

    private static long parseLong(String s) {
        try {
            return Long.parseLong(s.trim());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}
