package fr.mine.customnick;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

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

    private static final class Data {
        boolean enabled = true;
        String displayName = "&bMonPseudo";
        NickDesign design = null;

        boolean darkScreenEnabled = false;
        int darkScreenOpacity = 25;
        boolean darkScreenTintHud = false;
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
        try {
            Files.writeString(PATH, GSON.toJson(data));
        } catch (IOException e) {
            CustomNickClient.LOGGER.error("Impossible d'écrire {}", PATH, e);
        }
    }
}
