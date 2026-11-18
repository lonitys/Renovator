package com.stilwerk.detail;

import com.stilwerk.core.Change;
import com.stilwerk.core.ChangeSet;
import com.stilwerk.core.ResolvedSelection;
import com.stilwerk.core.Selection;
import com.stilwerk.detail.DetailTypes.Choice;
import com.stilwerk.detail.DetailTypes.Suggestion;
import com.stilwerk.detail.rule.CornerRule;
import com.stilwerk.detail.rule.EavesRule;
import com.stilwerk.detail.rule.EntranceRule;
import com.stilwerk.detail.rule.PillarsRule;
import com.stilwerk.detail.rule.PlainRule;
import com.stilwerk.detail.rule.PlinthRule;
import com.stilwerk.detail.rule.StairsRule;
import com.stilwerk.detail.rule.WindowRule;
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

    /** Half size of the analysis box around a clicked block (17³). */
    public static final int NEAR_RADIUS = 8;
    /** How close a suggestion must be to the clicked block. */
    public static final int NEAR_PICK = 3;

    private static final List<DetailRule> RULES = List.of(new StairsRule(), new EavesRule(), new WindowRule(), new PillarsRule(),
            new PlinthRule(), new CornerRule(), new EntranceRule(), new PlainRule());

    public static List<DetailRule> rules() {
        return RULES;
    }

    public static List<Suggestion> find(BlockGetter level, ResolvedSelection sel, long seed) {
        BuildingView view = new BuildingView(level, sel);
        DetailContext ctx = new DetailContext(view, seed);
        List<Suggestion> out = new ArrayList<>();
        for (DetailRule r : RULES) r.find(view, ctx, out);
        return out;
    }

    public static Selection nearSelection(BlockPos clicked) {
        return new Selection.Box(clicked.offset(-NEAR_RADIUS, -NEAR_RADIUS, -NEAR_RADIUS), clicked.offset(NEAR_RADIUS, NEAR_RADIUS, NEAR_RADIUS));
    }

    /** Suggestions whose area lies within {@link #NEAR_PICK} of the clicked block. */
    public static List<Suggestion> near(BlockGetter level, BlockPos clicked, long seed) {
        ResolvedSelection sel = nearSelection(clicked).resolve(level, Integer.MAX_VALUE);
        List<Suggestion> out = new ArrayList<>();
        for (Suggestion s : find(level, sel, seed)) {
            if (s.area().inflatedBy(NEAR_PICK).isInside(clicked)) out.add(s);
        }
        return out;
    }

    /** The changes of the chosen variants; a position changed by an earlier choice is not changed again. */
    public static ChangeSet combine(List<Suggestion> all, List<Choice> choices) {
        Map<String, Suggestion> byKey = new HashMap<>();
        for (Suggestion s : all) byKey.putIfAbsent(s.key(), s);
        LongOpenHashSet used = new LongOpenHashSet();
        ChangeSet.Builder b = ChangeSet.builder();
        for (Choice c : choices) {
            Suggestion s = byKey.get(c.key());
            if (s == null || c.variant() < 0 || c.variant() >= s.variants().size()) continue;
            for (Change ch : s.variants().get(c.variant()).changes().changes()) {
                if (used.add(ch.pos().asLong())) b.add(ch);
            }
        }
        return b.build();
    }
}
