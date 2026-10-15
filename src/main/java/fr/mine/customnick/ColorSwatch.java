package fr.mine.customnick;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;

/** Pastille de couleur arrondie, cliquable (palette) ou simple témoin de couleur (onPick = null). */
public class ColorSwatch extends AbstractWidget {
    private final IntSupplier color;
    private final BooleanSupplier selected;
    private final Runnable onPick;

    public ColorSwatch(int x, int y, int w, int h, IntSupplier color, BooleanSupplier selected, Runnable onPick) {
        super(x, y, w, h, Component.empty());
        this.color = color;
        this.selected = selected;
        this.onPick = onPick;
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        int x = getX();
        int y = getY();
        int w = getWidth();
        int h = getHeight();

        Glass.fillRounded(graphics, x, y, w, h, 4, 0xFF000000 | (color.getAsInt() & 0xFFFFFF));

        if (selected.getAsBoolean()) Glass.outlineRounded(graphics, x - 2, y - 2, w + 4, h + 4, 5, 0xFFFFFFFF);
        else if (onPick != null && isHovered()) Glass.outlineRounded(graphics, x - 1, y - 1, w + 2, h + 2, 5, 0xCCFFFFFF);
        else Glass.outlineRounded(graphics, x, y, w, h, 4, 0x55FFFFFF);
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        if (onPick != null) onPick.run();
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
    }
}
