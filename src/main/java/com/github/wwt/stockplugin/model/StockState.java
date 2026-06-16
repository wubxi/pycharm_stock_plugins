package com.github.wwt.stockplugin.model;

import java.util.ArrayList;
import java.util.List;

public class StockState {
    public List<StockGroup> watchGroups = new ArrayList<>();
    public List<HoldingGroup> holdingGroups = new ArrayList<>();
    public List<StockItem> indexStocks = new ArrayList<>();
    public String textColorHex = "#DDE6ED";
    public String profitColorHex = "#E05555";
    public String lossColorHex = "#4CAF50";
    public boolean privacyMode = false;
    public boolean hideAmountsInPrivacyMode = false;
    public boolean compactWatchMode = true;
    public boolean groupSidebarVisible = true;
    public int refreshSeconds = 15;
    public List<Integer> watchColumnWidths = new ArrayList<>();
    public List<Integer> holdingColumnWidths = new ArrayList<>();

    public StockState() {
        ensureDefaults();
    }

    public void ensureDefaults() {
        if (watchGroups == null) {
            watchGroups = new ArrayList<>();
        }
        if (holdingGroups == null) {
            holdingGroups = new ArrayList<>();
        }
        if (indexStocks == null) {
            indexStocks = new ArrayList<>();
        }
        if (watchColumnWidths == null) {
            watchColumnWidths = new ArrayList<>();
        }
        if (holdingColumnWidths == null) {
            holdingColumnWidths = new ArrayList<>();
        }
        if (watchGroups.isEmpty()) {
            watchGroups.add(new StockGroup("默认"));
        }
        if (holdingGroups.isEmpty()) {
            holdingGroups.add(new HoldingGroup("默认"));
        }
        if (indexStocks.isEmpty()) {
            indexStocks.add(new StockItem("SH", "000001", "上证指数"));
            indexStocks.add(new StockItem("SZ", "399001", "深证成指"));
            indexStocks.add(new StockItem("SZ", "399006", "创业板指"));
        }
        if (textColorHex == null || textColorHex.isBlank()) {
            textColorHex = "#DDE6ED";
        }
        if (profitColorHex == null || profitColorHex.isBlank()) {
            profitColorHex = "#E05555";
        }
        if (lossColorHex == null || lossColorHex.isBlank()) {
            lossColorHex = "#4CAF50";
        }
        if (refreshSeconds < 5) {
            refreshSeconds = 15;
        }
    }
}
