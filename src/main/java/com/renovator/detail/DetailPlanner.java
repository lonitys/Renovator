package com.renovator.detail;

import com.renovator.core.AutoSelect;
import com.renovator.core.Change;
import com.renovator.core.ChangeSet;
import com.renovator.core.ResolvedSelection;
import com.renovator.detail.DetailTypes.Choice;
import com.renovator.detail.DetailTypes.Suggestion;
import com.renovator.detail.rule.CornerRule;
import com.renovator.detail.rule.EavesRule;
import com.renovator.detail.rule.EntranceRule;
import com.renovator.detail.rule.PillarsRule;
import com.renovator.detail.rule.PlainRule;
import com.renovator.detail.rule.PlinthRule;
import com.renovator.detail.rule.StairsRule;
import com.renovator.detail.rule.WindowRule;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;

/** Finds detail suggestions for a selection (or around a clicked block) and combines the chosen variants. */
public final class DetailPlanner {
    private DetailPlanner() {}

    /** Radius of the building detection around a clicked block (fixed: client and server must find the same). */
    public static final int NEAR_RADIUS = 48;
    /** How far from the clicked block the detection may start when the block itself is not built (grass at the door). */
    public static final int NEAR_REACH = 2;
    /** How close a suggestion must be to the clicked block. */
    public static final int NEAR_PICK = 3;

    private static final List<DetailRule> RULES = List.of(new StairsRule(), new EavesRule(), new WindowRule(), new PillarsRule(),
            new PlinthRule(), new CornerRule(), new EntranceRule(), new PlainRule());

    public static List<DetailRule> rules() {
        return RULES;
    }

    /**
     * Suggestions for a selection. A marked box is narrowed to its buildings first, so roads, garden walls and field
     * fences that lead away from a building get no suggestions; a building selection is used as it is.
     */
    public static List<Suggestion> find(BlockGetter level, ResolvedSelection sel, long seed) {
        if (sel.isBox()) sel = AutoSelect.buildingsIn(level, sel);
        if (sel.isEmpty()) return List.of();
        BuildingView view = new BuildingView(level, sel);
        DetailContext ctx = new DetailContext(view, seed);
        List<Suggestion> out = new ArrayList<>();
        for (DetailRule r : RULES) r.find(view, ctx, out);
        return out;
    }

    /** Suggestions on the clicked building whose area lies within {@link #NEAR_PICK} of the clicked block. */
    public static List<Suggestion> near(BlockGetter level, BlockPos clicked, long seed) {
        BlockPos start = AutoSelect.nearestBuilt(level, clicked, NEAR_REACH);
        if (start == null) return List.of();
        ResolvedSelection sel = AutoSelect.flood(level, start, NEAR_RADIUS, Integer.MAX_VALUE);
        if (sel.isEmpty()) return List.of();
        List<Suggestion> out = new ArrayList<>();
        for (Suggestion s : find(level, sel, seed)) {
            if (s.area().inflatedBy(NEAR_PICK).isInside(clicked)) out.add(s);
        }
        return out;
    }

    /** The changes of the chosen variants; a suggestion that collides with an earlier choice is skipped whole. */
    public static ChangeSet combine(List<Suggestion> all, List<Choice> choices) {
        Map<String, Suggestion> byKey = new HashMap<>();
        for (Suggestion s : all) byKey.putIfAbsent(s.key(), s);
        LongOpenHashSet used = new LongOpenHashSet();
        ChangeSet.Builder b = ChangeSet.builder();
        for (Choice c : choices) {
            Suggestion s = byKey.get(c.key());
            if (s == null || c.variant() < 0 || c.variant() >= s.variants().size()) continue;
            List<Change> changes = s.variants().get(c.variant()).changes().changes();
            // a suggestion is applied whole or not at all: half of it (a post without its lantern) looks misplaced
            boolean free = true;
            for (Change ch : changes) {
                if (used.contains(ch.pos().asLong())) {
                    free = false;
                    break;
                }
            }
            if (!free) continue;
            for (Change ch : changes) {
                used.add(ch.pos().asLong());
                b.add(ch);
            }
        }
        return b.build();
    }
}
