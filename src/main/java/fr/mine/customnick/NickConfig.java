package fr.mine.customnick;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Config locale : .minecraft/config/whatif.json */
public final class NickConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("whatif.json");
    /** Ancien nom du fichier (avant le renommage du mod) : relu une fois pour migrer les réglages. */
    private static final Path LEGACY_PATH = FabricLoader.getInstance().getConfigDir().resolve("customnick.json");

    // ---- Pseudo personnalisé

    /** Remplacement actif ou non. */
    public static volatile boolean enabled = true;
    /** Texte au format {@link NickFormat} : c'est lui qui est affiché. */
    public static volatile String displayName = "&bMonPseudo";
    /** Réglages du menu simple (null si le pseudo a été écrit à la main en mode avancé). */
    public static volatile NickDesign design = null;

    // ---- Dark Screen

    public static volatile boolean darkScreenEnabled = false;
    /** Opacité du voile noir, en % (1 à 80). */
    public static volatile int darkScreenOpacity = 25;
    /** true = le HUD (hotbar, vie...) est assombri lui aussi. */
    public static volatile boolean darkScreenTintHud = false;

    // ---- HUD musique (MPRIS via playerctl)

    public static volatile boolean musicHudEnabled = false;
    /** Taille en % (50 à 200). */
    public static volatile int musicHudScale = 100;
    /** Position en % de l'espace libre de l'écran. */
    public static volatile float musicHudX = 1f;
    public static volatile float musicHudY = 30f;
    /** Lecteur à suivre (ex : "firefox", "chromium", "YoutubeMusic"), vide = automatique. */
    public static volatile String musicPlayer = "";
    /** Lecteurs à ignorer (ex : "firefox" pour ne pas afficher les vidéos YouTube). */
    public static volatile List<String> musicBlacklist = List.of();
    /** Opacité du fond du HUD musique, en % (0 à 100). */
    public static volatile int musicHudOpacity = 93;

    private static final class Data {
        boolean enabled = true;
        String displayName = "&bMonPseudo";
        NickDesign design = null;

        boolean darkScreenEnabled = false;
        int darkScreenOpacity = 25;
        boolean darkScreenTintHud = false;

        boolean musicHudEnabled = false;
        int musicHudScale = 100;
        float musicHudX = 1f;
        float musicHudY = 30f;
        String musicPlayer = "";
        List<String> musicBlacklist = new ArrayList<>();
        int musicHudOpacity = 93;
    }

    private NickConfig() {}

    public static void load() {
        Path source = Files.exists(PATH) ? PATH : (Files.exists(LEGACY_PATH) ? LEGACY_PATH : null);
        if (source == null) {
            save();
            return;
        }
        try {
            Data data = GSON.fromJson(Files.readString(source), Data.class);
            if (data == null) return;
            enabled = data.enabled;
            displayName = data.displayName == null ? "" : data.displayName;
            design = data.design;

            darkScreenEnabled = data.darkScreenEnabled;
            darkScreenOpacity = Math.max(1, Math.min(80, data.darkScreenOpacity));
            darkScreenTintHud = data.darkScreenTintHud;

            musicHudEnabled = data.musicHudEnabled;
            musicHudScale = Math.max(50, Math.min(200, data.musicHudScale));
            musicHudX = Math.max(0, Math.min(100, data.musicHudX));
            musicHudY = Math.max(0, Math.min(100, data.musicHudY));
            musicHudOpacity = Math.max(0, Math.min(100, data.musicHudOpacity));
            musicPlayer = data.musicPlayer == null ? "" : data.musicPlayer;
            musicBlacklist = data.musicBlacklist == null ? List.of()
                : data.musicBlacklist.stream().filter(s -> s != null && !s.isBlank()).map(String::trim).toList();

            if (source == LEGACY_PATH) save(); // migration vers whatif.json
        } catch (Exception e) {
            CustomNickClient.LOGGER.error("Impossible de lire {}", source, e);
        }
    }

    public static void save() {
        Data data = new Data();
        data.enabled = enabled;
        data.displayName = displayName;
        data.design = design;

        data.darkScreenEnabled = darkScreenEnabled;
        data.darkScreenOpacity = darkScreenOpacity;
        data.darkScreenTintHud = darkScreenTintHud;

        data.musicHudEnabled = musicHudEnabled;
        data.musicHudScale = musicHudScale;
        data.musicHudX = musicHudX;
        data.musicHudY = musicHudY;
        data.musicHudOpacity = musicHudOpacity;
        data.musicPlayer = musicPlayer;
        data.musicBlacklist = new ArrayList<>(musicBlacklist);
        try {
            Files.writeString(PATH, GSON.toJson(data));
        } catch (IOException e) {
            CustomNickClient.LOGGER.error("Impossible d'écrire {}", PATH, e);
        }
    }
}
