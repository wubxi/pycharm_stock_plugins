package com.github.wwt.stockplugin.ui;

import com.github.wwt.stockplugin.model.HoldingGroup;
import com.github.wwt.stockplugin.model.HoldingItem;
import com.github.wwt.stockplugin.model.Quote;
import com.github.wwt.stockplugin.model.StockGroup;
import com.github.wwt.stockplugin.model.StockItem;
import com.github.wwt.stockplugin.model.StockState;
import com.github.wwt.stockplugin.model.TradeRecord;
import com.github.wwt.stockplugin.service.ImportParser;
import com.github.wwt.stockplugin.service.StockQuoteService;
import com.github.wwt.stockplugin.service.StockStateService;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;

import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.JToolBar;
import javax.swing.SwingConstants;
import javax.swing.plaf.basic.BasicSplitPaneDivider;
import javax.swing.plaf.basic.BasicSplitPaneUI;
import javax.swing.ListSelectionModel;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.event.ChangeEvent;
import javax.swing.event.TableColumnModelEvent;
import javax.swing.event.TableColumnModelListener;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.GridLayout;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class StockToolWindowPanel extends JPanel {
    private static final DecimalFormat PRICE = new DecimalFormat("0.00");
    private static final DecimalFormat PERCENT = new DecimalFormat("+0.00%;-0.00%");
    private static final DecimalFormat MONEY = new DecimalFormat("#,##0.00");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final Project project;
    private final StockState state;
    private final StockQuoteService quoteService = new StockQuoteService();
    private final ImportParser importParser = new ImportParser(quoteService);
    private final Map<String, Quote> quotes = new ConcurrentHashMap<>();
    private final Map<String, String> quoteTimes = new ConcurrentHashMap<>();
    private final AtomicBoolean refreshing = new AtomicBoolean(false);

    private final JComboBox<StockGroup> watchGroupBox = new JComboBox<>();
    private final JComboBox<HoldingGroup> holdingGroupBox = new JComboBox<>();
    private final DefaultListModel<StockGroup> watchGroupModel = new DefaultListModel<>();
    private final DefaultListModel<HoldingGroup> holdingGroupModel = new DefaultListModel<>();
    private final JList<StockGroup> watchGroupList = new JList<>(watchGroupModel);
    private final JList<HoldingGroup> holdingGroupList = new JList<>(holdingGroupModel);
    private final JTextField watchSearchField = new PromptTextField("代码 / 名称");
    private final JTextField holdingSearchField = new PromptTextField("代码 / 名称");
    private final JLabel watchIndexLabel = new JLabel("指数 --");
    private final JLabel holdingIndexLabel = new JLabel("指数 --");
    private final DefaultTableModel watchModel = new DefaultTableModel(new Object[]{"代码", "名称", "当前价格", "当日涨幅%", "成交额", "更新时间"}, 0) {
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
    private final JLabel holdingCountTitleLabel = new JLabel("持仓数量");
    private final JLabel holdingCountLabel = new JLabel("--");
    private final JLabel statusLabel = new JLabel("准备刷新");
    private final JButton watchGroupToggleButton = button("分组", this::toggleGroupSidebar);
    private final JButton holdingGroupToggleButton = button("分组", this::toggleGroupSidebar);
    private JSplitPane watchSplitPane;
    private JSplitPane holdingSplitPane;
    private JPanel watchGroupSidePanel;
    private JPanel holdingGroupSidePanel;
    private javax.swing.Timer refreshTimer;

    public StockToolWindowPanel(Project project) {
        super(new BorderLayout());
        this.project = project;
        this.state = StockStateService.getInstance().getState();
        setMinimumSize(new Dimension(0, 0));
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
        panel.setMinimumSize(new Dimension(0, 0));
        panel.add(toolbarPanel(
                watchGroupToggleButton,
                watchSearchField,
                compactTextButton("添加", "搜索并添加到自选", () -> searchAddWatch(watchSearchField.getText())),
                null,
                watchMoreMenu()
        ), BorderLayout.NORTH);

        prepareTable(watchTable, false);
        watchSplitPane = groupSplitPane(watchGroupList, buildWatchGroupActions(), contentWithIndex(watchIndexLabel, tableScrollPane(watchTable)), false);
        panel.add(watchSplitPane, BorderLayout.CENTER);
        watchGroupList.addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) {
                watchGroupBox.setSelectedItem(currentWatchGroup());
                renderTables();
            }
        });
        watchSearchField.addActionListener(event -> searchAddWatch(watchSearchField.getText()));
        return panel;
    }

    private JPanel buildHoldingPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setMinimumSize(new Dimension(0, 0));
        panel.add(toolbarPanel(
                holdingGroupToggleButton,
                holdingSearchField,
                compactTextButton("添加", "搜索并添加到持仓", () -> searchAddHolding(holdingSearchField.getText())),
                compactTextButton("交易", "录入买入/卖出", this::recordTrade),
                holdingMoreMenu()
        ), BorderLayout.NORTH);

        prepareTable(holdingTable, true);
        holdingSplitPane = groupSplitPane(holdingGroupList, buildHoldingGroupActions(), contentWithIndex(holdingIndexLabel, tableScrollPane(holdingTable)), true);
        panel.add(holdingSplitPane, BorderLayout.CENTER);
        panel.add(buildHoldingSummaryPanel(), BorderLayout.SOUTH);
        holdingGroupList.addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) {
                holdingGroupBox.setSelectedItem(currentHoldingGroup());
                renderTables();
            }
        });
        holdingSearchField.addActionListener(event -> searchAddHolding(holdingSearchField.getText()));
        return panel;
    }

    private JPanel buildHoldingSummaryPanel() {
        JPanel panel = new JPanel(new GridLayout(1, 4, 10, 0));
        panel.setBorder(javax.swing.BorderFactory.createEmptyBorder(4, 10, 4, 10));
        panel.add(summaryBlock(totalMarketValueTitleLabel, totalMarketValueLabel));
        panel.add(summaryBlock(holdingTotalSummaryTitleLabel, holdingTotalSummaryLabel));
        panel.add(summaryBlock(holdingTodaySummaryTitleLabel, holdingTodaySummaryLabel));
        panel.add(summaryBlock(holdingCountTitleLabel, holdingCountLabel));
        return panel;
    }

    private JPanel contentWithIndex(JLabel indexLabel, JScrollPane tablePane) {
        JPanel panel = new JPanel(new BorderLayout());
        indexLabel.setBorder(javax.swing.BorderFactory.createEmptyBorder(3, 10, 3, 10));
        panel.add(indexLabel, BorderLayout.NORTH);
        panel.add(tablePane, BorderLayout.CENTER);
        return panel;
    }

    private JPanel summaryBlock(JLabel titleLabel, JLabel valueLabel) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.CENTER, 4, 0));
        titleLabel.setHorizontalAlignment(JLabel.RIGHT);
        valueLabel.setHorizontalAlignment(JLabel.LEFT);
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, titleLabel.getFont().getSize2D() - 1.0f));
        valueLabel.setFont(valueLabel.getFont().deriveFont(valueLabel.getFont().getSize2D() - 1.0f));
        panel.add(titleLabel);
        panel.add(valueLabel);
        return panel;
    }

    private JSplitPane groupSplitPane(JList<?> groupList, JPanel groupActions, Component content, boolean holding) {
        JPanel sidePanel = new JPanel(new BorderLayout(0, 6));
        sidePanel.setPreferredSize(new Dimension(148, 0));
        sidePanel.setMinimumSize(new Dimension(88, 0));
        sidePanel.add(new JLabel("分组"), BorderLayout.NORTH);
        sidePanel.add(new JScrollPane(groupList), BorderLayout.CENTER);
        sidePanel.add(groupActions, BorderLayout.SOUTH);
        if (holding) {
            holdingGroupSidePanel = sidePanel;
        } else {
            watchGroupSidePanel = sidePanel;
        }

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, sidePanel, content);
        splitPane.setResizeWeight(0.0);
        splitPane.setDividerLocation(148);
        splitPane.setDividerSize(1);
        splitPane.setBorder(null);
        splitPane.setMinimumSize(new Dimension(0, 0));
        splitPane.setUI(new BasicSplitPaneUI() {
            @Override
            public BasicSplitPaneDivider createDefaultDivider() {
                return new BasicSplitPaneDivider(this) {
                    @Override
                    public void paint(java.awt.Graphics graphics) {
                        graphics.setColor(new Color(75, 78, 81));
                        graphics.fillRect(0, 0, getWidth(), getHeight());
                    }
                };
            }
        });
        applyGroupSidebarVisibility(splitPane, sidePanel);
        return splitPane;
    }

    private void toggleGroupSidebar() {
        state.groupSidebarVisible = !state.groupSidebarVisible;
        applyGroupSidebarVisibility(watchSplitPane, watchGroupSidePanel);
        applyGroupSidebarVisibility(holdingSplitPane, holdingGroupSidePanel);
        updateGroupToggleButtons();
        revalidate();
        repaint();
    }

    private void applyGroupSidebarVisibility(JSplitPane splitPane, JPanel sidePanel) {
        if (splitPane == null || sidePanel == null) {
            return;
        }
        sidePanel.setVisible(state.groupSidebarVisible);
        splitPane.setDividerSize(state.groupSidebarVisible ? 1 : 0);
        if (state.groupSidebarVisible) {
            splitPane.setDividerLocation(148);
        } else {
            splitPane.setDividerLocation(0);
        }
        updateGroupToggleButtons();
    }

    private void updateGroupToggleButtons() {
        String tooltip = state.groupSidebarVisible ? "隐藏分组" : "显示分组";
        watchGroupToggleButton.setText("☰");
        holdingGroupToggleButton.setText("☰");
        watchGroupToggleButton.setToolTipText(tooltip);
        holdingGroupToggleButton.setToolTipText(tooltip);
        Dimension size = new Dimension(38, 30);
        watchGroupToggleButton.setPreferredSize(size);
        watchGroupToggleButton.setMaximumSize(size);
        holdingGroupToggleButton.setPreferredSize(size);
        holdingGroupToggleButton.setMaximumSize(size);
    }

    private JPanel buildWatchGroupActions() {
        JPanel panel = new JPanel(new GridLayout(1, 3, 4, 0));
        panel.add(button("+", this::addWatchGroup));
        panel.add(button("改名", this::renameWatchGroup));
        panel.add(button("-", this::removeWatchGroup));
        return panel;
    }

    private JPanel buildHoldingGroupActions() {
        JPanel panel = new JPanel(new GridLayout(1, 3, 4, 0));
        panel.add(button("+", this::addHoldingGroup));
        panel.add(button("改名", this::renameHoldingGroup));
        panel.add(button("-", this::removeHoldingGroup));
        return panel;
    }

    private JButton menuButton(String text, JPopupMenu menu) {
        JButton button = new JButton(text);
        button.setToolTipText("更多操作");
        Dimension size = new Dimension(38, 30);
        button.setPreferredSize(size);
        button.setMaximumSize(size);
        button.addActionListener(event -> menu.show(button, 0, button.getHeight()));
        return button;
    }

    private JPanel toolbarPanel(JButton groupButton, JTextField searchField, JButton addButton, JButton extraButton, JPopupMenu menu) {
        JPanel panel = new JPanel(new BorderLayout(8, 0));
        panel.setBorder(javax.swing.BorderFactory.createEmptyBorder(6, 8, 6, 8));

        JToolBar left = toolbar();
        left.add(groupButton);
        left.add(searchField);
        left.add(addButton);
        if (extraButton != null) {
            left.add(extraButton);
        }

        JToolBar right = toolbar();
        right.add(iconButton("⟳", "刷新行情", this::refreshQuotes));
        right.add(menuButton("⋯", menu));

        panel.add(left, BorderLayout.CENTER);
        panel.add(right, BorderLayout.EAST);
        return panel;
    }

    private JPopupMenu watchMoreMenu() {
        JPopupMenu menu = new JPopupMenu();
        menu.add(menuItem("新分组", this::addWatchGroup));
        menu.add(menuItem("重命名分组", this::renameWatchGroup));
        menu.add(menuItem("删除分组", this::removeWatchGroup));
        menu.addSeparator();
        menu.add(menuItem("删除股票", this::removeSelectedWatchStock));
        menu.add(menuItem("复制代码", this::copySelectedWatchCode));
        menu.add(menuItem("添加指数", this::addIndexStock));
        menu.add(menuItem("重置指数", this::resetIndexStocks));
        menu.add(menuItem("导入", this::importStocks));
        menu.addSeparator();
        menu.add(menuItem("设置", this::openSettings));
        return menu;
    }

    private JPopupMenu holdingMoreMenu() {
        JPopupMenu menu = new JPopupMenu();
        menu.add(menuItem("新分组", this::addHoldingGroup));
        menu.add(menuItem("重命名分组", this::renameHoldingGroup));
        menu.add(menuItem("删除分组", this::removeHoldingGroup));
        menu.addSeparator();
        menu.add(menuItem("新增持仓", this::addHolding));
        menu.add(menuItem("修改持仓", this::editSelectedHolding));
        menu.add(menuItem("删除持仓", this::removeSelectedHolding));
        menu.add(menuItem("复制代码", this::copySelectedHoldingCode));
        menu.add(menuItem("添加指数", this::addIndexStock));
        menu.add(menuItem("重置指数", this::resetIndexStocks));
        menu.add(menuItem("导入", this::importStocks));
        menu.addSeparator();
        menu.add(menuItem("设置", this::openSettings));
        return menu;
    }

    private JMenuItem menuItem(String text, Runnable action) {
        JMenuItem item = new JMenuItem(text);
        item.addActionListener(event -> action.run());
        return item;
    }

    private JToolBar toolbar() {
        JToolBar toolbar = new JToolBar();
        toolbar.setFloatable(false);
        toolbar.setLayout(new FlowLayout(FlowLayout.LEFT, 6, 3));
        toolbar.setMinimumSize(new Dimension(0, 0));
        watchSearchField.setPreferredSize(new Dimension(150, 30));
        watchSearchField.setMaximumSize(new Dimension(150, 30));
        holdingSearchField.setPreferredSize(new Dimension(150, 30));
        holdingSearchField.setMaximumSize(new Dimension(150, 30));
        return toolbar;
    }

    private JScrollPane toolbarScrollPane(JToolBar toolbar) {
        JScrollPane scrollPane = new JScrollPane(
                toolbar,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED
        );
        scrollPane.setBorder(null);
        scrollPane.setMinimumSize(new Dimension(0, toolbar.getPreferredSize().height));
        return scrollPane;
    }

    private JScrollPane tableScrollPane(JTable table) {
        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setMinimumSize(new Dimension(0, 0));
        return scrollPane;
    }

    private JButton button(String text, Runnable action) {
        JButton button = new JButton(text);
        button.addActionListener(event -> action.run());
        return button;
    }

    private JButton iconButton(String text, String tooltip, Runnable action) {
        JButton button = button(text, action);
        button.setToolTipText(tooltip);
        button.setPreferredSize(new Dimension(38, 30));
        button.setMaximumSize(new Dimension(38, 30));
        return button;
    }

    private JButton compactTextButton(String text, String tooltip, Runnable action) {
        JButton button = button(text, action);
        button.setToolTipText(tooltip);
        button.setPreferredSize(new Dimension(68, 30));
        button.setMaximumSize(new Dimension(68, 30));
        return button;
    }

    private void prepareTable(JTable table, boolean twoLineRows) {
        table.setFillsViewportHeight(true);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setAutoCreateRowSorter(true);
        table.setComponentPopupMenu(table == holdingTable ? holdingTableMenu() : watchTableMenu());
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent event) {
                selectPopupRow(table, event);
            }

            @Override
            public void mouseReleased(MouseEvent event) {
                selectPopupRow(table, event);
            }
        });
        table.setRowHeight(twoLineRows ? 48 : watchRowHeight());
        table.setMinimumSize(new Dimension(0, 0));
        ((DefaultTableCellRenderer) table.getTableHeader().getDefaultRenderer()).setHorizontalAlignment(JLabel.CENTER);
        ColorRenderer renderer = new ColorRenderer();
        for (int column = 0; column < table.getColumnCount(); column++) {
            table.getColumnModel().getColumn(column).setCellRenderer(renderer);
        }
        applySavedColumnWidths(table);
        table.getColumnModel().addColumnModelListener(new TableColumnModelListener() {
            @Override
            public void columnAdded(TableColumnModelEvent event) {
            }

            @Override
            public void columnRemoved(TableColumnModelEvent event) {
            }

            @Override
            public void columnMoved(TableColumnModelEvent event) {
            }

            @Override
            public void columnMarginChanged(ChangeEvent event) {
                saveColumnWidths(table);
            }

            @Override
            public void columnSelectionChanged(javax.swing.event.ListSelectionEvent event) {
            }
        });
    }

    private void selectPopupRow(JTable table, MouseEvent event) {
        if (!event.isPopupTrigger()) {
            return;
        }
        int row = table.rowAtPoint(event.getPoint());
        if (row >= 0) {
            table.setRowSelectionInterval(row, row);
        }
    }

    private int watchRowHeight() {
        return state.compactWatchMode ? 24 : 36;
    }

    private void applySavedColumnWidths(JTable table) {
        List<Integer> widths = table == watchTable ? state.watchColumnWidths : state.holdingColumnWidths;
        if (widths.size() != table.getColumnCount()) {
            return;
        }
        for (int index = 0; index < widths.size(); index++) {
            int width = widths.get(index);
            if (width > 20) {
                table.getColumnModel().getColumn(index).setPreferredWidth(width);
            }
        }
    }

    private void saveColumnWidths(JTable table) {
        List<Integer> widths = new ArrayList<>();
        for (int index = 0; index < table.getColumnCount(); index++) {
            widths.add(table.getColumnModel().getColumn(index).getWidth());
        }
        if (table == watchTable) {
            state.watchColumnWidths = widths;
        } else {
            state.holdingColumnWidths = widths;
        }
    }

    private JPopupMenu watchTableMenu() {
        JPopupMenu menu = new JPopupMenu();
        menu.add(menuItem("刷新", this::refreshQuotes));
        menu.add(menuItem("复制代码", this::copySelectedWatchCode));
        menu.add(menuItem("删除股票", this::removeSelectedWatchStock));
        return menu;
    }

    private JPopupMenu holdingTableMenu() {
        JPopupMenu menu = new JPopupMenu();
        menu.add(menuItem("买入/卖出", this::recordTrade));
        menu.add(menuItem("修改持仓", this::editSelectedHolding));
        menu.add(menuItem("复制代码", this::copySelectedHoldingCode));
        menu.add(menuItem("删除持仓", this::removeSelectedHolding));
        menu.addSeparator();
        menu.add(menuItem("刷新", this::refreshQuotes));
        return menu;
    }

    private void refreshGroupBoxes() {
        state.ensureDefaults();
        watchGroupBox.setModel(new DefaultComboBoxModel<>(state.watchGroups.toArray(new StockGroup[0])));
        holdingGroupBox.setModel(new DefaultComboBoxModel<>(state.holdingGroups.toArray(new HoldingGroup[0])));
        StockGroup selectedWatch = currentWatchGroup();
        HoldingGroup selectedHolding = currentHoldingGroup();
        watchGroupModel.clear();
        state.watchGroups.forEach(watchGroupModel::addElement);
        holdingGroupModel.clear();
        state.holdingGroups.forEach(holdingGroupModel::addElement);
        watchGroupList.setSelectedValue(selectedWatch, true);
        holdingGroupList.setSelectedValue(selectedHolding, true);
        if (watchGroupList.getSelectedIndex() < 0 && !state.watchGroups.isEmpty()) {
            watchGroupList.setSelectedIndex(0);
        }
        if (holdingGroupList.getSelectedIndex() < 0 && !state.holdingGroups.isEmpty()) {
            holdingGroupList.setSelectedIndex(0);
        }
    }

    private StockGroup currentWatchGroup() {
        StockGroup selected = watchGroupList.getSelectedValue();
        if (selected == null) {
            selected = (StockGroup) watchGroupBox.getSelectedItem();
        }
        return selected == null ? state.watchGroups.get(0) : selected;
    }

    private HoldingGroup currentHoldingGroup() {
        HoldingGroup selected = holdingGroupList.getSelectedValue();
        if (selected == null) {
            selected = (HoldingGroup) holdingGroupBox.getSelectedItem();
        }
        return selected == null ? state.holdingGroups.get(0) : selected;
    }

    private void addWatchGroup() {
        String name = Messages.showInputDialog(project, "自选分组名称", "新建分组", null);
        if (name == null || name.isBlank()) {
            return;
        }
        if (watchGroupNameExists(name.trim(), null)) {
            Messages.showErrorDialog(project, "自选分组名称已存在。", "新建失败");
            return;
        }
        state.watchGroups.add(new StockGroup(name.trim()));
        refreshGroupBoxes();
        watchGroupBox.setSelectedIndex(state.watchGroups.size() - 1);
    }

    private void renameWatchGroup() {
        StockGroup group = currentWatchGroup();
        String name = Messages.showInputDialog(project, "自选分组名称", "重命名分组", null, group.name, null);
        if (name == null || name.isBlank() || name.trim().equals(group.name)) {
            return;
        }
        if (watchGroupNameExists(name.trim(), group)) {
            Messages.showErrorDialog(project, "自选分组名称已存在。", "重命名失败");
            return;
        }
        group.name = name.trim();
        refreshGroupBoxes();
        watchGroupBox.setSelectedItem(group);
        renderTables();
    }

    private void addHoldingGroup() {
        String name = Messages.showInputDialog(project, "持仓分组名称", "新建分组", null);
        if (name == null || name.isBlank()) {
            return;
        }
        if (holdingGroupNameExists(name.trim(), null)) {
            Messages.showErrorDialog(project, "持仓分组名称已存在。", "新建失败");
            return;
        }
        state.holdingGroups.add(new HoldingGroup(name.trim()));
        refreshGroupBoxes();
        holdingGroupBox.setSelectedIndex(state.holdingGroups.size() - 1);
    }

    private void renameHoldingGroup() {
        HoldingGroup group = currentHoldingGroup();
        String name = Messages.showInputDialog(project, "持仓分组名称", "重命名分组", null, group.name, null);
        if (name == null || name.isBlank() || name.trim().equals(group.name)) {
            return;
        }
        if (holdingGroupNameExists(name.trim(), group)) {
            Messages.showErrorDialog(project, "持仓分组名称已存在。", "重命名失败");
            return;
        }
        group.name = name.trim();
        refreshGroupBoxes();
        holdingGroupBox.setSelectedItem(group);
        renderTables();
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

    private void searchAddWatch(String keyword) {
        SearchDialog dialog = new SearchDialog(project, quoteService, keyword);
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

    private void searchAddWatch() {
        searchAddWatch("");
    }

    private void renameSelectedHolding() {
        HoldingItem holding = selectedHolding();
        if (holding == null) {
            Messages.showInfoMessage(project, "请先选择一条持仓。", "无法修改");
            return;
        }
        String name = Messages.showInputDialog(project, "持仓名称", "修改持仓名称", null, holding.name, null);
        if (name == null || name.isBlank() || name.trim().equals(holding.name)) {
            return;
        }
        holding.name = name.trim();
        renderTables();
    }

    private void editSelectedHolding() {
        HoldingItem holding = selectedHolding();
        if (holding == null) {
            Messages.showInfoMessage(project, "请先选择一条持仓。", "无法修改");
            return;
        }
        HoldingDialog dialog = new HoldingDialog(project, quoteService, holding);
        if (!dialog.showAndGet()) {
            return;
        }
        HoldingItem edited = dialog.holding();
        holding.market = edited.market;
        holding.code = edited.code;
        holding.name = edited.name;
        holding.shares = edited.shares;
        holding.availableShares = edited.availableShares;
        holding.costPrice = edited.costPrice;
        holding.costAmount = edited.shares * edited.costPrice;
        renderTables();
        refreshQuotes();
    }

    private void addHolding() {
        HoldingDialog dialog = new HoldingDialog(project, quoteService);
        if (!dialog.showAndGet()) {
            return;
        }
        addHoldingItem(dialog.holding());
    }

    private void searchAddHolding(String keyword) {
        SearchDialog searchDialog = new SearchDialog(project, quoteService, keyword);
        if (!searchDialog.showAndGet() || searchDialog.selectedStock() == null) {
            return;
        }
        HoldingDialog holdingDialog = new HoldingDialog(project, quoteService, searchDialog.selectedStock());
        if (!holdingDialog.showAndGet()) {
            return;
        }
        addHoldingItem(holdingDialog.holding());
    }

    private void searchAddHolding() {
        searchAddHolding("");
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
            holding.sell(dialog.quantity(), dialog.price(), dialog.fee());
            Messages.showInfoMessage(project, "卖出交易已记录。", "交易已记录");
        }
        renderTables();
        refreshQuotes();
    }

    private void removeSelectedWatchStock() {
        StockItem stock = selectedWatchStock();
        if (stock == null) {
            return;
        }
        StockGroup group = currentWatchGroup();
        group.stocks.remove(stock);
        renderTables();
    }

    private StockItem selectedWatchStock() {
        int row = watchTable.getSelectedRow();
        if (row < 0) {
            return null;
        }
        int modelRow = watchTable.convertRowIndexToModel(row);
        StockGroup group = currentWatchGroup();
        if (modelRow < 0 || modelRow >= group.stocks.size()) {
            return null;
        }
        return group.stocks.get(modelRow);
    }

    private void removeSelectedHolding() {
        HoldingItem holding = selectedHolding();
        if (holding == null) {
            return;
        }
        HoldingGroup group = currentHoldingGroup();
        group.holdings.remove(holding);
        renderTables();
    }

    private HoldingItem selectedHolding() {
        int row = holdingTable.getSelectedRow();
        if (row < 0) {
            return null;
        }
        int modelRow = holdingTable.convertRowIndexToModel(row);
        HoldingGroup group = currentHoldingGroup();
        if (modelRow < 0 || modelRow >= group.holdings.size()) {
            return null;
        }
        return group.holdings.get(modelRow);
    }

    private boolean watchGroupNameExists(String name, StockGroup ignoredGroup) {
        return state.watchGroups.stream()
                .anyMatch(group -> group != ignoredGroup && name.equals(group.name));
    }

    private boolean holdingGroupNameExists(String name, HoldingGroup ignoredGroup) {
        return state.holdingGroups.stream()
                .anyMatch(group -> group != ignoredGroup && name.equals(group.name));
    }

    private void copySelectedWatchCode() {
        StockItem stock = selectedWatchStock();
        if (stock != null) {
            getToolkit().getSystemClipboard().setContents(new StringSelection(stock.displayCode()), null);
            statusLabel.setText("已复制 " + stock.displayCode());
        }
    }

    private void copySelectedHoldingCode() {
        HoldingItem holding = selectedHolding();
        if (holding != null) {
            getToolkit().getSystemClipboard().setContents(new StringSelection(holding.displayCode()), null);
            statusLabel.setText("已复制 " + holding.displayCode());
        }
    }

    private void addIndexStock() {
        String code = Messages.showInputDialog(project, "指数代码，例如 sh000001 / sz399006", "添加指数", null);
        if (code == null || code.isBlank()) {
            return;
        }
        quoteService.normalizeStock(code, "").ifPresentOrElse(index -> {
            if (state.indexStocks.stream().noneMatch(existing -> existing.key().equals(index.key()))) {
                state.indexStocks.add(index);
            }
            refreshQuotes();
        }, () -> Messages.showErrorDialog(project, "无法识别指数代码。", "添加失败"));
    }

    private void resetIndexStocks() {
        state.indexStocks.clear();
        state.indexStocks.add(new StockItem("SH", "000001", "上证指数"));
        state.indexStocks.add(new StockItem("SZ", "399001", "深证成指"));
        state.indexStocks.add(new StockItem("SZ", "399006", "创业板指"));
        refreshQuotes();
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
        List<StockItem> stocks = uniqueStocks(allStocks());
        String refreshTime = LocalTime.now().format(TIME);
        List<CompletableFuture<Void>> tasks = stocks.stream()
                .map(stock -> CompletableFuture.runAsync(() -> {
                Quote quote = quoteService.quote(stock);
                quotes.put(stock.key(), quote);
                quoteTimes.put(stock.key(), refreshTime);
                if (quote.stock.name != null && !quote.stock.name.isBlank()) {
                    stock.name = quote.stock.name;
                }
                }))
                .toList();
        CompletableFuture.allOf(tasks.toArray(new CompletableFuture[0])).whenComplete((ignored, throwable) -> SwingUtilities.invokeLater(() -> {
            refreshing.set(false);
            renderTables();
            statusLabel.setText(throwable == null ? "已刷新 " + refreshTime : "刷新失败，请稍后再试");
        }));
    }

    private List<StockItem> allStocks() {
        List<StockItem> stocks = new ArrayList<>();
        stocks.addAll(state.indexStocks);
        state.watchGroups.forEach(group -> stocks.addAll(group.stocks));
        state.holdingGroups.forEach(group -> stocks.addAll(group.holdings));
        return stocks;
    }

    private List<StockItem> uniqueStocks(List<StockItem> stocks) {
        List<StockItem> result = new ArrayList<>();
        Set<String> keys = new LinkedHashSet<>();
        for (StockItem stock : stocks) {
            if (stock != null && keys.add(stock.key())) {
                result.add(stock);
            }
        }
        return result;
    }

    private void renderTables() {
        applyTextColor();
        renderWatchTable();
        renderHoldingTable();
        renderIndexBar();
    }

    private void renderWatchTable() {
        watchModel.setRowCount(0);
        for (StockItem stock : currentWatchGroup().stocks) {
            Quote quote = quotes.getOrDefault(stock.key(), Quote.empty(stock));
            watchModel.addRow(new Object[]{
                    stock.displayCode(),
                    stock.name,
                    new DisplayCell(formatPriceValue(quote.price), "", quote.changePercent),
                    new DisplayCell(formatPercent(quote.changePercent), "", quote.changePercent),
                    formatAmountValue(quote.amount),
                    quoteTimes.getOrDefault(stock.key(), "--")
            });
        }
        watchTable.setRowHeight(watchRowHeight());
    }

    private void renderIndexBar() {
        StringBuilder builder = new StringBuilder("<html>");
        for (StockItem index : state.indexStocks) {
            Quote quote = quotes.getOrDefault(index.key(), Quote.empty(index));
            String name = index.name == null || index.name.isBlank() ? index.displayCode() : index.name;
            String color = colorHex(quote.changePercent);
            builder.append("<span style='color:")
                    .append(color)
                    .append("'><b>")
                    .append(escapeHtml(name))
                    .append("</b> ")
                    .append(escapeHtml(formatPrice(quote.price)))
                    .append(" ")
                    .append(escapeHtml(formatPercent(quote.changePercent)))
                    .append("</span>&nbsp;&nbsp;&nbsp;");
        }
        builder.append("</html>");
        String text = builder.toString();
        watchIndexLabel.setText(text);
        holdingIndexLabel.setText(text);
    }

    private void renderHoldingTable() {
        holdingModel.setRowCount(0);
        double totalMarketValue = 0.0;
        double totalTodayProfit = 0.0;
        double totalHoldingProfit = 0.0;
        boolean hasTodayProfit = false;
        for (HoldingItem holding : currentHoldingGroup().holdings) {
            normalizeHolding(holding);
            Quote quote = quotes.getOrDefault(holding.key(), Quote.empty(holding));
            double price = Double.isFinite(quote.price) ? quote.price : holding.costPrice;
            double marketValue = holding.shares * price;
            double holdingProfit = holding.holdingProfit(price);
            double todayProfit = todayProfit(holding, quote, price);
            totalMarketValue += marketValue;
            totalHoldingProfit += holdingProfit;
            if (Double.isFinite(todayProfit)) {
                totalTodayProfit += todayProfit;
                hasTodayProfit = true;
            }
            holdingModel.addRow(new Object[]{
                    new DisplayCell(holding.name, amountValue(marketValue), 0.0),
                    new DisplayCell(amountValue(holdingProfit), formatPercent(holdingProfitPercent(holding, holdingProfit)), holdingProfit),
                    new DisplayCell(String.valueOf(holding.shares), String.valueOf(holding.availableShares), 0.0),
                    new DisplayCell(holding.shares == 0 ? "--" : formatPriceValue(holding.costPrice), formatPriceValue(price), price - holding.costPrice),
                    new DisplayCell(formatAmountValue(todayProfit), formatPercent(quote.changePercent), todayProfit)
            });
        }
        updateHoldingSummary(totalMarketValue, hasTodayProfit ? totalTodayProfit : Double.NaN, totalHoldingProfit);
    }

    private void normalizeHolding(HoldingItem holding) {
        holding.ensureCostAmount();
        if (holding.shares == 0) {
            holding.availableShares = 0;
        }
    }

    private double holdingProfitPercent(HoldingItem holding, double holdingProfit) {
        if (holding.shares == 0 || !Double.isFinite(holding.costAmount) || holding.costAmount == 0.0) {
            return Double.NaN;
        }
        return holdingProfit / Math.abs(holding.costAmount) * 100.0;
    }

    private double todayProfit(HoldingItem holding, Quote quote, double currentPrice) {
        double zeroAxisPrice = zeroAxisPrice(quote);
        if (!Double.isFinite(zeroAxisPrice) || !Double.isFinite(currentPrice)) {
            return Double.NaN;
        }

        List<TradeRecord> todayTrades = todayTrades(holding);
        if (todayTrades.isEmpty()) {
            return holding.shares * (currentPrice - zeroAxisPrice);
        }

        int openingShares = openingShares(holding, todayTrades);
        List<DayLot> lots = new ArrayList<>();
        if (openingShares > 0) {
            lots.add(new DayLot(openingShares, zeroAxisPrice));
        }

        double profit = 0.0;
        for (TradeRecord record : todayTrades) {
            if ("BUY".equals(record.side)) {
                lots.add(new DayLot(record.quantity, record.price));
            } else if ("SELL".equals(record.side)) {
                profit += consumeLots(lots, record.quantity, record.price);
            }
        }
        for (DayLot lot : lots) {
            profit += lot.quantity * (currentPrice - lot.basisPrice);
        }
        return profit;
    }

    private double zeroAxisPrice(Quote quote) {
        if (Double.isFinite(quote.previousClose) && quote.previousClose > 0) {
            return quote.previousClose;
        }
        return quote.openPrice;
    }

    private List<TradeRecord> todayTrades(HoldingItem holding) {
        if (holding.trades == null || holding.trades.isEmpty()) {
            return List.of();
        }
        String today = LocalDate.now().toString();
        return holding.trades.stream()
                .filter(record -> today.equals(record.date))
                .toList();
    }

    private int openingShares(HoldingItem holding, List<TradeRecord> todayTrades) {
        int shares = holding.shares;
        for (TradeRecord record : todayTrades) {
            if ("BUY".equals(record.side)) {
                shares -= record.quantity;
            } else if ("SELL".equals(record.side)) {
                shares += record.quantity;
            }
        }
        return Math.max(0, shares);
    }

    private double consumeLots(List<DayLot> lots, int quantity, double sellPrice) {
        int remaining = quantity;
        double profit = 0.0;
        while (remaining > 0 && !lots.isEmpty()) {
            DayLot lot = lots.get(0);
            int matched = Math.min(remaining, lot.quantity);
            profit += matched * (sellPrice - lot.basisPrice);
            lot.quantity -= matched;
            remaining -= matched;
            if (lot.quantity == 0) {
                lots.remove(0);
            }
        }
        return profit;
    }

    private void updateHoldingSummary(double marketValue, double todayProfit, double holdingProfit) {
        totalMarketValueLabel.setText(formatAmountValue(marketValue));
        holdingTodaySummaryLabel.setText(formatAmountValue(todayProfit));
        holdingTotalSummaryLabel.setText(formatAmountValue(holdingProfit));
        holdingCountLabel.setText(String.valueOf(currentHoldingGroup().holdings.size()));

        Color textColor = parseColor(state.textColorHex);
        totalMarketValueTitleLabel.setForeground(textColor);
        totalMarketValueLabel.setForeground(textColor);
        holdingTodaySummaryTitleLabel.setForeground(signalColor(todayProfit));
        holdingTodaySummaryLabel.setForeground(signalColor(todayProfit));
        holdingTotalSummaryTitleLabel.setForeground(signalColor(holdingProfit));
        holdingTotalSummaryLabel.setForeground(signalColor(holdingProfit));
        holdingCountTitleLabel.setForeground(textColor);
        holdingCountLabel.setForeground(textColor);
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

    private String colorHex(double value) {
        if (state.privacyMode || !Double.isFinite(value) || value == 0.0) {
            return state.textColorHex;
        }
        return value > 0 ? state.profitColorHex : state.lossColorHex;
    }

    private String formatPrice(double value) {
        return Double.isFinite(value) ? PRICE.format(value) : "--";
    }

    private String formatPriceValue(double value) {
        return hideAmounts() ? "***" : formatPrice(value);
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

    private String formatAmountValue(double value) {
        return hideAmounts() ? "***" : formatAmount(value);
    }

    private String amountValue(double value) {
        return hideAmounts() ? "***" : MONEY.format(value);
    }

    private boolean hideAmounts() {
        return state.privacyMode && state.hideAmountsInPrivacyMode;
    }

    private static class PromptTextField extends JTextField {
        private final String prompt;

        private PromptTextField(String prompt) {
            super(14);
            this.prompt = prompt;
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            if (!getText().isEmpty() || isFocusOwner()) {
                return;
            }
            Graphics2D graphics2D = (Graphics2D) graphics.create();
            graphics2D.setColor(new Color(135, 140, 145));
            Insets insets = getInsets();
            Font font = getFont();
            graphics2D.setFont(font);
            int y = (getHeight() + graphics2D.getFontMetrics().getAscent() - graphics2D.getFontMetrics().getDescent()) / 2;
            graphics2D.drawString(prompt, insets.left + 4, y);
            graphics2D.dispose();
        }
    }

    private static class DayLot {
        private int quantity;
        private final double basisPrice;

        private DayLot(int quantity, double basisPrice) {
            this.quantity = quantity;
            this.basisPrice = basisPrice;
        }
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
