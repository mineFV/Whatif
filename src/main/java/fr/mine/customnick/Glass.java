package fr.mine.customnick;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Dessin d'éléments "verre" : rectangles arrondis, translucides, avec un fin liseré clair
 * et un reflet en haut. Tout passe par {@link Smooth}, donc les arrondis sont lissés
 * à la résolution réelle de l'écran (pas d'effet "pixel").
 */
public final class Glass {
    private Glass() {}

    /** Bleu d'accentuation utilisé pour les éléments sélectionnés. */
    public static final int ACCENT = 0x5AA9FF;

    /** Épaisseur du liseré, en pixels d'interface. */
    private static final float BORDER = 0.75f;

    public static void fillRounded(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int argb) {
        Smooth.roundedRect(g, x, y, w, h, r, argb);
    }

    public static void outlineRounded(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int argb) {
        Smooth.roundedOutline(g, x, y, w, h, r, BORDER, argb);
    }

    /** Fond translucide + reflet + liseré. */
    public static void glass(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int fill, int border) {
        Smooth.roundedRect(g, x, y, w, h, r, fill);

        // reflet : fine ligne claire sous le bord du haut
        if (h > 8) {
            float inset = Math.max(2f, r * 0.8f);
            float lineW = w - 2 * inset;
            if (lineW > 0) Smooth.roundedRect(g, x + inset, y + 1.2f, lineW, 0.6f, 0.3f, 0x2CFFFFFF);
        }

        Smooth.roundedOutline(g, x, y, w, h, r, BORDER, border);
    }

    /** Grand panneau sombre translucide (fenêtre). */
    public static void panel(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        glass(g, x, y, w, h, 12, 0x9A0E141C, 0x4CFFFFFF);
    }
}
