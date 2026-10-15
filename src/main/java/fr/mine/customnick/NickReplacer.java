package fr.mine.customnick;

import fr.mine.customnick.NickFormat.Glyph;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;

/**
 * Remplace ton vrai pseudo par le pseudo personnalisé dans le texte affiché.
 * Purement visuel et local : le serveur et les autres joueurs voient toujours ton vrai pseudo.
 */
public final class NickReplacer {
    private NickReplacer() {}

    /** Mis à true pendant qu'un menu CustomNick est ouvert : le texte du menu reste le vrai texte. */
    public static volatile boolean suspended = false;

    public static boolean active() {
        return !suspended && NickConfig.enabled && !NickConfig.displayName.isBlank();
    }

    public static String realNameOrEmpty() {
        String name = realName();
        return name == null ? "" : name;
    }

    private static String realName() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getUser() == null) return null;
        return mc.getUser().getName();
    }

    private static boolean isNameChar(int cp) {
        return cp == '_' || (cp < 128 && Character.isLetterOrDigit(cp));
    }

    // ---------------------------------------------------------------- String
    // Utilisé seulement pour calculer des largeurs : on garde le gras, pas les couleurs.

    public static String replaceString(String input) {
        if (input == null || !active()) return input;
        String real = realName();
        if (real == null || real.isEmpty() || !input.contains(real)) return input;

        String replacement = NickFormat.parse(NickConfig.displayName).legacyString();
        StringBuilder sb = null;
        int from = 0;
        int idx;

        while ((idx = input.indexOf(real, from)) >= 0) {
            int end = idx + real.length();

            // on saute les codes couleur §x situés juste avant le pseudo
            int before = idx;
            while (before >= 2 && input.charAt(before - 2) == '§') before -= 2;
            boolean okBefore = before == 0 || !isNameChar(input.charAt(before - 1));
            boolean okAfter = end >= input.length() || !isNameChar(input.charAt(end));

            if (sb == null) sb = new StringBuilder(input.length() + 16);
            if (okBefore && okAfter) sb.append(input, from, idx).append(replacement);
            else sb.append(input, from, end);
            from = end;
        }

        if (sb == null) return input;
        sb.append(input, from, input.length());
        return sb.toString();
    }

    // ------------------------------------------------------ FormattedCharSequence

    public static FormattedCharSequence replaceSequence(FormattedCharSequence seq) {
        if (seq == null || !active()) return seq;
        String real = realName();
        if (real == null || real.isEmpty()) return seq;

        List<Glyph> glyphs = new ArrayList<>();
        seq.accept((index, style, cp) -> {
            glyphs.add(new Glyph(cp, style));
            return true;
        });

        int[] target = real.codePoints().toArray();
        int n = glyphs.size();
        if (n < target.length) return seq;

        List<Glyph> replacement = null;
        List<Glyph> out = new ArrayList<>(n + 16);
        boolean matched = false;
        int i = 0;

        while (i < n) {
            if (matchesAt(glyphs, i, target)) {
                int end = i + target.length;
                boolean okBefore = i == 0 || !isNameChar(glyphs.get(i - 1).codePoint());
                boolean okAfter = end >= n || !isNameChar(glyphs.get(end).codePoint());

                if (okBefore && okAfter) {
                    if (replacement == null) replacement = NickFormat.parse(NickConfig.displayName).glyphs();
                    Style base = glyphs.get(i).style();
                    for (Glyph g : replacement) out.add(new Glyph(g.codePoint(), g.style().applyTo(base)));
                    i = end;
                    matched = true;
                    continue;
                }
            }
            out.add(glyphs.get(i));
            i++;
        }

        if (!matched) return seq;
        return NickFormat.toSequence(out);
    }

    private static boolean matchesAt(List<Glyph> glyphs, int start, int[] target) {
        if (start + target.length > glyphs.size()) return false;
        for (int k = 0; k < target.length; k++) {
            if (glyphs.get(start + k).codePoint() != target[k]) return false;
        }
        return true;
    }
}
