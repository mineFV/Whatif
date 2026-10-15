package fr.mine.customnick.mixin;

import fr.mine.customnick.NickReplacer;
import net.minecraft.client.gui.Font;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Même idée que MixinFont de NoammAddons : on modifie le texte juste avant le dessin
 * et le calcul de largeur, pour que l'affichage et la mise en page restent cohérents.
 *
 * Les descripteurs ci-dessous viennent de NoammAddons (Minecraft 26.1.2).
 * Si le jeu refuse de démarrer sur 26.2 avec une erreur Mixin, c'est ici qu'il faut ajuster.
 */
@Mixin(Font.class)
public class MixinFont {

    @ModifyVariable(
        method = "prepareText(Lnet/minecraft/util/FormattedCharSequence;FFIZZI)Lnet/minecraft/client/gui/Font$PreparedText;",
        at = @At("HEAD"),
        argsOnly = true
    )
    private FormattedCharSequence customnick$drawSequence(FormattedCharSequence text) {
        return NickReplacer.replaceSequence(text);
    }

    @ModifyVariable(
        method = "width(Lnet/minecraft/util/FormattedCharSequence;)I",
        at = @At("HEAD"),
        argsOnly = true
    )
    private FormattedCharSequence customnick$widthSequence(FormattedCharSequence text) {
        return NickReplacer.replaceSequence(text);
    }

    @ModifyVariable(
        method = "width(Ljava/lang/String;)I",
        at = @At("HEAD"),
        argsOnly = true
    )
    private String customnick$widthString(String text) {
        return NickReplacer.replaceString(text);
    }
}
