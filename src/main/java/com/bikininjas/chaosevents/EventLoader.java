package com.bikininjas.chaosevents;

import com.bikininjas.corelib.log.LogManager;
import com.bikininjas.corelib.log.ModLogger;
import com.bikininjas.corelib.randomevent.RandomEvent;
import com.bikininjas.corelib.randomevent.RandomEventManager;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;

/**
 * Loads chaos event definitions from datapack JSONs and registers them with
 * core-lib's {@link RandomEventManager}.
 * <p>
 * Events are read from {@code data/chaos_events/event/*.json} through
 * Minecraft's resource manager, merged with any KubeJS overrides automatically
 * (KubeJS applies its datapack on top of the mod's {@code src/main/resources}).
 * <p>
 * Supports a difficulty tier system: easy, normal, hard, insane.
 * Events can be filtered by difficulty via {@link #setActiveDifficulty(DifficultyLevel)}.
 * <p>
 * Supported event types: spawn_entity, apply_effect, explosion, give_items,
 * broadcast, potion_cloud, anvil_rain, lightning, launch_entities
 */
public final class EventLoader {

    private static final ModLogger LOGGER = LogManager.getLogger(ChaosEventsMod.MOD_ID, EventLoader.class);
    private static final Gson GSON = new Gson();
    private static final Random RNG = new Random();

    private static final String EVENTS_PATH = "event";

    /** All parsed event entries (including those filtered out by difficulty). */
    private static final List<ChaosEventEntry> allEvents = new ArrayList<>();

    /** Current active difficulty filter — events at or below this level are registered. */
    private static DifficultyLevel activeDifficulty = DifficultyLevel.NORMAL;

    private EventLoader() {
    }

    // -- Difficulty levels ----------------------------------------------------

    /**
     * Difficulty tiers for chaos events.
     * Events at or below the active difficulty threshold are eligible for selection.
     */
    public enum DifficultyLevel {
        EASY(0),
        NORMAL(1),
        HARD(2),
        INSANE(3);

        private final int level;

        DifficultyLevel(int level) {
            this.level = level;
        }

        public int getLevel() {
            return level;
        }

        /**
         * Returns true if this difficulty is included when the active level is {@code active}.
         * Events with difficulty &lt;= active difficulty are included.
         */
        public boolean isActiveFor(DifficultyLevel active) {
            return this.level <= active.level;
        }

        /**
         * Parse from a lowercase string. Returns NORMAL for unknown values.
         */
        public static @NotNull DifficultyLevel fromString(@NotNull String s) {
            for (var d : values()) {
                if (d.name().equalsIgnoreCase(s)) {
                    return d;
                }
            }
            return NORMAL;
        }
    }

    // -- Event entry record ---------------------------------------------------

    /**
     * Stores a parsed chaos event with all its metadata from JSON.
     */
    public record ChaosEventEntry(
            String fileName,
            String displayName,
            int weight,
            DifficultyLevel difficulty,
            int minPlayers,
            List<String> dimensions,
            int cooldownTicks,
            String type,
            JsonObject config,
            @Nullable String message,
            @Nullable String messageColor,
            double rewardChance,
            int rewardCount
    ) {}

    // -- Public API -----------------------------------------------------------

    /**
     * Load all event JSONs from the resource manager and register them with
     * {@link RandomEventManager}. Can be called multiple times (clears previous
     * registrations).
     *
     * @param server the Minecraft server instance
     */
    public static void loadAll(@NotNull MinecraftServer server) {
        Objects.requireNonNull(server, "server must not be null");

        allEvents.clear();
        var mgr = RandomEventManager.getInstance();
        mgr.reset();

        var resourceManager = server.getResourceManager();
        var eventResources = resourceManager.listResources(EVENTS_PATH,
                path -> path.getPath().endsWith(".json")
                      && ChaosEventsMod.MOD_ID.equals(path.getNamespace()));

        for (var entry : eventResources.entrySet()) {
            var location = entry.getKey();
            var resource = entry.getValue();

            try (var reader = resource.openAsReader()) {
                var json = GSON.fromJson(reader, JsonObject.class);
                var parsed = parseEventEntry(location, json);
                if (parsed != null) {
                    allEvents.add(parsed);
                    LOGGER.debug("Parsed event '{}' (difficulty: {}) from {}",
                            parsed.displayName(), parsed.difficulty(), location);
                }
            } catch (Exception e) {
                LOGGER.error("Failed to load event from {0}")
                        .ctx("location", location.toString())
                        .cause(e)
                        .report();
            }
        }

        // Register events matching the active difficulty
        registerFilteredEvents();

        var count = mgr.getEventCount();
        if (count > 0) {
            LOGGER.info("Loaded {} chaos event(s) ({} total parsed, filtered to {})",
                    count, allEvents.size(), activeDifficulty);
        } else {
            LOGGER.warn("No chaos events found. Create JSONs in data/chaos_events/event/");
        }
    }

    /**
     * Set the active difficulty filter. Only events at or below this level
     * will be registered for random selection.
     */
    public static void setActiveDifficulty(@NotNull DifficultyLevel difficulty) {
        Objects.requireNonNull(difficulty, "difficulty must not be null");
        activeDifficulty = difficulty;
        registerFilteredEvents();
        LOGGER.info("Chaos event difficulty set to {}", difficulty);
    }

    /**
     * Get the current active difficulty filter.
     */
    public static @NotNull DifficultyLevel getActiveDifficulty() {
        return activeDifficulty;
    }

    /**
     * Get the total number of parsed events (regardless of difficulty filter).
     */
    public static int getTotalEventCount() {
        return allEvents.size();
    }

    /**
     * Get the list of all parsed event file names.
     */
    public static @NotNull List<String> getAllEventFileNames() {
        return allEvents.stream().map(ChaosEventEntry::fileName).toList();
    }

    /**
     * Get the ChaosEventEntry for a given file name, or null if not found.
     */
    public static @Nullable ChaosEventEntry getEventEntry(@NotNull String fileName) {
        Objects.requireNonNull(fileName, "fileName must not be null");
        for (var e : allEvents) {
            if (e.fileName().equals(fileName)) {
                return e;
            }
        }
        return null;
    }

    /**
     * Fire a specific event by file name (without .json extension).
     *
     * @return true if the event was found and fired
     */
    public static boolean fireEventByName(@NotNull String fileName, @NotNull ServerLevel level, @NotNull Vec3 origin) {
        Objects.requireNonNull(fileName, "fileName must not be null");
        Objects.requireNonNull(level, "level must not be null");
        Objects.requireNonNull(origin, "origin must not be null");

        for (var e : allEvents) {
            if (e.fileName().equals(fileName)) {
                var event = createEventFromEntry(e);
                if (event != null) {
                    event.execute(level, origin);
                    return true;
                }
                return false;
            }
        }
        return false;
    }

    // -- Internal registration ------------------------------------------------

    /**
     * Re-register all events that match the current difficulty filter.
     */
    private static void registerFilteredEvents() {
        var mgr = RandomEventManager.getInstance();

        // Remove all existing chaos events (keep only internal mgr events if any)
        for (var name : mgr.getAllEvents()) {
            mgr.remove(name);
        }

        // Register events matching difficulty and other constraints
        int registered = 0;
        for (var entry : allEvents) {
            if (!entry.difficulty().isActiveFor(activeDifficulty)) {
                continue;
            }
            var event = createEventFromEntry(entry);
            if (event != null) {
                mgr.register(event, entry.fileName());
                registered++;
            }
        }

        LOGGER.debug("Re-registered {} events for difficulty {}", registered, activeDifficulty);
    }

    /**
     * Create a {@link RandomEvent} from a parsed {@link ChaosEventEntry}.
     */
    private static @Nullable RandomEvent createEventFromEntry(@NotNull ChaosEventEntry entry) {
        return switch (entry.type()) {
            case "spawn_entity" -> createSpawnEvent(entry);
            case "apply_effect" -> createEffectEvent(entry);
            case "explosion" -> createExplosionEvent(entry);
            case "give_items" -> createGiveItemsEvent(entry);
            case "broadcast" -> createBroadcastEvent(entry);
            case "potion_cloud" -> createPotionCloudEvent(entry);
            case "lightning" -> createLightningEvent(entry);
            case "launch_entities" -> createLaunchEntitiesEvent(entry);
            case "boss_spawn" -> createBossSpawnEvent(entry);
            default -> {
                LOGGER.warn("Unknown event type '{}' in event '{}'", entry.type(), entry.displayName());
                yield null;
            }
        };
    }

    // -- JSON parsing ---------------------------------------------------------

    /**
     * Parse a single event JSON into a {@link ChaosEventEntry}.
     */
    static @Nullable ChaosEventEntry parseEventEntry(ResourceLocation location, JsonObject json) {
        var type = getString(json, "type");
        if (type == null) {
            LOGGER.warn("Event {} missing 'type' field, skipping", location);
            return null;
        }

        var path = location.getPath();
        var fileName = path.substring(path.lastIndexOf('/') + 1);
        fileName = fileName.substring(0, fileName.lastIndexOf('.'));

        var displayName = getString(json, "name");
        if (displayName == null) {
            displayName = fileName;
        }

        var weight = getInt(json, "weight", 1);
        var difficultyStr = getString(json, "difficulty");
        var difficulty = difficultyStr != null
                ? DifficultyLevel.fromString(difficultyStr)
                : DifficultyLevel.NORMAL;
        var minPlayers = getInt(json, "min_players", 1);
        var dimensions = getStringList(json, "dimensions");
        if (dimensions.isEmpty()) {
            dimensions = List.of("minecraft:overworld");
        }
        var cooldownTicks = getInt(json, "cooldown_ticks", 6000);
        var message = getString(json, "message");
        var messageColor = getString(json, "message_color");
        var config = json.has("config") ? json.getAsJsonObject("config") : new JsonObject();
        var rewardChance = getDouble(json, "reward_chance", 0.0);
        var rewardCount = getInt(json, "reward_count", 1);

        return new ChaosEventEntry(
                fileName, displayName, weight, difficulty,
                minPlayers, dimensions, cooldownTicks,
                type, config, message, messageColor,
                rewardChance, rewardCount
        );
    }

    // -- Event factory methods -------------------------------------------------

    private static @Nullable RandomEvent createSpawnEvent(@NotNull ChaosEventEntry entry) {
        var config = entry.config();
        var entityId = getString(config, "entity");
        var count = getInt(config, "count", 1);
        var radius = getDouble(config, "radius", 5.0);

        if (entityId == null) {
            LOGGER.warn("spawn_entity event '{}' missing 'entity' config", entry.displayName());
            return null;
        }

        var entityType = BuiltInRegistries.ENTITY_TYPE.get(safeParse(entityId));
        if (entityType == null) {
            LOGGER.warn("Unknown entity '{}' in spawn_entity event '{}'", entityId, entry.displayName());
            return null;
        }

        return new RandomEvent() {
            @Override
            public @NotNull String name() {
                return entry.displayName();
            }

            @Override
            public int weight() {
                return entry.weight();
            }

            @Override
            public void execute(@NotNull ServerLevel level, @NotNull Vec3 origin) {
                broadcastIfPresent(level, entry);
                for (int i = 0; i < count; i++) {
                    var entity = entityType.create(level);
                    if (entity != null) {
                        var angle = 2 * Math.PI * i / count;
                        var pos = origin.add(
                                Math.cos(angle) * radius,
                                0.5,
                                Math.sin(angle) * radius);
                        entity.setPos(pos);
                        level.addFreshEntity(entity);
                    }
                }
                distributeSurvivorRewards(level, entry);
                broadcastEventVisuals(level, entry, origin);
            }
        };
    }

    private static @Nullable RandomEvent createEffectEvent(@NotNull ChaosEventEntry entry) {
        var config = entry.config();
        var effectId = getString(config, "effect");
        var duration = getInt(config, "duration", 200);
        var amplifier = getInt(config, "amplifier", 0);

        if (effectId == null) {
            LOGGER.warn("apply_effect event '{}' missing 'effect' config", entry.displayName());
            return null;
        }

        var effectIdRL = safeParse(effectId);
        if (effectIdRL == null) {
            LOGGER.warn("Invalid effect ID '{}' in apply_effect event '{}'", effectId, entry.displayName());
            return null;
        }
        var effect = BuiltInRegistries.MOB_EFFECT.get(effectIdRL);
        if (effect == null) {
            LOGGER.warn("Unknown effect '{}' in apply_effect event '{}'", effectId, entry.displayName());
            return null;
        }

        return new RandomEvent() {
            @Override
            public @NotNull String name() {
                return entry.displayName();
            }

            @Override
            public int weight() {
                return entry.weight();
            }

            @Override
            public void execute(@NotNull ServerLevel level, @NotNull Vec3 origin) {
                broadcastIfPresent(level, entry);
                var players = level.getServer().getPlayerList().getPlayers();
                for (var player : players) {
                    player.addEffect(new MobEffectInstance(Holder.direct(effect), duration, amplifier));
                }
                distributeSurvivorRewards(level, entry);
                broadcastEventVisuals(level, entry, origin);
            }
        };
    }

    private static @NotNull RandomEvent createExplosionEvent(@NotNull ChaosEventEntry entry) {
        var config = entry.config();
        var power = (float) getDouble(config, "power", 3.0);
        var fire = getBoolean(config, "fire", false);

        return new RandomEvent() {
            @Override
            public @NotNull String name() {
                return entry.displayName();
            }

            @Override
            public int weight() {
                return entry.weight();
            }

            @Override
            public void execute(@NotNull ServerLevel level, @NotNull Vec3 origin) {
                broadcastIfPresent(level, entry);
                level.explode(null, origin.x, origin.y, origin.z, power, fire,
                        Level.ExplosionInteraction.TNT);
                distributeSurvivorRewards(level, entry);
                broadcastEventVisuals(level, entry, origin);
            }
        };
    }

    private static @NotNull RandomEvent createGiveItemsEvent(@NotNull ChaosEventEntry entry) {
        var config = entry.config();
        var count = getInt(config, "count", 1);

        return new RandomEvent() {
            @Override
            public @NotNull String name() {
                return entry.displayName();
            }

            @Override
            public int weight() {
                return entry.weight();
            }

            @Override
            public void execute(@NotNull ServerLevel level, @NotNull Vec3 origin) {
                broadcastIfPresent(level, entry);

                // Resolve the #chaos_events:event_rewards tag
                var tagLookup = level.registryAccess()
                        .lookupOrThrow(Registries.ITEM);
                var rewardTag = TagKey.create(Registries.ITEM,
                        ResourceLocation.fromNamespaceAndPath("chaos_events", "event_rewards"));

                tagLookup.get(rewardTag).ifPresentOrElse(tag -> {
                    var items = tag.stream().toList();
                    if (!items.isEmpty()) {
                        // Give items to ALL online players
                        var players = level.getServer().getPlayerList().getPlayers();
                        for (var player : players) {
                            for (int i = 0; i < count; i++) {
                                var item = items.get(RNG.nextInt(items.size()));
                        if (!player.getInventory().add(new ItemStack(item))) {
                            player.drop(new ItemStack(item), false);
                        }
                            }
                        }
                    }
                }, () -> LOGGER.warn("Tag #chaos_events:event_rewards not found"));

                // If funny-effects is loaded, also give a random funny item to one player
                if (isFunnyEffectsLoaded()) {
                    var players = level.getServer().getPlayerList().getPlayers();
                    if (!players.isEmpty()) {
                        var luckyPlayer = players.get(RNG.nextInt(players.size()));
                        var funnyItem = getRandomFunnyEffectItem();
                        if (funnyItem != null) {
                            luckyPlayer.getInventory().add(funnyItem);
                            luckyPlayer.sendSystemMessage(
                                    Component.literal("§dYou also received a mysterious funny item!"));
                        }
                    }
                }
                broadcastEventVisuals(level, entry, origin);
            }
        };
    }

    private static @NotNull RandomEvent createBroadcastEvent(@NotNull ChaosEventEntry entry) {
        return new RandomEvent() {
            @Override
            public @NotNull String name() {
                return entry.displayName();
            }

            @Override
            public int weight() {
                return entry.weight();
            }

            @Override
            public void execute(@NotNull ServerLevel level, @NotNull Vec3 origin) {
                broadcastIfPresent(level, entry);
                distributeSurvivorRewards(level, entry);
                broadcastEventVisuals(level, entry, origin);
            }
        };
    }

    private static @NotNull RandomEvent createPotionCloudEvent(@NotNull ChaosEventEntry entry) {
        var config = entry.config();
        var radius = (float) getDouble(config, "radius", 3.0);
        var duration = getInt(config, "duration", 100);
        var cloudCount = getInt(config, "count", 1);
        var effectId = getString(config, "effect");

        return new RandomEvent() {
            @Override
            public @NotNull String name() {
                return entry.displayName();
            }

            @Override
            public int weight() {
                return entry.weight();
            }

            @Override
            public void execute(@NotNull ServerLevel level, @NotNull Vec3 origin) {
                broadcastIfPresent(level, entry);

                // Determine which effects to apply to the cloud
                MobEffectInstance cloudEffect;
                if (effectId != null) {
                    var effect = BuiltInRegistries.MOB_EFFECT.get(safeParse(effectId));
                    if (effect != null) {
                        cloudEffect = new MobEffectInstance(Holder.direct(effect), duration, 0);
                    } else {
                        cloudEffect = new MobEffectInstance(MobEffects.HARM, 40, 1);
                    }
                } else {
                    cloudEffect = new MobEffectInstance(MobEffects.HARM, 40, 1);
                }

                for (int i = 0; i < cloudCount; i++) {
                    var offsetX = (RNG.nextDouble() - 0.5) * 6.0;
                    var offsetZ = (RNG.nextDouble() - 0.5) * 6.0;
                    var cloud = new AreaEffectCloud(level,
                            origin.x + offsetX, origin.y, origin.z + offsetZ);
                    cloud.setRadius(radius);
                    cloud.setDuration(duration);
                    cloud.setRadiusPerTick(-radius / (float) duration);
                    cloud.addEffect(cloudEffect);
                    level.addFreshEntity(cloud);
                }
                distributeSurvivorRewards(level, entry);
                broadcastEventVisuals(level, entry, origin);
            }
        };
    }

    private static @NotNull RandomEvent createLightningEvent(@NotNull ChaosEventEntry entry) {
        var config = entry.config();
        var count = getInt(config, "count", 1);

        return new RandomEvent() {
            @Override
            public @NotNull String name() {
                return entry.displayName();
            }

            @Override
            public int weight() {
                return entry.weight();
            }

            @Override
            public void execute(@NotNull ServerLevel level, @NotNull Vec3 origin) {
                broadcastIfPresent(level, entry);
                var players = level.getServer().getPlayerList().getPlayers();
                if (players.isEmpty()) return;

                for (int i = 0; i < count; i++) {
                    var target = players.get(RNG.nextInt(players.size()));
                    var bolt = new LightningBolt(EntityType.LIGHTNING_BOLT, level);
                    bolt.setPos(target.getX(), target.getY(), target.getZ());
                    level.addFreshEntity(bolt);
                }
                distributeSurvivorRewards(level, entry);
                broadcastEventVisuals(level, entry, origin);
            }
        };
    }

    private static @NotNull RandomEvent createLaunchEntitiesEvent(@NotNull ChaosEventEntry entry) {
        var config = entry.config();
        var radius = getDouble(config, "radius", 8.0);
        var power = getDouble(config, "power", 1.5);

        return new RandomEvent() {
            @Override
            public @NotNull String name() {
                return entry.displayName();
            }

            @Override
            public int weight() {
                return entry.weight();
            }

            @Override
            public void execute(@NotNull ServerLevel level, @NotNull Vec3 origin) {
                broadcastIfPresent(level, entry);
                var aabb = new AABB(origin.x - radius, origin.y - radius, origin.z - radius,
                        origin.x + radius, origin.y + radius, origin.z + radius);
                var entities = level.getEntitiesOfClass(LivingEntity.class, aabb,
                        e -> !(e instanceof ServerPlayer)); // Don't launch players
                for (var entity : entities) {
                    entity.push(0.0, power, 0.0);
                    entity.hurtMarked = true;
                }
                distributeSurvivorRewards(level, entry);
                broadcastEventVisuals(level, entry, origin);
            }
        };
    }

    // -- Boss Spawn Event -----------------------------------------------------

    private static @Nullable RandomEvent createBossSpawnEvent(@NotNull ChaosEventEntry entry) {
        var config = entry.config();
        var entityId = getString(config, "entity");
        var healthMultiplier = getDouble(config, "health_multiplier", 3.0);
        var damageMultiplier = getDouble(config, "damage_multiplier", 2.0);

        if (entityId == null) {
            LOGGER.warn("boss_spawn event '{}' missing 'entity' config", entry.displayName());
            return null;
        }

        var entityType = BuiltInRegistries.ENTITY_TYPE.get(safeParse(entityId));
        if (entityType == null) {
            LOGGER.warn("Unknown entity '{}' in boss_spawn event '{}'", entityId, entry.displayName());
            return null;
        }

        return new RandomEvent() {
            @Override
            public @NotNull String name() {
                return entry.displayName();
            }

            @Override
            public int weight() {
                return entry.weight();
            }

            @Override
            public void execute(@NotNull ServerLevel level, @NotNull Vec3 origin) {
                broadcastIfPresent(level, entry);
                var entity = entityType.create(level);
                if (entity instanceof LivingEntity living) {
                    var maxHealthAttr = living.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
                    if (maxHealthAttr != null) {
                        var maxHealth = maxHealthAttr.getBaseValue() * healthMultiplier;
                        maxHealthAttr.setBaseValue(maxHealth);
                        living.setHealth((float) maxHealth);
                    }

                    var atkAttr = living.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
                    if (atkAttr != null) {
                        var baseDmg = atkAttr.getBaseValue();
                        atkAttr.setBaseValue(baseDmg * damageMultiplier);
                    }

                    living.setCustomName(Component.literal("§4§l" + entry.displayName()));
                    living.setCustomNameVisible(true);
                    if (living instanceof net.minecraft.world.entity.Mob mob) {
                        mob.setPersistenceRequired();
                    }
                    living.setPos(origin);
                    level.addFreshEntity(living);
                }
                distributeSurvivorRewards(level, entry);
                broadcastEventVisuals(level, entry, origin);
            }
        };
    }

    // -- Survivor Rewards -----------------------------------------------------

    /**
     * Distribute survivor rewards to all online players after an event executes,
     * if the event has a reward_chance > 0.
     */
    private static void distributeSurvivorRewards(ServerLevel level, ChaosEventEntry entry) {
        if (entry.rewardChance() <= 0.0) return;
        if (RNG.nextDouble() > entry.rewardChance()) return;

        var tagLookup = level.registryAccess().lookupOrThrow(Registries.ITEM);
        var rewardTag = TagKey.create(Registries.ITEM,
                ResourceLocation.fromNamespaceAndPath("chaos_events", "event_rewards"));

        tagLookup.get(rewardTag).ifPresent(tag -> {
            var items = tag.stream().toList();
            if (!items.isEmpty()) {
                var players = level.getServer().getPlayerList().getPlayers();
                for (var player : players) {
                    for (int i = 0; i < entry.rewardCount(); i++) {
                        var item = items.get(RNG.nextInt(items.size()));
                        var rewardStack = new ItemStack(item);
                        if (!player.getInventory().add(rewardStack)) {
                            level.addFreshEntity(
                                    new net.minecraft.world.entity.item.ItemEntity(
                                            level, player.getX(), player.getY(), player.getZ(), rewardStack));
                        }
                    }
                    player.sendSystemMessage(
                            Component.literal("§eYou survived the chaos and received a reward!"));
                }
            }
        });
    }

    // -- Event Visual Effects -------------------------------------------------

    /**
     * Broadcast visual and audio effects to all players when an event fires.
     */
    private static void broadcastEventVisuals(ServerLevel level, ChaosEventEntry entry, Vec3 origin) {
        var players = level.getServer().getPlayerList().getPlayers();
        for (var player : players) {
            // Brief nausea for screen-shake effect
            player.addEffect(new MobEffectInstance(
                    MobEffects.CONFUSION, 60, 0, true, false));

            // Play event sound at player's position
            player.playNotifySound(net.minecraft.sounds.SoundEvents.ENDER_DRAGON_GROWL,
                    net.minecraft.sounds.SoundSource.MASTER, 0.5F, 1.0F);
        }
    }

    // -- Funny-Effects Integration --------------------------------------------

    private static boolean funnyEffectsChecked = false;
    private static boolean funnyEffectsLoaded = false;

    /**
     * Check if the funny-effects mod is loaded.
     */
    private static boolean isFunnyEffectsLoaded() {
        if (!funnyEffectsChecked) {
            try {
                funnyEffectsLoaded = net.neoforged.fml.ModList.get().isLoaded("funnyeffects");
            } catch (Exception e) {
                funnyEffectsLoaded = false;
            }
            funnyEffectsChecked = true;
        }
        return funnyEffectsLoaded;
    }

    /**
     * Known funny-effects item IDs for integration.
     */
    private static final String[] FUNNY_EFFECT_ITEMS = {
            "funnyeffects:bouncy_slime",
            "funnyeffects:magnetic_glove",
            "funnyeffects:flatulent_bean",
            "funnyeffects:squeaky_toy",
            "funnyeffects:void_pearl",
            "funnyeffects:party_popper",
            "funnyeffects:infinite_pearl",
            "funnyeffects:chicken_wand",
            "funnyeffects:xp_magnet"
    };

    /**
     * Try to get a random funny-effects item stack.
     * Returns null if funny-effects is not loaded or the item can't be resolved.
     */
    private static @Nullable ItemStack getRandomFunnyEffectItem() {
        if (!isFunnyEffectsLoaded()) return null;

        var id = FUNNY_EFFECT_ITEMS[RNG.nextInt(FUNNY_EFFECT_ITEMS.length)];
        var item = BuiltInRegistries.ITEM.get(safeParse(id));
        if (item == null || item == Items.AIR) {
            return null;
        }
        return new ItemStack(item);
    }

    // -- Broadcast helpers ----------------------------------------------------

    /**
     * Broadcast an event message to all players, optionally with color.
     */
    private static void broadcastIfPresent(ServerLevel level, @NotNull ChaosEventEntry entry) {
        var message = entry.message();
        if (message == null || message.isBlank()) return;

        Component component;
        var colorStr = entry.messageColor();
        if (colorStr != null && !colorStr.isBlank()) {
            try {
                var parsed = TextColor.parseColor(colorStr);
                var color = parsed.result().orElse(null);
                if (color != null) {
                    component = Component.literal(message).withStyle(style ->
                            style.withColor(color));
                } else {
                    component = Component.literal(message);
                }
            } catch (Exception e) {
                component = Component.literal(message);
            }
        } else {
            component = Component.literal(message);
        }

        level.getServer().getPlayerList()
                .broadcastSystemMessage(component, false);
    }

    // -- JSON helpers ---------------------------------------------------------

    @Nullable
    private static String getString(JsonObject json, String key) {
        var el = json.get(key);
        return el != null && el.isJsonPrimitive() ? el.getAsString() : null;
    }

    private static int getInt(JsonObject json, String key, int defaultValue) {
        var el = json.get(key);
        return el != null && el.isJsonPrimitive() ? el.getAsInt() : defaultValue;
    }

    private static double getDouble(JsonObject json, String key, double defaultValue) {
        var el = json.get(key);
        return el != null && el.isJsonPrimitive() ? el.getAsDouble() : defaultValue;
    }

    @Nullable
    private static ResourceLocation safeParse(@NotNull String id) {
        try {
            return ResourceLocation.parse(Objects.requireNonNull(id, "id"));
        } catch (net.minecraft.ResourceLocationException e) {
            LOGGER.warn("Invalid resource location '{}' — skipping", id);
            return null;
        }
    }

    private static boolean getBoolean(JsonObject json, String key, boolean defaultValue) {
        var el = json.get(key);
        return el != null && el.isJsonPrimitive() ? el.getAsBoolean() : defaultValue;
    }

    private static @NotNull List<String> getStringList(JsonObject json, String key) {
        var el = json.get(key);
        if (el != null && el.isJsonArray()) {
            var list = new ArrayList<String>();
            for (var e : el.getAsJsonArray()) {
                if (e.isJsonPrimitive()) {
                    list.add(e.getAsString());
                }
            }
            return list;
        }
        return List.of();
    }
}
