package fr.mine.customnick;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Menu principal du mod QoL (commande /qol) : une carte par fonctionnalité, avec son interrupteur
 * et ses réglages. Pour ajouter une fonctionnalité, on ajoute une carte ici.
 */
public class QolScreen extends Screen {
    private static final int CARD1_H = 56;
    private static final int CARD2_H = 68;

    private final Screen parent;
    private int px, py, pw, ph;
    private int x0, inner;
    private int card1Y, card2Y;

    public QolScreen(Screen parent) {
        super(Component.literal("WhatIf"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        pw = Math.min(this.width - 24, 420);
        ph = Math.min(this.height - 16, 214);
        px = (this.width - pw) / 2;
        py = (this.height - ph) / 2;

        int pad = 12;
        x0 = px + pad;
        inner = pw - 2 * pad;
        card1Y = py + 32;
        card2Y = card1Y + CARD1_H + 8;

        int bw = 92;
        int rx = x0 + inner - 10 - bw;

        // carte 1 : pseudo personnalisé
        glass(rx, card1Y + 6, bw, 20, toggleLabel(NickConfig.enabled), btn -> {
            NickConfig.enabled = !NickConfig.enabled;
            btn.setMessage(toggleLabel(NickConfig.enabled));
        }, () -> NickConfig.enabled);

        glass(rx, card1Y + 30, bw, 20, Component.literal("Configurer"),
            btn -> this.minecraft.setScreenAndShow(new NickScreen(this)), null);

        // carte 2 : Dark Screen
        glass(rx, card2Y + 6, bw, 20, toggleLabel(NickConfig.darkScreenEnabled), btn -> {
            NickConfig.darkScreenEnabled = !NickConfig.darkScreenEnabled;
            btn.setMessage(toggleLabel(NickConfig.darkScreenEnabled));
        }, () -> NickConfig.darkScreenEnabled);

        int rowY = card2Y + 40;
        int tintW = 112;
        this.addRenderableWidget(new ValueSlider(x0 + 10, rowY, inner - 20 - tintW - 8, 18, "Opacité", 1, 80, "%",
            () -> NickConfig.darkScreenOpacity, v -> NickConfig.darkScreenOpacity = v));

        glass(x0 + inner - 10 - tintW, rowY, tintW, 18, Component.literal("Teinter le HUD"),
            btn -> NickConfig.darkScreenTintHud = !NickConfig.darkScreenTintHud, () -> NickConfig.darkScreenTintHud);

        glass(px + pw - 12 - 90, py + ph - 12 - 20, 90, 20, Component.literal("Fermer"), btn -> this.onClose(), () -> true);
    }

    private GlassButton glass(int x, int y, int w, int h, Component message, Consumer<GlassButton> press, BooleanSupplier selected) {
        return this.addRenderableWidget(new GlassButton(x, y, w, h, message, press, selected));
    }

    private static Component toggleLabel(boolean on) {
        return Component.literal(on ? "Activé" : "Désactivé");
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);

        Glass.panel(graphics, px, py, pw, ph);
        Glass.glass(graphics, x0, card1Y, inner, CARD1_H, 8, 0x2AFFFFFF, 0x34FFFFFF);
        Glass.glass(graphics, x0, card2Y, inner, CARD2_H, 8, 0x2AFFFFFF, 0x34FFFFFF);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);

        graphics.text(this.font, "§lWhatIf", x0 + 2, py + 12, 0xFFFFFFFF, true);
        graphics.text(this.font, "Petites améliorations de confort", x0 + 2, py + 23, 0xFF8A93A0, true);

        graphics.text(this.font, "§lPseudo personnalisé", x0 + 10, card1Y + 10, 0xFFFFFFFF, true);
        graphics.text(this.font, "Un autre pseudo affiché pour toi seulement.", x0 + 10, card1Y + 24, 0xFFB4BCC8, true);

        graphics.text(this.font, "§lDark Screen", x0 + 10, card2Y + 10, 0xFFFFFFFF, true);
        graphics.text(this.font, "Assombrit l'écran de jeu.", x0 + 10, card2Y + 24, 0xFFB4BCC8, true);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreenAndShow(parent);
    }

    @Override
    public void removed() {
        NickConfig.save();
    }
}
