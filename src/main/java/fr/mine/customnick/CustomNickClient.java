package fr.mine.customnick;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CustomNickClient implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("whatif");

    // Le chat se ferme juste après l'exécution d'une commande et écraserait l'écran ouvert
    // immédiatement : on demande l'ouverture ici, et on l'effectue au tick suivant.
    private static volatile boolean openMenuRequested = false;

    @Override
    public void onInitializeClient() {
        NickConfig.load();
        MusicTracker.start();

        // Seule commande du mod : /whatif ouvre le menu principal
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
            dispatcher.register(
                LiteralArgumentBuilder.<FabricClientCommandSource>literal("whatif").executes(context -> {
                    openMenuRequested = true;
                    return 1;
                })
            )
        );

        ClientTickEvents.END_CLIENT_TICK.register((Minecraft mc) -> {
            if (!openMenuRequested) return;
            openMenuRequested = false;
            mc.setScreenAndShow(new QolScreen(mc.gui.screen()));
        });
    }
}
