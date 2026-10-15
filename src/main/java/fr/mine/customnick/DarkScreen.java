package fr.mine.customnick;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Dark Screen : voile noir translucide sur tout l'écran de jeu. */
public final class DarkScreen {
    private DarkScreen() {}

    public static boolean tintHud() {
        return NickConfig.darkScreenTintHud;
    }

    public static void draw(GuiGraphicsExtractor graphics) {
        if (!NickConfig.darkScreenEnabled) return;

        int percent = Math.max(1, Math.min(80, NickConfig.darkScreenOpacity));
        int alpha = Math.round(percent / 100f * 255f);
        graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), alpha << 24);
    }
}
