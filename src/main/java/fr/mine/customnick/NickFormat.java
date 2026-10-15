package fr.mine.customnick;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;

/**
 * Format du pseudo personnalisé.
 *
 * <pre>
 *  &amp;0-9 &amp;a-f        couleurs classiques
 *  &amp;l &amp;o &amp;m &amp;n &amp;k    gras, italique, barré, souligné, obfusqué
 *  &amp;r               reset
 *  &amp;#RRGGBB         couleur hexadécimale        (ex : &amp;#4498DB)
 *  &amp;s#RRGGBB        couleur de l'ombre          (ex : &amp;s#222222)
 *  &lt;gradient:#RRGGBB:#RRGGBB[:#RRGGBB...]&gt;texte&lt;/gradient&gt;   dégradé lettre par lettre
 * </pre>
 * Les caractères Unicode (✿ ★ ♛ ...) passent tels quels et servent de "badge".
 * Le symbole § est accepté à la place de &amp;.
 */
public final class NickFormat {
    private NickFormat() {}

    public record Glyph(int codePoint, Style style) {}

    /** Résultat du parsing : glyphes stylés + version "§" (pour le calcul de largeur). */
    public record Parsed(List<Glyph> glyphs, String legacyString, FormattedCharSequence sequence) {}

    private record Cache(String input, Parsed parsed) {}
    private static volatile Cache cache;

    /** Parse avec un cache d'une entrée (le pseudo ne change que quand tu modifies la config). */
    public static Parsed parse(String input) {
        Cache c = cache;
        if (c != null && c.input().equals(input)) return c.parsed();
        Parsed parsed = parseFresh(input);
        cache = new Cache(input, parsed);
        return parsed;
    }

    /** Parse sans cache (utilisé par l'aperçu du menu, pour ne pas écraser le cache du vrai pseudo). */
    public static Parsed parseFresh(String text) {
        List<Glyph> glyphs = parseGlyphs(text);
        return new Parsed(glyphs, toLegacyString(glyphs), toSequence(glyphs));
    }

    // ------------------------------------------------------------------ parsing

    private static List<Glyph> parseGlyphs(String text) {
        int[] cps = text.codePoints().toArray();
        List<Glyph> result = new ArrayList<>();

        Style current = Style.EMPTY;
        List<Integer> gradientStops = null;   // non null = on est dans un <gradient>
        List<Integer> gradientIndexes = new ArrayList<>(); // positions dans `result` à colorier

        int i = 0;
        while (i < cps.length) {
            int cp = cps[i];

            // <gradient:#AAAAAA:#BBBBBB>
            if (cp == '<' && startsWith(cps, i, "<gradient:")) {
                int close = indexOf(cps, '>', i);
                if (close > 0) {
                    List<Integer> stops = parseStops(new String(cps, i + "<gradient:".length(), close - i - "<gradient:".length()));
                    if (stops != null && stops.size() >= 2) {
                        applyGradient(result, gradientIndexes, gradientStops); // ferme un éventuel précédent
                        gradientStops = stops;
                        gradientIndexes = new ArrayList<>();
                        i = close + 1;
                        continue;
                    }
                }
            }
            // </gradient>
            if (cp == '<' && startsWith(cps, i, "</gradient>")) {
                applyGradient(result, gradientIndexes, gradientStops);
                gradientStops = null;
                gradientIndexes = new ArrayList<>();
                i += "</gradient>".length();
                continue;
            }

            if ((cp == '&' || cp == '§') && i + 1 < cps.length) {
                int next = cps[i + 1];

                // &#RRGGBB
                if (next == '#') {
                    Integer rgb = parseHex(cps, i + 2);
                    if (rgb != null) {
                        current = current.withColor(rgb);
                        i += 8;
                        continue;
                    }
                }
                // &s#RRGGBB  (ombre)
                if ((next == 's' || next == 'S') && i + 2 < cps.length && cps[i + 2] == '#') {
                    Integer rgb = parseHex(cps, i + 3);
                    if (rgb != null) {
                        current = current.withShadowColor(0xFF000000 | rgb);
                        i += 9;
                        continue;
                    }
                }
                // &x classique
                ChatFormatting fmt = ChatFormatting.getByCode((char) Character.toLowerCase(next));
                if (fmt != null) {
                    current = fmt == ChatFormatting.RESET ? Style.EMPTY : current.applyFormat(fmt);
                    i += 2;
                    continue;
                }
            }

            result.add(new Glyph(cp, current));
            if (gradientStops != null) gradientIndexes.add(result.size() - 1);
            i++;
        }

        applyGradient(result, gradientIndexes, gradientStops); // dégradé non fermé : on l'applique quand même
        return result;
    }

    private static void applyGradient(List<Glyph> result, List<Integer> indexes, List<Integer> stops) {
        if (stops == null || indexes.isEmpty()) return;
        int n = indexes.size();
        for (int k = 0; k < n; k++) {
            float t = n == 1 ? 0f : (float) k / (n - 1);
            int rgb = sample(stops, t);
            int idx = indexes.get(k);
            Glyph g = result.get(idx);
            result.set(idx, new Glyph(g.codePoint(), g.style().withColor(rgb)));
        }
    }

    /** Interpole linéairement entre les couleurs du dégradé. */
    private static int sample(List<Integer> stops, float t) {
        float scaled = t * (stops.size() - 1);
        int a = Math.min((int) scaled, stops.size() - 2);
        float local = scaled - a;
        int c1 = stops.get(a);
        int c2 = stops.get(a + 1);
        int r = lerp((c1 >> 16) & 0xFF, (c2 >> 16) & 0xFF, local);
        int g = lerp((c1 >> 8) & 0xFF, (c2 >> 8) & 0xFF, local);
        int b = lerp(c1 & 0xFF, c2 & 0xFF, local);
        return (r << 16) | (g << 8) | b;
    }

    private static int lerp(int a, int b, float t) {
        return Math.round(a + (b - a) * t);
    }

    private static List<Integer> parseStops(String raw) {
        List<Integer> stops = new ArrayList<>();
        for (String part : raw.split(":")) {
            String p = part.trim();
            if (!p.startsWith("#") || p.length() != 7) return null;
            try {
                stops.add(Integer.parseInt(p.substring(1), 16));
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return stops;
    }

    private static Integer parseHex(int[] cps, int start) {
        if (start + 6 > cps.length) return null;
        try {
            return Integer.parseInt(new String(cps, start, 6), 16);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static boolean startsWith(int[] cps, int at, String prefix) {
        if (at + prefix.length() > cps.length) return false;
        for (int k = 0; k < prefix.length(); k++) {
            if (cps[at + k] != prefix.charAt(k)) return false;
        }
        return true;
    }

    private static int indexOf(int[] cps, int target, int from) {
        for (int k = from; k < cps.length; k++) if (cps[k] == target) return k;
        return -1;
    }

    // ------------------------------------------------------------------ sorties

    public static FormattedCharSequence toSequence(List<Glyph> glyphs) {
        return sink -> {
            for (int k = 0; k < glyphs.size(); k++) {
                Glyph g = glyphs.get(k);
                if (!sink.accept(k, g.style(), g.codePoint())) return false;
            }
            return true;
        };
    }

    /**
     * Version "§" qui ne garde que les formats qui changent la largeur du texte (gras...).
     * Sert uniquement pour Font.width(String), où on ne peut pas renvoyer de styles hex.
     */
    private static String toLegacyString(List<Glyph> glyphs) {
        StringBuilder sb = new StringBuilder();
        boolean bold = false;
        for (Glyph g : glyphs) {
            boolean b = g.style().isBold();
            if (b != bold) {
                sb.append(b ? "§l" : "§r");
                bold = b;
            }
            sb.appendCodePoint(g.codePoint());
        }
        if (bold) sb.append("§r");
        return sb.toString();
    }
}
