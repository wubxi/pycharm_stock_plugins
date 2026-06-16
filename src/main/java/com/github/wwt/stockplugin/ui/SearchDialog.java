package com.github.wwt.stockplugin.ui;

import com.github.wwt.stockplugin.model.StockItem;
import com.github.wwt.stockplugin.service.StockQuoteService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import org.jetbrains.annotations.Nullable;

import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class SearchDialog extends DialogWrapper {
    private final StockQuoteService quoteService;
    private final JTextField keywordField = new JTextField();
    private final DefaultListModel<StockItem> resultModel = new DefaultListModel<>();
    private final JList<StockItem> resultList = new JList<>(resultModel);
    private final JLabel statusLabel = new JLabel("输入股票代码或名称后搜索");

    public SearchDialog(@Nullable Project project, StockQuoteService quoteService) {
        super(project);
        this.quoteService = quoteService;
        setTitle("搜索股票");
        init();
    }

    public SearchDialog(@Nullable Project project, StockQuoteService quoteService, String keyword) {
        this(project, quoteService);
        if (keyword != null && !keyword.isBlank()) {
            keywordField.setText(keyword.trim());
            runSearch();
        }
    }

    public StockItem selectedStock() {
        return resultList.getSelectedValue();
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        JPanel top = new JPanel(new BorderLayout(8, 8));
        JButton searchButton = new JButton("搜索");
        top.add(keywordField, BorderLayout.CENTER);
        top.add(searchButton, BorderLayout.EAST);
        panel.add(top, BorderLayout.NORTH);

        resultList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        resultList.setVisibleRowCount(8);
        panel.add(new JScrollPane(resultList), BorderLayout.CENTER);
        panel.add(statusLabel, BorderLayout.SOUTH);
        panel.setPreferredSize(new Dimension(420, 280));

        searchButton.addActionListener(event -> runSearch());
        keywordField.addActionListener(event -> runSearch());
        resultList.addListSelectionListener(event -> setOKActionEnabled(selectedStock() != null));
        setOKActionEnabled(false);
        return panel;
    }

    private void runSearch() {
        String keyword = keywordField.getText().trim();
        if (keyword.isEmpty()) {
            return;
        }
        statusLabel.setText("搜索中...");
        resultModel.clear();
        CompletableFuture.supplyAsync(() -> {
            try {
                return quoteService.search(keyword);
            } catch (Exception exception) {
                return List.<StockItem>of();
            }
        }).thenAccept(items -> javax.swing.SwingUtilities.invokeLater(() -> {
            items.forEach(resultModel::addElement);
            statusLabel.setText(items.isEmpty() ? "未找到匹配股票" : "选择一条股票后确认");
            if (!items.isEmpty()) {
                resultList.setSelectedIndex(0);
            }
        }));
    }
}
