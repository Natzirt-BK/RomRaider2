/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.logger.api;

import static com.romraider.util.ParamChecker.checkNotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.LinkedHashSet;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Toolkit-neutral catalog and selection commands for the active Logger.
 */
public final class LoggerChannelService {
    private final LoggerChannelSelectionCommand selectionCommand;
    private final BiConsumer<String, String> unitCommand;
    private final Consumer<RuntimeException> failureHandler;
    private final CopyOnWriteArrayList<Consumer<List<LoggerChannel>>> listeners =
            new CopyOnWriteArrayList<Consumer<List<LoggerChannel>>>();
    private Map<String, LoggerChannel> channels =
            Collections.emptyMap();

    public LoggerChannelService(BiConsumer<String, Boolean> selectionCommand,
            Consumer<RuntimeException> failureHandler) {
        this((parameterIds, selected) -> {
            for (String parameterId : parameterIds) {
                selectionCommand.accept(parameterId, selected);
            }
        }, (parameterId, optionId) -> { }, failureHandler);
        checkNotNull(selectionCommand);
    }

    public LoggerChannelService(
            LoggerChannelSelectionCommand selectionCommand,
            BiConsumer<String, String> unitCommand,
            Consumer<RuntimeException> failureHandler) {
        checkNotNull(selectionCommand, unitCommand, failureHandler);
        this.selectionCommand = selectionCommand;
        this.unitCommand = unitCommand;
        this.failureHandler = failureHandler;
    }

    public synchronized List<LoggerChannel> getChannels() {
        return snapshot();
    }

    public void addListener(Consumer<List<LoggerChannel>> listener) {
        if (listener == null || listeners.contains(listener)) return;
        listeners.add(listener);
        notifyListener(listener, getChannels());
    }

    public void removeListener(Consumer<List<LoggerChannel>> listener) {
        listeners.remove(listener);
    }

    public void replaceChannels(Collection<LoggerChannel> nextChannels) {
        checkNotNull(nextChannels);
        LinkedHashMap<String, LoggerChannel> next =
                new LinkedHashMap<String, LoggerChannel>();
        for (LoggerChannel channel : nextChannels) {
            if (channel != null) next.put(channel.getParameterId(), channel);
        }
        List<LoggerChannel> snapshot;
        synchronized (this) {
            channels = Collections.unmodifiableMap(next);
            snapshot = snapshot();
        }
        notifyListeners(snapshot);
    }

    public void setSelected(String parameterId, boolean selected) {
        setSelected(Collections.singletonList(parameterId), selected);
    }

    public void setSelected(Collection<String> parameterIds,
            boolean selected) {
        if (parameterIds == null || parameterIds.isEmpty()) return;
        Set<String> changed = new LinkedHashSet<String>();
        synchronized (this) {
            for (String parameterId : parameterIds) {
                if (parameterId == null || parameterId.trim().isEmpty()) {
                    continue;
                }
                LoggerChannel current = channels.get(parameterId);
                if (current != null && current.isSelected() != selected) {
                    changed.add(parameterId);
                }
            }
        }
        if (changed.isEmpty()) return;
        try {
            selectionCommand.setSelected(Collections.unmodifiableList(
                    new ArrayList<String>(changed)), selected);
        } catch (RuntimeException failure) {
            failureHandler.accept(failure);
        }
    }

    public void setUnitOption(String parameterId, String optionId) {
        if (parameterId == null || parameterId.trim().isEmpty()
                || optionId == null || optionId.trim().isEmpty()) return;
        LoggerChannel current;
        synchronized (this) {
            current = channels.get(parameterId);
        }
        if (current == null) return;
        LoggerChannelUnitOption requested = null;
        for (LoggerChannelUnitOption option : current.getUnitOptions()) {
            if (option.getId().equals(optionId)) requested = option;
        }
        if (requested == null) {
            failureHandler.accept(new IllegalArgumentException(
                    "Unknown unit option for " + current.getName()));
            return;
        }
        if (requested.isSelected()) return;
        try {
            unitCommand.accept(parameterId, optionId);
        } catch (RuntimeException failure) {
            failureHandler.accept(failure);
        }
    }

    private synchronized List<LoggerChannel> snapshot() {
        return Collections.unmodifiableList(
                new ArrayList<LoggerChannel>(channels.values()));
    }

    private void notifyListeners(List<LoggerChannel> snapshot) {
        for (Consumer<List<LoggerChannel>> listener : listeners) {
            notifyListener(listener, snapshot);
        }
    }

    private void notifyListener(Consumer<List<LoggerChannel>> listener,
            List<LoggerChannel> snapshot) {
        try {
            listener.accept(snapshot);
        } catch (RuntimeException failure) {
            failureHandler.accept(failure);
        }
    }
}
