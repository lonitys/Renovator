package com.renovator.detail;

import com.renovator.detail.DetailTypes.Suggestion;
import java.util.List;

/** One kind of improvement (stairs railings, window shutters ...). */
public interface DetailRule {
    String id();

    void find(BuildingView view, DetailContext ctx, List<Suggestion> out);
}
