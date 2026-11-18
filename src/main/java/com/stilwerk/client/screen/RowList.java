package com.stilwerk.client.screen;

import java.util.Collection;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;

/** A scrolling list of rows that may contain widgets, with a configurable row width. */
public class RowList<E extends ContainerObjectSelectionList.Entry<E>> extends ContainerObjectSelectionList<E> {
    private final int rowWidth;

    public RowList(Minecraft mc, int x, int y, int width, int height, int itemHeight) {
        super(mc, width, height, y, itemHeight);
        setX(x);
        this.rowWidth = width - 14;
    }

    @Override
    public int getRowWidth() {
        return rowWidth;
    }

    @Override
    protected int getScrollbarPosition() {
        return getX() + width - 6;
    }

    public void set(Collection<E> rows) {
        double scroll = getScrollAmount();
        replaceEntries(rows);
        setScrollAmount(scroll);
    }

    public int rowLeft() {
        return getRowLeft();
    }
}
