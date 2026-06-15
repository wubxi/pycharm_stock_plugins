package com.github.wwt.stockplugin.service;

import com.github.wwt.stockplugin.model.StockState;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import org.jetbrains.annotations.NotNull;

@State(name = "StockMonitorSettings", storages = @Storage("stock-monitor.xml"))
public final class StockStateService implements PersistentStateComponent<StockState> {
    private StockState state = new StockState();

    public static StockStateService getInstance() {
        return ApplicationManager.getApplication().getService(StockStateService.class);
    }

    @Override
    public @NotNull StockState getState() {
        state.ensureDefaults();
        return state;
    }

    @Override
    public void loadState(@NotNull StockState state) {
        this.state = state;
        this.state.ensureDefaults();
    }
}
