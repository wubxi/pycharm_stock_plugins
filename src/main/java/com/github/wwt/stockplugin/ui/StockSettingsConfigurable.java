package com.github.wwt.stockplugin.ui;

import com.github.wwt.stockplugin.model.StockState;
import com.github.wwt.stockplugin.service.StockStateService;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.BorderFactory;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import java.awt.Color;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

public class StockSettingsConfigurable implements Configurable {
    private JTextField colorField;
    private JTextField profitColorField;
    private JTextField lossColorField;
    private JCheckBox privacyModeCheckBox;
    private JSpinner refreshSpinner;

    @Override
    public @Nls String getDisplayName() {
        return "Stock Monitor";
    }

    @Override
    public @Nullable JComponent createComponent() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.insets = new Insets(6, 6, 6, 6);
        constraints.anchor = GridBagConstraints.WEST;

        colorField = new JTextField(12);
        profitColorField = new JTextField(12);
        lossColorField = new JTextField(12);
        privacyModeCheckBox = new JCheckBox("隐私模式：盈亏不使用红绿颜色");
        refreshSpinner = new JSpinner(new SpinnerNumberModel(15, 5, 300, 1));

        constraints.gridx = 0;
        constraints.gridy = 0;
        panel.add(new JLabel("文本颜色"), constraints);
        constraints.gridx = 1;
        panel.add(colorField, constraints);

        constraints.gridx = 0;
        constraints.gridy = 1;
        panel.add(new JLabel("盈利颜色"), constraints);
        constraints.gridx = 1;
        panel.add(profitColorField, constraints);

        constraints.gridx = 0;
        constraints.gridy = 2;
        panel.add(new JLabel("亏损颜色"), constraints);
        constraints.gridx = 1;
        panel.add(lossColorField, constraints);

        constraints.gridx = 0;
        constraints.gridy = 3;
        panel.add(new JLabel("颜色模式"), constraints);
        constraints.gridx = 1;
        panel.add(privacyModeCheckBox, constraints);

        constraints.gridx = 0;
        constraints.gridy = 4;
        panel.add(new JLabel("刷新间隔(秒)"), constraints);
        constraints.gridx = 1;
        panel.add(refreshSpinner, constraints);

        reset();
        return panel;
    }

    @Override
    public boolean isModified() {
        StockState state = StockStateService.getInstance().getState();
        return !state.textColorHex.equals(colorField.getText().trim())
                || !state.profitColorHex.equals(profitColorField.getText().trim())
                || !state.lossColorHex.equals(lossColorField.getText().trim())
                || state.privacyMode != privacyModeCheckBox.isSelected()
                || state.refreshSeconds != ((Number) refreshSpinner.getValue()).intValue();
    }

    @Override
    public void apply() throws ConfigurationException {
        String color = validateColor(colorField.getText().trim(), "文本颜色");
        String profitColor = validateColor(profitColorField.getText().trim(), "盈利颜色");
        String lossColor = validateColor(lossColorField.getText().trim(), "亏损颜色");
        StockState state = StockStateService.getInstance().getState();
        state.textColorHex = color;
        state.profitColorHex = profitColor;
        state.lossColorHex = lossColor;
        state.privacyMode = privacyModeCheckBox.isSelected();
        state.refreshSeconds = ((Number) refreshSpinner.getValue()).intValue();
    }

    @Override
    public void reset() {
        StockState state = StockStateService.getInstance().getState();
        if (colorField != null) {
            colorField.setText(state.textColorHex);
        }
        if (profitColorField != null) {
            profitColorField.setText(state.profitColorHex);
        }
        if (lossColorField != null) {
            lossColorField.setText(state.lossColorHex);
        }
        if (privacyModeCheckBox != null) {
            privacyModeCheckBox.setSelected(state.privacyMode);
        }
        if (refreshSpinner != null) {
            refreshSpinner.setValue(state.refreshSeconds);
        }
    }

    @Override
    public void disposeUIResources() {
        colorField = null;
        profitColorField = null;
        lossColorField = null;
        privacyModeCheckBox = null;
        refreshSpinner = null;
    }

    private String validateColor(String color, String label) throws ConfigurationException {
        try {
            Color.decode(color);
            return color;
        } catch (NumberFormatException exception) {
            throw new ConfigurationException(label + "必须是十六进制格式，例如 #DDE6ED");
        }
    }
}
