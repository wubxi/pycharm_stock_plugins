package com.github.wwt.stockplugin.ui;

import com.github.wwt.stockplugin.model.HoldingGroup;
import com.github.wwt.stockplugin.model.HoldingItem;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

public class TradeDialog extends DialogWrapper {
    private final JComboBox<HoldingItem> holdingBox;
    private final JComboBox<String> sideBox = new JComboBox<>(new String[]{"买入", "卖出"});
    private final JSpinner quantitySpinner = new JSpinner(new SpinnerNumberModel(100, 1, 100000000, 1));
    private final JSpinner priceSpinner = new JSpinner(new SpinnerNumberModel(0.0, 0.0, 1000000.0, 0.01));
    private final JSpinner feeSpinner = new JSpinner(new SpinnerNumberModel(0.0, 0.0, 1000000.0, 0.01));

    public TradeDialog(@Nullable Project project, HoldingGroup group) {
        super(project);
        holdingBox = new JComboBox<>(group.holdings.toArray(new HoldingItem[0]));
        setTitle("当日买入/卖出");
        init();
    }

    public HoldingItem holding() {
        return (HoldingItem) holdingBox.getSelectedItem();
    }

    public boolean isBuy() {
        return sideBox.getSelectedIndex() == 0;
    }

    public int quantity() {
        return ((Number) quantitySpinner.getValue()).intValue();
    }

    public double price() {
        return ((Number) priceSpinner.getValue()).doubleValue();
    }

    public double fee() {
        return ((Number) feeSpinner.getValue()).doubleValue();
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(6, 6, 6, 6);
        c.anchor = GridBagConstraints.WEST;
        addRow(panel, c, 0, "股票", holdingBox);
        addRow(panel, c, 1, "方向", sideBox);
        addRow(panel, c, 2, "数量", quantitySpinner);
        addRow(panel, c, 3, "价格", priceSpinner);
        addRow(panel, c, 4, "手续费", feeSpinner);
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
