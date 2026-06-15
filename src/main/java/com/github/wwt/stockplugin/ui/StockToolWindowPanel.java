package com.github.wwt.stockplugin.ui;

import com.github.wwt.stockplugin.model.HoldingGroup;
import com.github.wwt.stockplugin.model.HoldingItem;
import com.github.wwt.stockplugin.model.Quote;
import com.github.wwt.stockplugin.model.StockGroup;
import com.github.wwt.stockplugin.model.StockItem;
import com.github.wwt.stockplugin.model.StockState;
import com.github.wwt.stockplugin.service.ImportParser;
import com.github.wwt.stockplugin.service.StockQuoteService;
import com.github.wwt.stockplugin.service.StockStateService;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;

import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JToolBar;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class StockToolWindowPanel extends JPanel {
    private static final DecimalFormat PRICE = new DecimalFormat("0.00");
    private static final DecimalFormat PERCENT = new DecimalFormat("+0.00%;-0.00%");
    private static final DecimalFormat MONEY = new DecimalFormat("#,##0.00");

    private final Project project;
    private final StockState state;
    private final StockQuoteService quoteService = new StockQuoteService();
    private final ImportParser importParser = new ImportParser(quoteService);
    private final Map<String, Quote> quotes = new ConcurrentHashMap<>();
    private final AtomicBoolean refreshing = new AtomicBoolean(false);

    private final JComboBox<StockGroup> watchGroupBox = new JComboBox<>();
    private final JComboBox<HoldingGroup> holdingGroupBox = new JComboBox<>();
    private final DefaultTableModel watchModel = new DefaultTableModel(new Object[]{"代码", "名称", "当前价格", "当日涨幅%", "成交额"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final DefaultTableModel holdingModel = new DefaultTableModel(new Object[]{"名称/市值", "持仓盈亏", "持仓/可用", "成本/现价", "今日收益"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JTable watchTable = new JTable(watchModel);
    private final JTable holdingTable = new JTable(holdingModel);
    private final JLabel totalMarketValueTitleLabel = new JLabel("总市值");
    private final JLabel totalMarketValueLabel = new JLabel("--");
    private final JLabel holdingTotalSummaryTitleLabel = new JLabel("持仓总盈亏");
    private final JLabel holdingTotalSummaryLabel = new JLabel("--");
    private final JLabel holdingTodaySummaryTitleLabel = new JLabel("今日总盈亏");
    private final JLabel holdingTodaySummaryLabel = new JLabel("--");
    private final JLabel statusLabel = new JLabel("准备刷新");
    private javax.swing.Timer refreshTimer;

    public StockToolWindowPanel(Project project) {
        super(new BorderLayout());
        this.project = project;
        this.state = StockStateService.getInstance().getState();
        buildUi();
        refreshGroupBoxes();
        renderTables();
        startTimer();
        refreshQuotes();
    }

    private void buildUi() {
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("自选", buildWatchPanel());
        tabs.addTab("持仓", buildHoldingPanel());
        add(tabs, BorderLayout.CENTER);
        add(statusLabel, BorderLayout.SOUTH);
    }

    private JPanel buildWatchPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        JToolBar toolbar = toolbar();
        toolbar.add(new JLabel("分组 "));
        toolbar.add(watchGroupBox);
        toolbar.add(button("新分组", this::addWatchGroup));
        toolbar.add(button("删除分组", this::removeWatchGroup));
        toolbar.addSeparator();
        toolbar.add(button("搜索添加", this::searchAddWatch));
        toolbar.add(button("删除股票", this::removeSelectedWatchStock));
        toolbar.add(button("导入", this::importStocks));
        toolbar.add(button("刷新", this::refreshQuotes));
        toolbar.add(button("设置", this::openSettings));
        panel.add(toolbar, BorderLayout.NORTH);

        prepareTable(watchTable, false);
        panel.add(new JScrollPane(watchTable), BorderLayout.CENTER);
        watchGroupBox.addActionListener(event -> renderTables());
        return panel;
    }

    private JPanel buildHoldingPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        JToolBar toolbar = toolbar();
        toolbar.add(new JLabel("分组 "));
        toolbar.add(holdingGroupBox);
        toolbar.add(button("新分组", this::addHoldingGroup));
        toolbar.add(button("删除分组", this::removeHoldingGroup));
        toolbar.addSeparator();
        toolbar.add(button("搜索添加", this::searchAddHolding));
        toolbar.add(button("新增持仓", this::addHolding));
        toolbar.add(button("买入/卖出", this::recordTrade));
        toolbar.add(button("删除持仓", this::removeSelectedHolding));
        toolbar.add(button("导入", this::importStocks));
        toolbar.add(button("刷新", this::refreshQuotes));
        panel.add(toolbar, BorderLayout.NORTH);

        prepareTable(holdingTable, true);
        panel.add(new JScrollPane(holdingTable), BorderLayout.CENTER);
        panel.add(buildHoldingSummaryPanel(), BorderLayout.SOUTH);
        holdingGroupBox.addActionListener(event -> renderTables());
        return panel;
    }

    private JPanel buildHoldingSummaryPanel() {
        JPanel panel = new JPanel(new java.awt.GridLayout(1, 3, 24, 0));
        panel.setBorder(javax.swing.BorderFactory.createEmptyBorder(14, 20, 14, 20));
        panel.add(summaryBlock(totalMarketValueTitleLabel, totalMarketValueLabel));
        panel.add(summaryBlock(holdingTotalSummaryTitleLabel, holdingTotalSummaryLabel));
        panel.add(summaryBlock(holdingTodaySummaryTitleLabel, holdingTodaySummaryLabel));
        return panel;
    }

    private JPanel summaryBlock(JLabel titleLabel, JLabel valueLabel) {
        JPanel panel = new JPanel(new BorderLayout(0, 8));
        titleLabel.setHorizontalAlignment(JLabel.CENTER);
        valueLabel.setHorizontalAlignment(JLabel.CENTER);
        titleLabel.setFont(titleLabel.getFont().deriveFont(java.awt.Font.BOLD, titleLabel.getFont().getSize2D() + 1.0f));
        valueLabel.setFont(valueLabel.getFont().deriveFont(valueLabel.getFont().getSize2D() + 1.0f));
        panel.add(titleLabel, BorderLayout.NORTH);
        panel.add(valueLabel, BorderLayout.CENTER);
        return panel;
    }

    private JToolBar toolbar() {
        JToolBar toolbar = new JToolBar();
        toolbar.setFloatable(false);
        toolbar.setLayout(new FlowLayout(FlowLayout.LEFT, 6, 3));
        return toolbar;
    }

    private JButton button(String text, Runnable action) {
        JButton button = new JButton(text);
        button.addActionListener(event -> action.run());
        return button;
    }

    private void prepareTable(JTable table, boolean twoLineRows) {
        table.setFillsViewportHeight(true);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setAutoCreateRowSorter(true);
        table.setComponentPopupMenu(tableMenu(table));
        table.setRowHeight(twoLineRows ? 48 : 30);
        ((DefaultTableCellRenderer) table.getTableHeader().getDefaultRenderer()).setHorizontalAlignment(JLabel.CENTER);
        ColorRenderer renderer = new ColorRenderer();
        for (int column = 0; column < table.getColumnCount(); column++) {
            table.getColumnModel().getColumn(column).setCellRenderer(renderer);
        }
    }

    private JPopupMenu tableMenu(JTable table) {
        JPopupMenu menu = new JPopupMenu();
        JMenuItem refresh = new JMenuItem("刷新");
        refresh.addActionListener(event -> refreshQuotes());
        menu.add(refresh);
        return menu;
    }

    private void refreshGroupBoxes() {
        state.ensureDefaults();
        watchGroupBox.setModel(new DefaultComboBoxModel<>(state.watchGroups.toArray(new StockGroup[0])));
        holdingGroupBox.setModel(new DefaultComboBoxModel<>(state.holdingGroups.toArray(new HoldingGroup[0])));
    }

    private StockGroup currentWatchGroup() {
        StockGroup selected = (StockGroup) watchGroupBox.getSelectedItem();
        return selected == null ? state.watchGroups.get(0) : selected;
    }

    private HoldingGroup currentHoldingGroup() {
        HoldingGroup selected = (HoldingGroup) holdingGroupBox.getSelectedItem();
        return selected == null ? state.holdingGroups.get(0) : selected;
    }

    private void addWatchGroup() {
        String name = Messages.showInputDialog(project, "自选分组名称", "新建分组", null);
        if (name == null || name.isBlank()) {
            return;
        }
        state.watchGroups.add(new StockGroup(name.trim()));
        refreshGroupBoxes();
        watchGroupBox.setSelectedIndex(state.watchGroups.size() - 1);
    }

    private void addHoldingGroup() {
        String name = Messages.showInputDialog(project, "持仓分组名称", "新建分组", null);
        if (name == null || name.isBlank()) {
            return;
        }
        state.holdingGroups.add(new HoldingGroup(name.trim()));
        refreshGroupBoxes();
        holdingGroupBox.setSelectedIndex(state.holdingGroups.size() - 1);
    }

    private void removeWatchGroup() {
        if (state.watchGroups.size() <= 1) {
            Messages.showInfoMessage(project, "至少保留一个自选分组。", "无法删除");
            return;
        }
        state.watchGroups.remove(currentWatchGroup());
        refreshGroupBoxes();
        renderTables();
    }

    private void removeHoldingGroup() {
        if (state.holdingGroups.size() <= 1) {
            Messages.showInfoMessage(project, "至少保留一个持仓分组。", "无法删除");
            return;
        }
        state.holdingGroups.remove(currentHoldingGroup());
        refreshGroupBoxes();
        renderTables();
    }

    private void searchAddWatch() {
        SearchDialog dialog = new SearchDialog(project, quoteService);
        if (!dialog.showAndGet() || dialog.selectedStock() == null) {
            return;
        }
        StockGroup group = currentWatchGroup();
        StockItem selected = dialog.selectedStock();
        if (group.stocks.stream().noneMatch(stock -> stock.key().equals(selected.key()))) {
            group.stocks.add(selected);
        }
        refreshQuotes();
    }

    private void addHolding() {
        HoldingDialog dialog = new HoldingDialog(project, quoteService);
        if (!dialog.showAndGet()) {
            return;
        }
        addHoldingItem(dialog.holding());
    }

    private void searchAddHolding() {
        SearchDialog searchDialog = new SearchDialog(project, quoteService);
        if (!searchDialog.showAndGet() || searchDialog.selectedStock() == null) {
            return;
        }
        HoldingDialog holdingDialog = new HoldingDialog(project, quoteService, searchDialog.selectedStock());
        if (!holdingDialog.showAndGet()) {
            return;
        }
        addHoldingItem(holdingDialog.holding());
    }

    private void addHoldingItem(HoldingItem holding) {
        if (holding.code.isBlank()) {
            Messages.showErrorDialog(project, "股票代码不能为空。", "新增失败");
            return;
        }
        HoldingGroup group = currentHoldingGroup();
        if (group.holdings.stream().noneMatch(item -> item.key().equals(holding.key()))) {
            group.holdings.add(holding);
        }
        refreshQuotes();
    }

    private void recordTrade() {
        HoldingGroup group = currentHoldingGroup();
        if (group.holdings.isEmpty()) {
            Messages.showInfoMessage(project, "当前持仓分组还没有股票。", "无法录入交易");
            return;
        }
        TradeDialog dialog = new TradeDialog(project, group);
        if (!dialog.showAndGet() || dialog.holding() == null) {
            return;
        }
        HoldingItem holding = dialog.holding();
        if (dialog.isBuy()) {
            holding.buy(dialog.quantity(), dialog.price(), dialog.fee());
        } else {
            if (dialog.quantity() > holding.shares) {
                Messages.showErrorDialog(project, "卖出数量不能大于当前持仓。", "交易失败");
                return;
            }
            double profit = holding.sell(dialog.quantity(), dialog.price(), dialog.fee());
            Messages.showInfoMessage(project, "本次实际盈亏：" + MONEY.format(profit), "交易已记录");
        }
        renderTables();
        refreshQuotes();
    }

    private void removeSelectedWatchStock() {
        int row = watchTable.getSelectedRow();
        if (row < 0) {
            return;
        }
        int modelRow = watchTable.convertRowIndexToModel(row);
        StockGroup group = currentWatchGroup();
        if (modelRow >= 0 && modelRow < group.stocks.size()) {
            group.stocks.remove(modelRow);
            renderTables();
        }
    }

    private void removeSelectedHolding() {
        int row = holdingTable.getSelectedRow();
        if (row < 0) {
            return;
        }
        int modelRow = holdingTable.convertRowIndexToModel(row);
        HoldingGroup group = currentHoldingGroup();
        if (modelRow >= 0 && modelRow < group.holdings.size()) {
            group.holdings.remove(modelRow);
            renderTables();
        }
    }

    private void importStocks() {
        ImportDialog dialog = new ImportDialog(project);
        if (!dialog.showAndGet()) {
            return;
        }
        int imported = importParser.importText(dialog.importText(), state, currentWatchGroup(), currentHoldingGroup());
        refreshGroupBoxes();
        renderTables();
        refreshQuotes();
        Messages.showInfoMessage(project, "已导入 " + imported + " 条记录。", "导入完成");
    }

    private void openSettings() {
        ShowSettingsUtil.getInstance().showSettingsDialog(project, StockSettingsConfigurable.class);
        startTimer();
        renderTables();
    }

    private void startTimer() {
        if (refreshTimer != null) {
            refreshTimer.stop();
        }
        refreshTimer = new javax.swing.Timer(Math.max(5, state.refreshSeconds) * 1000, event -> refreshQuotes());
        refreshTimer.start();
    }

    private void refreshQuotes() {
        if (!refreshing.compareAndSet(false, true)) {
            return;
        }
        statusLabel.setText("行情刷新中...");
        List<StockItem> stocks = allStocks();
        CompletableFuture.runAsync(() -> {
            for (StockItem stock : stocks) {
                Quote quote = quoteService.quote(stock);
                quotes.put(stock.key(), quote);
                if (quote.stock.name != null && !quote.stock.name.isBlank()) {
                    stock.name = quote.stock.name;
                }
            }
        }).whenComplete((ignored, throwable) -> SwingUtilities.invokeLater(() -> {
            refreshing.set(false);
            renderTables();
            statusLabel.setText(throwable == null ? "行情已刷新" : "刷新失败，请稍后再试");
        }));
    }

    private List<StockItem> allStocks() {
        List<StockItem> stocks = new ArrayList<>();
        state.watchGroups.forEach(group -> stocks.addAll(group.stocks));
        state.holdingGroups.forEach(group -> stocks.addAll(group.holdings));
        return stocks;
    }

    private void renderTables() {
        applyTextColor();
        renderWatchTable();
        renderHoldingTable();
    }

    private void renderWatchTable() {
        watchModel.setRowCount(0);
        for (StockItem stock : currentWatchGroup().stocks) {
            Quote quote = quotes.getOrDefault(stock.key(), Quote.empty(stock));
            watchModel.addRow(new Object[]{
                    stock.displayCode(),
                    stock.name,
                    new DisplayCell(formatPrice(quote.price), "", quote.changePercent),
                    new DisplayCell(formatPercent(quote.changePercent), "", quote.changePercent),
                    formatAmount(quote.amount)
            });
        }
    }

    private void renderHoldingTable() {
        holdingModel.setRowCount(0);
        double totalMarketValue = 0.0;
        double totalTodayProfit = 0.0;
        double totalHoldingProfit = 0.0;
        boolean hasTodayProfit = false;
        for (HoldingItem holding : currentHoldingGroup().holdings) {
            Quote quote = quotes.getOrDefault(holding.key(), Quote.empty(holding));
            double price = Double.isFinite(quote.price) ? quote.price : holding.costPrice;
            double marketValue = holding.shares * price;
            double floatingProfit = holding.shares * (price - holding.costPrice);
            double todayProfit = Double.isFinite(quote.previousClose) ? holding.shares * (price - quote.previousClose) : Double.NaN;
            totalMarketValue += marketValue;
            totalHoldingProfit += floatingProfit + holding.realizedProfit;
            if (Double.isFinite(todayProfit)) {
                totalTodayProfit += todayProfit;
                hasTodayProfit = true;
            }
            holdingModel.addRow(new Object[]{
                    new DisplayCell(holding.name, MONEY.format(marketValue), 0.0),
                    new DisplayCell(MONEY.format(floatingProfit), "已实现 " + MONEY.format(holding.realizedProfit), floatingProfit + holding.realizedProfit),
                    new DisplayCell(String.valueOf(holding.shares), String.valueOf(holding.availableShares), 0.0),
                    new DisplayCell(formatPrice(holding.costPrice), formatPrice(price), price - holding.costPrice),
                    new DisplayCell(formatAmount(todayProfit), formatPercent(quote.changePercent), todayProfit)
            });
        }
        updateHoldingSummary(totalMarketValue, hasTodayProfit ? totalTodayProfit : Double.NaN, totalHoldingProfit);
    }

    private void updateHoldingSummary(double marketValue, double todayProfit, double holdingProfit) {
        totalMarketValueLabel.setText(formatAmount(marketValue));
        holdingTodaySummaryLabel.setText(formatAmount(todayProfit));
        holdingTotalSummaryLabel.setText(formatAmount(holdingProfit));

        Color textColor = parseColor(state.textColorHex);
        totalMarketValueTitleLabel.setForeground(textColor);
        totalMarketValueLabel.setForeground(textColor);
        holdingTodaySummaryTitleLabel.setForeground(signalColor(todayProfit));
        holdingTodaySummaryLabel.setForeground(signalColor(todayProfit));
        holdingTotalSummaryTitleLabel.setForeground(signalColor(holdingProfit));
        holdingTotalSummaryLabel.setForeground(signalColor(holdingProfit));
    }

    private void applyTextColor() {
        Color color = parseColor(state.textColorHex);
        applyTextColor(this, color);
        repaint();
    }

    private void applyTextColor(Component component, Color color) {
        component.setForeground(color);
        if (component instanceof java.awt.Container container) {
            for (Component child : container.getComponents()) {
                applyTextColor(child, color);
            }
        }
    }

    private Color parseColor(String hex) {
        try {
            return Color.decode(hex);
        } catch (NumberFormatException exception) {
            return Color.decode("#DDE6ED");
        }
    }

    private Color signalColor(double value) {
        if (state.privacyMode || !Double.isFinite(value) || value == 0.0) {
            return parseColor(state.textColorHex);
        }
        return parseColor(value > 0 ? state.profitColorHex : state.lossColorHex);
    }

    private String formatPrice(double value) {
        return Double.isFinite(value) ? PRICE.format(value) : "--";
    }

    private String formatPercent(double value) {
        return Double.isFinite(value) ? PERCENT.format(value / 100.0) : "--";
    }

    private String formatAmount(double value) {
        if (!Double.isFinite(value)) {
            return "--";
        }
        double abs = Math.abs(value);
        if (abs >= 100000000) {
            return MONEY.format(value / 100000000) + "亿";
        }
        if (abs >= 10000) {
            return MONEY.format(value / 10000) + "万";
        }
        return MONEY.format(value);
    }

    private static class DisplayCell {
        private final String top;
        private final String bottom;
        private final double signal;

        private DisplayCell(String top, String bottom, double signal) {
            this.top = top == null || top.isBlank() ? "--" : top;
            this.bottom = bottom == null ? "" : bottom;
            this.signal = signal;
        }

        private boolean twoLine() {
            return !bottom.isBlank();
        }

        @Override
        public String toString() {
            return twoLine() ? top + " " + bottom : top;
        }
    }

    private class ColorRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean selected, boolean focus, int row, int column) {
            Component component = super.getTableCellRendererComponent(table, value, selected, focus, row, column);
            setVerticalAlignment(CENTER);
            setHorizontalAlignment(CENTER);
            if (value instanceof DisplayCell cell) {
                setText(cell.twoLine()
                        ? "<html><div style='text-align:center'><b>" + escapeHtml(cell.top) + "</b><br><span style='font-size:90%'>" + escapeHtml(cell.bottom) + "</span></div></html>"
                        : escapeHtml(cell.top));
                if (!selected) {
                    component.setForeground(signalColor(cell.signal));
                }
                return component;
            }
            if (!selected) {
                component.setForeground(parseColor(state.textColorHex));
            }
            return component;
        }
    }

    private String escapeHtml(String text) {
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
