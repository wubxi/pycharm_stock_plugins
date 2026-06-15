package com.github.wwt.stockplugin.model;

import java.util.ArrayList;
import java.util.List;

public class StockGroup {
    public String name = "默认";
    public List<StockItem> stocks = new ArrayList<>();

    public StockGroup() {
    }

    public StockGroup(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return name;
    }
}
