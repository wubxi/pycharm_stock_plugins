package com.github.wwt.stockplugin.model;

import java.util.Locale;
import java.util.Objects;

public class StockItem {
    public String market = "";
    public String code = "";
    public String name = "";

    public StockItem() {
    }

    public StockItem(String market, String code, String name) {
        this.market = market == null ? "" : market;
        this.code = code == null ? "" : code;
        this.name = name == null ? "" : name;
    }

    public String key() {
        return (market + ":" + code).toLowerCase(Locale.ROOT);
    }

    public String displayCode() {
        if ("HK".equalsIgnoreCase(market)) {
            return "HK" + code;
        }
        return market.toUpperCase(Locale.ROOT) + code;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof StockItem item)) {
            return false;
        }
        return key().equals(item.key());
    }

    @Override
    public int hashCode() {
        return Objects.hash(key());
    }

    @Override
    public String toString() {
        String displayName = name == null || name.isBlank() ? "未命名" : name;
        return displayCode() + " " + displayName;
    }
}
