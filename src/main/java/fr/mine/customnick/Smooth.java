package fr.mine.customnick;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.joml.Matrix3x2f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Formes lissées (anticrénelées), utilisées par tout le mod.
 *
 * Minecraft dessine en "pixels d'interface", qui valent chacun 2, 3 ou 4 vrais pixels :
 * un coin arrondi tracé à ce niveau fait des marches d'escalier. Ici, on repasse temporairement
 * en vrais pixels d'écran et on calcule pour chaque pixel du bord la part couverte par la forme,
 * qui devient son opacité. Résultat : des arrondis nets, sans effet "pixel".
 *
 * Les coordonnées sont en unités du repère courant ; l'échelle (interface x zoom en cours)
 * est lue automatiquement dans la matrice de transformation.
 */
public final class Smooth {
    private Smooth() {}

    /** Vrais pixels d'écran par unité du repère courant. */
    private static float k(GuiGraphicsExtractor g) {
        Matrix3x2f m = g.pose();
        float poseScale = (float) Math.sqrt(m.m00() * m.m00() + m.m01() * m.m01());
        return Math.max(0.01f, Minecraft.getInstance().getWindow().getGuiScale() * poseScale);
    }

    // ------------------------------------------------------------------ cache des coins

    /** Coin rempli de rayon R : premier pixel plein par ligne + pixels partiels (i, j, couverture). */
    private record FillCorner(int[] fullStart, List<float[]> partial) {}

    /** Coin de contour de rayon R et d'épaisseur T : pixels (i, j, couverture). */
    private record RingCorner(List<float[]> pixels) {}

    private static final Map<Integer, FillCorner> FILL_CACHE = new HashMap<>();
    private static final Map<Long, RingCorner> RING_CACHE = new HashMap<>();

    private static FillCorner fillCorner(int r) {
        return FILL_CACHE.computeIfAbsent(r, R -> {
            int[] full = new int[R];
            List<float[]> partial = new ArrayList<>();
            for (int j = 0; j < R; j++) {
                float cy = R - (j + 0.5f);
                full[j] = R;
                for (int i = 0; i < R; i++) {
                    float cx = R - (i + 0.5f);
                    float cov = clamp(0.5f - ((float) Math.sqrt(cx * cx + cy * cy) - R));
                    if (cov >= 0.999f) {
                        full[j] = i;
                        break;
                    }
                    if (cov > 0.003f) partial.add(new float[]{i, j, cov});
                }
            }
            return new FillCorner(full, partial);
        });
    }

    private static RingCorner ringCorner(int r, int t) {
        long key = ((long) r << 32) | t;
        return RING_CACHE.computeIfAbsent(key, kk -> {
            List<float[]> pixels = new ArrayList<>();
            int inner = r - t;
            for (int j = 0; j < r; j++) {
                for (int i = 0; i < r; i++) {
                    float cx = r - (i + 0.5f);
                    float cy = r - (j + 0.5f);
                    float dist = (float) Math.sqrt(cx * cx + cy * cy);
                    float outer = clamp(0.5f - (dist - r));
                    float in = inner > 0 ? clamp(0.5f - (dist - inner)) : ((i >= t && j >= t) ? 1f : 0f);
                    float cov = outer - in;
                    if (cov > 0.003f) pixels.add(new float[]{i, j, cov});
                }
            }
            return new RingCorner(pixels);
        });
    }

    // ------------------------------------------------------------------ rectangle arrondi plein

    public static void roundedRect(GuiGraphicsExtractor g, float x, float y, float w, float h, float r, int argb) {
        if (w <= 0 || h <= 0 || (argb >>> 24) == 0) return;
        float k = k(g);
        g.pose().pushMatrix();
        g.pose().scale(1f / k, 1f / k);

        int px = Math.round(x * k);
        int py = Math.round(y * k);
        int pw = Math.round(w * k);
        int ph = Math.round(h * k);
        int pr = Math.max(0, Math.min(Math.round(r * k), Math.min(pw, ph) / 2));

        int baseAlpha = (argb >>> 24) & 0xFF;
        int rgb = argb & 0xFFFFFF;

        if (ph - 2 * pr > 0) g.fill(px, py + pr, px + pw, py + ph - pr, argb);

        if (pr > 0) {
            FillCorner c = fillCorner(pr);
            for (int j = 0; j < pr; j++) {
                int full = c.fullStart()[j];
                if (pw - 2 * full > 0) {
                    g.fill(px + full, py + j, px + pw - full, py + j + 1, argb);
                    g.fill(px + full, py + ph - 1 - j, px + pw - full, py + ph - j, argb);
                }
            }
            for (float[] p : c.partial()) {
                int i = (int) p[0];
                int j = (int) p[1];
                int col = withAlpha(rgb, Math.round(baseAlpha * p[2]));
                pixel(g, px + i, py + j, col);
                pixel(g, px + pw - 1 - i, py + j, col);
                pixel(g, px + i, py + ph - 1 - j, col);
                pixel(g, px + pw - 1 - i, py + ph - 1 - j, col);
            }
        }

        g.pose().popMatrix();
    }

    // ------------------------------------------------------------------ contour arrondi

    /** Contour d'épaisseur `thickness` (en unités du repère, au moins un vrai pixel). */
    public static void roundedOutline(GuiGraphicsExtractor g, float x, float y, float w, float h, float r,
                                      float thickness, int argb) {
        if (w <= 0 || h <= 0 || (argb >>> 24) == 0) return;
        float k = k(g);
        g.pose().pushMatrix();
        g.pose().scale(1f / k, 1f / k);

        int px = Math.round(x * k);
        int py = Math.round(y * k);
        int pw = Math.round(w * k);
        int ph = Math.round(h * k);
        int pr = Math.max(0, Math.min(Math.round(r * k), Math.min(pw, ph) / 2));
        int pt = Math.max(1, Math.round(thickness * k));
        pt = Math.min(pt, Math.min(pw, ph) / 2);

        int baseAlpha = (argb >>> 24) & 0xFF;
        int rgb = argb & 0xFFFFFF;

        // côtés droits
        if (pw - 2 * pr > 0) {
            g.fill(px + pr, py, px + pw - pr, py + pt, argb);
            g.fill(px + pr, py + ph - pt, px + pw - pr, py + ph, argb);
        }
        if (ph - 2 * pr > 0) {
            g.fill(px, py + pr, px + pt, py + ph - pr, argb);
            g.fill(px + pw - pt, py + pr, px + pw, py + ph - pr, argb);
        }

        // coins
        if (pr > 0) {
            for (float[] p : ringCorner(pr, pt).pixels()) {
                int i = (int) p[0];
                int j = (int) p[1];
                int col = withAlpha(rgb, Math.round(baseAlpha * p[2]));
                pixel(g, px + i, py + j, col);
                pixel(g, px + pw - 1 - i, py + j, col);
                pixel(g, px + i, py + ph - 1 - j, col);
                pixel(g, px + pw - 1 - i, py + ph - 1 - j, col);
            }
        }

        g.pose().popMatrix();
    }

    // ------------------------------------------------------------------ trait ondulé

    /**
     * Vague de x0 à x1 autour de la ligne cy : amplitude, longueur d'onde et épaisseur en unités du repère.
     * `phase` en radians (pour l'animer).
     */
    public static void wave(GuiGraphicsExtractor g, float x0, float x1, float cy,
                            float amplitude, float wavelength, float thickness, double phase, int argb) {
        if (x1 <= x0) return;
        float k = k(g);
        g.pose().pushMatrix();
        g.pose().scale(1f / k, 1f / k);

        int baseAlpha = (argb >>> 24) & 0xFF;
        int rgb = argb & 0xFFFFFF;
        float half = thickness * k / 2f;

        int start = Math.round(x0 * k);
        int end = Math.round(x1 * k);
        for (int px = start; px < end; px++) {
            float u = (px + 0.5f) / k - x0;
            double angle = u * 2.0 * Math.PI / wavelength - phase;
            float yc = (float) (cy + amplitude * Math.sin(angle)) * k;

            // épaisseur verticale corrigée selon la pente, pour un trait d'épaisseur constante
            double slope = amplitude * 2.0 * Math.PI / wavelength * Math.cos(angle);
            float h = half * (float) Math.sqrt(1.0 + slope * slope);

            float top = yc - h;
            float bottom = yc + h;
            int fullTop = (int) Math.ceil(top);
            int fullBottom = (int) Math.floor(bottom);

            if (fullBottom > fullTop) g.fill(px, fullTop, px + 1, fullBottom, argb);

            float covTop = fullTop - top;
            if (covTop > 0.003f) pixel(g, px, fullTop - 1, withAlpha(rgb, Math.round(baseAlpha * clamp(covTop))));
            float covBottom = bottom - fullBottom;
            if (covBottom > 0.003f) pixel(g, px, fullBottom, withAlpha(rgb, Math.round(baseAlpha * clamp(covBottom))));
        }

        g.pose().popMatrix();
    }

    // ------------------------------------------------------------------ outils

    private static void pixel(GuiGraphicsExtractor g, int x, int y, int argb) {
        g.fill(x, y, x + 1, y + 1, argb);
    }

    private static int withAlpha(int rgb, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | rgb;
    }

    private static float clamp(float v) {
        return v < 0 ? 0 : (v > 1 ? 1 : v);
    }
}
