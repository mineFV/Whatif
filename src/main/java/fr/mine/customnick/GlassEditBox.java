package fr.mine.customnick;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Champ de texte arrondi et translucide (le fond "verre" est dessiné derrière un EditBox sans bordure). */
public class GlassEditBox extends EditBox {
    private final int ox;
    private final int oy;
    private final int ow;
    private final int oh;

    public GlassEditBox(Font font, int x, int y, int w, int h, Component message) {
        // zone de saisie réelle : un peu rétrécie pour laisser une marge dans le fond arrondi
        super(font, x + 8, y + (h - 8) / 2, w - 16, 10, message);
        this.ox = x;
        this.oy = y;
        this.ow = w;
        this.oh = h;
        this.setBordered(false);
    }

    @Override
    public void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        boolean focused = isFocused();
        int fill = focused ? 0x3CFFFFFF : 0x24FFFFFF;
        int border = focused ? 0xDDA9D4FF : 0x44FFFFFF;
        Glass.glass(graphics, ox, oy, ow, oh, Math.min(8, oh / 2), fill, border);
        super.extractWidgetRenderState(graphics, mouseX, mouseY, partialTick);
    }

    /** Un clic dans la marge du fond arrondi donne aussi le focus au champ. */
    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        boolean inOuter = event.x() >= ox && event.x() < ox + ow && event.y() >= oy && event.y() < oy + oh;
        if (inOuter && !isMouseOver(event.x(), event.y())) {
            setFocused(true);
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }
}
