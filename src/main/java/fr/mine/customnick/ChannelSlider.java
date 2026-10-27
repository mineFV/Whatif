package fr.mine.customnick;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/** Slider 0-255 arrondi pour un canal (rouge, vert ou bleu) d'une couleur RGB. */
public class ChannelSlider extends AbstractWidget implements ArrowAdjustable {
    private final String name;
    private final int shift;
    private final int tint;
    private final IntSupplier colorGetter;
    private final IntConsumer colorSetter;

    public ChannelSlider(int x, int y, int w, int h, String name, int shift, int tint,
                         IntSupplier colorGetter, IntConsumer colorSetter) {
        super(x, y, w, h, Component.literal(name));
        this.name = name;
        this.shift = shift;
        this.tint = tint;
        this.colorGetter = colorGetter;
        this.colorSetter = colorSetter;
    }

    private int value() {
        return (colorGetter.getAsInt() >> shift) & 0xFF;
    }

    private void setFromMouse(double mouseX) {
        double t = (mouseX - (getX() + 3)) / (double) (getWidth() - 6);
        t = Math.max(0.0, Math.min(1.0, t));
        int v = (int) Math.round(t * 255);
        int color = colorGetter.getAsInt();
        colorSetter.accept((color & ~(0xFF << shift)) | (v << shift));
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        int x = getX();
        int y = getY();
        int w = getWidth();
        int h = getHeight();
        int v = value();

        // piste
        Glass.fillRounded(graphics, x, y, w, h, 6, 0x26FFFFFF);

        // partie remplie, teintée selon le canal
        int fillW = (int) Math.round(w * (v / 255.0));
        if (fillW > 0) Glass.fillRounded(graphics, x, y, Math.max(fillW, 8), h, 6, 0xA0000000 | (tint & 0xFFFFFF));

        Glass.outlineRounded(graphics, x, y, w, h, 6, (isHovered() || isFocused()) ? 0xDDA9D4FF : 0x3CFFFFFF);

        // poignée
        int kx = x + (int) Math.round((w - 6) * (v / 255.0));
        Glass.fillRounded(graphics, kx, y + 1, 6, h - 2, 3, 0xF2FFFFFF);

        // texte : nom à gauche, valeur à droite
        Font font = Minecraft.getInstance().font;
        int ty = y + (h - font.lineHeight) / 2 + 1;
        graphics.text(font, name, x + 8, ty, 0xFFFFFFFF, true);
        String val = String.valueOf(v);
        graphics.text(font, val, x + w - 8 - font.width(val), ty, 0xFFFFFFFF, true);
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        setFromMouse(event.x());
    }

    @Override
    protected void onDrag(MouseButtonEvent event, double dragX, double dragY) {
        setFromMouse(event.x());
    }

    /** Flèches : ±1 sur le canal (±10 avec Maj). */
    @Override
    public boolean adjustWithArrows(KeyEvent event) {
        int dir = ArrowAdjustable.direction(event);
        if (dir == 0) return false;
        int v = Math.max(0, Math.min(255, value() + dir * ArrowAdjustable.multiplier(event)));
        int color = colorGetter.getAsInt();
        colorSetter.accept((color & ~(0xFF << shift)) | (v << shift));
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        return adjustWithArrows(event) || super.keyPressed(event);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
    }
}
