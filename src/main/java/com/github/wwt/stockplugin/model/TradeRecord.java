package com.github.wwt.stockplugin.model;

public class TradeRecord {
    public String date = "";
    public String side = "";
    public int quantity = 0;
    public double price = 0.0;
    public double fee = 0.0;
    public double realizedProfit = 0.0;

    public TradeRecord() {
    }

    public TradeRecord(String date, String side, int quantity, double price, double fee, double realizedProfit) {
        this.date = date;
        this.side = side;
        this.quantity = quantity;
        this.price = price;
        this.fee = fee;
        this.realizedProfit = realizedProfit;
    }
}
