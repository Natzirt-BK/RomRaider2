/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider2.javafx;

import com.romraider.logger.analysis.*;
import java.util.*;
import javafx.collections.FXCollections;
import javafx.scene.control.*;
import javafx.scene.layout.*;

/** Named scalar conditions, independently enabled and explicitly mapped in logged units. */
final class FxFuelOperatingConditionsPane extends TitledPane {
    record ConditionDraft(FuelOperatingCondition kind, boolean enabled, LogChannel channel, String minimum, String maximum) {
        FuelAnalysisSetup.Condition setup(LogDataset data) {
            if (!enabled) return null;
            if (data == null || channel == null || channel.getIndex() < 0 || channel.getIndex() >= data.getChannelCount()
                    || data.getChannels().get(channel.getIndex()) != channel || channel.isTimeChannel())
                throw new IllegalArgumentException("Map " + kind.label() + " to a current numeric CSV column, or explicitly disable it.");
            try {
                Double low = kind.mode() == FuelOperatingCondition.Mode.MAXIMUM ? null : Double.valueOf(minimum.trim());
                Double high = switch (kind.mode()) {
                    case EQUAL -> low;
                    case MINIMUM -> null;
                    default -> Double.valueOf(maximum.trim());
                };
                return new FuelAnalysisSetup.Condition(kind, FuelAnalysisSetup.Channel.of(channel), low, high);
            } catch (NumberFormatException failure) { throw new IllegalArgumentException("Enter finite " + kind.label() + " limits in the mapped channel's units."); }
        }
        String summary() {
            String bounds = switch (kind.mode()) {
                case EQUAL -> " = " + minimum;
                case RANGE -> " from " + minimum + " through " + maximum;
                case MINIMUM -> " ≥ " + minimum;
                case MAXIMUM -> " ≤ " + maximum;
            };
            return kind.label() + ": " + (channel == null ? "unresolved column" : "column " + (channel.getIndex() + 1) + " " + channel.getLabel()) + bounds;
        }
    }
    record Draft(List<ConditionDraft> conditions) {
        Draft {
            conditions = List.copyOf(conditions);
            if (conditions.size() != FuelOperatingCondition.values().length
                    || conditions.stream().map(ConditionDraft::kind).distinct().count() != conditions.size())
                throw new IllegalArgumentException("Incomplete operating condition layout");
        }
        static Draft empty() {
            return new Draft(Arrays.stream(FuelOperatingCondition.values()).map(kind -> new ConditionDraft(kind, false, null, "", "")).toList());
        }
        List<FuelLogAnalysis.Filter> filters(LogDataset data) {
            List<FuelLogAnalysis.Filter> result = new ArrayList<>();
            for (ConditionDraft draft : conditions) {
                var condition = draft.setup(data);
                if (condition != null) result.add(condition.kind().filter(draft.channel().getIndex(), condition.minimum(), condition.maximum()));
            }
            return List.copyOf(result);
        }
        List<FuelAnalysisSetup.Condition> setup(LogDataset data) {
            return conditions.stream().map(condition -> condition.setup(data)).filter(Objects::nonNull).toList();
        }
        String summary() {
            List<String> enabled = conditions.stream().filter(ConditionDraft::enabled).map(ConditionDraft::summary).toList();
            return enabled.isEmpty() ? "Named operating conditions off" : String.join("\n", enabled);
        }
    }
    private final Map<FuelOperatingCondition, Row> rows = new EnumMap<>(FuelOperatingCondition.class);

    FxFuelOperatingConditionsPane(Runnable changed) {
        setText("Operating conditions · 0 enabled"); setExpanded(false);
        VBox content = new VBox(10);
        Label help = new Label("All enabled conditions are combined with AND, plus custom and rate filters. "
                + "Use logged units and verify state codes. Nothing is guessed or converted; missing enabled inputs reject the sample.");
        help.setWrapText(true); content.getChildren().add(help);
        for (FuelOperatingCondition kind : FuelOperatingCondition.values()) {
            Row row = new Row(kind); rows.put(kind, row); content.getChildren().add(row.content);
            row.enabled.selectedProperty().addListener((value, before, after) -> {
                setText("Operating conditions · " + rows.values().stream().filter(r -> r.enabled.isSelected()).count() + " enabled"); changed.run();
            });
            row.channel.valueProperty().addListener((value, before, after) -> changed.run());
            row.minimum.textProperty().addListener((value, before, after) -> changed.run());
            row.maximum.textProperty().addListener((value, before, after) -> changed.run());
        }
        setContent(content);
    }
    Draft draft() {
        return new Draft(rows.entrySet().stream().map(entry -> {
            Row row = entry.getValue(); return new ConditionDraft(entry.getKey(), row.enabled.isSelected(), row.channel.getValue(), row.minimum.getText(), row.maximum.getText());
        }).toList());
    }
    void apply(Draft draft) {
        for (ConditionDraft value : draft.conditions()) {
            Row row = rows.get(value.kind()); row.enabled.setSelected(value.enabled()); row.channel.setValue(value.channel());
            row.minimum.setText(value.minimum()); row.maximum.setText(value.maximum());
        }
    }
    void setDataset(LogDataset data) {
        for (Row row : rows.values()) {
            row.enabled.setSelected(false);
            row.channel.setItems(FXCollections.observableArrayList(data.getChannels().stream().filter(c -> !c.isTimeChannel()).toList()));
            row.channel.setTooltip(null);
        }
        apply(Draft.empty());
    }
    void restore(List<FuelAnalysisSetup.Condition> conditions, LogDataset data, List<String> unresolved) {
        apply(Draft.empty()); rows.values().forEach(row -> row.channel.setTooltip(null));
        for (var condition : conditions) {
            Row row = rows.get(condition.kind()); LogChannel channel = condition.channel().resolve(data);
            row.enabled.setSelected(true); row.channel.setValue(channel);
            row.minimum.setText(condition.minimum() == null ? "" : Double.toString(condition.minimum()));
            row.maximum.setText(condition.maximum() == null || condition.kind().mode() == FuelOperatingCondition.Mode.EQUAL ? "" : Double.toString(condition.maximum()));
            row.channel.setTooltip(new Tooltip("Saved " + condition.kind().label() + ": " + condition.channel().label() + " · " + condition.channel().units()));
            if (channel == null) unresolved.add(condition.kind().label() + ": " + condition.channel().label());
        }
        if (!conditions.isEmpty()) setExpanded(true);
    }
    private static final class Row {
        final CheckBox enabled;
        final ComboBox<LogChannel> channel = new ComboBox<>();
        final TextField minimum = new TextField(), maximum = new TextField();
        final VBox content;
        Row(FuelOperatingCondition kind) {
            enabled = new CheckBox(kind.label()); enabled.setWrapText(true);
            channel.setMaxWidth(Double.MAX_VALUE); channel.setPromptText("Choose column; review its units");
            channel.setCellFactory(ignored -> cell()); channel.setButtonCell(cell());
            minimum.setPromptText(kind.mode() == FuelOperatingCondition.Mode.EQUAL ? "Exact required value" : "Minimum (inclusive)");
            maximum.setPromptText("Maximum (inclusive)");
            VBox fields = new VBox(4, channel);
            if (kind.mode() != FuelOperatingCondition.Mode.MAXIMUM) fields.getChildren().add(bound(kind.mode() == FuelOperatingCondition.Mode.EQUAL ? "Equals" : "Min (incl.)", minimum));
            if (kind.mode() == FuelOperatingCondition.Mode.RANGE || kind.mode() == FuelOperatingCondition.Mode.MAXIMUM) fields.getChildren().add(bound("Max (incl.)", maximum));
            fields.visibleProperty().bind(enabled.selectedProperty()); fields.managedProperty().bind(enabled.selectedProperty());
            content = new VBox(5, enabled, fields);
        }
        private static HBox bound(String text, TextField field) {
            Label label = new Label(text); label.setMinWidth(75); field.setPrefColumnCount(8); field.setMinWidth(80);
            HBox row = new HBox(5, label, field); row.setAlignment(javafx.geometry.Pos.CENTER_LEFT); HBox.setHgrow(field, Priority.ALWAYS); return row;
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
}
