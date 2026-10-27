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

/** Slider arrondi pour une valeur entière entre min et max (ex : opacité 1-80 %). */
public class ValueSlider extends AbstractWidget implements ArrowAdjustable {
    private final String name;
    private final int min;
    private final int max;
    private final String unit;
    private final IntSupplier getter;
    private final IntConsumer setter;

    public ValueSlider(int x, int y, int w, int h, String name, int min, int max, String unit,
                       IntSupplier getter, IntConsumer setter) {
        super(x, y, w, h, Component.literal(name));
        this.name = name;
        this.min = min;
        this.max = max;
        this.unit = unit;
        this.getter = getter;
        this.setter = setter;
    }

    private double ratio() {
        return (Math.max(min, Math.min(max, getter.getAsInt())) - min) / (double) (max - min);
    }

    private void setFromMouse(double mouseX) {
        double t = (mouseX - (getX() + 3)) / (double) (getWidth() - 6);
        t = Math.max(0.0, Math.min(1.0, t));
        setter.accept((int) Math.round(min + t * (max - min)));
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        int x = getX();
        int y = getY();
        int w = getWidth();
        int h = getHeight();
        double r = ratio();

        Glass.fillRounded(graphics, x, y, w, h, 6, 0x26FFFFFF);

        int fillW = (int) Math.round(w * r);
        if (fillW > 0) Glass.fillRounded(graphics, x, y, Math.max(fillW, 8), h, 6, 0x90000000 | Glass.ACCENT);

        Glass.outlineRounded(graphics, x, y, w, h, 6, (isHovered() || isFocused()) ? 0xDDA9D4FF : 0x3CFFFFFF);

        int kx = x + (int) Math.round((w - 6) * r);
        Glass.fillRounded(graphics, kx, y + 1, 6, h - 2, 3, 0xF2FFFFFF);

        Font font = Minecraft.getInstance().font;
        int ty = y + (h - font.lineHeight) / 2 + 1;
        graphics.text(font, name, x + 8, ty, 0xFFFFFFFF, true);
        String val = getter.getAsInt() + unit;
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

    /** Flèches : ±1 (±10 avec Maj). */
    @Override
    public boolean adjustWithArrows(KeyEvent event) {
        int dir = ArrowAdjustable.direction(event);
        if (dir == 0) return false;
        int v = getter.getAsInt() + dir * ArrowAdjustable.multiplier(event);
        setter.accept(Math.max(min, Math.min(max, v)));
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
