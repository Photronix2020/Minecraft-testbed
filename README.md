# Core Siege (Minecraft Forge 1.20.1)

A self-contained tower defense game, Core Siege, for Minecraft Java **1.20.1 / Forge 47+**. Join a world and play, with no setup commands.

## How to play
1. **The battleground:** in a **brand-new world** it is built for you automatically around where you first spawn (turn this off in `config/towerdefense-common.toml`; existing worlds are never touched). Anywhere else, type `/tdarena` while standing on open ground (singleplayer, or ops on a server). It builds a walled arena with a spawn pad and the **Defense Core** in the middle, dirt roads lit by lamp posts, gatehouses and a few buildings, sets the world spawn there, and adds a world border. `/tdarena noborder` removes the border. You can also skip this and place the Core yourself on open, flat ground.
2. **Right-click the Core** to start. (New players automatically get a Core, 20 coins and a crafting table. `/tdkit` gives the kit again.)
3. You get 30 seconds of prep time, then **12 waves** of glowing monsters spawn in a ring ~22 blocks from the Core and walk to it. Boss waves are 6 and 12.
4. **Craft towers** at a crafting table with coins only (shapeless recipes). Open the **recipe book**: towers you haven't unlocked are hidden, and **new towers unlock as you clear waves** (with the usual "recipe unlocked" toast). `/tdrecipes` lists every tower, its cost and unlock wave.

| Tower | Cost | Unlocks | Does |
|---|---|---|---|
| Arrow | 3 coins | start | fast, single target |
| Frost | 5 coins | wave 2 | light damage, slows enemies in an area |
| Flame | 4 coins | wave 3 | very fast, short range |
| Cannon | 7 coins | wave 4 | slow, big area damage |
| Storm | 9 coins | wave 6 | chain lightning hits up to 5 enemies |
| Sniper | 8 coins | wave 8 | huge range, heavy damage |
| Wither | 9 coins | wave 10 | applies Wither, deadly over time |

5. **Shoot monsters yourself** with the **Siege Rifle** (in your starter kit): a bolt-action hitscan rifle. Right-click to fire (about one shot a second); **hold Sneak to scope in** (zoom, and a perfectly accurate shot, otherwise there's a little hip-fire spread). Each shot does 14 damage, **headshots do double**, and the bullet **pierces up to 3 monsters** in a line (less damage each). Range 100, unlimited ammo, only hurts monsters. Recipe: 2 iron ingots + 4 coins.
6. Place towers along the enemy path. Killing monsters drops coins; clearing a wave gives a bonus.
7. Every monster that touches the Core damages it (Core health 20; vindicators 2, ravagers 5). Sneak + right-click the Core during prep to start the next wave early.

While a game runs it is **night** (the clock is restored afterwards) and nearby players get **Night Vision**; both can be switched off in the config. Natural monsters (zombies, slimes, creepers...) can't spawn within ~80 blocks of a Core, and leftover ones are cleared when a Core is placed or a game starts, so the area stays quiet until the waves begin. World difficulty must not be Peaceful. Works in singleplayer and on servers (everyone near the Core shares the game). Defense Core recipe (if you need another): 8 iron ingots around a diamond.

## Using another gun mod (TaCZ, etc.)
Core Siege works alongside other weapon mods: wave enemies drop coins whoever kills them. In `config/towerdefense-common.toml`:
- `giveSiegeRifleInKit = false` leaves the Siege Rifle out of the starter kit.
- `extraKitItems` adds items to the kit (new players and `/tdkit`), in `modid:item{nbt} xCOUNT` form. Entries for mods that aren't installed are skipped. To get an item's exact id and NBT, hold it and run `/data get entity @s SelectedItem`. TaCZ gun example (the gun id comes from whichever gun pack you use): `'tacz:modern_kinetic_gun{GunId:"pack:gun_id",GunFireMode:"AUTO",GunCurrentAmmoCount:30,HasBulletInBarrel:1b}'`

## Recipe book
Only Core Siege recipes (towers, Defense Core, Siege Rifle) appear in the recipe book. The mod replaces Minecraft's automatic recipe-unlock advancements (`data/minecraft/advancements/recipes/**`) with inert versions, and removes vanilla recipes from a player's book at login (`cleanRecipeBookAtLogin` in the config). Vanilla recipes can still be crafted by hand. Delete the advancements folder from the jar's source to restore normal unlocking.

## Uneven or non-flat terrain
`/tdarena` and the automatic arena level everything inside the wall to one height: hills are cut away, and valleys, lakes and caves are filled up to 48 blocks deep so the arena and wall never float. A flat world needs the least work, but it isn't required. If you place a Core by hand on rough ground, enemies spawn on whatever ground they can find near the Core's height and fall back to closer rings, so building on flatter ground still plays best.

## Install
Drop `dist/core-siege-1.5.0.jar` into the `mods` folder of a Minecraft 1.20.1 Forge profile.

## Build from source
Needs JDK 17.

    ./gradlew build        # jar appears in build/libs/
    ./gradlew runClient    # dev client
    ./gradlew runGameTestServer   # headless smoke test: spawns a game, checks towers clear wave 1

## Rebalancing
- Tower stats: `TowerStats.java`; costs and unlock waves: `TowerType.java` (keep the recipe JSON in `data/towerdefense/recipes/` in sync with the cost).
- Waves, enemy types, Core health, timings: top of `CoreBlockEntity.java`.
- Arena size, wall and buildings: `ArenaBuilder.java`.
- Placeholder textures are generated by `tools/generate_textures.py` (replace the PNGs with real art any time).

## Publishing to CurseForge
Create a project of type *Mods*, game version 1.20.1, modloader Forge, and upload the jar from `dist/` (or `build/libs/`). Add a logo and screenshots on the project page.
