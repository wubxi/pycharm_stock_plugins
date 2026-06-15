package com.github.wwt.stockplugin.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import java.awt.BorderLayout;
import java.awt.Dimension;

public class ImportDialog extends DialogWrapper {
    private final JTextArea textArea = new JTextArea();

    public ImportDialog(@Nullable Project project) {
        super(project);
        setTitle("导入股票");
        init();
    }

    public String importText() {
        return textArea.getText();
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        textArea.setText("自选|科技|sh600519|贵州茅台\n持仓|长线|00700|腾讯控股|100|100|320.50");
        panel.add(new JScrollPane(textArea), BorderLayout.CENTER);
        panel.setPreferredSize(new Dimension(560, 320));
        return panel;
    }
}
