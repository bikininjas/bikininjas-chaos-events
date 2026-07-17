package com.bikininjas.chaosevents;

import com.bikininjas.chaosevents.item.ModItems;
import com.bikininjas.corelib.config.BikiniConfigRegistry;
import com.bikininjas.corelib.log.LogManager;
import com.bikininjas.corelib.log.ModLogger;
import com.bikininjas.corelib.randomevent.RandomEventManager;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
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
        // Register items
        ModItems.MOD_ITEMS.register(modBus);

        BikiniConfigRegistry.registerEnum(MOD_ID, "difficulty", "Difficulty", "normal",
                "easy", "normal", "hard", "insane");
        BikiniConfigRegistry.registerInt(MOD_ID, "interval_min", "Interval Min (s)", 300);
        BikiniConfigRegistry.registerInt(MOD_ID, "interval_max", "Interval Max (s)", 900);

        NeoForge.EVENT_BUS.register(EventHandler.class);

        LOGGER.info("Chaos Events mod initialized");
    }

    // -- Event Handlers ---------------------------------------------------------

    private static final class EventHandler {

        @SubscribeEvent
        static void onServerAboutToStart(ServerAboutToStartEvent event) {
            var server = event.getServer();
            EventLoader.loadAll(server);

            // Apply config interval
            var mgr = RandomEventManager.getInstance();
            int minSec = parseIntConfig(MOD_ID, "interval_min", 300);
            int maxSec = parseIntConfig(MOD_ID, "interval_max", 900);
            mgr.setInterval(minSec * 20, maxSec * 20);
            mgr.setEnabled(true);

            LOGGER.info("Chaos Events loaded {} event(s) from datapack, interval {}-{}s",
                    mgr.getEventCount(), minSec, maxSec);
        }

        static int parseIntConfig(String modId, String key, int fallback) {
            try {
                return Integer.parseInt(BikiniConfigRegistry.getString(modId, key, String.valueOf(fallback)));
            } catch (NumberFormatException e) {
                return fallback;
            }
        }

        @SubscribeEvent
        static void onRegisterCommands(RegisterCommandsEvent event) {
            ChaosCommand.register(event.getDispatcher());
            LOGGER.info("/chaos command registered");
        }
    }
}
