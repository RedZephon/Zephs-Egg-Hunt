# EggHunt v2

Two-mode Easter Egg Hunt for Cobble & Commons.

## Modes

### Admin Hidden
Ops place Nexo furniture eggs around spawn with `/egghunt wand`. Each egg is **claimable by every player once**. After claiming, the player sees the `claimed_easter_egg` texture while other players still see the real egg. Eggs stay in the world permanently until cleared.

This works via Paper's `Player.hideEntity()` / `Player.showEntity()`. Each admin egg is actually **two overlapping Nexo furniture entities** at the same location: the real egg and the claimed overlay. Per-player visibility toggles which one you see. Claim state persists across restarts.

### Player Spawning
Every 5 minutes, each online player has a 35% roll for an invisible baby chicken to spawn 8-30 blocks away. Ambient enchantment particles + amethyst chime help locate them. Punch to collect. Single-use (despawns on claim or after 3 min timeout).

## Config

### Eggs
```yaml
eggs:
  common:
    nexo-ids:                        # list = visual variety per tier
      - "orange_easter_egg"
      - "pink_easter_egg"
      - "blue_and_pink_easter_egg"
      - "blue_easter_egg"
    weight: 60
    name: "&aEaster Egg"

claimed-egg-id: "claimed_easter_egg" # what claimed players see
```

### Rewards
```yaml
rewards:
  common:
    - "diamond 5"                          # material amount
    - "golden_apple 1 &eShiny Apple"       # material amount custom_name
    - "cmd: eco give {player} 100"         # console command
    - "cmd: nexo give {player} item_id 1"  # nexo items via command
```

## Commands

| Command | Description |
|---|---|
| `/egghunt start` | Activate event, start player spawner |
| `/egghunt stop` | End event, broadcast leaderboard |
| `/egghunt wand` | Get admin placement wand |
| `/egghunt clear [admin\|player\|all]` | Remove eggs |
| `/egghunt reload` | Reload config |
| `/egghunt status` | Event info |
| `/egghunt leaderboard` | Top collectors |

## Wand

| Action | Effect |
|---|---|
| Right-click block | Place egg (random Nexo ID from selected tier) |
| Left-click | Cycle tier |
| Sneak + right-click | Remove nearest admin egg (3 block radius) |

## Build

```bash
./gradlew shadowJar
```

Requires Paper 1.21.4+, Java 21, Nexo.
