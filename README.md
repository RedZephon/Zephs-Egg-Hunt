# Zeph's EggHunt

Give your player's an activity to do this Easter! An Easter Egg hunt built for Nexo so you get real Egg Models for players to hunt. Hide eggs around your world for players to find, or let the server scatter them automatically near players as they explore. Both modes work at the same time.

## How It Works

### Event Eggs (Admin Hidden)
Use a placement wand to hide Nexo furniture eggs around your world. Every player can find and claim each egg once. Once someone collects an egg, it changes to a "claimed" look just for them.

### Random Eggs (Player Spawning)
Every few minutes, each online player has a chance of an egg popping up somewhere nearby. A chime plays when it spawns, and optional particles and ambient sounds pulse to help players track it down. If nobody grabs it, it despawns after a timeout.

### The Wand
You get a blaze rod wand via `/egghunt wand`. Right-click a block to place an egg facing your direction. Left-click to cycle tiers — there's also a **Random** option that rolls a weighted tier each time you place. Sneak + right-click removes the nearest egg.

### Tiers and Visual Variety
Set up as many tiers as you want. Each tier can have multiple Nexo furniture IDs, so you get visual variety while sharing the same reward pool. The plugin picks one randomly when placing.

### Rewards
Rewards are just strings in the config. No nested YAML, no type declarations:
```yaml
- "diamond 5"
- "golden_apple 1 &eShiny Apple"
- "cmd: eco give {player} 500"
```
Equal chance per line. Want one to show up more? Add it twice.

### Action Bar
Action bar shows the collection process. Action bar shows up in the world set by the config. By default, it's set to "event" if you plan to hide the eggs in an event world. You can change it to "world" to make the action bar show up in the main world. Collecting a random egg shows a quick one-shot confirmation. Both are configurable.

### Stats and Leaderboard
Event eggs and random eggs are tracked separately. Players can check their own stats with `/egghunt stats`, and the leaderboard shows top collectors across both modes. Top hunters are announced when the event ends.

### PlaceholderAPI
Optional — everything works without it. But if you have PlaceholderAPI, you get placeholders for player counts, event status, leaderboard positions, and more. Useful for scoreboards, tab, holograms, or whatever you're running.

| Placeholder | What it returns |
|---|---|
| `%egghunt_collected%` | Player's total eggs |
| `%egghunt_event_collected%` | Player's event eggs |
| `%egghunt_random_collected%` | Player's random eggs |
| `%egghunt_total%` | Server-wide total collected |
| `%egghunt_active%` | Whether the event is running |
| `%egghunt_rank%` | Player's leaderboard position |
| `%egghunt_top_1_name%` | #1 collector's name |
| `%egghunt_top_1_count%` | #1 collector's count |

## Commands

| Command | Permission | What it does |
|---|---|---|
| `/egghunt stats` | `egghunt.stats` | Your personal stats breakdown |
| `/egghunt leaderboard` | `egghunt.leaderboard` | Top collectors |
| `/egghunt start` | `egghunt.admin` | Start the event |
| `/egghunt stop` | `egghunt.admin` | End the event, show results |
| `/egghunt wand` | `egghunt.admin` | Get the placement wand |
| `/egghunt clear [admin\|player\|all]` | `egghunt.admin` | Remove eggs by mode |
| `/egghunt cleanup` | `egghunt.admin` | Clean up orphaned entities |
| `/egghunt reload` | `egghunt.admin` | Reload config |
| `/egghunt status` | `egghunt.admin` | Event info at a glance |
| `/egghunt debugspawn` | `egghunt.admin` | Force-spawn a test egg nearby |
| `/egghunt reindex` | `egghunt.admin` | Re-link entity IDs after chunk issues |

## Permissions

| Permission | Default | Description |
|---|---|---|
| `egghunt.admin` | op | All admin commands |
| `egghunt.collect` | true | Collect eggs |
| `egghunt.stats` | true | View personal stats |
| `egghunt.leaderboard` | true | View leaderboard |

## Requirements

- **Paper 1.21.4+** (not Spigot — uses Paper APIs)
- **Java 21**
- **[Nexo](https://nexomc.com/)** for the egg furniture models
- **[ProtocolLib](https://www.spigotmc.org/resources/protocollib.1997/)** for per-player visuals

**Optional:** [PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.6245/) for scoreboards, tab, holograms, etc.

## Egg Models
This plugin doesn't include any models — you'll need Nexo furniture items set up for your eggs. The ones we use on Cobble & Commons are from MCModels and work great out of the box:

[**Easter Egg Collection on MCModels**](https://mcmodels.net/profile/orders/c6483ef6-aaf1-48df-9fa2-9cb45986544b)

Highly recommended if you want a polished look without building your own. Just drop them into your Nexo config and match the furniture IDs in the EggHunt config.

## Build

```bash
./gradlew shadowJar
```

Output JAR is in `build/libs/`.

## Try It Live
Want to see it in action before setting it up? Come check it out on **Cobble & Commons** — the server where this plugin was built and tested. We run seasonal egg hunts with both modes active.

**Server Address:** `mc.cobblecommons.com`
