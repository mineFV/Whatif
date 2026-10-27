package fr.mine.customnick;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/** Mode avancé : écris directement le texte formaté (codes &, &#hex, <gradient>...). */
public class NickAdvancedScreen extends Screen {
    private static final int PREVIEW_Y = 90;
    private static final int PREVIEW_H = 32;

    private final Screen parent;
    private GlassEditBox nameInput;
    private int px, py, pw, ph;

    public NickAdvancedScreen(Screen parent) {
        super(Component.literal("WhatIf - mode avancé"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        NickReplacer.suspended = true;

        pw = Math.min(this.width - 24, 340);
        ph = Math.min(this.height - 16, 184);
        px = (this.width - pw) / 2;
        py = (this.height - ph) / 2;

        int pad = 10;
        int inner = pw - 2 * pad;
        int x0 = px + pad;
        int gap = 6;
        int bw = (inner - gap) / 2;

        nameInput = new GlassEditBox(this.font, x0, py + 62, inner, 22, Component.literal("Texte formaté"));
        nameInput.setMaxLength(256);
        nameInput.setValue(NickConfig.displayName);
        this.addRenderableWidget(nameInput);
        this.setInitialFocus(nameInput);

        int rowY = py + 130;

        this.addRenderableWidget(new GlassButton(x0, rowY, bw, 20, Component.literal("Sauvegarder"), btn -> {
            NickConfig.displayName = nameInput.getValue();
            NickConfig.design = null; // le texte brut remplace les réglages du menu simple
            NickConfig.save();
            this.onClose();
        }, () -> true));

        this.addRenderableWidget(new GlassButton(x0 + bw + gap, rowY, bw, 20, Component.literal("Annuler"),
            btn -> this.onClose(), null));

        this.addRenderableWidget(new GlassButton(x0, rowY + 24, inner, 20, Component.literal("Retour au mode simple"),
            btn -> this.minecraft.gui.setScreen(new NickScreen(parent)), null));
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);

        Glass.panel(graphics, px, py, pw, ph);
        Glass.glass(graphics, px + 10, py + PREVIEW_Y, pw - 20, PREVIEW_H, 8, 0x50000000, 0x30FFFFFF);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);

        int x0 = px + 12;
        graphics.text(this.font, "§lMode avancé", x0, py + 10, 0xFFFFFFFF, true);
        graphics.text(this.font, "&a..&f couleurs   &l gras   &o italique   &m barré   &n souligné", x0, py + 24, 0xFFB4BCC8, true);
        graphics.text(this.font, "&#RRGGBB couleur hex   &s#RRGGBB ombre   &r reset", x0, py + 35, 0xFFB4BCC8, true);
        graphics.text(this.font, "<gradient:#AAAAAA:#BBBBBB>texte</gradient>", x0, py + 46, 0xFFB4BCC8, true);

        FormattedCharSequence preview = NickFormat.parseFresh(nameInput.getValue()).sequence();
        int w = this.font.width(preview);
        graphics.text(this.font, preview, this.width / 2 - w / 2, py + PREVIEW_Y + (PREVIEW_H - this.font.lineHeight) / 2 + 1,
            0xFFFFFFFF, true);
    }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(parent);
    }

    @Override
    public void removed() {
        NickReplacer.suspended = false;
    }
}
