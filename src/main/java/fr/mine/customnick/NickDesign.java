package fr.mine.customnick;

/**
 * Ce que tu règles dans le menu simple. Se convertit en texte au format de {@link NickFormat}
 * (c'est ce texte qui est réellement utilisé pour l'affichage).
 */
public final class NickDesign {
    public String text = "";
    public String prefix = "";
    public String suffix = "";

    public boolean bold;
    public boolean italic;
    public boolean underlined;
    public boolean strikethrough;

    /** false = couleur unie (color1), true = dégradé color1 → color2 */
    public boolean gradient;
    public int color1 = 0x55FFFF;
    public int color2 = 0xFF55FF;

    public boolean shadow;
    public int shadowColor = 0x222222;

    public NickDesign() {}

    public static NickDesign defaultFor(String name) {
        NickDesign d = new NickDesign();
        d.text = name == null ? "" : name;
        return d;
    }

    public NickDesign copy() {
        NickDesign d = new NickDesign();
        d.text = text;
        d.prefix = prefix;
        d.suffix = suffix;
        d.bold = bold;
        d.italic = italic;
        d.underlined = underlined;
        d.strikethrough = strikethrough;
        d.gradient = gradient;
        d.color1 = color1;
        d.color2 = color2;
        d.shadow = shadow;
        d.shadowColor = shadowColor;
        return d;
    }

    public String toFormat() {
        StringBuilder sb = new StringBuilder();
        if (shadow) sb.append("&s#").append(hex(shadowColor));
        if (bold) sb.append("&l");
        if (italic) sb.append("&o");
        if (underlined) sb.append("&n");
        if (strikethrough) sb.append("&m");

        String body = (prefix.isEmpty() ? "" : prefix + " ") + text + (suffix.isEmpty() ? "" : " " + suffix);

        if (gradient) {
            sb.append("<gradient:#").append(hex(color1)).append(":#").append(hex(color2)).append('>')
                .append(body).append("</gradient>");
        } else {
            sb.append("&#").append(hex(color1)).append(body);
        }
        return sb.toString();
    }

    public static String hex(int rgb) {
        return String.format("%06X", rgb & 0xFFFFFF);
    }
}
