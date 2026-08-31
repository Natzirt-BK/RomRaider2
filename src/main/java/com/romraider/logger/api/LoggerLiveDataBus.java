/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.logger.api;

import static org.apache.log4j.Logger.getLogger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import org.apache.log4j.Logger;

import com.romraider.logger.ecu.definition.EcuDataConvertor;
import com.romraider.logger.ecu.definition.LoggerData;
import com.romraider.logger.ecu.ui.StatusChangeListener;

/** Thread-safe bridge between the existing logger backend and integrated views. */
public final class LoggerLiveDataBus implements StatusChangeListener {
    private static final Logger LOGGER = getLogger(LoggerLiveDataBus.class);
    private static final int MAX_HISTORY_SAMPLES = 240;
    private static final LoggerLiveDataBus INSTANCE = new LoggerLiveDataBus();
    private final CopyOnWriteArrayList<LoggerLiveDataListener> listeners =
            new CopyOnWriteArrayList<LoggerLiveDataListener>();
    private final Map<String, LiveDataSample> latest =
            new LinkedHashMap<String, LiveDataSample>();
    private final Map<String, LinkedList<LiveDataSample>> history =
            new LinkedHashMap<String, LinkedList<LiveDataSample>>();
    private volatile LoggerSessionState state = LoggerSessionState.STOPPED;

    private LoggerLiveDataBus() {
    }

    public static LoggerLiveDataBus getInstance() { return INSTANCE; }

    public void addListener(LoggerLiveDataListener listener) {
        if (listener == null || listeners.contains(listener)) return;
        listeners.add(listener);
        notifyListener(listener, value -> value.sessionStateChanged(state));
        for (LiveDataSample sample : getLatestSamples()) {
            notifyListener(listener, value -> value.sampleUpdated(sample));
        }
    }

    public void removeListener(LoggerLiveDataListener listener) {
        listeners.remove(listener);
    }

    public LoggerSessionState getState() { return state; }

    public synchronized List<LiveDataSample> getLatestSamples() {
        return Collections.unmodifiableList(
                new ArrayList<LiveDataSample>(latest.values()));
    }

    public synchronized Map<String, List<LiveDataSample>> getRecentSamples() {
        Map<String, List<LiveDataSample>> copy =
                new LinkedHashMap<String, List<LiveDataSample>>();
        for (Map.Entry<String, LinkedList<LiveDataSample>> entry
                : history.entrySet()) {
            copy.put(entry.getKey(), Collections.unmodifiableList(
                    new ArrayList<LiveDataSample>(entry.getValue())));
        }
        return Collections.unmodifiableMap(copy);
    }

    public void publish(LoggerData data, double rawValue) {
        if (data == null) return;
        EcuDataConvertor convertor = data.getSelectedConvertor();
        String displayValue;
        String units;
        try {
            displayValue = convertor == null ? Double.toString(rawValue)
                    : convertor.format(rawValue);
            units = convertor == null ? "" : convertor.getUnits();
        } catch (RuntimeException failure) {
            displayValue = Double.toString(rawValue);
            units = "";
        }
        LiveDataSample sample = new LiveDataSample(data.getId(), data.getName(),
                rawValue, displayValue, units, System.currentTimeMillis());
        synchronized (this) {
            latest.put(sample.getParameterId(), sample);
            LinkedList<LiveDataSample> samples = history.get(
                    sample.getParameterId());
            if (samples == null) {
                samples = new LinkedList<LiveDataSample>();
                history.put(sample.getParameterId(), samples);
            }
            samples.addLast(sample);
            while (samples.size() > MAX_HISTORY_SAMPLES) samples.removeFirst();
        }
        for (LoggerLiveDataListener listener : listeners) {
            notifyListener(listener, value -> value.sampleUpdated(sample));
        }
    }

    public void remove(LoggerData data) {
        if (data == null) return;
        synchronized (this) {
            latest.remove(data.getId());
            history.remove(data.getId());
        }
        for (LoggerLiveDataListener listener : listeners) {
            notifyListener(listener,
                    value -> value.parameterRemoved(data.getId()));
        }
    }

    public void clearSamples() {
        List<String> ids;
        synchronized (this) {
            ids = new ArrayList<String>(latest.keySet());
            latest.clear();
            history.clear();
        }
        for (String id : ids) {
            for (LoggerLiveDataListener listener : listeners) {
                notifyListener(listener, value -> value.parameterRemoved(id));
            }
        }
    }

    public void connecting() { setState(LoggerSessionState.CONNECTING); }
    public void readingData() { setState(LoggerSessionState.LIVE_ECU); }
    public void readingDataExternal() { setState(LoggerSessionState.LIVE_EXTERNAL); }
    public void loggingData() { setState(LoggerSessionState.RECORDING); }
    public void stopped() { setState(LoggerSessionState.STOPPED); }

    private void setState(LoggerSessionState next) {
        if (next == null || next == state) return;
        state = next;
        for (LoggerLiveDataListener listener : listeners) {
            notifyListener(listener, value -> value.sessionStateChanged(next));
        }
    }

    private static void notifyListener(LoggerLiveDataListener listener,
            Consumer<LoggerLiveDataListener> notification) {
        try {
            notification.accept(listener);
        } catch (RuntimeException exception) {
            LOGGER.warn("Live-data listener failed", exception);
        }
    }
}
