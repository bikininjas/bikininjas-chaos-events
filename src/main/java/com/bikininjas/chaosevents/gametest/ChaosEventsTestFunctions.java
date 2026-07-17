package com.bikininjas.chaosevents.gametest;

import com.bikininjas.chaosevents.ChaosEventsMod;
import com.bikininjas.corelib.randomevent.RandomEvent;
import com.bikininjas.corelib.randomevent.RandomEventManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.neoforged.testframework.annotation.ForEachTest;
import net.neoforged.testframework.gametest.EmptyTemplate;
import net.neoforged.testframework.gametest.ExtendedGameTestHelper;
import org.jetbrains.annotations.NotNull;

/**
 * GameTest functions for Chaos Events mod.
 * <p>
 * Validates that RandomEventManager can register, enumerate, and fire
 * events without crashing.
 */
@ForEachTest(groups = "chaos_events")
public final class ChaosEventsTestFunctions {

    @EmptyTemplate(value = "3x3x3", floor = true)
    public void eventRegistrationWorks(final @NotNull ExtendedGameTestHelper helper) {
        var mgr = RandomEventManager.getInstance();
        mgr.reset();

        // Register a test event
        mgr.register(new RandomEvent() {
            @Override
            public @NotNull String name() {
                return "test_event";
            }

            @Override
            public int weight() {
                return 1;
            }

            @Override
            public void execute(@NotNull ServerLevel level, @NotNull Vec3 origin) {
                // no-op
            }
        }, "test_event");

        // Verify registration
        var events = mgr.getAllEvents();
        helper.assertTrue(events.contains("test_event"), "test_event should be registered");
        helper.assertTrue(events.size() == 1, "Expected 1 event, got " + events.size());
        helper.succeed();
    }

    @EmptyTemplate(value = "3x3x3", floor = true)
    public void eventFiresWithoutCrash(final @NotNull ExtendedGameTestHelper helper) {
        var mgr = RandomEventManager.getInstance();
        mgr.reset();

        // Register a safe test event
        mgr.register(new RandomEvent() {
            @Override
            public @NotNull String name() {
                return "safe_event";
            }

            @Override
            public int weight() {
                return 1;
            }

            @Override
            public void execute(@NotNull ServerLevel level, @NotNull Vec3 origin) {
                // Simply broadcast a message — safe operation
                level.getServer().getPlayerList()
                        .broadcastSystemMessage(
                                net.minecraft.network.chat.Component.literal("Test event fired!"),
                                false);
            }
        }, "safe_event");

        mgr.setEnabled(true);
        mgr.setInterval(20, 40); // 1-2 seconds for testing

        // Fire the event manually once
        var level = helper.getLevel();
        var spawned = mgr.fireRandomEvent(level);
        helper.assertTrue(spawned != null, "event should fire without crash");
        helper.succeed();
    }

    @EmptyTemplate(value = "3x3x3", floor = true)
    public void weightedSelectionRespectsWeights(final @NotNull ExtendedGameTestHelper helper) {
        var mgr = RandomEventManager.getInstance();
        mgr.reset();

        // Register two events with different weights
        mgr.register(new RandomEvent() {
            @Override
            public @NotNull String name() {
                return "heavy";
            }

            @Override
            public int weight() {
                return 99;
            }

            @Override
            public void execute(@NotNull ServerLevel level, @NotNull Vec3 origin) {
                // no-op
            }
        }, "heavy");

        mgr.register(new RandomEvent() {
            @Override
            public @NotNull String name() {
                return "light";
            }

            @Override
            public int weight() {
                return 1;
            }

            @Override
            public void execute(@NotNull ServerLevel level, @NotNull Vec3 origin) {
                // no-op
            }
        }, "light");

        // Run many selections and check heavy is picked > 80%
        int heavy = 0;
        int total = 1000;
        for (int i = 0; i < total; i++) {
            var selected = mgr.selectRandomEvent();
            if (selected != null && "heavy".equals(selected.name())) {
                heavy++;
            }
        }

        double ratio = (double) heavy / total;
        helper.assertTrue(ratio > 0.80,
                "Expected heavy event ratio > 0.80 but got " + ratio);
        helper.succeed();
    }
}
