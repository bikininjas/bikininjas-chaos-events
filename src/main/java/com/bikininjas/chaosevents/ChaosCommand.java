package com.bikininjas.chaosevents;

import com.bikininjas.corelib.log.LogManager;
import com.bikininjas.corelib.log.ModLogger;
import com.bikininjas.corelib.randomevent.RandomEventManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * The {@code /chaos} command for controlling the Chaos Events mod at runtime.
 * Subcommands: status, toggle, trigger, difficulty, interval.
 */
public final class ChaosCommand {

    private static final ModLogger LOGGER = LogManager.getLogger(ChaosEventsMod.MOD_ID, ChaosCommand.class);
    private static final String PREFIX = "§6[Chaos]§r ";

    private ChaosCommand() {
    }

    public static void register(@NotNull CommandDispatcher<CommandSourceStack> dispatcher) {
        Objects.requireNonNull(dispatcher, "dispatcher must not be null");

        var statusNode = Commands.literal("status")
                .executes(ctx -> executeStatus(ctx.getSource()));

        var toggleNode = Commands.literal("toggle")
                .requires(src -> src.hasPermission(2))
                .executes(ctx -> executeToggle(ctx.getSource()));

        var triggerNode = Commands.literal("trigger")
                .requires(src -> src.hasPermission(2))
                .then(Commands.argument("name", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            for (var name : EventLoader.getAllEventFileNames()) {
                                builder.suggest(name);
                            }
                            return builder.buildFuture();
                        })
                        .executes(ctx -> executeTrigger(ctx.getSource(),
                                StringArgumentType.getString(ctx, "name"))));

        var difficultyNode = Commands.literal("difficulty")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("easy")
                        .executes(ctx -> executeDifficulty(ctx.getSource(), EventLoader.DifficultyLevel.EASY)))
                .then(Commands.literal("normal")
                        .executes(ctx -> executeDifficulty(ctx.getSource(), EventLoader.DifficultyLevel.NORMAL)))
                .then(Commands.literal("hard")
                        .executes(ctx -> executeDifficulty(ctx.getSource(), EventLoader.DifficultyLevel.HARD)))
                .then(Commands.literal("insane")
                        .executes(ctx -> executeDifficulty(ctx.getSource(), EventLoader.DifficultyLevel.INSANE)));

        var intervalNode = Commands.literal("interval")
                .requires(src -> src.hasPermission(2))
                .then(Commands.argument("min", IntegerArgumentType.integer(1, 3600))
                        .then(Commands.argument("max", IntegerArgumentType.integer(1, 3600))
                                .executes(ctx -> executeInterval(ctx.getSource(),
                                        IntegerArgumentType.getInteger(ctx, "min"),
                                        IntegerArgumentType.getInteger(ctx, "max")))));

        var helpNode = Commands.literal("help")
                .executes(ctx -> executeHelp(ctx.getSource()));

        dispatcher.register(Commands.literal("chaos")
                .then(statusNode)
                .then(toggleNode)
                .then(triggerNode)
                .then(difficultyNode)
                .then(intervalNode)
                .then(helpNode)
                .executes(ctx -> executeHelp(ctx.getSource())));
    }

    private static int executeStatus(@NotNull CommandSourceStack source) {
        var activeDiff = EventLoader.getActiveDifficulty();
        var totalEvents = EventLoader.getTotalEventCount();
        var activeEvents = RandomEventManager.getInstance().getEventCount();

        source.sendSuccess(() -> Component.literal(PREFIX + "§e=== Chaos Events Status ==="), false);
        source.sendSuccess(() -> Component.literal(PREFIX + "§7Enabled: §" + (RandomEventManager.getInstance().isEnabled() ? "aYes" : "cNo")), false);
        source.sendSuccess(() -> Component.literal(PREFIX + "§7Difficulty: §b" + activeDiff.name().toLowerCase()), false);
        source.sendSuccess(() -> Component.literal(PREFIX + "§7Total Events: §e" + totalEvents), false);
        source.sendSuccess(() -> Component.literal(PREFIX + "§7Active Events: §e" + activeEvents), false);
        source.sendSuccess(() -> Component.literal(PREFIX + "§7Events: §f" + String.join("§7, §f", EventLoader.getAllEventFileNames())), false);

        LOGGER.info("Chaos status queried by {}", source.getTextName());
        return 1;
    }

    private static int executeToggle(@NotNull CommandSourceStack source) {
        var mgr = RandomEventManager.getInstance();
        var wasEnabled = mgr.isEnabled();
        mgr.setEnabled(!wasEnabled);

        var msg = !wasEnabled ? "§aChaos events enabled!" : "§cChaos events disabled!";
        source.sendSuccess(() -> Component.literal(PREFIX + msg), true);
        LOGGER.info("Chaos events toggled {} by {}", mgr.isEnabled() ? "ON" : "OFF", source.getTextName());
        return 1;
    }

    private static int executeTrigger(@NotNull CommandSourceStack source, @NotNull String name) {
        var level = source.getLevel();
        var origin = source.getPosition();

        var found = EventLoader.fireEventByName(name, level, origin);
        if (found) {
            source.sendSuccess(() -> Component.literal(PREFIX + "§aTriggered event '§f" + name + "§a'!"), true);
            LOGGER.info("Event '{}' manually triggered by {}", name, source.getTextName());
            return 1;
        } else {
            source.sendFailure(Component.literal(PREFIX + "§cUnknown event '§f" + name + "§c'."));
            source.sendFailure(Component.literal(PREFIX + "§7Available: §f" + String.join("§7, §f", EventLoader.getAllEventFileNames())));
            return 0;
        }
    }

    private static int executeDifficulty(@NotNull CommandSourceStack source,
                                          @NotNull EventLoader.DifficultyLevel difficulty) {
        EventLoader.setActiveDifficulty(difficulty);
        var activeCount = RandomEventManager.getInstance().getEventCount();

        source.sendSuccess(() -> Component.literal(PREFIX + "§aDifficulty set to §b" + difficulty.name().toLowerCase()
                + "§a. §e" + activeCount + " §aevents active."), true);
        LOGGER.info("Chaos difficulty set to {} by {}", difficulty, source.getTextName());
        return 1;
    }

    private static int executeInterval(@NotNull CommandSourceStack source, int minSeconds, int maxSeconds) {
        if (minSeconds > maxSeconds) {
            source.sendFailure(Component.literal(PREFIX + "§cMin must be <= max."));
            return 0;
        }

        RandomEventManager.getInstance().setInterval(minSeconds * 20, maxSeconds * 20);
        source.sendSuccess(() -> Component.literal(PREFIX + "§aEvent interval set to §e"
                + minSeconds + "s §7- §e" + maxSeconds + "s"), true);
        LOGGER.info("Chaos interval set to {}-{}s by {}", minSeconds, maxSeconds, source.getTextName());
        return 1;
    }

    private static int executeHelp(@NotNull CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal(PREFIX + "§e=== Chaos Events Commands ==="), false);
        source.sendSuccess(() -> Component.literal("§7/chaos status §8- Show current state"), false);
        source.sendSuccess(() -> Component.literal("§7/chaos toggle §8- Enable/disable events (perm 2)"), false);
        source.sendSuccess(() -> Component.literal("§7/chaos trigger <name> §8- Fire an event (perm 2)"), false);
        source.sendSuccess(() -> Component.literal("§7/chaos difficulty <level> §8- Set difficulty filter (perm 2)"), false);
        source.sendSuccess(() -> Component.literal("§7/chaos interval <min> <max> §8- Set interval in seconds (perm 2)"), false);
        return 1;
    }
}
