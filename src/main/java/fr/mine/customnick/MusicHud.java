package fr.mine.customnick;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/**
 * HUD "musique en cours", purement visuel (aucune commande) :
 * pochette à gauche, titre / artiste / temps, barre de progression ondulée.
 * Le fond prend la couleur moyenne de la pochette, avec un fondu quand le morceau change.
 * Toutes les formes sont lissées (voir {@link Smooth}).
 */
public final class MusicHud {
    private MusicHud() {}

    public static final int W = 200;
    public static final int H = 62;
    private static final int ART = 48;
    private static final int TEXT_X = 64;
    private static final int DEFAULT_BG = 0x2A3236;

    // couleur de fond affichée (suit la couleur cible en fondu)
    private static float curR = (DEFAULT_BG >> 16) & 0xFF;
    private static float curG = (DEFAULT_BG >> 8) & 0xFF;
    private static float curB = DEFAULT_BG & 0xFF;
    private static long lastFrame = 0;

    /** Vrai pendant qu'un écran affiche son propre aperçu du HUD : le vrai HUD ne se dessine pas en double. */
    public static volatile boolean previewActive = false;

    /** Morceau en cours, ou un exemple si rien ne joue (pour les aperçus). */
    public static MusicTracker.Track previewTrack() {
        MusicTracker.Track track = MusicTracker.current();
        if (track != null && !track.title().isEmpty()) return track;
        return new MusicTracker.Track("exemple", true, "Titre d'exemple", "Artiste",
            43_000_000L, 189_000_000L, "", System.currentTimeMillis());
    }

    public static void draw(GuiGraphicsExtractor g) {
        if (!NickConfig.musicHudEnabled || previewActive) return;
        MusicTracker.Track track = MusicTracker.current();
        if (track == null || track.title().isEmpty()) return;

        draw(g, track, NickConfig.musicHudX, NickConfig.musicHudY, NickConfig.musicHudScale);
    }

    public static void draw(GuiGraphicsExtractor g, MusicTracker.Track track, float xPercent, float yPercent, int scalePercent) {
        Font font = Minecraft.getInstance().font;
        float scale = scalePercent / 100f;
        float x = (g.guiWidth() - W * scale) * xPercent / 100f;
        float y = (g.guiHeight() - H * scale) * yPercent / 100f;

        Identifier art = AlbumArt.get(track.artUrl());
        int average = AlbumArt.averageColor(track.artUrl());

        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(scale, scale);

        // fond : couleur moyenne de la pochette, opacité réglable
        int bg = backgroundColor(average);
        int alpha = Math.round(255f * NickConfig.musicHudOpacity / 100f);
        Smooth.roundedRect(g, 0, 0, W, H, 14, (alpha << 24) | bg);

        // pochette
        int ay = (H - ART) / 2;
        if (art != null) {
            g.pose().pushMatrix();
            g.pose().translate(ay, ay);
            g.pose().scale(ART / (float) AlbumArt.SIZE, ART / (float) AlbumArt.SIZE);
            g.blit(RenderPipelines.GUI_TEXTURED, art, 0, 0, 0f, 0f, AlbumArt.SIZE, AlbumArt.SIZE, AlbumArt.SIZE, AlbumArt.SIZE);
            g.pose().popMatrix();
        } else {
            Smooth.roundedRect(g, ay, ay, ART, ART, 8, 0x26FFFFFF);
            g.pose().pushMatrix();
            g.pose().translate(ay + ART / 2f, ay + ART / 2f);
            g.pose().scale(2f, 2f);
            String note = "♪";
            g.text(font, note, -font.width(note) / 2, -4, 0xB3FFFFFF, false);
            g.pose().popMatrix();
        }

        // textes
        int textMaxW = W - TEXT_X - 10;
        g.pose().pushMatrix();
        g.pose().translate(TEXT_X, 9);
        g.pose().scale(1.2f, 1.2f);
        g.text(font, ellipsize(font, track.title(), (int) (textMaxW / 1.2f)), 0, 0, 0xFFFFFFFF, false);
        g.pose().popMatrix();

        g.text(font, ellipsize(font, track.artist(), textMaxW), TEXT_X, 23, 0xCCFFFFFF, false);

        boolean hasLength = track.lengthUs() > 0;
        long pos = track.currentPositionUs();
        if (hasLength) {
            g.text(font, formatTime(pos) + " / " + formatTime(track.lengthUs()), TEXT_X, 34, 0xB3FFFFFF, false);
        }

        // barre de progression ondulée
        float barX0 = TEXT_X;
        float barX1 = W - 10;
        float cy = 49;
        double ratio = hasLength ? Math.min(1.0, pos / (double) track.lengthUs()) : 0.0;
        float knob = barX0 + (float) ((barX1 - barX0) * ratio);

        if (knob + 2 < barX1) {
            Smooth.roundedRect(g, knob + 2, cy - 0.6f, barX1 - knob - 2, 1.2f, 0.6f, 0x66FFFFFF);
        }
        double phase = track.playing() ? (System.currentTimeMillis() % 1_000_000L) / 1000.0 * 2.0 * Math.PI * 0.5 : 0.0;
        Smooth.wave(g, barX0, knob - 2, cy, 1.5f, 16f, 1.4f, phase, 0xF2FFFFFF);
        Smooth.roundedRect(g, knob - 1.25f, cy - 5f, 2.5f, 10f, 1.25f, 0xFFFFFFFF);

        g.pose().popMatrix();
    }

    // ------------------------------------------------------------------ couleur

    /** Couleur de fond : moyenne de la pochette, assombrie si trop claire pour lire le texte blanc, en fondu. */
    private static int backgroundColor(int average) {
        int target = average < 0 ? DEFAULT_BG : readable(average);
        float tr = (target >> 16) & 0xFF;
        float tg = (target >> 8) & 0xFF;
        float tb = target & 0xFF;

        long now = System.currentTimeMillis();
        float dt = lastFrame == 0 ? 1000f : Math.min(1000f, now - lastFrame);
        lastFrame = now;
        float t = 1f - (float) Math.exp(-dt / 250f); // ~0,5 s pour passer d'une couleur à l'autre

        curR += (tr - curR) * t;
        curG += (tg - curG) * t;
        curB += (tb - curB) * t;
        return (Math.round(curR) << 16) | (Math.round(curG) << 8) | Math.round(curB);
    }

    /** Assombrit une couleur trop claire pour que le texte blanc reste lisible. */
    private static int readable(int rgb) {
        float r = (rgb >> 16) & 0xFF;
        float g = (rgb >> 8) & 0xFF;
        float b = rgb & 0xFF;
        float luminance = (0.2126f * r + 0.7152f * g + 0.0722f * b) / 255f;
        float max = 0.42f;
        if (luminance > max) {
            float f = max / luminance;
            r *= f;
            g *= f;
            b *= f;
        }
        return (Math.round(r) << 16) | (Math.round(g) << 8) | Math.round(b);
    }

    // ------------------------------------------------------------------ texte

    private static String ellipsize(Font font, String text, int maxWidth) {
        if (maxWidth <= 0) return "";
        if (font.width(text) <= maxWidth) return text;
        String dots = "…";
        int dotsW = font.width(dots);
        String s = text;
        while (!s.isEmpty() && font.width(s) + dotsW > maxWidth) s = s.substring(0, s.length() - 1);
        return s + dots;
    }

    private static String formatTime(long micros) {
        long total = Math.max(0, micros / 1_000_000L);
        long h = total / 3600;
        long m = (total % 3600) / 60;
        long s = total % 60;
        return h > 0 ? String.format("%d:%02d:%02d", h, m, s) : String.format("%d:%02d", m, s);
    }
}
