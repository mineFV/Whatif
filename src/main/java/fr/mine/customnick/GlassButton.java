package fr.mine.customnick;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Bouton arrondi et translucide. `selected` allume le bouton en bleu (onglet actif, option activée...). */
public class GlassButton extends AbstractWidget {
    private final Consumer<GlassButton> onPress;
    private final BooleanSupplier selected;

    public GlassButton(int x, int y, int w, int h, Component message, Consumer<GlassButton> onPress, BooleanSupplier selected) {
        super(x, y, w, h, message);
        this.onPress = onPress;
        this.selected = selected;
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        int x = getX();
        int y = getY();
        int w = getWidth();
        int h = getHeight();

        boolean sel = selected != null && selected.getAsBoolean();
        boolean hover = active && isHovered();

        int fill;
        int border;
        if (!active) {
            fill = 0x14FFFFFF;
            border = 0x20FFFFFF;
        } else if (sel) {
            fill = hover ? 0x9065B2FF : 0x805AA9FF;
            border = 0xDDA9D4FF;
        } else {
            fill = hover ? 0x4AFFFFFF : 0x2CFFFFFF;
            border = hover ? 0x70FFFFFF : 0x3CFFFFFF;
        }

        Glass.glass(graphics, x, y, w, h, Math.min(8, h / 2), fill, border);

        Font font = Minecraft.getInstance().font;
        int textColor = active ? 0xFFFFFFFF : 0xFF888888;
        graphics.centeredText(font, getMessage(), x + w / 2, y + (h - font.lineHeight) / 2 + 1, textColor);
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        onPress.accept(this);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
    }
}
