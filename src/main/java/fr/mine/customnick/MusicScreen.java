package fr.mine.customnick;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Réglages du HUD musique : lecteurs à ignorer (liste noire), taille et position,
 * avec un aperçu du HUD à sa vraie place à l'écran.
 */
public class MusicScreen extends Screen {
    private final Screen parent;
    private int px, py, pw, ph;
    private int x0, inner;
    private int chipsLabelY, fieldLabelY, slidersLabelY;

    private GlassEditBox blacklistBox;
    private boolean updatingBox;
    private List<String> shownChips = List.of();

    public MusicScreen(Screen parent) {
        super(Component.literal("Musique en cours"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        MusicTracker.listPlayers = true;

        pw = Math.min(this.width - 24, 380);
        ph = Math.min(this.height - 16, 176);
        px = (this.width - pw) / 2;
        py = (this.height - ph) / 2;
        x0 = px + 12;
        inner = pw - 24;

        glass(px + pw - 12 - 70, py + 10, 70, 18, Component.literal("Retour"), btn -> this.onClose(), () -> true);

        // lecteurs détectés (+ ceux déjà ignorés) : bleu = affiché, gris = ignoré
        chipsLabelY = py + 42;
        shownChips = chipNames();
        int cx = x0;
        int cy = chipsLabelY + 11;
        for (String name : shownChips) {
            int w = this.font.width(name) + 18;
            if (cx + w > x0 + inner) { // retour à la ligne
                cx = x0;
                cy += 22;
            }
            final String n = name;
            glass(cx, cy, w, 18, Component.literal(n), btn -> toggleIgnored(n), () -> !isIgnored(n));
            cx += w + 6;
        }
        if (shownChips.isEmpty()) cy -= 4; // pas de puces : on remonte un peu

        // liste noire à la main
        fieldLabelY = cy + 26;
        blacklistBox = new GlassEditBox(this.font, x0, fieldLabelY + 11, inner, 20, Component.literal("Lecteurs ignorés"));
        blacklistBox.setMaxLength(256);
        blacklistBox.setValue(String.join(", ", NickConfig.musicBlacklist));
        blacklistBox.setResponder(text -> {
            if (updatingBox) return;
            NickConfig.musicBlacklist = parse(text);
        });
        this.addRenderableWidget(blacklistBox);

        // taille et position
        slidersLabelY = fieldLabelY + 38;
        int third = (inner - 16) / 3;
        int sy = slidersLabelY + 11;
        this.addRenderableWidget(new ValueSlider(x0, sy, third, 18, "Taille", 50, 200, "%",
            () -> NickConfig.musicHudScale, v -> NickConfig.musicHudScale = v));
        this.addRenderableWidget(new ValueSlider(x0 + third + 8, sy, third, 18, "X", 0, 100, "%",
            () -> NickConfig.musicHudX, v -> NickConfig.musicHudX = v));
        this.addRenderableWidget(new ValueSlider(x0 + 2 * (third + 8), sy, third, 18, "Y", 0, 100, "%",
            () -> NickConfig.musicHudY, v -> NickConfig.musicHudY = v));

        ph = Math.max(ph, sy + 18 + 12 - py);
    }

    private GlassButton glass(int x, int y, int w, int h, Component message, Consumer<GlassButton> press, BooleanSupplier selected) {
        return this.addRenderableWidget(new GlassButton(x, y, w, h, message, press, selected));
    }

    // ------------------------------------------------------------------ liste noire

    /** Lecteurs détectés + lecteurs ignorés (pour pouvoir les réactiver même s'ils sont fermés). */
    private static List<String> chipNames() {
        Set<String> names = new LinkedHashSet<>(MusicTracker.availablePlayers());
        names.addAll(NickConfig.musicBlacklist);
        return List.copyOf(names);
    }

    private static boolean isIgnored(String name) {
        return NickConfig.musicBlacklist.stream().anyMatch(s -> s.equalsIgnoreCase(name));
    }

    private void toggleIgnored(String name) {
        List<String> list = new ArrayList<>(NickConfig.musicBlacklist);
        if (!list.removeIf(s -> s.equalsIgnoreCase(name))) list.add(name);
        NickConfig.musicBlacklist = List.copyOf(list);

        updatingBox = true;
        blacklistBox.setValue(String.join(", ", NickConfig.musicBlacklist));
        updatingBox = false;
    }

    private static List<String> parse(String text) {
        return Arrays.stream(text.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .distinct()
            .toList();
    }

    @Override
    public void tick() {
        super.tick();
        // nouveaux lecteurs détectés : on refait les puces (sauf pendant la saisie dans le champ)
        if (!chipNames().equals(shownChips) && (blacklistBox == null || !blacklistBox.isFocused())) {
            rebuildWidgets();
        }
    }

    // ------------------------------------------------------------------ rendu

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        Glass.panel(graphics, px, py, pw, ph);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);

        graphics.text(this.font, "§lMusique en cours", x0, py + 12, 0xFFFFFFFF, true);
        graphics.text(this.font, status(), x0, py + 26, 0xFF8A93A0, true);

        graphics.text(this.font, "Lecteurs détectés : bleu = affiché, gris = ignoré", x0, chipsLabelY, 0xFFB4BCC8, true);
        if (shownChips.isEmpty()) {
            graphics.text(this.font, "Aucun lecteur ouvert pour l'instant.", x0, chipsLabelY + 11, 0xFF8A93A0, true);
        }
        graphics.text(this.font, "Lecteurs ignorés (séparés par des virgules)", x0, fieldLabelY, 0xFFB4BCC8, true);
        graphics.text(this.font, "Taille et position du HUD", x0, slidersLabelY, 0xFFB4BCC8, true);

        // aperçu du HUD à sa vraie place (morceau en cours, ou exemple)
        MusicTracker.Track track = MusicTracker.current();
        if (track == null || track.title().isEmpty()) {
            track = new MusicTracker.Track("exemple", true, "Titre d'exemple", "Artiste",
                43_000_000L, 189_000_000L, "", System.currentTimeMillis());
        }
        MusicHud.draw(graphics, track, NickConfig.musicHudX, NickConfig.musicHudY, NickConfig.musicHudScale);
    }

    private String status() {
        String text;
        if (MusicTracker.problem() != null) text = MusicTracker.problem();
        else if (!NickConfig.musicHudEnabled) text = "HUD désactivé (active-le dans le menu).";
        else if (MusicTracker.current() == null) text = "Rien en lecture (hors lecteurs ignorés).";
        else text = "Affiché : " + MusicTracker.current().title() + " · " + MusicTracker.baseName(MusicTracker.current().player());

        int maxW = inner - 80;
        if (this.font.width(text) <= maxW) return text;
        while (!text.isEmpty() && this.font.width(text + "…") > maxW) text = text.substring(0, text.length() - 1);
        return text + "…";
    }

    @Override
    public void onClose() {
        this.minecraft.setScreenAndShow(parent);
    }

    @Override
    public void removed() {
        MusicTracker.listPlayers = false;
        NickConfig.save();
    }
}
