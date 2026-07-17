package com.bikininjas.chaosevents;

import com.bikininjas.corelib.log.LogManager;
import com.bikininjas.corelib.log.ModLogger;
import com.bikininjas.corelib.randomevent.RandomEventManager;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;

/**
 * Chaos Events mod entry point.
 * <p>
 * A server-side mod that spawns random chaotic events using core-lib's
 * {@link RandomEventManager}. Events are defined as datapack JSONs —
 * fully KubeJS-configurable.
 * <p>
 * Can integrate with funny-effects items for event rewards.
 */
@Mod(ChaosEventsMod.MOD_ID)
public final class ChaosEventsMod {

    public static final String MOD_ID = "chaos_events";

    private static final ModLogger LOGGER = LogManager.getLogger(MOD_ID, ChaosEventsMod.class);

    public ChaosEventsMod(IEventBus modBus) {
        // Load datapack event definitions on server start
        NeoForge.EVENT_BUS.addListener((ServerAboutToStartEvent event) -> {
            var server = event.getServer();
            EventLoader.loadAll(server);
            RandomEventManager.getInstance().setEnabled(true);
            LOGGER.info("Chaos Events loaded {} event(s) from datapack",
                    RandomEventManager.getInstance().getEventCount());
        });

        // Register the /chaos command
        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent event) -> {
            ChaosCommand.register(event.getDispatcher());
            LOGGER.debug("/chaos command registered");
        });

        LOGGER.info("Chaos Events mod initialized");
    }
}
