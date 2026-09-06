/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider2.javafx;

import com.romraider.logger.analysis.*;
import java.util.List;
import javafx.collections.FXCollections;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;

/** Explicit recorded-time transient condition, never a wall-clock or sample-index derivative. */
final class FxFuelRateFilterPane extends TitledPane {
    record Draft(boolean enabled, LogChannel signal, LogChannel time, String scale, String maximum, String gap) {
        static Draft empty() { return new Draft(false, null, null, "", "", ""); }
        FuelRateFilter filter(LogDataset data) {
            if (!enabled) return null;
            checkChannel(data, signal, false); checkChannel(data, time, true);
            try {
                FuelRateFilter filter = new FuelRateFilter(signal.getIndex(), time.getIndex(), Double.parseDouble(scale.trim()),
                        Double.parseDouble(maximum.trim()), Double.parseDouble(gap.trim()));
                filter.validate(data); return filter;
            } catch (NumberFormatException failure) { throw new IllegalArgumentException("Enter the rate limit, seconds per recorded time unit, and maximum gap as finite numbers."); }
        }
        private static void checkChannel(LogDataset data, LogChannel channel, boolean time) {
            if (data == null || channel == null || channel.getIndex() < 0 || channel.getIndex() >= data.getChannelCount()
                    || data.getChannels().get(channel.getIndex()) != channel || channel.isTimeChannel() != time)
                throw new IllegalArgumentException("Map the rate signal and recorded-time column from the current CSV; missing or ambiguous channels are not guessed.");
        }
        String summary() {
            return !enabled ? "Rate filter off" : "Absolute rate: " + column(signal)
                    + " ≤ " + maximum + " signal units/s; time " + column(time)
                    + " × " + scale + " s/unit; maximum gap " + gap + " s. First/missing/invalid-time rows are unavailable.";
        }
        private static String column(LogChannel channel) {
            return channel == null ? "unresolved column" : "column " + (channel.getIndex() + 1) + " " + channel.getLabel();
        }
    }
    private final CheckBox enabled = new CheckBox("Enable absolute rate limit");
    private final ComboBox<LogChannel> signal = choice(), time = choice();
    private final TextField scale = new TextField(), maximum = new TextField(), gap = new TextField();

    FxFuelRateFilterPane(Runnable changed) {
        setText("Transient rate filter · off"); setExpanded(false);
        scale.setPromptText("Seconds per time unit: e.g. 0.001 for milliseconds");
        maximum.setPromptText("Maximum absolute change per second"); gap.setPromptText("Maximum adjacent sample gap in seconds");
        Label help = new Label("Uses adjacent recorded rows inside the chosen range, including rows rejected by other filters. "
                + "The first selected row is unavailable. No bridging missing values, duplicate/reversed timestamps or long gaps. Review signal units and time scale explicitly; no default tuning limit is supplied.");
        help.setWrapText(true);
        VBox fields = new VBox(5, new Label("Signal (for example, MAF voltage)"), signal, new Label("Recorded-time column"), time,
                new Label("Seconds per recorded time unit"), scale, new Label("Maximum |change| / second (inclusive)"), maximum,
                new Label("Maximum time gap (seconds, inclusive)"), gap, help);
        fields.disableProperty().bind(enabled.selectedProperty().not()); setContent(new VBox(8, enabled, fields));
        enabled.selectedProperty().addListener((value, before, after) -> { setText("Transient rate filter · " + (after ? "enabled" : "off")); changed.run(); });
        for (ComboBox<LogChannel> choice : List.of(signal, time)) choice.valueProperty().addListener((value, before, after) -> changed.run());
        for (TextField field : List.of(scale, maximum, gap)) field.textProperty().addListener((value, before, after) -> changed.run());
    }
    void setDataset(LogDataset data) {
        enabled.setSelected(false);
        signal.setItems(FXCollections.observableArrayList(data.getChannels().stream().filter(c -> !c.isTimeChannel()).toList()));
        time.setItems(FXCollections.observableArrayList(data.getChannels().stream().filter(LogChannel::isTimeChannel).toList()));
        apply(Draft.empty()); signal.setTooltip(null); time.setTooltip(null);
    }
    Draft draft() { return new Draft(enabled.isSelected(), signal.getValue(), time.getValue(), scale.getText(), maximum.getText(), gap.getText()); }
    void apply(Draft draft) {
        enabled.setSelected(draft.enabled()); signal.setValue(draft.signal()); time.setValue(draft.time());
        scale.setText(draft.scale()); maximum.setText(draft.maximum()); gap.setText(draft.gap());
    }
    FuelAnalysisSetup.Rate setup(LogDataset data) {
        FuelRateFilter rate = draft().filter(data); if (rate == null) return null;
        return new FuelAnalysisSetup.Rate(FuelAnalysisSetup.Channel.of(signal.getValue()),
                new FuelAnalysisSetup.Channel(time.getValue().getLabel(), time.getValue().getUnits()),
                rate.secondsPerTimeUnit(), rate.maximumRate(), rate.maximumGapSeconds());
    }
    void restore(FuelAnalysisSetup.Rate rate, LogDataset data, List<String> unresolved) {
        if (rate == null) { apply(Draft.empty()); signal.setTooltip(null); time.setTooltip(null); return; }
        LogChannel mappedSignal = rate.signal().resolve(data), mappedTime = rate.time().resolveTime(data);
        if (mappedSignal == null) unresolved.add("rate signal: " + rate.signal().label());
        if (mappedTime == null) unresolved.add("recorded time: " + rate.time().label());
        apply(new Draft(true, mappedSignal, mappedTime, Double.toString(rate.secondsPerTimeUnit()), Double.toString(rate.maximumRate()), Double.toString(rate.maximumGapSeconds())));
        signal.setTooltip(new Tooltip("Saved signal: " + rate.signal().label() + " · " + rate.signal().units()));
        time.setTooltip(new Tooltip("Saved time: " + rate.time().label() + " · " + rate.time().units()));
        setExpanded(true);
    }
    private static ComboBox<LogChannel> choice() {
        ComboBox<LogChannel> choice = new ComboBox<>(); choice.setMaxWidth(Double.MAX_VALUE); choice.setPromptText("Choose column; review units");
        choice.setCellFactory(ignored -> cell()); choice.setButtonCell(cell()); return choice;
    }
    private static ListCell<LogChannel> cell() {
        return new ListCell<>() {
            @Override protected void updateItem(LogChannel value, boolean empty) {
                super.updateItem(value, empty);
                setText(value == null ? null : "[" + (value.getIndex() + 1) + "] " + value.getLabel() + " · " + value.getUnits());
            }
        };
    }
}
