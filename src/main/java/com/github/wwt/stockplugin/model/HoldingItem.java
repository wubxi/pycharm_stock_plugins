package com.github.wwt.stockplugin.model;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class HoldingItem extends StockItem {
    public int shares = 0;
    public int availableShares = 0;
    public double costPrice = 0.0;
    public double realizedProfit = 0.0;
    public List<TradeRecord> trades = new ArrayList<>();

    public HoldingItem() {
    }

    public HoldingItem(String market, String code, String name, int shares, int availableShares, double costPrice) {
        super(market, code, name);
        this.shares = shares;
        this.availableShares = availableShares;
        this.costPrice = costPrice;
    }

    public void buy(int quantity, double price, double fee) {
        double currentCost = shares * costPrice;
        double newCost = currentCost + quantity * price + fee;
        shares += quantity;
        availableShares += quantity;
        costPrice = shares == 0 ? 0 : newCost / shares;

        TradeRecord record = new TradeRecord(LocalDate.now().toString(), "BUY", quantity, price, fee, 0.0);
        trades.add(record);
    }

    public double sell(int quantity, double price, double fee) {
        int sellQuantity = Math.min(quantity, shares);
        double profit = sellQuantity * (price - costPrice) - fee;
        shares -= sellQuantity;
        availableShares = Math.max(0, availableShares - sellQuantity);
        realizedProfit += profit;

        TradeRecord record = new TradeRecord(LocalDate.now().toString(), "SELL", sellQuantity, price, fee, profit);
        trades.add(record);
        return profit;
    }
}
