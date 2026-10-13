# The Self Made Flesh: Slices 1 to 4 (Fabric, Minecraft 26.3)

Working title. "Eidolon" is already the name of an existing Minecraft mod, so the mod id here is `selfmade`
(change it in `fabric.mod.json`, `SelfMade.MOD_ID` and the package name whenever you pick a real title).

## What you will see in game
- **The Eidolon** (slice 2, a real creature): every online player has one. It floats near you, always looking at you, and **its body is built from your eight hidden traits**.
  It begins as a small, dithered, flickering shadow and fills in as you become someone. A bare silhouette (mask, cloak, wisps) is always there; each trait grows its own parts once it is strong enough:

  | Trait | What grows | Where it hovers |
  |---|---|---|
  | Violence | claws and horns, hearts of crit sparks | close to you, restless |
  | Empathy | two shield wings in front of it | a guardian at your side |
  | Resolve | bone pauldrons on the shoulders | a guardian at your side |
  | Fear | **no face**, arms too long and too thin; it whispers warnings when something hostile closes in | **ahead of you** (it leaves before you do) |
  | Curiosity | eyes that circle it | far ahead, scouting |
  | Ego | a turning halo and a crown, and it gets bigger | looming above you |
  | Will | a spine of light and a burning red core | right behind you |
  | Attachment | a red locket and threads trailing off it | a wide ring, never leaves |

  Other players can see yours, and you can see theirs. It cannot be hit or pushed. It is fullbright and is not saved to disk (it is simply recreated).
- **Identity Mirror** (now a real screen): you are given one the first time you join a world. Right-click it. The mirror shows your Eidolon as flat pixel art, no numbers, no labels, plus a few lines of text. Things to notice:
  - It **sometimes lies**, more often the prouder (Ego) or more frightened (Fear) you are. A lie is consistent: the picture and the words agree.
  - The reflection **glitches** and drifts out of step with you, more when you are afraid.
  - A **faint figure to one side** is "who you could have been" (the second-best of the five Identity Paths, or the opposite of your best).
  - A line about "far ahead" hints at the **Identity Path** you are heading toward, in poetry, never by name (Protector, Wanderer, Conqueror, Survivor, Creator are not shown).
  - Sometimes **something tall and dark stands behind you**, with eyes. It shows up more if you are afraid or have died a lot.
  Craftable: amethyst shard on top, iron ingot + glass pane + iron ingot in the middle, stick below. `/eidolon mirror` gives a new one.
- **Death echoes**: soul particles where you have died. Die 3+ times in one spot and it gets heavier, with a whispered message.
- A whispered omen the first time a trait settles in. The game never shows numbers.

### Commands
`/eidolon` help, `/eidolon read` (omens in chat), `/eidolon mirror`, `/eidolon debug` (raw numbers).
**Dev tools for looking at every kind of Eidolon without grinding** (anyone can run them for now, lock them down before a public release):
`/eidolon set <trait> <amount>` (try `violence 60`, then `empathy 80`, then `fear 90`), `/eidolon reset`, `/eidolon respawn`.
A score of about 10 is enough for a trait's first parts to appear; 45+ is strong.

There is **no** Inner World, final boss or Self-Actualization yet (slice 5).

## What it watches
| Trait | Signal |
|---|---|
| Violence | Kills (innocents like villagers count most, animals least). Repeating the same kill quickly is worth less each time, so mob farms barely move you. |
| Fear | Dying; running away after nearly dying to a creature. |
| Resolve | Holding your ground after a close call; killing the thing that nearly killed you; going back to where you died. |
| Will | Going back to where you died within 10 minutes; pressing on after a close call. |
| Curiosity | Entering a 64x64-block patch of the world you have never stood in. |
| Ego | Killing a boss (150+ max health: dragon, wither, warden); wearing very strong armour (slowly). The *Hollow Crown* ability also feeds it. |
| Empathy | Killing the thing that was hurting another player who was close to dying. |
| Attachment | Losing a tamed pet; being saved by someone else. |
| Fear (more) | Also falls when you reclaim a Grave of You (see below). |

Old behaviour fades slowly (only while you are online), so people can change.

## Getting the jar
Upload everything in this folder to your GitHub repository (the `build` workflow in `.github/workflows` builds it on every commit).
When the run is green, download the **selfmade-jar** artifact from the bottom of the run page and unzip it.
To build on your own computer instead: JDK 25 and Gradle 9.5.1 or newer, then `gradle build`; the jar lands in `build/libs/`.

## Playing it
Minecraft Java 26.3, Fabric Loader, and Fabric API for 26.3 (0.160.5+26.3 or newer) in the `mods` folder, together with `selfmade-0.5.0.jar`.
**Remove any older `selfmade-*.jar` from the `mods` folder first.**

## If the build fails
Send the first error from the log. The usual cause is a Minecraft method name that changed between versions.
Fabric's current recommended versions are listed at https://fabricmc.net/develop/.

## Project layout (slices 1-3)
`src/main` is code that runs everywhere (identity engine, the Eidolon entity, the mirror's data and packet).
`src/client` is client-only (the Eidolon's model and renderer, the mirror screen). It is a separate source set so a dedicated server can never load it.
The mirror's reading is made on the **server** and sent as one packet, so the client never receives the real trait scores.

## Slice 3: physicalized identity, memories, and death that accumulates

### Memory Fragments (items)
Five items, each earned **once per player** by something that actually happened. The game whispers when one forms (it is put in your inventory the next time you are alive).

| Fragment | Earned by |
|---|---|
| Mortality | your first death |
| Inferno | your first time in the Nether |
| Triumph | killing a boss (150+ max health), or beating The Other You |
| Compassion | killing the thing that was hurting another player who was nearly dead |
| Grief | losing a tamed pet |

Held in the air a fragment only remembers out loud. The Identity Mirror also now mentions a memory you carry.

### Memory Altars
There is no custom block (it keeps the build simple): an altar is a **lectern standing on a block of crying obsidian**.
- Right-click it holding a fragment to place it. It holds **three**, one of each kind.
- Sneak + empty hand takes the last one back. Break the altar and the fragments pop out.
- An altar belongs to whoever first uses it. Other players get "This altar remembers someone else."
- **Every pair of memories in the same altar makes an ability** (3 memories = 3 abilities). Take one out and its abilities go quiet.

| Pair | Ability |
|---|---|
| Mortality + Inferno | **Ashen Return**: once per 10 minutes, a fatal hit leaves you on 3 hearts instead |
| Mortality + Triumph | **Deathbed Fury**: Strength II and Resistance when under 35% health |
| Mortality + Compassion | **Hospice**: players within 10 blocks (you too) regenerate |
| Mortality + Grief | **Wake**: near one of your own death sites, you regenerate and resist damage |
| Inferno + Triumph | **Triumphant Flame**: fire resistance; Strength in the Nether |
| Inferno + Compassion | **Ember Heart**: a fresh Absorption shield every minute |
| Inferno + Grief | **Cinder Sight**: night vision in the Nether and below y=40 |
| Triumph + Compassion | **Shepherd**: players within 16 blocks (you too) get Speed |
| Triumph + Grief | **Hollow Crown**: permanent Strength, but it slowly feeds your Ego |
| Compassion + Grief | **Mourning Veil**: Resistance when under half health |

### Death does not reset you
1. **3 deaths within 12 blocks**: the place is haunted (slice 1) and now sometimes an **Echo of you** (a flickering shade named for what killed you) walks out and hunts you. At most two at once; they fade after a minute.
2. **5 deaths within 12 blocks**: **The Grave of You** forms: four lantern pillars, a tombstone, and a floating "Here lies <you>" over it. It only fills empty air, so it never eats a build.
3. Walk within 14 blocks of an unfinished grave and it wakes: a **Death-Eidolon** (big, 140 health, built from your fear and violence) plus echoes of how you died. If you leave, it fades and wakes again when you return.
4. **Defeat the Death-Eidolon to reclaim part of yourself**: half your Fear is lifted, you gain Will and Resolve, 300 XP, a new Fragment of Mortality, up to two pieces of the gear you died with (at 25% durability), and the haunting around that spot ends.

### The world changes with who you are
| Trait | At about this score | What happens |
|---|---|---|
| Fear | 20+ | A shadow wanders around you on its own. It only moves while you stand still, and keeps its distance |
| Fear | 35+ | Hostile mobs near you come out the wrong size, once each (everyone sees it) |
| Fear | 45+ | Your Eidolon sometimes steps to the wrong side and turns its back (your reflection moving incorrectly) |
| Fear | 45+ | **Watchers** appear 18-28 blocks away: two dark bricks and a carved face turned toward you. Max 3, and they are taken down when your fear drops under 25 |
| Fear | 50+ | Darkness smoke follows you, and now and then the Darkness effect pulses |
| Ego | 25+ | Villagers within 10 blocks stare at you: angry if you are more violent than kind, delighted if not |
| Ego | 30+ | Every ~12 minutes (random), a **statue of you** (your Eidolon in its proudest form) goes up nearby with your name over it. Max 6 |
| Ego | 20+ | After a boss falls, or every 25th hostile kill: a gold block and a floating "Conquered by <you>" |
| Ego | 70+ | **The Other You** appears (every ~15 minutes at most, one at a time): hostile, tall, crowned, hunts only you. Beat it and your Ego halves |

Statues, claims and grave names are saved as plain data and their entities are recreated when you walk near, so they survive restarts. All of this belongs to the player whose identity caused it.

### Dev commands (new)
`/eidolon memory <mortality|inferno|triumph|compassion|grief>`, `/eidolon grave` (raises a grave six blocks east of you; walk up to it), `/eidolon other`, `/eidolon statue`. `/eidolon debug` now also lists memories, abilities, graves and watchers.
Quick test: `/eidolon memory mortality`, `/eidolon memory grief`, build a lectern on crying obsidian, place both: Wake appears. For the world effects: `/eidolon set fear 60` (watch the shadow and your Eidolon), `/eidolon set ego 80` then wait or run `/eidolon other` and `/eidolon statue`.

### Tuning knobs
Thresholds are numbers in `Physicalize.java` (the 20/35/45/50 and 25/30/70 above), grave rules in `Graves.java` (`DEATHS_TO_FORM`, `RADIUS`, boss stats in `ShadeEntity.setup`), ability numbers in `Abilities.java`. Reclaimed gear is a plain worn copy (no enchants); if you think it is exploitable, delete the `worn(...)` loop in `Graves.reclaim`.

## Slice 4: Identity Evolution, and the starting book

### Field Notes (the book)
Every player gets **Field Notes** once (existing players get it the next time they join; it is also craftable: book + amethyst shard, and `/eidolon book` gives one). Right-click to read: a ten-page dark book with Next/Previous buttons that explains how the mod works (the shadow, the mirror, what it watches, memories and altars, death and graves, the world answering fear and ego, and the ways of becoming) without ever saying what any particular player is. Its text is in `BookText.java`, the screen is `BookScreen.java`. If the client does not have the mod, it reads the pages out in chat instead.

### Identity Paths
Instead of a skill tree you lean toward one of the five paths from slice 2 (a path's score is the geometric mean of its two traits). The game commits you to the leading path once its score reaches 6, and only switches if another path gets 25% ahead, so it does not flicker. Lean away entirely and the gifts go quiet. **Nothing is named to the player**: gifts arrive as whispers, and the mirror and Eidolon show the shape.

Each path has three powers, given at path score 6, 14 and 26:

| Path | 1st | 2nd | 3rd |
|---|---|---|---|
| **Protector** (Empathy + Resolve) | **Barrier** *(active)*: a ring of glass around you for ~11 seconds, allies inside regenerate | **Redirect** *(passive)*: when a friend within 12 blocks loses health, 40% of it is healed and moves to you (never below 2 hearts) | **Revive** *(passive)*: if a friend within 16 blocks would die, they come back at 4 hearts and you pay half your health (once per 10 minutes). Also makes the Barrier last 20 seconds |
| **Wanderer** (Curiosity + Resolve) | **Sense** *(active)*: points to the nearest unwalked 64-block area | **Step** *(active)*: teleport up to 10 blocks where you look | **Orient** *(active)*: points to the door you came into this dimension by |
| **Conqueror** (Ego + Violence) | **Intimidate** *(passive)*: small hostile mobs within 8 blocks lose interest and slow down | **Territory** *(active)*: claim a 12-block area (max 3). Inside it you get Strength and Resistance and hostile mobs are weakened | **Giant-slayer** *(passive)*: Strength II near big hostile mobs (III and Resistance near 100+ health ones) |
| **Survivor** (Fear + Resolve) | **Last stand** *(passive)*: under 40% health Strength and Resistance; under 20% also Speed II and Regeneration | **Detect** *(passive)*: hostile mobs within 20 blocks glow | **Escape** *(active)*: 5 seconds of Speed IV, Resistance and invisibility, and mobs lose track of you |
| **Creator** (Curiosity + Empathy) | **Construct** *(active)*: a small construct (1 minute) that follows you and attacks hostile mobs | **Reshape** *(active)*: in front of a wall it carves a 6-block tunnel for 8 seconds; in open air it lays a glass bridge | **Life** *(passive)*: constructs last 3 minutes, heal, and you can have two |

**Reaching for an active power:** sneak and right-click the Identity Mirror. If you have more than one active power, sneak-use while **looking down at your feet** to cycle to the next (the game whispers which one you have chosen), then sneak-use normally to use it. Powers have cooldowns ("You are still empty from the last time").

**The Eidolon takes the shape of your path** (Protector: guardian wings and shoulder plates; Wanderer: more and more eyes; Conqueror: crown, horns, armour; Survivor: faceless, long, feral, with a red core; Creator: eyes, wings and trailing threads). This works by raising the traits that make up that body, so no new model was needed. The mirror's picture changes to match; its words stay honest.

Not done exactly as written in the design notes: **Sense** points to unexplored *areas*, not specific structures (finding real structures on demand is slow and fragile); **Barrier, bridges, tunnels** are temporary blocks put back by `TempBlocks` (everything is restored when the server stops); "Create artificial life" is the **Life** upgrade to constructs.

### Dev commands (new)
`/eidolon path` (the one place the game names your path, plus scores and powers), `/eidolon rest` (every power ready), `/eidolon book`. To see a path quickly: `/eidolon set empathy 40` and `/eidolon set resolve 40`, wait a second, then sneak-right-click the mirror. Territories and constructs need an open space; Reshape needs a wall in front of you.

### Tuning knobs
Thresholds are `PathPowers.TIERS`; cooldowns are the numbers in the `Power` enum; the body shape comes from `formTraits` in the same file.

## Where slices 2 to 4 are most likely to need a fix
Neither slice could be compiled where it was written (the Java was only checked for syntax and for references between the mod's own classes), so the first build is the real test. If it fails, send the first error. The likeliest spots, all one-line fixes:
- **Slice 3, new Minecraft names:** `Fx.java` (every `MobEffects.*` constant, in one place); `EntityTypes.VILLAGER/WANDERING_TRADER` in `Physicalize` (same style slice 1 already used); `EntityType.Builder.noSave()` and `.fireImmune()` in `ModEntities`; `Identifier.tryParse` and `BuiltInRegistries.ITEM.getOptional` in `Graves.worn`; `OwnableEntity.getOwner()` in `SelfMade.onDeath`; `Level.hasChunkAt` and `Attributes.SCALE`.
- **Slice 3, Fabric API:** `UseBlockCallback` (the altar, in `Memories`) and `ServerLivingEntityEvents.ALLOW_DEATH` (`SelfMade.allowDeath`).
- **Slice 4, new Minecraft names:** `Fx.java` (new effects `SLOWNESS`, `WEAKNESS`, `GLOWING`, `INVISIBILITY`); `p.connection.teleport(x, y, z, yRot, xRot)` for Step in `PathPowers.step` (the likeliest one); `Mob.getNavigation().moveTo(Entity, double)` in `ShadeEntity`; `BlockState.getDestroySpeed`, `getFluidState` and `ServerLevel.getBlockEntity` in `TempBlocks.carve`; `Direction.getClockWise`, `BlockPos.relative` and `Entity.getDirection` in `PathPowers.reshape`.
- **Slice 4, client:** `BookScreen` (the first screen with real buttons: `Button.builder(...).bounds(...).build()`, `addRenderableWidget`, `Button.active`) and the `BookPayload` receiver in `SelfMadeClient`. The client source set has not been compiled yet at all, so errors there will show up on the first build that gets past the main one.
- **Slice 2:** `EntityType.Builder` calls in `ModEntities`; `MobRenderer`, `EntityModel` and `ModelPart` use in `EidolonRenderer`, `ShadeRenderer` and `EidolonModel`; the screen calls in `MirrorScreen`; the packet codec in `MirrorPayload`.
