package fr.mine.customnick.mixin;

import fr.mine.customnick.DarkScreen;
import fr.mine.customnick.MusicHud;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Dark Screen. Même idée que DarkMode de NoammAddons, mais en 26.2 le HUD est dans la classe Hud
 * (et non plus Gui) : Hud.extractRenderState(GuiGraphicsExtractor, DeltaTracker).
 *
 * - voile dessiné AVANT le HUD  -> le monde est assombri, le HUD reste net
 * - voile dessiné APRÈS le HUD  -> le HUD est assombri aussi ("Teinter le HUD")
 */
@Mixin(Hud.class)
public abstract class MixinHud {
    private static final String RENDER =
        "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V";

    @Inject(method = RENDER, at = @At("HEAD"))
    private void customnick$darkBeforeHud(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (!DarkScreen.tintHud()) DarkScreen.draw(graphics);
    }

    @Inject(method = RENDER, at = @At("TAIL"))
    private void customnick$darkAfterHud(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        MusicHud.draw(graphics); // fait partie du HUD
        if (DarkScreen.tintHud()) DarkScreen.draw(graphics);
    }
}
