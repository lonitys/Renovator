package com.stilwerk.core;

import java.util.List;

/** Creative mode: everything is free and nothing is refunded. */
public final class CreativeGate implements Gate {
    public static final CreativeGate INSTANCE = new CreativeGate();

    private CreativeGate() {}

    @Override
    public boolean admit(List<Change> group) {
        return true;
    }

    @Override
    public void settled(List<Change> applied) {}
}
