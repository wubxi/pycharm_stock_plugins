package com.github.wwt.stockplugin.model;

public class Quote {
    public final StockItem stock;
    public final double price;
    public final double changePercent;
    public final double amount;
    public final double previousClose;

    public Quote(StockItem stock, double price, double changePercent, double amount, double previousClose) {
        this.stock = stock;
        this.price = price;
        this.changePercent = changePercent;
        this.amount = amount;
        this.previousClose = previousClose;
    }

    public static Quote empty(StockItem stock) {
        return new Quote(stock, Double.NaN, Double.NaN, Double.NaN, Double.NaN);
    }
}
