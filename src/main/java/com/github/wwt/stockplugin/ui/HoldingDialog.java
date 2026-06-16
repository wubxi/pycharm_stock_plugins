package com.github.wwt.stockplugin.ui;

import com.github.wwt.stockplugin.model.HoldingItem;
import com.github.wwt.stockplugin.model.StockItem;
import com.github.wwt.stockplugin.service.StockQuoteService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

public class HoldingDialog extends DialogWrapper {
    private final StockQuoteService quoteService;
    private final JTextField codeField = new JTextField(14);
    private final JTextField nameField = new JTextField(14);
    private final JSpinner sharesSpinner = new JSpinner(new SpinnerNumberModel(100, 0, 100000000, 1));
    private final JSpinner availableSpinner = new JSpinner(new SpinnerNumberModel(100, 0, 100000000, 1));
    private final JSpinner costSpinner = new JSpinner(new SpinnerNumberModel(0.0, 0.0, 1000000.0, 0.01));

    public HoldingDialog(@Nullable Project project, StockQuoteService quoteService) {
        super(project);
        this.quoteService = quoteService;
        setTitle("新增持仓");
        init();
    }

    public HoldingDialog(@Nullable Project project, StockQuoteService quoteService, StockItem stock) {
        this(project, quoteService);
        if (stock != null) {
            codeField.setText(stock.displayCode());
            nameField.setText(stock.name);
        }
    }

    public HoldingDialog(@Nullable Project project, StockQuoteService quoteService, HoldingItem holding) {
        this(project, quoteService);
        setTitle("修改持仓");
        if (holding != null) {
            codeField.setText(holding.displayCode());
            nameField.setText(holding.name);
            sharesSpinner.setValue(holding.shares);
            availableSpinner.setValue(holding.availableShares);
            costSpinner.setValue(holding.costPrice);
        }
    }

    public HoldingItem holding() {
        StockItem stock = quoteService.normalizeStock(codeField.getText(), nameField.getText())
                .orElse(new StockItem("", codeField.getText().trim(), nameField.getText().trim()));
        return new HoldingItem(
                stock.market,
                stock.code,
                stock.name,
                ((Number) sharesSpinner.getValue()).intValue(),
                ((Number) availableSpinner.getValue()).intValue(),
                ((Number) costSpinner.getValue()).doubleValue()
        );
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(6, 6, 6, 6);
        c.anchor = GridBagConstraints.WEST;
        addRow(panel, c, 0, "代码", codeField);
        addRow(panel, c, 1, "名称", nameField);
        addRow(panel, c, 2, "持仓", sharesSpinner);
        addRow(panel, c, 3, "可用", availableSpinner);
        addRow(panel, c, 4, "成本价", costSpinner);
        return panel;
    }

    private void addRow(JPanel panel, GridBagConstraints c, int y, String label, JComponent component) {
        c.gridx = 0;
        c.gridy = y;
        panel.add(new JLabel(label), c);
        c.gridx = 1;
        panel.add(component, c);
    }
}
