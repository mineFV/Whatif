package fr.mine.customnick;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2f;

import java.util.HashMap;
import java.util.Map;

/**
 * Formes lissées (anticrénelées), utilisées par tout le mod.
 *
 * Minecraft dessine en "pixels d'interface", qui valent chacun 2, 3 ou 4 vrais pixels : un coin arrondi
 * tracé à ce niveau fait des marches d'escalier. Ici on dessine en vrais pixels d'écran.
 *
 * Pour rester rapide (le menu affiche des dizaines d'éléments), les coins ne sont PAS calculés pixel
 * par pixel à chaque image : pour chaque rayon (en vrais pixels), un disque ou un anneau anticrénelé
 * est calculé UNE fois dans une petite texture blanche, puis ses quarts sont dessinés aux 4 coins,
 * teintés dans la bonne couleur. Un rectangle arrondi = 4 textures + 3 aplats.
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

    // ------------------------------------------------------------------ textures de coins (cache)

    private static final Map<Integer, Identifier> DISKS = new HashMap<>();
    private static final Map<Long, Identifier> RINGS = new HashMap<>();

    /** Disque blanc anticrénelé de rayon r (texture 2r x 2r). */
    private static Identifier disk(int r) {
        return DISKS.computeIfAbsent(r, R -> {
            int size = 2 * R;
            NativeImage img = new NativeImage(size, size, false);
            for (int y = 0; y < size; y++) {
                for (int x = 0; x < size; x++) {
                    float dx = R - (x + 0.5f);
                    float dy = R - (y + 0.5f);
                    float cov = clamp(0.5f - ((float) Math.sqrt(dx * dx + dy * dy) - R));
                    img.setPixel(x, y, alpha(cov));
                }
            }
            return register("smooth/disk_" + R, img);
        });
    }

    /** Anneau blanc anticrénelé de rayon extérieur r et d'épaisseur t (texture 2r x 2r). */
    private static Identifier ring(int r, int t) {
        long key = ((long) r << 32) | t;
        return RINGS.computeIfAbsent(key, kk -> {
            int size = 2 * r;
            int inner = r - t;
            NativeImage img = new NativeImage(size, size, false);
            for (int y = 0; y < size; y++) {
                for (int x = 0; x < size; x++) {
                    float dx = r - (x + 0.5f);
                    float dy = r - (y + 0.5f);
                    float dist = (float) Math.sqrt(dx * dx + dy * dy);
                    float outer = clamp(0.5f - (dist - r));
                    float in;
                    if (inner > 0) in = clamp(0.5f - (dist - inner));
                    else in = (x >= t && x < size - t && y >= t && y < size - t) ? 1f : 0f;
                    img.setPixel(x, y, alpha(outer - in));
                }
            }
            return register("smooth/ring_" + r + "_" + t, img);
        });
    }

    private static Identifier register(String path, NativeImage img) {
        Identifier id = Identifier.fromNamespaceAndPath("whatif", path);
        Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(() -> "whatif " + path, img));
        return id;
    }

    private static int alpha(float cov) {
        int a = Math.round(clamp(cov) * 255f);
        return (a << 24) | 0xFFFFFF;
    }

    /** Dessine les 4 quarts d'une texture 2r x 2r aux coins du rectangle (px, py, pw, ph). */
    private static void corners(GuiGraphicsExtractor g, Identifier tex, int px, int py, int pw, int ph, int r, int argb) {
        int size = 2 * r;
        g.blit(RenderPipelines.GUI_TEXTURED, tex, px, py, 0, 0, r, r, size, size, argb);
        g.blit(RenderPipelines.GUI_TEXTURED, tex, px + pw - r, py, r, 0, r, r, size, size, argb);
        g.blit(RenderPipelines.GUI_TEXTURED, tex, px, py + ph - r, 0, r, r, r, size, size, argb);
        g.blit(RenderPipelines.GUI_TEXTURED, tex, px + pw - r, py + ph - r, r, r, r, r, size, size, argb);
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

        if (pr == 0) {
            g.fill(px, py, px + pw, py + ph, argb);
        } else {
            corners(g, disk(pr), px, py, pw, ph, pr, argb);
            if (pw - 2 * pr > 0) {
                g.fill(px + pr, py, px + pw - pr, py + pr, argb);               // bande du haut
                g.fill(px + pr, py + ph - pr, px + pw - pr, py + ph, argb);     // bande du bas
            }
            if (ph - 2 * pr > 0) g.fill(px, py + pr, px + pw, py + ph - pr, argb); // milieu
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
        pt = Math.min(pt, Math.max(1, Math.min(pw, ph) / 2));

        // côtés droits
        if (pw - 2 * pr > 0) {
            g.fill(px + pr, py, px + pw - pr, py + pt, argb);
            g.fill(px + pr, py + ph - pt, px + pw - pr, py + ph, argb);
        }
        if (ph - 2 * pr > 0) {
            g.fill(px, py + pr, px + pt, py + ph - pr, argb);
            g.fill(px + pw - pt, py + pr, px + pw, py + ph - pr, argb);
        }

        if (pr > 0) corners(g, ring(pr, pt), px, py, pw, ph, pr, argb);

        g.pose().popMatrix();
    }

    // ------------------------------------------------------------------ trait ondulé

    /**
     * Vague de x0 à x1 autour de la ligne cy : amplitude, longueur d'onde et épaisseur en unités du repère.
     * `phase` en radians (pour l'animer). Utilisée seulement par le HUD musique (une seule vague).
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
