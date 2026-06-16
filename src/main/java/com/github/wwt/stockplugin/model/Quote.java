package com.github.wwt.stockplugin.model;

public class Quote {
    public final StockItem stock;
    public final double price;
    public final double changePercent;
    public final double amount;
    public final double previousClose;
    public final double openPrice;

    public Quote(StockItem stock, double price, double changePercent, double amount, double previousClose, double openPrice) {
        this.stock = stock;
        this.price = price;
        this.changePercent = changePercent;
        this.amount = amount;
        this.previousClose = previousClose;
        this.openPrice = openPrice;
    }

    public static Quote empty(StockItem stock) {
        return new Quote(stock, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN);
    }
}
