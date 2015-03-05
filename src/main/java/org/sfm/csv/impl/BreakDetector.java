package org.sfm.csv.impl;

import org.sfm.csv.CsvColumnKey;

import java.util.Arrays;

public class BreakDetector  {
    private final CsvColumnKey[] keys;
    private final BreakDetector parent;
    private final int lastIndex;

    private boolean brokenCheck;
    private Object[] lastKeys;
    private boolean broken;
    private boolean isNotNull = true;

    public BreakDetector(CsvColumnKey[] keys, BreakDetector parent, int delayedSetterEnd) {
        this.keys = keys;
        this.parent = parent;
        this.lastIndex = Math.max(delayedSetterEnd, getLastIndex(keys, parent));
    }

    private int getLastIndex(CsvColumnKey[] keys, BreakDetector parent) {
        if (keys.length == 0) return parent.lastIndex;

        int i = 0;
        for(CsvColumnKey k : keys) {
            i = Math.max(i, k.getIndex());
        }
        return i;
    }

    public boolean updateStatus(DelayedCellSetter<?, ?>[] delayedCellSetters, int cellIndex) {
        if (cellIndex == lastIndex) {
            if (brokenCheck) {
                throw new IllegalArgumentException();
            }
            if (keys.length > 0) {
                Object[] currentKeys = getKeys(delayedCellSetters);
                broken = lastKeys == null || !Arrays.equals(currentKeys, lastKeys);
                lastKeys = currentKeys;
            }
            brokenCheck = true;
            return true;
        }
        return false;
    }

    public boolean broken() {
        statusCheck();
        return broken || (parent != null && parent.broken());
    }

    private void statusCheck() {
        if (!brokenCheck) {
            throw new IllegalStateException();
        }
    }

    public boolean isNotNull() {
        statusCheck();
        return isNotNull;
    }

    private Object[] getKeys(DelayedCellSetter<?, ?>[] delayedCellSetters) {
        isNotNull = true;
        Object[] currentKeys = new Object[keys.length];
        for(int i = 0; i < keys.length ; i++) {
            final Object o = delayedCellSetters[keys[i].getIndex()].peekValue();
            isNotNull = isNotNull && o != null;
            currentKeys[i] = o;
        }
        return currentKeys;
    }

    public void reset() {
        brokenCheck = false;
        broken = false;
    }


    public boolean isEmpty() {
        return keys.length == 0;
    }
}
