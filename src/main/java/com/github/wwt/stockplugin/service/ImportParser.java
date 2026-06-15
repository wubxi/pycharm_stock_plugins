package com.github.wwt.stockplugin.service;

import com.github.wwt.stockplugin.model.HoldingGroup;
import com.github.wwt.stockplugin.model.HoldingItem;
import com.github.wwt.stockplugin.model.StockGroup;
import com.github.wwt.stockplugin.model.StockItem;
import com.github.wwt.stockplugin.model.StockState;

import java.util.Optional;

public class ImportParser {
    private final StockQuoteService quoteService;

    public ImportParser(StockQuoteService quoteService) {
        this.quoteService = quoteService;
    }

    public int importText(String text, StockState state, StockGroup currentWatchGroup, HoldingGroup currentHoldingGroup) {
        int count = 0;
        for (String rawLine : text.split("\\R")) {
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            String[] parts = line.contains("|") ? line.split("\\|") : line.split("[,\\t ]+");
            if (parts.length == 0) {
                continue;
            }

            String type = parts[0].trim().toUpperCase();
            if ("自选".equals(parts[0]) || "WATCH".equals(type) || "W".equals(type)) {
                count += importWatch(parts, state);
            } else if ("持仓".equals(parts[0]) || "HOLDING".equals(type) || "H".equals(type)) {
                count += importHolding(parts, state);
            } else if (parts.length >= 1) {
                count += importSimpleWatch(parts, currentWatchGroup);
            }
        }
        return count;
    }

    private int importWatch(String[] parts, StockState state) {
        if (parts.length < 3) {
            return 0;
        }
        StockGroup group = findWatchGroup(state, parts[1].trim());
        String code = parts[2].trim();
        String name = parts.length > 3 ? parts[3].trim() : "";
        return quoteService.normalizeStock(code, name).map(item -> addStock(group, item)).orElse(0);
    }

    private int importSimpleWatch(String[] parts, StockGroup group) {
        String code = parts[0].trim();
        String name = parts.length > 1 ? parts[1].trim() : "";
        return quoteService.normalizeStock(code, name).map(item -> addStock(group, item)).orElse(0);
    }

    private int importHolding(String[] parts, StockState state) {
        if (parts.length < 7) {
            return 0;
        }
        HoldingGroup group = findHoldingGroup(state, parts[1].trim());
        Optional<StockItem> stock = quoteService.normalizeStock(parts[2].trim(), parts[3].trim());
        if (stock.isEmpty()) {
            return 0;
        }
        HoldingItem holding = new HoldingItem(
                stock.get().market,
                stock.get().code,
                stock.get().name,
                intValue(parts[4]),
                intValue(parts[5]),
                doubleValue(parts[6])
        );
        if (group.holdings.stream().noneMatch(existing -> existing.key().equals(holding.key()))) {
            group.holdings.add(holding);
            return 1;
        }
        return 0;
    }

    private int addStock(StockGroup group, StockItem item) {
        if (group.stocks.stream().noneMatch(existing -> existing.key().equals(item.key()))) {
            group.stocks.add(item);
            return 1;
        }
        return 0;
    }

    private StockGroup findWatchGroup(StockState state, String name) {
        String groupName = name == null || name.isBlank() ? "默认" : name;
        return state.watchGroups.stream()
                .filter(group -> groupName.equals(group.name))
                .findFirst()
                .orElseGet(() -> {
                    StockGroup group = new StockGroup(groupName);
                    state.watchGroups.add(group);
                    return group;
                });
    }

    private HoldingGroup findHoldingGroup(StockState state, String name) {
        String groupName = name == null || name.isBlank() ? "默认" : name;
        return state.holdingGroups.stream()
                .filter(group -> groupName.equals(group.name))
                .findFirst()
                .orElseGet(() -> {
                    HoldingGroup group = new HoldingGroup(groupName);
                    state.holdingGroups.add(group);
                    return group;
                });
    }

    private int intValue(String raw) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private double doubleValue(String raw) {
        try {
            return Double.parseDouble(raw.trim());
        } catch (NumberFormatException ignored) {
            return 0.0;
        }
    }
}
