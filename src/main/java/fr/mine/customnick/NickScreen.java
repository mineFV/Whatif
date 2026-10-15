package fr.mine.customnick;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Menu simple de CustomNick (commande /nick) : panneau "verre" flottant, menu d'onglets en haut à gauche,
 * contenu à droite, aperçu en direct et actions en bas.
 */
public class NickScreen extends Screen {
    private enum Tab {
        TEXT("Texte"), STYLE("Style"), COLORS("Couleurs"), PRESETS("Préréglages");

        final String label;

        Tab(String label) {
            this.label = label;
        }
    }

    private enum Target {COLOR1, COLOR2, SHADOW}

    private record Label(String text, int x, int y, int color) {}

    private record Preset(String name, int c1, int c2, boolean bold, boolean italic, boolean underlined, boolean strike) {}

    private static final String[] SYMBOLS = {"✿", "★", "♛", "♥", "☠", "⚔", "✦", "☯", "♪", "✪"};

    private static final int[] PALETTE = {
        0xFF5555, 0xFFAA00, 0xFFFF55, 0x55FF55, 0x00AA00, 0x55FFFF, 0x00AAAA, 0x5555FF,
        0xAA00AA, 0xFF55FF, 0xFFFFFF, 0xAAAAAA, 0x555555, 0x000000, 0xFF6EC7, 0xFFD700
    };

    private static final Preset[] PRESETS = {
        new Preset("§bOcéan", 0x00C6FF, 0x0072FF, true, false, false, false),
        new Preset("§cFeu", 0xFF512F, 0xF09819, true, false, false, false),
        new Preset("§dNéon", 0xFF00CC, 0x3333FF, true, true, false, false),
        new Preset("§6Doré", 0xFFD700, 0xB8860B, true, false, false, false),
        new Preset("§aToxique", 0x00FF87, 0x60EFFF, true, false, false, false),
        new Preset("§fGlace", 0xFFFFFF, 0x7DD3FC, false, false, false, false),
        new Preset("§9Galaxie", 0x4498DB, 0xE43A96, true, false, false, false),
        new Preset("§7Blanc → Noir (barré)", 0xFFFFFF, 0x444444, true, true, false, true),
    };

    private static final int LABEL_COLOR = 0xFFB4BCC8;
    private static final int PREVIEW_H = 54;

    private final Screen parent;
    private final NickDesign design;
    private boolean enabled;
    private Tab tab = Tab.TEXT;
    private Target target = Target.COLOR1;

    // géométrie courante (recalculée à chaque init)
    private int px, py, pw, ph;       // panneau
    private int cx0, cw, ct;          // zone de contenu (x, largeur, haut)
    private int actionsY, previewY;   // barre d'actions, aperçu

    private final List<Label> labels = new ArrayList<>();
    private final Map<Target, GlassButton> targetButtons = new EnumMap<>(Target.class);
    private GlassEditBox textBox;
    private GlassEditBox hexBox;
    private boolean updatingHex;

    public NickScreen(Screen parent) {
        super(Component.literal("WhatIf"));
        this.parent = parent;
        this.design = NickConfig.design != null
            ? NickConfig.design.copy()
            : NickDesign.defaultFor(NickReplacer.realNameOrEmpty());
        this.enabled = NickConfig.enabled;
    }

    // ------------------------------------------------------------------ construction

    @Override
    protected void init() {
        NickReplacer.suspended = true; // le menu affiche le vrai texte, pas le pseudo remplacé

        labels.clear();
        targetButtons.clear();
        textBox = null;
        hexBox = null;

        pw = Math.min(this.width - 24, 470);
        ph = Math.min(this.height - 16, 250);
        px = (this.width - pw) / 2;
        py = (this.height - ph) / 2;

        int pad = 10;
        int sideW = 96;
        int sx = px + pad;
        int sy = py + pad;

        cx0 = sx + sideW + 10;
        cw = px + pw - pad - cx0;
        ct = py + pad;
        actionsY = py + ph - pad - 20;
        previewY = actionsY - 6 - PREVIEW_H;

        labels.add(new Label("§lPseudo", sx + 4, sy + 3, 0xFFFFFFFF));
        buildSidebar(sx, sy + 20, sideW);
        buildActions();

        switch (tab) {
            case TEXT -> buildTextTab();
            case STYLE -> buildStyleTab();
            case COLORS -> buildColorsTab();
            case PRESETS -> buildPresetsTab();
        }
    }

    private GlassButton glass(int x, int y, int w, int h, Component message, Consumer<GlassButton> press, BooleanSupplier selected) {
        return this.addRenderableWidget(new GlassButton(x, y, w, h, message, press, selected));
    }

    private void buildSidebar(int sx, int top, int sideW) {
        Tab[] tabs = Tab.values();
        for (int i = 0; i < tabs.length; i++) {
            final Tab t = tabs[i];
            glass(sx, top + i * 26, sideW, 22, Component.literal(t.label), btn -> {
                tab = t;
                rebuildWidgets();
            }, () -> tab == t);
        }

        glass(sx, actionsY, sideW, 20, Component.literal("Mode avancé"),
            btn -> this.minecraft.setScreenAndShow(new NickAdvancedScreen(parent)), null);
    }

    private void buildActions() {
        int gap = 6;
        int bw = (cw - gap * 2) / 3;

        glass(cx0, actionsY, bw, 20, enabledLabel(), btn -> {
            enabled = !enabled;
            btn.setMessage(enabledLabel());
        }, () -> enabled);

        glass(cx0 + bw + gap, actionsY, bw, 20, Component.literal("Annuler"), btn -> this.onClose(), null);

        glass(cx0 + 2 * (bw + gap), actionsY, bw, 20, Component.literal("Sauvegarder"), btn -> save(), () -> true);
    }

    private Component enabledLabel() {
        return Component.literal(enabled ? "Activé" : "Désactivé");
    }

    // ------------------------------------------------------------------ onglet Texte

    private void buildTextTab() {
        int y0 = ct;
        labels.add(new Label("Texte affiché", cx0 + 2, y0, LABEL_COLOR));

        int btnW = 100;
        textBox = new GlassEditBox(this.font, cx0, y0 + 12, cw - btnW - 6, 22, Component.literal("Texte affiché"));
        textBox.setMaxLength(64);
        textBox.setValue(design.text);
        textBox.setResponder(s -> design.text = s);
        this.addRenderableWidget(textBox);
        this.setInitialFocus(textBox);

        glass(cx0 + cw - btnW, y0 + 12, btnW, 22, Component.literal("Mon pseudo"), btn -> {
            design.text = NickReplacer.realNameOrEmpty();
            textBox.setValue(design.text);
        }, null);

        int y1 = y0 + 46;
        labels.add(new Label("Symbole avant", cx0 + 2, y1, LABEL_COLOR));
        buildSymbolRow(y1 + 12, true);

        int y2 = y1 + 38;
        labels.add(new Label("Symbole après", cx0 + 2, y2, LABEL_COLOR));
        buildSymbolRow(y2 + 12, false);
    }

    private void buildSymbolRow(int y, boolean prefix) {
        int step = Math.min(26, cw / (SYMBOLS.length + 1));
        int w = step - 2;

        for (int i = 0; i < SYMBOLS.length; i++) {
            final String symbol = SYMBOLS[i];
            glass(cx0 + i * step, y, w, 20, Component.literal(symbol), btn -> setSymbol(prefix, symbol),
                () -> symbol.equals(prefix ? design.prefix : design.suffix));
        }

        glass(cx0 + SYMBOLS.length * step, y, w, 20, Component.literal("✕"), btn -> setSymbol(prefix, ""), null);
    }

    private void setSymbol(boolean prefix, String symbol) {
        if (prefix) design.prefix = symbol;
        else design.suffix = symbol;
    }

    // ------------------------------------------------------------------ onglet Style

    private void buildStyleTab() {
        int gap = 8;
        int w = (cw - gap) / 2;
        int x2 = cx0 + w + gap;
        int y0 = ct;

        addToggle(cx0, y0, w, 28, "§lGras", () -> design.bold, v -> design.bold = v);
        addToggle(x2, y0, w, 28, "§oItalique", () -> design.italic, v -> design.italic = v);
        addToggle(cx0, y0 + 34, w, 28, "§nSouligné", () -> design.underlined, v -> design.underlined = v);
        addToggle(x2, y0 + 34, w, 28, "§mBarré", () -> design.strikethrough, v -> design.strikethrough = v);

        labels.add(new Label("Le style s'applique à tout le pseudo, symboles compris.", cx0 + 2, y0 + 72, 0xFF8A93A0));
    }

    private void addToggle(int x, int y, int w, int h, String label, BooleanSupplier get, Consumer<Boolean> set) {
        glass(x, y, w, h, Component.literal(label), btn -> set.accept(!get.getAsBoolean()), get);
    }

    // ------------------------------------------------------------------ onglet Couleurs

    private void buildColorsTab() {
        int y0 = ct;
        int lw = 120;
        int rx = cx0 + lw + 10;
        int rw = cw - lw - 10;

        // colonne de gauche : mode + quelle couleur on édite
        glass(cx0, y0, lw, 22, modeLabel(), btn -> {
            design.gradient = !design.gradient;
            if (!design.gradient && target == Target.COLOR2) target = Target.COLOR1;
            rebuildWidgets();
        }, () -> design.gradient);

        addTargetButton(Target.COLOR1, cx0, y0 + 28, lw);
        if (design.gradient) addTargetButton(Target.COLOR2, cx0, y0 + 54, lw);

        GlassButton shadowButton = glass(cx0, y0 + 82, lw - 26, 22, targetLabel(Target.SHADOW), btn -> {
            design.shadow = true;
            selectTarget(Target.SHADOW);
        }, () -> target == Target.SHADOW);
        targetButtons.put(Target.SHADOW, shadowButton);

        glass(cx0 + lw - 22, y0 + 82, 22, 22, Component.literal("✕"), btn -> {
            design.shadow = false;
            if (target == Target.SHADOW) target = Target.COLOR1;
            refreshTargetButtons();
            syncHex();
        }, null);

        // colonne de droite : sliders RGB, hex, palette
        String[] names = {"Rouge", "Vert", "Bleu"};
        int[] shifts = {16, 8, 0};
        int[] tints = {0xFF5555, 0x55FF55, 0x5588FF};
        for (int i = 0; i < 3; i++) {
            this.addRenderableWidget(new ChannelSlider(rx, y0 + i * 22, rw, 18, names[i], shifts[i], tints[i],
                this::currentColor, rgb -> applyColor(rgb, true)));
        }

        int hy = y0 + 70;
        labels.add(new Label("Hex #", rx + 2, hy + 7, LABEL_COLOR));
        hexBox = new GlassEditBox(this.font, rx + 38, hy, 76, 22, Component.literal("Hex"));
        hexBox.setMaxLength(7);
        hexBox.setResponder(s -> {
            if (updatingHex) return;
            String h = s.startsWith("#") ? s.substring(1) : s;
            if (h.length() != 6) return;
            try {
                applyColor(Integer.parseInt(h, 16), false);
            } catch (NumberFormatException ignored) {
            }
        });
        this.addRenderableWidget(hexBox);

        this.addRenderableWidget(new ColorSwatch(rx + 122, hy + 2, 18, 18, this::currentColor, () -> false, null));

        int py2 = y0 + 98;
        int sw = Math.max(9, rw / PALETTE.length);
        for (int i = 0; i < PALETTE.length; i++) {
            final int color = PALETTE[i];
            this.addRenderableWidget(new ColorSwatch(rx + i * sw, py2, sw - 2, 16,
                () -> color, () -> currentColor() == color, () -> applyColor(color, true)));
        }

        refreshTargetButtons();
        syncHex();
    }

    private Component modeLabel() {
        return Component.literal(design.gradient ? "Dégradé" : "Couleur unie");
    }

    private void addTargetButton(Target t, int x, int y, int w) {
        GlassButton b = glass(x, y, w, 22, targetLabel(t), btn -> selectTarget(t), () -> target == t);
        targetButtons.put(t, b);
    }

    private Component targetLabel(Target t) {
        return Component.literal(switch (t) {
            case COLOR1 -> design.gradient ? "Début du dégradé" : "Couleur";
            case COLOR2 -> "Fin du dégradé";
            case SHADOW -> design.shadow ? "Ombre" : "Ombre (off)";
        });
    }

    private void selectTarget(Target t) {
        target = t;
        refreshTargetButtons();
        syncHex();
    }

    private void refreshTargetButtons() {
        for (Map.Entry<Target, GlassButton> e : targetButtons.entrySet()) {
            e.getValue().setMessage(targetLabel(e.getKey()));
        }
    }

    private int currentColor() {
        return switch (target) {
            case COLOR1 -> design.color1;
            case COLOR2 -> design.color2;
            case SHADOW -> design.shadowColor;
        };
    }

    private void applyColor(int rgb, boolean updateHex) {
        switch (target) {
            case COLOR1 -> design.color1 = rgb;
            case COLOR2 -> design.color2 = rgb;
            case SHADOW -> {
                design.shadowColor = rgb;
                design.shadow = true;
            }
        }
        if (target == Target.SHADOW) refreshTargetButtons();
        if (updateHex) syncHex();
    }

    /** Recale le champ hex sur la couleur en cours d'édition. */
    private void syncHex() {
        if (hexBox == null) return;
        updatingHex = true;
        hexBox.setValue(NickDesign.hex(currentColor()));
        updatingHex = false;
    }

    // ------------------------------------------------------------------ onglet Préréglages

    private void buildPresetsTab() {
        int gap = 8;
        int w = (cw - gap) / 2;

        for (int i = 0; i < PRESETS.length; i++) {
            final Preset p = PRESETS[i];
            int x = cx0 + (i % 2) * (w + gap);
            int y = ct + (i / 2) * 24;
            glass(x, y, w, 22, Component.literal(p.name()), btn -> applyPreset(p), null);
        }

        glass(cx0, ct + (PRESETS.length / 2) * 24, cw, 22, Component.literal("Réinitialiser le style"),
            btn -> resetStyle(), null);
    }

    private void applyPreset(Preset p) {
        design.gradient = true;
        design.color1 = p.c1();
        design.color2 = p.c2();
        design.bold = p.bold();
        design.italic = p.italic();
        design.underlined = p.underlined();
        design.strikethrough = p.strike();
        design.shadow = false;
        target = Target.COLOR1;
    }

    private void resetStyle() {
        design.bold = false;
        design.italic = false;
        design.underlined = false;
        design.strikethrough = false;
        design.gradient = false;
        design.color1 = 0x55FFFF;
        design.color2 = 0xFF55FF;
        design.shadow = false;
        design.shadowColor = 0x222222;
        target = Target.COLOR1;
    }

    // ------------------------------------------------------------------ actions

    private void save() {
        if (design.text.isBlank()) design.text = NickReplacer.realNameOrEmpty();
        NickConfig.design = design.copy();
        NickConfig.displayName = design.toFormat();
        NickConfig.enabled = enabled;
        NickConfig.save();
        this.onClose();
    }

    @Override
    public void onClose() {
        this.minecraft.setScreenAndShow(parent);
    }

    @Override
    public void removed() {
        NickReplacer.suspended = false;
    }

    // ------------------------------------------------------------------ rendu

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);

        Glass.panel(graphics, px, py, pw, ph);
        Glass.glass(graphics, cx0, previewY, cw, PREVIEW_H, 8, 0x50000000, 0x30FFFFFF);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);

        for (Label l : labels) {
            graphics.text(this.font, l.text(), l.x(), l.y(), l.color(), true);
        }

        drawPreview(graphics);
    }

    private void drawPreview(GuiGraphicsExtractor graphics) {
        graphics.text(this.font, "Aperçu", cx0 + 8, previewY + 5, 0xFF7C8591, true);

        FormattedCharSequence seq = NickFormat.parseFresh(design.toFormat()).sequence();
        int w = this.font.width(seq);

        int scale = 3;
        while (scale > 1 && w * scale > cw - 16) scale--;

        float cx = cx0 + cw / 2f;
        float cy = previewY + PREVIEW_H / 2f - (this.font.lineHeight * scale) / 2f + 3;

        graphics.pose().pushMatrix();
        graphics.pose().translate(cx, cy);
        graphics.pose().scale((float) scale, (float) scale);
        graphics.text(this.font, seq, -w / 2, 0, 0xFFFFFFFF, true);
        graphics.pose().popMatrix();
    }
}
