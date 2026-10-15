package fr.mine.customnick;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Dessin d'éléments "verre" : rectangles arrondis, translucides, avec un fin liseré clair
 * et un reflet en haut. Les coins sont tracés ligne par ligne (pas de textures).
 */
public final class Glass {
    private Glass() {}

    /** Bleu d'accentuation utilisé pour les éléments sélectionnés. */
    public static final int ACCENT = 0x5AA9FF;

    private static int radius(int w, int h, int r) {
        return Math.max(0, Math.min(r, Math.min(w, h) / 2));
    }

    /** Décalage horizontal (coin arrondi) d'une ligne, pour un rectangle de hauteur h et de rayon r. */
    private static int inset(int row, int h, int r) {
        int d = Math.min(row, h - 1 - row);
        if (d >= r) return 0;
        double dy = r - d - 0.5;
        return r - (int) Math.round(Math.sqrt(r * r - dy * dy));
    }

    public static void fillRounded(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int argb) {
        if (w <= 0 || h <= 0) return;
        r = radius(w, h, r);

        int row = 0;
        while (row < h) {
            int o = inset(row, h, r);
            int end = row + 1;
            while (end < h && inset(end, h, r) == o) end++; // on regroupe les lignes identiques
            g.fill(x + o, y + row, x + w - o, y + end, argb);
            row = end;
        }
    }

    public static void outlineRounded(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int argb) {
        if (w <= 2 || h <= 2) return;
        r = radius(w, h, r);

        for (int row = 0; row < h; row++) {
            int o = inset(row, h, r);
            if (row == 0 || row == h - 1) {
                g.fill(x + o, y + row, x + w - o, y + row + 1, argb);
                continue;
            }
            int i = 1 + inset(row - 1, h - 2, Math.max(r - 1, 0));
            if (i <= o) i = o + 1;
            g.fill(x + o, y + row, x + i, y + row + 1, argb);
            g.fill(x + w - i, y + row, x + w - o, y + row + 1, argb);
        }
    }

    /** Fond translucide + reflet + liseré. */
    public static void glass(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int fill, int border) {
        fillRounded(g, x, y, w, h, r, fill);
        if (h > 8) {
            int o = inset(1, h, radius(w, h, r)) + 2;
            g.fill(x + o, y + 1, x + w - o, y + 2, 0x2CFFFFFF);
        }
        outlineRounded(g, x, y, w, h, r, border);
    }

    /** Grand panneau sombre translucide (fenêtre). */
    public static void panel(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        glass(g, x, y, w, h, 10, 0x9A0E141C, 0x4CFFFFFF);
    }
}
