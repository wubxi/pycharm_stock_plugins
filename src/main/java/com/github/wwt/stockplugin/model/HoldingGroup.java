package com.github.wwt.stockplugin.model;

import java.util.ArrayList;
import java.util.List;

public class HoldingGroup {
    public String name = "默认";
    public List<HoldingItem> holdings = new ArrayList<>();

    public HoldingGroup() {
    }

    public HoldingGroup(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return name;
    }
}
