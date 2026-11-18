package com.stilwerk.detail;

import com.stilwerk.core.ChangeSet;
import com.stilwerk.detail.DetailTypes.Variant;
import java.util.function.Consumer;

/** Per-run data for the rules: seed, materials and a factory for variant edits. */
public final class DetailContext {
    private final BuildingView view;
    private final long seed;
    private final Materials mats;

    public DetailContext(BuildingView view, long seed) {
        this.view = view;
        this.seed = seed;
        this.mats = new Materials(view.wood(), view.stone());
    }

    public long seed() {
        return seed;
    }

    public Materials mats() {
        return mats;
    }

    public Edit edit() {
        return new Edit(view);
    }

    /** Builds a variant with the given translation key suffix (stilwerk.detail.&lt;rule&gt;.&lt;name&gt;). */
    public Variant variant(String rule, String name, Consumer<Edit> body) {
        Edit e = edit();
        body.accept(e);
        ChangeSet cs = e.build();
        return new Variant("stilwerk.detail." + rule + "." + name, cs);
    }
}
