package com.stilwerk.interior;

import java.util.List;

/** A room recipe: which furniture categories, how many, how important, and where they like to stand. */
public record RoomType(String id, List<Slot> slots, boolean rug, boolean light) {
    /** {@code prefer}: far_from_door, near_window, corner, centre, near_wall. */
    public record Slot(String category, int min, int max, int priority, String prefer) {}

    public String translationKey() {
        return "stilwerk.interior.type." + id;
    }
}
