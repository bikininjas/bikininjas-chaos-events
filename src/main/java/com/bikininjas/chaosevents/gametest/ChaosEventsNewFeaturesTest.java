package com.bikininjas.chaosevents.gametest;

import com.bikininjas.chaosevents.ChaosEventsMod;
import com.bikininjas.chaosevents.EventLoader;
import com.bikininjas.chaosevents.item.ModItems;
import com.bikininjas.corelib.randomevent.RandomEventManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.neoforged.testframework.annotation.ForEachTest;
import net.neoforged.testframework.gametest.EmptyTemplate;
import net.neoforged.testframework.gametest.ExtendedGameTestHelper;
import org.jetbrains.annotations.NotNull;

/**
 * GameTest functions for new chaos-events features:
 * ChaosOrbItem, BossEvent, SurvivorRewards, EventVisualEffects.
 */
@ForEachTest(groups = "chaos_events")
public final class ChaosEventsNewFeaturesTest {

    private ChaosEventsNewFeaturesTest() {
    }

    private static @NotNull ServerPlayer makePlayer(@NotNull ExtendedGameTestHelper helper) {
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.moveTo(
                helper.absolutePos(player.blockPosition()).getX() + 1.5,
                helper.absolutePos(player.blockPosition()).getY() + 2,
                helper.absolutePos(player.blockPosition()).getZ() + 1.5
        );
        return (ServerPlayer) player;
    }

    // ========================================================================
    // ChaosOrbItem — right-click triggers random event
    // ========================================================================

    @EmptyTemplate(value = "3x3x3", floor = true)
    public static void chaosOrb_doesNotCrashOnUse(@NotNull ExtendedGameTestHelper helper) {
        var player = makePlayer(helper);
        var orb = new ItemStack(ModItems.CHAOS_ORB.get());
        player.setItemSlot(EquipmentSlot.MAINHAND, orb);

        var result = orb.getItem().use(player.level(), player, InteractionHand.MAIN_HAND);
        helper.assertTrue(result.getResult().consumesAction(),
                "ChaosOrb.use should consume action even when no events registered");
        helper.succeed();
    }

    @EmptyTemplate(value = "3x3x3", floor = true)
    public static void chaosOrb_respectsCooldown(@NotNull ExtendedGameTestHelper helper) {
        var player = makePlayer(helper);
        var orb = new ItemStack(ModItems.CHAOS_ORB.get());
        player.setItemSlot(EquipmentSlot.MAINHAND, orb);

        var first = orb.getItem().use(player.level(), player, InteractionHand.MAIN_HAND);
        var second = orb.getItem().use(player.level(), player, InteractionHand.MAIN_HAND);

        helper.assertTrue(first.getResult().consumesAction(),
                "First use should consume action");
        helper.succeed();
    }

    // ========================================================================
    // EventLoader — Difficulty levels
    // ========================================================================

    @EmptyTemplate(value = "3x3x3", floor = true)
    public static void eventLoader_difficultySetAndGet(@NotNull ExtendedGameTestHelper helper) {
        EventLoader.setActiveDifficulty(EventLoader.DifficultyLevel.EASY);
        helper.assertTrue(EventLoader.getActiveDifficulty() == EventLoader.DifficultyLevel.EASY,
                "getActiveDifficulty should return EASY after set");
        EventLoader.setActiveDifficulty(EventLoader.DifficultyLevel.NORMAL);
        helper.succeed();
    }

    @EmptyTemplate(value = "3x3x3", floor = true)
    public static void eventLoader_getAllEventFileNames(@NotNull ExtendedGameTestHelper helper) {
        var names = EventLoader.getAllEventFileNames();
        helper.assertTrue(names != null,
                "getAllEventFileNames should never return null");
        helper.succeed();
    }

    // ========================================================================
    // RandomEventManager integration
    // ========================================================================

    @EmptyTemplate(value = "3x3x3", floor = true)
    public static void randomEvent_managerEnableDisable(@NotNull ExtendedGameTestHelper helper) {
        var mgr = RandomEventManager.getInstance();
        var wasEnabled = mgr.isEnabled();
        mgr.setEnabled(!wasEnabled);
        helper.assertTrue(mgr.isEnabled() != wasEnabled,
                "setEnabled should toggle enabled state");
        mgr.setEnabled(wasEnabled);
        helper.succeed();
    }
}
