# Stilwerk Stage 1+2 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the Stilwerk NeoForge 1.21.1 mod: architect wand, selections, family-aware style swap with mix palettes and templates, blueprint library with .nbt/.schem/.litematic import, preview, batched apply, survival costs (inventory + optional ProjectE EMC), persistent undo.

**Architecture:** Pure, deterministic planners (`SwapPlanner`, `BlueprintPlanner`) turn (world view + selection/blueprint + mapping + seed) into a `ChangeSet`. The client runs them for the ghost preview; the server re-runs them on request, settles costs per group through a `Gate`, applies with a tick-budgeted `Applier` and records `UndoHistory`. Only small requests cross the network (selection descriptor, mapping, mix definitions, seed); blueprints are uploaded in 30 000-byte chunks.

**Tech Stack:** Java 21, NeoForge 21.1.219 (ModDevGradle 2.0.148, Parchment 2024.11.17), GameTests, Python 3 for test fixtures, optional ProjectE PE1.1.0 via reflection.

**Spec:** `docs/superpowers/specs/2026-11-18-stilwerk-design.md`

**Plan convention:** Interfaces, data formats, algorithms and test assertions below are binding. Straightforward method bodies are written during execution; every non-obvious algorithm is given as code here.

---

## Conventions for every task

- Build/test command: `./gradlew runGameTestServer` (from `C:\modwork\stilwerk`, Bash). Expected end line: `All N required tests passed :)` (N grows per task). Compile-only check: `./gradlew compileJava`.
- GameTests live in `src/main/java/com/stilwerk/test/` (excluded from the jar), class annotations `@GameTestHolder(Stilwerk.MODID) @PrefixGameTestTemplate(false)`, template `"empty"` (16×16×16, stone floor at y=0) written by `tools/make_empty_structure.py`.
- Java files are written with the Write tool (Bash heredocs with apostrophes break here).
- Commit after each task: `git add -A && git commit -m "<type>: <summary>"` + the `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>` trailer.
- All user-visible text is translatable (`assets/stilwerk/lang/en_us.json` + `de_de.json`), keys prefixed `stilwerk.`.

## File map

```
build.gradle, gradle.properties, settings.gradle
src/main/templates/META-INF/neoforge.mods.toml
src/main/java/com/stilwerk/
  Stilwerk.java                    mod entry, registries, event wiring
  config/ServerConfig.java         limits, budget, undo, upload, flood radius, ProjectE toggle
  config/ClientConfig.java         ghost alpha, max ghost blocks
  item/ArchitectWandItem.java      use/useOn → client hooks; server returns CONSUME
  item/WandMode.java               SELECT, SWAP, BLUEPRINT (+ codec, data component)
  item/WandHooks.java              static client hook slots (no client class refs in common code)
  core/Selection.java              sealed: Box | Flood; resolve() → Resolved
  core/ResolvedSelection.java      position set + bounds, deterministic iteration
  core/AutoSelect.java             flood fill over built blocks
  core/NatureFilter.java           natural test incl. tree logs (per-run cache)
  core/Protection.java             protected-block test
  core/Groups.java                 multi-block partner (door halves, bed parts)
  core/Change.java                 record
  core/ChangeSet.java              groups of changes, inverse, NBT save/load
  core/Costs.java                  item + count per state
  core/Gate.java                   interface admit/settled
  core/CreativeGate.java
  core/MaterialLedger.java         survival gate: inventory → EMC, refunds
  core/Applier.java                tick budget, two passes, progress
  core/UndoHistory.java            per-player persistent stack
  family/Shape.java                enum + fallback chains
  family/Family.java               record
  family/FamilyIndex.java          build + lookup (singleton per side, rebuilt on reload)
  family/FamilyOverrides.java      datapack JSON loader
  family/StateTransfer.java        property copy
  mix/MixPattern.java              enum
  mix/MixEntry.java                record
  mix/MixPalette.java              record + pick()
  mix/PickContext.java             bounds + edge test
  mix/Hash.java                    splitmix64 helpers
  mix/Mixes.java                   built-ins + config dir loader/saver
  swap/Target.java                 KEEP | FAMILY | MIX
  swap/SwapMapping.java            family/shape → Target, codecs
  swap/SwapPlanner.java            plan(), census(), mapState()
  swap/Template.java               record + codec
  swap/Templates.java              built-ins, wood order, config dir + datapack loader
  blueprint/Blueprint.java         size, palette, cells (VOID/air/state), BE tags, meta
  blueprint/BlueprintMeta.java     record
  blueprint/Transform.java         rotate/mirror blueprint
  blueprint/BlueprintIO.java       vanilla structure .nbt (+ stilwerk meta) read/write; network bytes
  blueprint/BlueprintPlanner.java  blueprint → ChangeSet at origin
  blueprint/importer/BlockStateUpgrader.java
  blueprint/importer/VanillaNbtImporter.java
  blueprint/importer/SpongeSchemImporter.java
  blueprint/importer/LitematicImporter.java
  blueprint/importer/Importers.java   by extension → Blueprint or ImportError
  compat/ProjectECompat.java       reflection: emc get/set/sync, value, knows
  net/Net.java                     payload registration
  net/SwapRequest.java, PlaceRequest.java, BlueprintChunk.java, ConfirmRequest.java, UndoRequest.java (C2S)
  net/MaterialReport.java (S2C)
  net/ServerOps.java               validate → plan → report/confirm → apply → undo
  net/UploadBuffer.java            chunk assembly + per-player hash cache
  command/StilwerkCommand.java     /stilwerk undo
  client/ClientSetup.java          keys, renderers, hooks, screens registration
  client/Keys.java
  client/ClientState.java          selection, mode, preview, placement, clipboard
  client/WandClient.java           wand click handling (corners, flood, menus)
  client/SelectionRenderer.java
  client/GhostMesh.java            builds/draws a VertexBuffer from a ChangeSet
  client/PreviewController.java    recompute preview, before/after, confirm/cancel
  client/PlacementController.java  ghost follows crosshair, rotate/mirror/height
  client/ClientLibrary.java        folder scan, cache, save/rename/delete, import
  client/Thumbnails.java           top-down MapColor image → DynamicTexture
  client/screen/ModeWheelScreen.java
  client/screen/SwapScreen.java
  client/screen/FamilyPickerScreen.java
  client/screen/MixEditorScreen.java
  client/screen/LibraryScreen.java
  client/screen/BlueprintMenuScreen.java
  client/screen/NameInputScreen.java
  client/screen/MaterialReportScreen.java
  test/*                           GameTests + DevScene (excluded from jar)
src/main/resources/
  assets/stilwerk/lang/{en_us,de_de}.json
  assets/stilwerk/models/item/architect_wand.json, textures/item/architect_wand.png
  data/stilwerk/recipe/architect_wand.json
  data/stilwerk/tags/block/{natural,protected}.json
  data/stilwerk/structure/empty.nbt                      (test only)
  data/stilwerk/stilwerk_fixtures/*.{nbt,schem,litematic} (test only)
tools/make_empty_structure.py, tools/make_fixtures.py, tools/make_wand_texture.py
```

---

### Task 1: Project scaffold + smoke GameTest

**Files:** Create `build.gradle`, `gradle.properties`, `src/main/templates/META-INF/neoforge.mods.toml`, `Stilwerk.java`, `tools/make_empty_structure.py`, `test/StilwerkGameTests.java`, lang files, item model/texture/recipe, `item/ArchitectWandItem.java` (stub), `tools/make_wand_texture.py`.

- [ ] Copy Slab Mix `build.gradle`, rename ids (`stilwerk`, `com.stilwerk`), keep `quickPlay` + `stilwerk.devScene` property, add `-PwithProjectE=<jar>` → `localRuntime files(...)`, exclude `com/stilwerk/test/**`, `data/stilwerk/structure/**`, `data/stilwerk/stilwerk_fixtures/**` from the jar.
- [ ] `gradle.properties`: neo_version=21.1.219, mod_id=stilwerk, mod_name=Stilwerk, mod_license=All Rights Reserved, mod_version=1.0.0, mod_group_id=com.stilwerk (author Lonitys in mods.toml).
- [ ] `make_empty_structure.py` from Slab Mix with size 16 and output `data/stilwerk/structure/empty.nbt`; run it.
- [ ] Register item `architect_wand` (stack size 1) via DeferredRegister; creative tab entry in TOOLS_AND_UTILITIES. Recipe shaped `["  A", " G ", "S  "]`, A=amethyst_shard, G=gold_ingot, S=stick. Texture 16×16 generated by `tools/make_wand_texture.py` (stick handle, gold band, amethyst tip).
- [ ] Smoke test `smoke(GameTestHelper)` → `succeed()`.
- [ ] Run `./gradlew runGameTestServer` → `All 1 required tests passed`. Commit `chore: project scaffold`.

### Task 2: Config

**Files:** `config/ServerConfig.java`, `config/ClientConfig.java`, register in `Stilwerk`.

ServerConfig (ModConfigSpec, type SERVER): `maxBlocksSurvival`=20000 (1..1_000_000), `maxBlocksCreative`=200000, `blocksPerTick`=4000 (100..100000), `undoSteps`=20 (0..100), `undoMaxBlocks`=1_000_000, `maxUploadBytes`=4_194_304, `floodRadius`=48 (4..256), `useProjectE`=true.
ClientConfig (CLIENT): `ghostAlpha`=0.6 (0.1..1.0), `maxGhostBlocks`=60000.
Static accessors with safe defaults when the config is not loaded (GameTests load SERVER config; accessor pattern `ServerConfig.blocksPerTick()`).

- [ ] Write, compile, commit `feat: server and client config`.

### Task 3: NatureFilter, Protection, Groups (+ tags)

**Files:** `core/NatureFilter.java`, `core/Protection.java`, `core/Groups.java`, `data/stilwerk/tags/block/natural.json`, `protected.json` (empty `values: []`), test `test/CoreFilterTests.java`.

`natural.json` values: `#minecraft:dirt`, `#minecraft:sand`, `#minecraft:base_stone_overworld`, `#minecraft:base_stone_nether`, `minecraft:end_stone`, `#c:ores` (required:false), `#minecraft:flowers`, `#minecraft:saplings`, `#minecraft:replaceable`, `#minecraft:ice`, `#minecraft:snow`, `minecraft:gravel`, `minecraft:clay`, `minecraft:water`, `minecraft:lava`, `minecraft:bedrock`, `minecraft:grass_block`, `minecraft:mycelium`, `minecraft:podzol`, `minecraft:mud`, `minecraft:moss_block`, `minecraft:sugar_cane`, `minecraft:cactus`, `minecraft:kelp`, `minecraft:kelp_plant`, `minecraft:seagrass`, `minecraft:tall_seagrass`, `minecraft:vine`, `minecraft:pointed_dripstone`, `minecraft:dripstone_block`, `minecraft:amethyst_block`, `minecraft:budding_amethyst`, `minecraft:obsidian`, `minecraft:magma_block`, `minecraft:soul_sand`, `minecraft:soul_soil`, `minecraft:nether_wart_block`, `minecraft:warped_wart_block`, `minecraft:crimson_nylium`, `minecraft:warped_nylium`, `minecraft:mushroom_stem`, `minecraft:red_mushroom_block`, `minecraft:brown_mushroom_block`.

```java
public final class NatureFilter {
    /** One instance per planning run; caches tree decisions per log position. */
    public NatureFilter(BlockGetter level) {...}
    public boolean isNatural(BlockPos pos, BlockState state) {
        if (state.isAir()) return false;
        if (state.is(StilwerkTags.NATURAL)) return true;
        if (state.hasProperty(LeavesBlock.PERSISTENT)) return !state.getValue(LeavesBlock.PERSISTENT);
        if (state.is(BlockTags.LOGS)) return isTreeLog(pos);
        return false;
    }
    // isTreeLog: BFS over 6-connected positions whose state is in BlockTags.LOGS, max 512 visited;
    // true if any visited log has a 6-neighbour with LeavesBlock.PERSISTENT == false.
    // All visited positions get the same cached answer (Long2BooleanOpenHashMap).
}
public final class Protection {
    public static boolean isProtected(BlockGetter level, BlockPos pos, BlockState state) {
        if (state.getDestroySpeed(level, pos) < 0) return true;
        if (state.is(StilwerkTags.PROTECTED)) return true;
        return state.hasBlockEntity() && !(state.getBlock() instanceof SignBlock);
    }
}
public final class Groups {
    /** The other half of a two-block block, or empty. Lower door half → above; upper → below; bed foot → facing; head → opposite. */
    public static Optional<BlockPos> partner(BlockPos pos, BlockState state) {...}
    /** True for the half that owns the group (lower door half, bed foot). */
    public static boolean isPrimary(BlockState state) {...}
}
```

Tests (each builds blocks with `h.setBlock` inside the 16³ template):
- `naturalTreeLogIsNature`: oak_log column y1..3 + oak_leaves (persistent=false) at y4 → `isNatural(log)` true.
- `builtLogsAreNotNature`: oak_log column without leaves → false; with persistent=true leaves → false.
- `leavesPersistence`: leaves persistent=false → natural; true → not.
- `protectedBlocks`: chest → protected; bedrock → protected; oak_sign → not; oak_planks → not.
- `doorPartners`: placed oak door: lower partner = above, upper partner = below, `isPrimary(lower)` true.
- [ ] Write tests → run (fail: classes missing) → implement → run (pass, 6 tests) → commit `feat: nature filter, protection, block groups`.

### Task 4: Selection, ResolvedSelection, AutoSelect

**Files:** `core/Selection.java`, `core/ResolvedSelection.java`, `core/AutoSelect.java`, test `test/SelectionTests.java`.

```java
public sealed interface Selection permits Selection.Box, Selection.Flood {
    record Box(BlockPos a, BlockPos b) implements Selection {}
    record Flood(BlockPos start, int radius) implements Selection {}
    BoundingBox roughBounds();                       // Box: exact; Flood: start ± radius
    ResolvedSelection resolve(BlockGetter level, int limit);
    StreamCodec<ByteBuf, Selection> STREAM_CODEC;     // byte tag 0=Box,1=Flood
}
public final class ResolvedSelection {
    public BoundingBox bounds();
    public int size();
    public boolean contains(BlockPos p);
    public boolean truncated();                      // hit the limit
    public void forEach(Consumer<BlockPos.MutableBlockPos> c); // order: y, z, x ascending
}
```
Box resolve: all positions in the box (truncated if volume > limit → resolve returns empty + truncated=true; ServerOps rejects). Flood resolve via `AutoSelect.flood(level, start, radius, limit)`: BFS over 26-neighbours; a position joins if within `radius` (Chebyshev) of start, not air, not natural (`NatureFilter`); start must be built else empty. Positions stored in a `LongOpenHashSet`; iteration sorts by (y,z,x) once (LongArrayList sorted with comparator on unpacked coords).

Tests:
- `boxResolve`: Box((1,1,1),(3,2,4)) → size 3*2*4=24, contains (2,2,3), not (4,1,1).
- `floodStopsAtNature`: a 3×3×3 hut of oak_planks on the stone floor next to grass/dirt → flood from a plank selects exactly 27 (floor stone is natural).
- `floodRadius`: 10-long plank line, radius 3 → 4 blocks.
- `floodLimit`: limit 5 on the hut → truncated=true.
- [ ] Tests → fail → implement → pass → commit `feat: selections and auto-select`.

### Task 5: Families

**Files:** `family/Shape.java`, `family/Family.java`, `family/FamilyIndex.java`, `family/FamilyOverrides.java`, `StilwerkTags`, test `test/FamilyTests.java`.

```java
public enum Shape { FULL, STAIRS, SLAB, WALL, FENCE, FENCE_GATE, DOOR, TRAPDOOR, BUTTON, PRESSURE_PLATE,
    LOG, WOOD, STRIPPED_LOG, STRIPPED_WOOD, SIGN, WALL_SIGN, HANGING_SIGN, WALL_HANGING_SIGN, PANE;
    /** Ordered substitutes when a target family lacks this shape (the shape itself first). */
    public List<Shape> chain() {
        return switch (this) {
            case FENCE -> List.of(FENCE, WALL);
            case WALL -> List.of(WALL, FENCE);
            case LOG -> List.of(LOG, FULL);
            case WOOD -> List.of(WOOD, LOG, FULL);
            case STRIPPED_LOG -> List.of(STRIPPED_LOG, LOG, FULL);
            case STRIPPED_WOOD -> List.of(STRIPPED_WOOD, STRIPPED_LOG, WOOD, LOG, FULL);
            default -> List.of(this);
        };
    }
}
public record Family(ResourceLocation id, Map<Shape, Block> blocks, boolean wood) {
    public @Nullable Block get(Shape s);
    public Block icon();                          // FULL, else first by Shape order
    public boolean singleton();                   // only FULL
    /** First shape in s.chain() this family has, or null. */
    public @Nullable Shape resolve(Shape s);
}
public final class FamilyIndex {
    public record Member(Family family, Shape shape) {}
    public static FamilyIndex get();              // current; built lazily from BuiltInRegistries.BLOCK + overrides
    public static void rebuild(FamilyOverrides o);
    public @Nullable Member lookup(Block b);
    public @Nullable Family byId(ResourceLocation id);
    public Collection<Family> all();              // sorted by id
}
```

Build algorithm (deterministic):
1. Overrides first: each JSON `{"family":"ns:id","wood":false,"blocks":{"FULL":"ns:block","STAIRS":"ns:block",...}}` assigns members.
2. Wood: for each `WoodType.values()` with name `n` (split `ns:path`, default ns `minecraft`): family id `ns:path`, members by id if present: `path_planks`→FULL, `path_log`|`path_stem`|(`bamboo_block` for bamboo)→LOG, `path_wood`|`path_hyphae`→WOOD, `stripped_path_log`|`stripped_path_stem`|`stripped_bamboo_block`→STRIPPED_LOG, `stripped_path_wood`|`stripped_path_hyphae`→STRIPPED_WOOD.
3. Shaped blocks by class (StairBlock, SlabBlock, WallBlock, FenceBlock, FenceGateBlock, DoorBlock, TrapDoorBlock, ButtonBlock, BasePressurePlateBlock, StandingSignBlock, WallSignBlock, CeilingHangingSignBlock, WallHangingSignBlock, IronBarsBlock whose path ends `_pane`), suffix stripped from the path (`_stairs,_slab,_wall,_fence,_fence_gate,_door,_trapdoor,_button,_pressure_plate,_sign,_wall_sign,_hanging_sign,_wall_hanging_sign,_pane`, longest suffix first). Base `b` → family id: first existing block among `b`, `b+"s"`, `b+"_planks"`, `b+"_block"` in the same namespace (that block becomes FULL); if a wood family `ns:b` exists use it; else family id `ns:b` without FULL.
4. Drop non-wood families that have neither FULL nor ≥2 shapes; their blocks fall to step 5.
5. Every remaining block with `asItem() != Items.AIR` that is not in a family → singleton family (id = block id, shape FULL).
First assignment wins (overrides > wood > shaped > singleton).

`FamilyOverrides` is a `SimpleJsonResourceReloadListener` on directory `stilwerk/families`, registered via `AddReloadListenerEvent`; on apply → `FamilyIndex.rebuild`. Client: rebuild without overrides on `ClientPlayerNetworkEvent.LoggingIn` (overrides are server-side data; client preview uses built-in rules — documented limitation).

Tests:
- `vanillaWoodFamilies`: for each of oak, spruce, birch, jungle, acacia, dark_oak, mangrove, cherry, bamboo, crimson, warped: family exists with FULL, STAIRS, SLAB, FENCE, FENCE_GATE, DOOR, TRAPDOOR, BUTTON, PRESSURE_PLATE, SIGN, WALL_SIGN, HANGING_SIGN, WALL_HANGING_SIGN; oak also LOG, WOOD, STRIPPED_LOG, STRIPPED_WOOD.
- `stoneBrickFamily`: lookup(STONE_BRICK_STAIRS) → family `minecraft:stone_bricks`, FULL = STONE_BRICKS, has SLAB, WALL.
- `quartzAndPurpur`: QUARTZ_STAIRS → FULL QUARTZ_BLOCK; PURPUR_SLAB → FULL PURPUR_BLOCK.
- `mossySeparate`: MOSSY_STONE_BRICKS family ≠ STONE_BRICKS family.
- `glassPanes`: WHITE_STAINED_GLASS_PANE → family `minecraft:white_stained_glass` with FULL + PANE.
- `singletons`: WHITE_WOOL → singleton; LANTERN → singleton; FIRE → lookup null (no item).
- `chain`: Family stone_bricks.resolve(FENCE) == WALL; resolve(DOOR) == null.
- [ ] Tests → fail → implement → pass → commit `feat: block families`.

### Task 6: StateTransfer

**Files:** `family/StateTransfer.java`, test `test/TransferTests.java`.

```java
public final class StateTransfer {
    /** Copies every property whose name exists on the target and whose value name is valid there. */
    public static BlockState transfer(BlockState from, Block to) {
        BlockState out = to.defaultBlockState();
        for (Property<?> p : from.getProperties()) {
            Property<?> q = to.getStateDefinition().getProperty(p.getName());
            if (q != null) out = copy(out, q, valueName(from, p));
        }
        return out;
    }
    private static <T extends Comparable<T>> BlockState copy(BlockState s, Property<T> q, String name) {
        return q.getValue(name).map(v -> s.setValue(q, v)).orElse(s);
    }
    private static <T extends Comparable<T>> String valueName(BlockState s, Property<T> p) { return p.getName(s.getValue(p)); }
}
```
Tests: oak_stairs(facing=east, half=top, shape=outer_left, waterlogged=true) → spruce_stairs same props; oak_slab type=double → stone_brick_slab double; oak_door lower/upper with hinge/open → spruce_door same; oak_fence(north=true) → stone_brick_wall: default connections (north=none) [reshape handles]; oak_log axis=x → birch_log axis=x; lantern hanging=true → soul_lantern hanging=true.
- [ ] Tests → fail → implement → pass → commit `feat: state transfer`.

### Task 7: Change, ChangeSet, Costs

**Files:** `core/Change.java`, `core/ChangeSet.java`, `core/Costs.java`, test `test/ChangeSetTests.java`.

```java
public record Change(BlockPos pos, BlockState oldState, BlockState newState, @Nullable CompoundTag blockEntity, boolean reshape) {}
public final class ChangeSet {
    public static final class Builder { public Builder add(Change c); public Builder group(List<Change> g); public ChangeSet build(); }
    public List<List<Change>> groups();
    public int size();                              // number of changes
    public ChangeSet inverse();                     // old↔new, blockEntity dropped except signs (kept from old? no: inverse carries no BE data), reshape kept
    public CompoundTag save();                      // palette of states (NbtUtils.writeBlockState) + int arrays; BE tags list
    public static ChangeSet load(CompoundTag tag, HolderGetter<Block> blocks);
}
public final class Costs {
    /** Item that pays for this state; Items.AIR when it cannot be obtained. */
    public static Item item(BlockState s) { return s.getBlock().asItem(); }
    /** How many of item(s) one block of this state costs: 0 for secondary halves; slab double 2;
     *  CANDLES / PICKLES / EGGS / FLOWER_AMOUNT / LAYERS(snow) → their value; else 1; air 0. */
    public static int count(BlockState s);
    /** Sum of item counts over states (skips AIR items). */
    public static Object2IntOpenHashMap<Item> tally(Iterable<BlockState> states);
}
```
Tests: save/load round trip of a 3-group set (incl. a sign BE tag) equals original; inverse swaps states; Costs: slab double=2, door upper=0, door lower=1, bed head=0, candles(3)=3, planks=1, air=0.
- [ ] Tests → fail → implement → pass → commit `feat: change sets and costs`.

### Task 8: Gate, MaterialLedger, ProjectECompat

**Files:** `core/Gate.java`, `core/CreativeGate.java`, `core/MaterialLedger.java`, `compat/ProjectECompat.java`, test `test/LedgerTests.java`, `test/TestPlayers.java`.

```java
public interface Gate {
    /** Pay for the group before it is set; false = skip the group (nothing taken). */
    boolean admit(List<Change> group);
    /** Called with the changes that were actually set (refund old states here). */
    void settled(List<Change> applied);
}
public final class MaterialLedger implements Gate {
    public record Line(Item item, int need, int inInventory, boolean viaEmc, int missing) {}
    public record Report(List<Line> lines, boolean complete) {}
    public MaterialLedger(ServerPlayer player, BlockPos dropFallback, boolean useEmc);
    public Report check(ChangeSet set);             // need = tally(newStates) ; EMC covers missing if learned and affordable (cumulative)
    @Override public boolean admit(List<Change> group); // all-or-nothing per group: inventory first, then EMC for the rest
    @Override public void settled(List<Change> applied); // refund tally(oldStates): inventory → EMC (value>0) → drop at player (or dropFallback when player removed)
}
public final class ProjectECompat {  // reflection, pattern from Celestial Ascent compat/ProjectECompat
    public static boolean available();
    public static BigInteger emc(ServerPlayer p);           // ZERO without ProjectE
    public static boolean setEmc(ServerPlayer p, BigInteger v);
    public static long value(ItemLike item);
    public static boolean knows(ServerPlayer p, Item item); // IKnowledgeProvider.hasKnowledge(ItemInfo.fromItem(item))
    public static void sync(ServerPlayer p);
}
```
Inventory take/give uses `player.getInventory()` (main + offhand, not armor); creative players never use a ledger. Verify ProjectE API names with `javap` against the instance jar before writing (`ItemInfo` class + `fromItem` signature).

Tests (survival FakePlayer from `TestPlayers.survival`):
- `ledgerPaysFromInventory`: player has 10 spruce_planks; group of 4 oak→spruce planks changes → admit true; inventory 6; settled → +4 oak_planks.
- `ledgerRejectsShortGroup`: 1 spruce plank, group needs 2 → admit false, inventory unchanged.
- `ledgerReport`: need 30 spruce_stairs, have 12 → Line(missing 18), complete false.
- `ledgerRefundOverflowDrops`: full inventory of dirt stacks → settled drops oak planks as ItemEntity near player.
- `ledgerEmc` (only asserts when `ProjectECompat.available()`, else succeed): learned item + EMC → admit takes EMC.
- [ ] Tests → fail → implement → pass → commit `feat: survival material ledger and ProjectE compat`.

### Task 9: Applier

**Files:** `core/Applier.java`, wire `ServerTickEvent.Post`, test `test/ApplierTests.java`.

```java
public final class Applier {
    public record Job(UUID owner, ServerLevel level, ChangeSet set, Gate gate, Consumer<ChangeSet> onDone, @Nullable ServerPlayer progressTo) {}
    public static void submit(Job job);
    public static boolean busy(UUID owner);
    public static void tick(MinecraftServer server);         // runs jobs FIFO within config budget
    /** Synchronous apply for tests and tiny jobs. */
    public static ChangeSet applyNow(ServerLevel level, ChangeSet set, Gate gate);
}
```
Per group: skip if any change's current state ≠ `oldState` or `!gate.admit(group)`; else `level.setBlock(pos, newState, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE)`; if `blockEntity != null` load it into the new BE (`loadWithComponents`, set pos keys removed) and `setChanged`; collect applied; `gate.settled(appliedOfGroup)`. Budget counts changes; a group never splits across ticks. After the last group: reshape pass over applied changes with `reshape=true` plus their 6-neighbours that are `FenceBlock | WallBlock | IronBarsBlock`: `Block.updateFromNeighbourShapes(state, level, pos)` and set with the same flags if different. Progress every 10 ticks: action bar `Component.translatable("stilwerk.progress", bar, pct)` with a 20-char ▮/▯ bar. `onDone` gets the applied subset (for undo).

Tests:
- `applierSetsAndSkips`: 3 groups, the 2nd has a mismatching oldState → applied has 2 groups; world reflects.
- `applierDoorStaysWhole`: oak door → spruce door group (2 changes) applied → both halves spruce, door intact (`h.assertBlockPresent`).
- `applierBudget`: budget 2 (test override `Applier.budgetOverride`) with 5 single changes → after 1 tick ≤ 2 applied; after 3 ticks all applied (use `h.runAfterDelay`).
- `applierReshape`: oak_fence row → stone_brick_wall with reshape=true → middle wall has north/south connection ≠ NONE where neighbours are walls.
- [ ] Tests → fail → implement → pass → commit `feat: tick-budgeted applier`.

### Task 10: UndoHistory

**Files:** `core/UndoHistory.java`, test `test/UndoTests.java`.

```java
public final class UndoHistory {
    public record Entry(ResourceKey<Level> dimension, ChangeSet applied, long time) {}
    public static void push(MinecraftServer server, UUID player, Entry e); // trims to undoSteps and undoMaxBlocks (drop oldest)
    public static Optional<Entry> pop(MinecraftServer server, UUID player);
    public static int depth(MinecraftServer server, UUID player);
    static Path file(MinecraftServer s, UUID id);   // <world>/stilwerk/undo/<uuid>.nbt, NbtIo.writeCompressed
}
```
In-memory cache per server instance (`WeakHashMap<MinecraftServer, Map<UUID, Deque<Entry>>>`), loaded lazily from file, written after every push/pop. Undo itself (in ServerOps): `pop` → `entry.applied().inverse()` → Applier job (mismatch skip implements "only if still ours") → nothing pushed.

Tests: push 3 → depth 3; undoSteps override 2 → depth 2 (oldest gone); file written + reload (clear cache via `UndoHistory.clearCacheForTests`) → depth preserved; swap then undo restores (via Applier.applyNow + inverse); undo skips a block changed after the swap.
- [ ] Tests → fail → implement → pass → commit `feat: persistent undo history`.

### Task 11: Mix palettes

**Files:** `mix/Hash.java`, `mix/MixPattern.java`, `mix/MixEntry.java`, `mix/MixPalette.java`, `mix/PickContext.java`, `mix/Mixes.java`, test `test/MixTests.java`.

```java
public final class Hash {
    public static long mix(long z) { z = (z ^ (z >>> 30)) * 0xbf58476d1ce4e5b9L; z = (z ^ (z >>> 27)) * 0x94d049bb133111ebL; return z ^ (z >>> 31); }
    public static double unit(long seed, BlockPos p, int salt) { return (mix(seed ^ mix(p.asLong() + 0x9E3779B97F4A7C15L * salt)) >>> 11) * 0x1.0p-53; }
}
public enum MixPattern { SCATTER, GRADIENT, PATCHES, EDGES }
public record MixEntry(ResourceLocation family, int weight) {}           // weight 1..100
public record MixPalette(String id, String name, List<MixEntry> entries, MixPattern pattern,
                         Optional<ResourceLocation> edge, Direction.Axis axis, float scale) {
    public static final Codec<MixPalette> CODEC;                           // RecordCodecBuilder, defaults: axis Y, scale 0.12
    public static final StreamCodec<ByteBuf, MixPalette> STREAM_CODEC;     // ByteBufCodecs.fromCodec(CODEC)
    public ResourceLocation base() { return entries.get(0).family(); }
    public ResourceLocation pick(long seed, BlockPos pos, PickContext ctx) {
        return switch (pattern) {
            case SCATTER -> byWeight(Hash.unit(seed, pos, 1));
            case GRADIENT -> {
                double t = ctx.along(axis, pos);                               // 0 at min, 1 at max of bounds
                double v = Mth.clamp(t + (Hash.unit(seed, pos, 2) - 0.5) * 0.35, 0, 0.999999);
                yield byWeight(v);
            }
            case PATCHES -> {
                double n = ctx.noise(seed).getValue(pos.getX() * scale, pos.getY() * scale, pos.getZ() * scale); // ~[-1,1]
                double v = Mth.clamp((n + 1) / 2 + (Hash.unit(seed, pos, 3) - 0.5) * 0.1, 0, 0.999999);
                yield byWeight(v);
            }
            case EDGES -> ctx.isEdge(pos) && edge.isPresent() ? edge.get() : byWeight(Hash.unit(seed, pos, 4));
        };
    }
    private ResourceLocation byWeight(double u) { /* cumulative weights over entries in list order */ }
}
public final class PickContext {
    public PickContext(BoundingBox bounds, LongSet members);   // members = positions being mixed
    public double along(Direction.Axis a, BlockPos p);
    public boolean isEdge(BlockPos p);  // count axes where a neighbour along that axis is not a member; edge if count >= 2
    public SimplexNoise noise(long seed); // cached new SimplexNoise(new LegacyRandomSource(seed))
}
public final class Mixes {
    public static List<MixPalette> builtIn();   // ids: stilwerk:old_mossy, castle_wall, weathered_wood, rough_deepslate, sandstone_worn, nether_rough (+ rechiseled_detail only if ModList has "rechiseled", entries chosen from blocks that exist)
    public static List<MixPalette> user();      // config/stilwerk/mischungen/*.json
    public static void save(MixPalette p);      // writes <id>.json (id sanitized)
    public static Optional<MixPalette> byId(String id); // user overrides built-in
}
```
Built-in definitions:
- `old_mossy` (GRADIENT Y): mossy_stone_bricks 30, cracked_stone_bricks 20, stone_bricks 50 → listed bottom→top: `[mossy_stone_bricks 30, cracked_stone_bricks 20, stone_bricks 50]`.
- `castle_wall` (PATCHES): stone_bricks 50, cobblestone 15, andesite 15, tuff_bricks 10, cracked_stone_bricks 10.
- `weathered_wood` (SCATTER): spruce 65, dark_oak 20, oak 15.
- `rough_deepslate` (PATCHES): deepslate_bricks 45, cracked_deepslate_bricks 15, deepslate_tiles 20, cobbled_deepslate 20.
- `sandstone_worn` (GRADIENT Y): sandstone 40, cut_sandstone 30, smooth_sandstone 30.
- `nether_rough` (SCATTER): nether_bricks 60, cracked_nether_bricks 25, chiseled_nether_bricks 15.
(Family ids are `minecraft:<name>`; for wood families the id is the wood name.)

Tests: determinism (two picks same seed/pos equal; 1000 positions identical across two palette instances); SCATTER distribution of 10 000 picks within ±3 % of weights; GRADIENT: in a 1×20×1 column, bottom 5 blocks ≥ 60 % first entry, top 5 ≥ 60 % last entry; EDGES: 5×5×1 wall → corner is edge, centre is not; codec round trip.
- [ ] Tests → fail → implement → pass → commit `feat: mix palettes`.

### Task 12: SwapMapping, Target, SwapPlanner

**Files:** `swap/Target.java`, `swap/SwapMapping.java`, `swap/SwapPlanner.java`, test `test/SwapTests.java`.

```java
public record Target(Kind kind, Optional<ResourceLocation> family, Optional<String> mix) {
    public enum Kind { KEEP, FAMILY, MIX }
    public static Target keep(); public static Target family(ResourceLocation f); public static Target mix(String id);
    public static final Codec<Target> CODEC;
}
public record SwapMapping(Map<ResourceLocation, Target> families, Map<ResourceLocation, Map<Shape, Target>> shapes) {
    public static final SwapMapping EMPTY;
    public static final Codec<SwapMapping> CODEC;  public static final StreamCodec<ByteBuf, SwapMapping> STREAM_CODEC;
    public Target resolve(ResourceLocation family, Shape shape); // shape override > family > KEEP
    public SwapMapping with(ResourceLocation family, Target t);  public SwapMapping withShape(ResourceLocation f, Shape s, Target t);
}
public final class SwapPlanner {
    public record Options(boolean includeNature, boolean survival) {}
    public record Kept(ResourceLocation source, Shape shape, Target target, int count) {}
    public record Result(ChangeSet changes, List<Kept> kept) {}
    /** Families present (built, unprotected) with counts, sorted by count desc. */
    public static List<Object2IntMap.Entry<Family>> census(BlockGetter level, ResolvedSelection sel, boolean includeNature);
    public static Result plan(BlockGetter level, ResolvedSelection sel, SwapMapping mapping, Map<String, MixPalette> mixes, long seed, Options opt);
    /** Core per-state mapping shared with BlueprintPlanner; returns null when the block stays. */
    public static @Nullable BlockState mapState(BlockState state, BlockPos pos, SwapMapping mapping, Map<String, MixPalette> mixes, long seed, MixContexts ctx, List<Kept> keptSink);
}
```
`plan` algorithm:
1. includeNature forced false when survival. `NatureFilter nf = new NatureFilter(level)`.
2. Iterate selection; skip air, protected, natural (unless includeNature), secondary halves (`Groups.isPrimary==false`, handled with primary).
3. `Member m = FamilyIndex.get().lookup(block)`; null → skip. `Target t = mapping.resolve(m.family().id(), m.shape())`; KEEP → skip.
4. Target family → `f`. Target mix → `MixContexts` provides one `PickContext` per (source family id) built from the member positions of that family in the selection (pre-pass collecting positions per family) → `f = mix.pick(seed, pos, ctx)`; if `f` lacks the shape, fall back to `mix.base()`.
5. `Shape s2 = targetFamily.resolve(m.shape())`; null → record Kept, skip. `newState = StateTransfer.transfer(state, targetFamily.get(s2))`; reshape = s2 != m.shape(). Same state → skip.
6. Primary with partner (door): partner change built by transferring the partner state with the same target block; group of 2. Partner protected/mismatch → skip both.
7. Kept aggregated by (source, shape, target).

Tests:
- `oakToSpruceHouse`: 5×4×5 oak hut with planks walls, oak_stairs roof (facing set), oak_door, oak_fence, oak_trapdoor; mapping oak→spruce; plan+applyNow → no oak block remains; stairs facing/half equal; door intact.
- `shapeOverride`: mapping oak→spruce plus shape override SLAB→KEEP → slabs stay oak.
- `fallbackAndKept`: oak→stone_bricks: fence→wall (reshape true), door kept (Kept entry count 2 for DOOR halves counted as 1 door), trapdoor kept.
- `natureUntouched`: natural oak tree inside the box + oak plank hut → tree logs stay oak.
- `mixApplied`: stone_bricks wall 8×6 → mix old_mossy → contains ≥ 2 distinct block types, all from mix families; stairs of stone bricks → mossy_stone_brick_stairs or stone_brick_stairs only (cracked has none → base).
- `deterministic`: two plans same seed → identical ChangeSet.
- `census`: hut census lists oak family count = number of oak blocks (door counted once).
- [ ] Tests → fail → implement → pass → commit `feat: style swap planner`.

### Task 13: Templates

**Files:** `swap/Template.java`, `swap/Templates.java`, test `test/TemplateTests.java`.

```java
public record Template(String id, String name, Map<ResourceLocation, Target> mapping) {  // codec
    public SwapMapping applyTo(SwapMapping base, Collection<ResourceLocation> present);  // only present families
}
public final class Templates {
    public static final List<String> WOOD_ORDER = List.of("birch","bamboo","oak","jungle","acacia","cherry","spruce","mangrove","dark_oak");
    public static List<Template> builtIn();   // dynamic: darken/lighten computed from WOOD_ORDER
    public static List<Template> user();      // config/stilwerk/vorlagen/*.json
    public static List<Template> datapack();  // data/<ns>/stilwerk/templates/*.json (reload listener)
    public static void save(Template t);
}
```
Built-ins (family ids without namespace mean `minecraft:`):
- `japanese_to_nordic`: acacia→spruce, jungle→dark_oak, cherry→spruce, oak→spruce, bamboo→spruce.
- `darken_wood`, `lighten_wood`: one step along WOOD_ORDER; the ends have no mapping.
- `stone_to_deepslate`: cobblestone→cobbled_deepslate, stone_bricks→deepslate_bricks, mossy_stone_bricks→deepslate_tiles, cracked_stone_bricks→cracked_deepslate_bricks, polished_andesite→polished_deepslate, smooth_stone→polished_deepslate, chiseled_stone_bricks→chiseled_deepslate.
- `desert_to_snow`: sandstone→smooth_quartz, cut_sandstone→quartz_bricks, smooth_sandstone→smooth_quartz, chiseled_sandstone→chiseled_quartz_block, acacia→spruce, terracotta→white_terracotta.
- `medieval_to_dark`: oak→dark_oak, spruce→dark_oak, stone_bricks→polished_blackstone_bricks, cobblestone→blackstone.
- `oak_to_cherry`: oak→cherry.
- `stone_to_tuff`: stone_bricks→tuff_bricks, cobblestone→polished_tuff, andesite→tuff_bricks.
- `brick_to_mud`: bricks→mud_bricks.
- `everything_mossy`: stone_bricks→mix `stilwerk:old_mossy`, cobblestone→mossy_cobblestone.
Family ids used in templates are validated at load; unknown ids are dropped with a log warning.

Tests: darken maps oak→jungle, dark_oak stays absent (no mapping); applyTo with present={oak, stone_bricks} on japanese_to_nordic only maps oak; user template save → reload round trip (in a temp config dir via `Templates.dirOverride`).
- [ ] Tests → fail → implement → pass → commit `feat: style templates`.

### Task 14: Blueprint model, Transform, BlueprintIO, capture

**Files:** `blueprint/Blueprint.java`, `blueprint/BlueprintMeta.java`, `blueprint/Transform.java`, `blueprint/BlueprintIO.java`, test `test/BlueprintTests.java`.

```java
public final class Blueprint {
    public static final int VOID = -1;          // cell untouched
    public Blueprint(Vec3i size, List<BlockState> palette, int[] cells, Int2ObjectMap<CompoundTag> blockEntities, BlueprintMeta meta);
    public Vec3i size();  public int index(int x, int y, int z) { return (y * size.getZ() + z) * size.getX() + x; }
    public @Nullable BlockState state(int x, int y, int z);  // null = VOID
    public @Nullable CompoundTag blockEntity(int x, int y, int z);
    public int nonAir();
    public BlueprintMeta meta();  public Blueprint withMeta(BlueprintMeta m);
    public Blueprint mapStates(BiFunction<BlockPos, BlockState, BlockState> f);
    public static Blueprint capture(BlockGetter level, ResolvedSelection sel, boolean skipNature, HolderLookup.Provider regs, BlueprintMeta meta);
    public byte[] sha1();                       // over BlueprintIO.toNetwork bytes
}
public record BlueprintMeta(String name, String author, long created, String source) { codec; }
public final class Transform { public static Blueprint apply(Blueprint bp, Rotation rot, Mirror mirror); } // mirror first, then rotation; positions remapped into new size; states via state.mirror/rotate
public final class BlueprintIO {
    public static void write(Blueprint bp, Path file) throws IOException;   // vanilla structure: DataVersion, size, palette, blocks (non-VOID incl. air), entities [], stilwerk{name,author,created,source,format:1}
    public static Blueprint read(Path file, HolderGetter<Block> blocks) throws IOException; // delegates to VanillaNbtImporter
    public static byte[] toNetwork(Blueprint bp);                // gzip(NBT compact: size, palette list, cells as varint-packed byte[] (value+1 so VOID=0), BE list {i, tag})
    public static Blueprint fromNetwork(byte[] data, HolderGetter<Block> blocks, long maxBlocks) throws IOException;
}
```
Capture: Box → every position: air → air cell; natural & skipNature → VOID; else state; BE tag via `be.saveWithoutMetadata(regs)` when the client/server has it. Flood → members only, others VOID. Bounds origin = selection min corner.

Tests: capture 3×3×3 hut → write → read → equal states and size; toNetwork/fromNetwork round trip incl. sign BE text; Transform: stairs facing=north rotated CLOCKWISE_90 → facing east at remapped position; size (2,1,3) rotated → (3,1,2); mirror FRONT_BACK flips facing south↔north.
- [ ] Tests → fail → implement → pass → commit `feat: blueprint model, transform and IO`.

### Task 15: Importers + fixtures

**Files:** `tools/make_fixtures.py`, `blueprint/importer/*.java`, fixtures in `src/main/resources/data/stilwerk/stilwerk_fixtures/`, test `test/ImportTests.java`.

`make_fixtures.py` (pure Python NBT writer, gzip): writes
- `house_v2.schem` (Sponge v2, DataVersion 3955, 4×3×5, palette with stairs states, varints incl. an id ≥ 128 by padding palette to 130 entries of distinct `minecraft:<color>_wool`/`*_concrete` etc.), a sign BlockEntity with text.
- `house_v3.schem` (Sponge v3 same content under `Schematic.Blocks`).
- `old_grass.schem` (v2, DataVersion 3465, contains `minecraft:grass` → must import as `minecraft:short_grass`).
- `multi.litematic` (Version 6, MinecraftDataVersion 3955, two regions, one with negative size, palette of 5 → 3 bits so values span longs).
- `plain.nbt` (vanilla structure with an explicit air block and an omitted position).
- `unknown.schem` (palette contains `nosuchmod:block` → air + warning).

Java:
```java
public final class BlockStateUpgrader {
    public static CompoundTag upgrade(CompoundTag stateTag, int fromVersion);  // DataFixers.getDataFixer().update(References.BLOCK_STATE, new Dynamic<>(NbtOps.INSTANCE, tag), from, current)
    public static CompoundTag parseStateString(String s);                     // "ns:id[k=v,...]" → {Name, Properties}
}
public record ImportResult(Blueprint blueprint, List<String> warnings) {}
public final class Importers {
    public static ImportResult load(Path file, HolderGetter<Block> blocks) throws IOException; // by extension .nbt/.schem/.schematic(v2/v3 only)/.litematic
}
```
Sponge varint decode:
```java
int i = 0, idx = 0;
while (i < data.length) { int value = 0, shift = 0, b;
    do { b = data[i++] & 0xFF; value |= (b & 0x7F) << shift; shift += 7; } while ((b & 0x80) != 0);
    cells[idx++] = paletteMap[value]; }
// index = x + z*W + y*W*L  → convert to Blueprint.index(x,y,z)
```
Litematic unpack:
```java
int bits = Math.max(2, 32 - Integer.numberOfLeadingZeros(paletteSize - 1));
long mask = (1L << bits) - 1;
long start = (long) i * bits; int a = (int) (start >> 6), b = (int) (((long) (i + 1) * bits - 1) >> 6); int off = (int) (start & 63);
int v = (int) (a == b ? (arr[a] >>> off) & mask : ((arr[a] >>> off) | (arr[b] << (64 - off))) & mask);
// region index i = (y * |sz| + z) * |sx| + x ; region min corner = pos + (size<0 ? size+1 : 0) per axis; tile entities x,y,z relative to region min.
```
Unknown block ids → air cell + warning "unknown: <id> ×n". Upgrades run when the file's DataVersion < `SharedConstants.getCurrentVersion().getDataVersion().getVersion()`.

Tests: each fixture loads; v2 & v3 houses equal each other; a stairs state's facing/half matches; sign text present; varint id ≥128 correct block; old_grass → short_grass; litematic merged size and a block in each region at the expected merged position; plain.nbt explicit air → air cell, omitted → VOID; unknown → warning contains `nosuchmod:block`.
- [ ] Write fixture script, run it, tests → fail → implement → pass → commit `feat: schematic importers`.

### Task 16: BlueprintPlanner

**Files:** `blueprint/BlueprintPlanner.java`, test `test/PlacementTests.java`.

```java
public final class BlueprintPlanner {
    public record Options(boolean overwrite, boolean survival) {}
    public record Result(ChangeSet changes, int blocked) {}
    public static Result plan(BlockGetter level, Blueprint transformed, BlockPos origin, SwapMapping mapping, Map<String, MixPalette> mixes, long seed, Options opt);
}
```
Algorithm: optional swap via `SwapPlanner.mapState` over blueprint cells (mix contexts built from blueprint member positions per family, in blueprint coordinates offset by origin). For each non-VOID cell: target = origin + (x,y,z); bp state air → only when `overwrite && !survival` and current not air and not protected; `structure_void` → skip; current == new → skip. Survival: current must be air or `canBeReplaced()` and not protected, else blocked++ (group-wise: both halves). Creative non-overwrite: replace only air/replaceable; overwrite: replace anything not protected. BE tag: survival → only if new block is SignBlock; creative → tag as is. Groups via `Groups.partner` within the blueprint (primary + partner cell).

Tests: place captured hut at another spot (creative) → identical; rotated placement → stairs facing rotated; survival placement into a spot with one stone block in the way → blocked=1 and that cell skipped; survival strips chest BE but keeps sign text; overwrite=true clears a dirt block where blueprint has air.
- [ ] Tests → fail → implement → pass → commit `feat: blueprint placement planner`.

### Task 17: Networking + ServerOps + command

**Files:** `net/*.java`, `command/StilwerkCommand.java`, test `test/ServerOpsTests.java`.

Payloads (all `CustomPacketPayload` records with `StreamCodec<RegistryFriendlyByteBuf, …>`):
- `SwapRequest(int id, Selection selection, SwapMapping mapping, List<MixPalette> mixes, long seed, boolean includeNature, boolean partialOk)`
- `PlaceRequest(int id, byte[] hash, BlockPos origin, Rotation rotation, Mirror mirror, SwapMapping mapping, List<MixPalette> mixes, long seed, boolean overwrite, boolean partialOk)`
- `BlueprintChunk(int uploadId, int index, int total, byte[] data)` — data ≤ 30 000 bytes
- `ConfirmRequest(int id, boolean proceed)`
- `UndoRequest()`
- S2C `MaterialReport(int id, List<Line> lines, boolean complete)` with `Line(Item item, int need, int have, int missing, boolean emc)`

```java
public final class ServerOps {
    public static void swap(ServerPlayer p, SwapRequest r);
    public static void place(ServerPlayer p, PlaceRequest r);
    public static void confirm(ServerPlayer p, ConfirmRequest r);
    public static void undo(ServerPlayer p);
}
public final class UploadBuffer {
    public static void accept(ServerPlayer p, BlueprintChunk c);   // assemble; on complete: size check, decode (fromNetwork with block limit), sanitize BE (survival → signs only), cache by sha1 (LRU 4 per player); reply chat error on failure
    public static @Nullable Blueprint get(ServerPlayer p, byte[] hash);
}
```
Flow (both ops): busy? → error `stilwerk.error.busy`. Resolve selection (`selection.resolve(level, limit)`), require `level.hasChunksAt(bounds)` → else `stilwerk.error.unloaded`; truncated or size > limit (survival/creative) → `stilwerk.error.too_big`. Plan. Empty → `stilwerk.info.nothing`. Creative → submit with CreativeGate. Survival → `MaterialLedger.check`; complete or `partialOk` → submit; else store pending (id, ledger, changeset) and send `MaterialReport`; `ConfirmRequest(proceed=true)` → submit pending (partial). onDone → `UndoHistory.push` (if applied non-empty) + chat `stilwerk.info.done` (count). `undo`: pop → inverse → gate (creative/survival ledger) → Applier → chat `stilwerk.info.undone`/`stilwerk.info.undo_empty`.
Command: `/stilwerk undo` (any player) → `ServerOps.undo`.

Tests (call ServerOps directly with FakePlayer; FakePlayer connection null → MaterialReport sending guarded by `p.connection != null`, tests read `ServerOps.pendingForTests`):
- creative swap request applies after ticks and pushes undo; undo restores;
- survival swap without materials → pending report, nothing changed; with materials → applied & paid;
- too-big selection rejected;
- place request with uploaded blueprint (UploadBuffer.accept chunks from `BlueprintIO.toNetwork`) applies.
- [ ] Tests → fail → implement → pass → commit `feat: networking and server operations`.

### Task 18: Wand item, modes, client state, keys, selection rendering

**Files:** `item/WandMode.java`, `item/WandHooks.java`, `item/ArchitectWandItem.java`, `client/ClientSetup.java`, `client/Keys.java`, `client/ClientState.java`, `client/WandClient.java`, `client/SelectionRenderer.java`, `client/screen/ModeWheelScreen.java`, `client/screen/NameInputScreen.java`.

- `WandMode` stored in a data component `stilwerk:wand_mode` (codec + stream codec); mode change sent as… client sets locally and sends nothing — mode is client-only UI state kept in `ClientState` (no component needed). Decision: no data component; `ClientState.mode`.
- Keys (category `key.categories.stilwerk`): `mode_wheel` = M, `confirm` = ENTER, `toggle_preview` = DOWN, `rotate_left` = LEFT, `rotate_right` = RIGHT, `mirror` = UP, `undo` = U. Active only while the main hand holds the wand (checked in handlers).
- Wand clicks (client, via `WandHooks.useOn/use`; server side returns `InteractionResult.CONSUME` and does nothing):
  - SELECT: right-click block → corner A, then B (alternating, shown in action bar); sneak+right-click block → Flood(start, ClientConfig flood radius = server default 48); sneak+right-click air → clear.
  - SWAP: right-click → open `SwapScreen` (selection mode if a selection exists; blueprint mode while placing; else message `stilwerk.hint.select_first`).
  - BLUEPRINT: right-click → `BlueprintMenuScreen` (Kopieren if selection, Bibliothek, Einsetzen abbrechen if placing).
- Ctrl + scroll (`InputEvent.MouseScrollingEvent`, cancel): SELECT with Box → push/pull the face the player looks at (`Direction.orderedByNearest(player)[0]`), BLUEPRINT placement → height ±1.
- `SelectionRenderer` (`RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS`): `LevelRenderer.renderLineBox` of bounds (Box cyan, Flood gold), corner A marker; action bar shows `W×H×L · n Blöcke`.
- `ModeWheelScreen`: opened while M held; 3 sectors drawn with `GuiGraphics.fill`, item icons + labels; mouse angle picks; releasing M (key up polled in `tick`) or click selects; not a pause screen.
- `NameInputScreen(title, initial, Consumer<String>)`.
- Pause-screen intercept: `ScreenEvent.Opening` with `PauseScreen` while preview/placement active → cancel event and cancel preview/placement.
- [ ] Implement, `./gradlew compileJava`, run GameTests (still green), commit `feat: wand, modes, keys and selection rendering`.

### Task 19: Ghost preview

**Files:** `client/GhostMesh.java`, `client/PreviewController.java`.

```java
public final class GhostMesh implements AutoCloseable {
    /** Tessellates newState of every change (≤ maxGhostBlocks) into a STATIC VertexBuffer, block format, alpha from config, cubes inflated by 0.003 around the block centre to avoid z-fighting. */
    public static GhostMesh build(ClientLevel level, ChangeSet set, float alpha);
    public void draw(PoseStack pose, Matrix4f projection, Vec3 camera);  // RenderType.translucent() state + GameRenderer.getRendertypeTranslucentShader()
}
public final class PreviewController {
    public static void showSwap(ResolvedSelection sel, SwapMapping m, Map<String, MixPalette> mixes, long seed, boolean includeNature); // runs SwapPlanner on the client level
    public static void showPlacement(ChangeSet set);
    public static void toggleBeforeAfter();  public static void cancel();  public static boolean active();
    public static void confirm();            // sends SwapRequest / delegates to PlacementController
}
```
Tessellation: for each change, `BlockRenderDispatcher.getBlockModel(state)`; `ModelBlockRenderer.renderModel(pose, alphaConsumer, state, model, r, g, b, light=LightTexture.FULL_BRIGHT? no → packed light from level at pos, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, RenderType.translucent())`, with tint from `BlockColors.getColor(state, level, pos, 0)` when the model has tinted quads; `alphaConsumer` wraps the BufferBuilder and multiplies vertex alpha. Fluids are skipped. Rebuild only when the ChangeSet changes.
- [ ] Implement, dev-run check deferred to Task 23, compile, commit `feat: ghost preview`.

### Task 20: SwapScreen, FamilyPickerScreen, MaterialReportScreen

**Files:** `client/screen/SwapScreen.java`, `client/screen/FamilyPickerScreen.java`, `client/screen/MaterialReportScreen.java`, net client handler for `MaterialReport`.

SwapScreen layout (not pausing, no blur: override `renderBackground` with a translucent panel only):
- Top bar: template `CycleButton` ("Vorlage: —" + built-in + user + datapack) → `Template.applyTo(mapping, present)`; checkbox "Natur einschließen" (creative only).
- Left list (`ObjectSelectionList`): one row per census family (icon, name via block name of icon, count, arrow "→", current target label). Click row → FamilyPickerScreen for that family. Row toggle "▸" expands per-shape sub-rows (shape name, count, target).
- FamilyPickerScreen: search `EditBox` + list of all families (icon + name, filtered by name/id contains), extra rows on top: "Nicht tauschen", "Mischung: <name>" per known mix, "Neue Mischung…" (→ MixEditorScreen, returns new mix id).
- Footer: survival estimate line (client inventory tally vs. need, "EMC wird beim Anwenden geprüft" when ProjectE loaded), kept notes from the last client plan ("12 Türen bleiben …"), buttons *Vorschau* (close + `PreviewController.showSwap`), *Anwenden* (send `SwapRequest` directly), *Als Vorlage speichern* (NameInputScreen → `Templates.save`), *Abbrechen*.
- Blueprint mode: census over the blueprint (`SwapPlanner.census` variant over blueprint cells), Vorschau/Anwenden update the placement mapping instead.
- MaterialReportScreen: table item icon · need · have · missing (red), EMC marker; buttons *Abbrechen* (`ConfirmRequest(false)`) and *So weit es reicht* (`ConfirmRequest(true)`).
- [ ] Implement, compile, commit `feat: swap screen and material report`.

### Task 21: MixEditorScreen

**Files:** `client/screen/MixEditorScreen.java`.

Fields: name `EditBox`; entries list (family button → FamilyPickerScreen, weight slider 1..100, remove ✕), "+ Block", pattern `CycleButton` (Gestreut/Verlauf/Flecken/Kanten), axis cycle (Verlauf), scale slider 0.03..0.5 (Flecken), edge family button (Kanten); *Speichern* (`Mixes.save`, returns id to caller), *Abbrechen*. Live preview: every change (debounced 250 ms) runs `PreviewController.showSwap` with mapping {sourceFamily → this mix} when opened from SwapScreen with a selection.
- [ ] Implement, compile, commit `feat: mix editor`.

### Task 22: Library, thumbnails, placement

**Files:** `client/ClientLibrary.java`, `client/Thumbnails.java`, `client/screen/LibraryScreen.java`, `client/screen/BlueprintMenuScreen.java`, `client/PlacementController.java`.

```java
public final class ClientLibrary {
    public record Entry(Path file, @Nullable Blueprint blueprint, List<String> warnings, @Nullable String error) {}
    public static Path dir();                         // gameDir/stilwerk/bauplaene (created)
    public static List<Entry> scan();                 // .nbt/.schem/.schematic/.litematic, cached by (path, lastModified)
    public static Path save(Blueprint bp);            // <sanitized name>.nbt, unique suffix
    public static void rename(Entry e, String name);  // rewrites meta for own .nbt; renames file for foreign formats
    public static void delete(Entry e);
    public static List<Path> importFiles(List<Path> dropped); // copy supported files into dir
}
public final class Thumbnails { public static ResourceLocation get(Blueprint bp); } // 64×64 top-down: highest non-air cell per column → MapColor.calculateRGBColor(MapColor.Brightness.NORMAL), scaled; DynamicTexture registered once per blueprint hash
public final class PlacementController {
    public static void start(Blueprint bp);  public static void stop();  public static boolean active();
    public static void rotate(int dir); public static void mirror(); public static void height(int d);
    public static void setMapping(SwapMapping m, Map<String, MixPalette> mixes);
    public static void tick();     // origin from crosshair hit: hit.getBlockPos().relative(face) - (sizeX/2, 0, sizeZ/2) + (0, height, 0); rebuild preview when origin/rotation/mirror/mapping changed (BlueprintPlanner on client level)
    public static void confirm();  // upload chunks unless server acked hash this session (client remembers hashes it uploaded) → PlaceRequest
}
```
LibraryScreen: search box; list rows (thumbnail 32×32, name, `W×H×L`, block count, warning/error marker); buttons *Einsetzen* (PlacementController.start + close), *Umbenennen*, *Löschen*, *Ordner öffnen* (`Util.getPlatform().openPath(dir)`), *Aktualisieren*; `onFilesDrop` → importFiles → rescan.
BlueprintMenuScreen: *Auswahl kopieren* (NameInputScreen → `Blueprint.capture` on client level → `ClientLibrary.save` → start placement), *Bibliothek*, *Einsetzen abbrechen*, *Im Stil anpassen* (SwapScreen blueprint mode) when placing; option toggle *Bereich überschreiben* (creative).
- [ ] Implement, compile, commit `feat: blueprint library and placement`.

### Task 23: Dev scene client check + language files

**Files:** `test/DevScene.java`, lang files completed (en_us + de_de).

DevScene (system property `stilwerk.devScene`, runs on client tick after world join, like Slab Mix): builds a small Japanese-style hut (acacia planks/logs, jungle stairs roof, oak fences, oak door, stone brick base wall) via the integrated server; sets a Box selection; screenshots `stilwerk_selection.png`; opens SwapScreen with template japanese_to_nordic → `stilwerk_swapscreen.png`; preview → `stilwerk_preview.png`; applies → waits → `stilwerk_after.png`, CHECK no acacia left; mix old_mossy on base wall → `stilwerk_mix.png`; copies to library, opens LibraryScreen → `stilwerk_library.png`; starts placement → `stilwerk_placement.png`; places → CHECK block count equal; undo → CHECK restored; quits. Log lines `CHECK <name> OK|FAIL`.
- Run: copy `run/world` (GameTest world) is superflat → create the dev world by `--quickPlaySingleplayer stilwerk_dev` with a flat preset generated on first run (DevScene creates nothing if world missing → use `tools/make_dev_world.sh` copying `run/world` to `run/saves/stilwerk_dev`).
- [ ] Implement, run `./gradlew runClient -PquickPlay=stilwerk_dev`, inspect every screenshot with Read, fix visual issues, commit `test: dev scene client check`.

### Task 24: Release build + real-pack check

- [ ] `./gradlew build` → `build/libs/stilwerk-1.0.0.jar`; jar contains no `com/stilwerk/test`, no fixtures/structure (`unzip -l | grep -c test` == 0).
- [ ] Start a production client/server copy with the user's mod list (copy of instance mods + our jar into a temp instance dir, never the user's live instance) and confirm load without errors (`grep -i "stilwerk" latest.log`, no `Exception` from stilwerk).
- [ ] Commit `chore: release 1.0.0`, update memory file with workflow notes.

---

## Self-review notes
- Spec coverage: Basis (Tasks 3–10, 17–19), Familien/Tausch/Vorlagen/Mischungen (5, 6, 11–13, 20, 21), Bibliothek/Import/Einsetzen (14–16, 22), ProjectE (8), Tests/Client-Check (all + 23), Release (24).
- Simplification vs. spec: wand mode is client-only state (no item component) — no behaviour difference.
