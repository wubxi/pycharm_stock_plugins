package com.github.wwt.stockplugin.model;

import java.util.ArrayList;
import java.util.List;

public class StockState {
    public List<StockGroup> watchGroups = new ArrayList<>();
    public List<HoldingGroup> holdingGroups = new ArrayList<>();
    public String textColorHex = "#DDE6ED";
    public String profitColorHex = "#E05555";
    public String lossColorHex = "#4CAF50";
    public boolean privacyMode = false;
    public int refreshSeconds = 15;

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
        if (watchGroups.isEmpty()) {
            watchGroups.add(new StockGroup("默认"));
        }
        if (holdingGroups.isEmpty()) {
            holdingGroups.add(new HoldingGroup("默认"));
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
