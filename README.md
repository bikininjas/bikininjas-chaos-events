# Chaos Events

A Minecraft NeoForge server-side mod that spawns random chaotic events using core-lib's RandomEventManager. Events are fully KubeJS-configurable via datapack JSONs.

## Event Types (10 total)

| Type | Description |
|---|---|
| `spawn_entity` | Spawns entities around players |
| `apply_effect` | Applies potion effects to all players |
| `explosion` | Creates explosions at random locations |
| `give_items` | Drops items at random positions (funny-effects integration) |
| `broadcast` | Sends a chat message to all players |
| `potion_cloud` | Spawns lingering potion clouds |
| `lightning` | Strikes lightning at random positions |
| `launch_entities` | Launches nearby entities into the air |
| `boss_spawn` | Spawns a scaled boss mob with custom HP, damage, and reward |
| _extensible_ | New types can be added in EventLoader |

## Difficulty System

| Level | Events Active |
|---|---|
| `EASY` | Only easy-tier events |
| `NORMAL` | Easy + normal (default) |
| `HARD` | Easy + normal + hard |
| `INSANE` | All events, no filter |

## Survival Rewards

Each event can be configured with `reward_chance` (0.0–1.0) and `reward_count`. When the chance succeeds, all online players receive random items from the `#chaos_events:event_rewards` tag.

## Event Visual Effects

When any event fires, all online players experience:
- Brief nausea (screen shake, 3s)
- Ender dragon growl sound

## Chaos Orb

Craftable item (blaze powder + obsidian + ender pearl) — right-click to manually trigger a random event. 5-second cooldown between uses.

## Boss Spawn Event

The `boss_spawn` event type creates a custom boss mob with:
- Scaled max HP based on difficulty multiplier
- Scaled attack damage based on difficulty multiplier
- Custom boss name with dark red styling
- Survivor rewards on defeat (if configured)

## Commands

| Command | Permission | Description |
|---|---|---|
| `/chaos status` | Anyone | Show current state (enabled, difficulty, active events) |
| `/chaos toggle` | Level 2 | Enable/disable events |
| `/chaos trigger <name>` | Level 2 | Fire a specific event by name |
| `/chaos difficulty <level>` | Level 2 | Set difficulty filter (easy/normal/hard/insane) |
| `/chaos interval <min> <max>` | Level 2 | Set random interval in seconds |
| `/chaos reload` | Level 2 | Reload events from datapack |
| `/chaos help` | Anyone | Show command list |

## KubeJS / Datapack Configuration

Events are defined as JSON files in `data/chaos_events/events/`. Example:

```json
{
  "name": "Zombie Horde",
  "type": "spawn_entity",
  "weight": 10,
  "difficulty": "normal",
  "cooldown_seconds": 120,
  "entity": "minecraft:zombie",
  "count": 5,
  "radius": 10.0,
  "reward_chance": 0.5,
  "reward_count": 2
}
```

## Dependencies
- **core-lib** (required) — shared RandomEventManager
- **funny-effects** (optional) — give_items event can reward funny-effects items

## BikiniConfig Options
Accessible via `/bn` in-game: Chaos Events toggle, Difficulty level, Announce Events.

