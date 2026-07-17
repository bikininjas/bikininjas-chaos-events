package com.bikininjas.chaosevents.item;

import com.bikininjas.chaosevents.ChaosEventsMod;
import com.bikininjas.corelib.log.LogManager;
import com.bikininjas.corelib.log.ModLogger;
import com.bikininjas.corelib.randomevent.RandomEventManager;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Right-click to trigger a random chaos event.
 * <p>
 * Uses core-lib's {@link RandomEventManager} to fire a random registered event
 * at the player's location. Has a 5-second cooldown to prevent spam.
 */
public class ChaosOrbItem extends Item {

    private static final ModLogger LOGGER = LogManager.getLogger(ChaosEventsMod.MOD_ID, ChaosOrbItem.class);

    public ChaosOrbItem(@NotNull Properties properties) {
        super(Objects.requireNonNull(properties, "properties must not be null"));
    }

    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(@NotNull Level level, @NotNull Player player,
                                                            @NotNull InteractionHand usedHand) {
        ItemStack stack = player.getItemInHand(usedHand);

        if (level.isClientSide()) {
            return InteractionResultHolder.success(stack);
        }

        var serverLevel = (ServerLevel) level;
        var mgr = RandomEventManager.getInstance();

        if (!mgr.isEnabled()) {
            player.displayClientMessage(
                    Component.literal("§c[Chaos] Chaos events are currently disabled."), true);
            return InteractionResultHolder.fail(stack);
        }

        mgr.fireRandomEvent(serverLevel);

        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.getCooldowns().addCooldown(this, 100); // 5-second cooldown
        }

        return InteractionResultHolder.consume(stack);
    }
}
