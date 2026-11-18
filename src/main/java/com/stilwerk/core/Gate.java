package com.stilwerk.core;

import java.util.List;

/** Decides per group whether it may be applied (survival: pays for it) and settles what was replaced. */
public interface Gate {
    /** Pay for the group before it is set; false skips the group and takes nothing. */
    boolean admit(List<Change> group);

    /** Called with the changes of one group that were actually set (refund the old blocks here). */
    void settled(List<Change> applied);

    /** Called once when the whole job is done. */
    default void finished() {}
}
