/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider2.javafx;

import java.util.List;
import java.util.Locale;
import java.util.function.BiPredicate;
import java.util.function.BooleanSupplier;

import com.romraider.logger.api.LoggerChannel;
import com.romraider.logger.api.LoggerChannelKind;
import com.romraider.logger.api.LoggerChannelService;
import com.romraider.logger.api.LoggerChannelUnitOption;

import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

/** Channel controls only; filtering never changes the polling selection. */
final class FxLoggerChannelPane extends VBox {
    enum Category {
        ALL("All channels", null),
        PARAMETERS("Parameters", LoggerChannelKind.PARAMETER),
        SWITCHES("Switches", LoggerChannelKind.SWITCH),
        EXTERNAL("External Sensors", LoggerChannelKind.EXTERNAL);

        final String label;
        final LoggerChannelKind kind;
        Category(String label, LoggerChannelKind kind) {
            this.label = label;
            this.kind = kind;
        }
        boolean includes(LoggerChannel channel) {
            return kind == null || kind == channel.getKind();
        }
        @Override public String toString() { return label; }
    }

    private final LoggerChannelService service;
    private final BiPredicate<String, String> confirm;
    private final BooleanSupplier recordingNow;
    private final ComboBox<Category> category = new ComboBox<>();
    private final TextField search = new TextField();
    private final VBox rows = new VBox(6);
    private final Label count = new Label();
    private final Button clearCategory = new Button("Clear category…");
    private final Button clearAll = new Button("Clear all…");
    private List<LoggerChannel> snapshot = List.of();
    private boolean recording;

    FxLoggerChannelPane(LoggerChannelService service,
            BiPredicate<String, String> confirm, BooleanSupplier recordingNow) {
        this.service = service;
        this.confirm = confirm;
        this.recordingNow = recordingNow;
        setSpacing(9);
        setPadding(new Insets(12));
        setMinWidth(230);
        setPrefWidth(320);
        getStyleClass().add("nav-pane");
        Label heading = new Label("CHANNELS");
        heading.getStyleClass().add("section-kicker");
        category.setId("logger-channel-category");
        category.setAccessibleText("Channel category");
        category.getItems().setAll(Category.values());
        category.setValue(Category.ALL);
        category.setMaxWidth(Double.MAX_VALUE);
        category.valueProperty().addListener((value, oldValue, newValue) -> rebuild());
        search.setId("logger-channel-search");
        search.setPromptText("Search name, ID or units");
        search.textProperty().addListener((value, oldValue, newValue) -> rebuild());
        clearCategory.setId("logger-clear-category");
        clearCategory.setTooltip(new Tooltip("Clear the whole category, including search-hidden channels"));
        clearCategory.setOnAction(event -> clear(category.getValue()));
        clearAll.setId("logger-clear-all");
        clearAll.setOnAction(event -> clear(Category.ALL));
        count.setWrapText(true);
        count.getStyleClass().add("muted");
        rows.setId("logger-channel-rows");
        ScrollPane scroll = new ScrollPane(rows);
        scroll.setFitToWidth(true);
        VBox.setVgrow(scroll, Priority.ALWAYS);
        getChildren().addAll(heading, category, search,
                new HBox(6, clearCategory, clearAll), count, scroll);
        update(service.getChannels());
    }

    void update(List<LoggerChannel> channels) {
        snapshot = List.copyOf(channels);
        rebuild();
    }

    void setRecording(boolean value) {
        if (recording == value) return;
        recording = value;
        rebuild();
    }

    private void clear(Category scope) {
        if (scope == null) return;
        // Freeze exact IDs before opening the modal dialog. New channels loaded
        // during confirmation must not become unexpected deletion targets.
        List<String> ids = service.getChannels().stream()
                .filter(scope::includes).filter(LoggerChannel::isSelected)
                .map(LoggerChannel::getParameterId).toList();
        if (ids.isEmpty()) return;
        String message = "Deselect " + ids.size() + " channel(s) in "
                + scope.label + ", including channels hidden by search?"
                + " This changes polling and recording selection; saved logs are unchanged.";
        if (confirm.test("Clear " + scope.label, message)) {
            service.setSelected(ids, false);
        }
    }

    private void rebuild() {
        Category scope = category.getValue();
        if (scope == null) scope = Category.ALL;
        String query = search.getText() == null ? ""
                : search.getText().trim().toLowerCase(Locale.ROOT);
        rows.getChildren().clear();
        int shown = 0;
        int total = 0;
        int selectedInCategory = 0;
        for (LoggerChannel channel : snapshot) {
            if (!scope.includes(channel)) continue;
            total++;
            if (channel.isSelected()) selectedInCategory++;
            String label = channel.getName() + " " + channel.getParameterId()
                    + " " + channel.getUnits();
            if (!label.toLowerCase(Locale.ROOT).contains(query)) continue;
            shown++;
            CheckBox selected = new CheckBox(channel.getName());
            selected.setUserData(channel.getParameterId());
            selected.setSelected(channel.isSelected());
            selected.setMaxWidth(Double.MAX_VALUE);
            selected.setWrapText(true);
            selected.setTooltip(new Tooltip(label));
            selected.setOnAction(event -> service.setSelected(
                    channel.getParameterId(), selected.isSelected()));
            VBox row = new VBox(3, selected);
            if (channel.getUnitOptions().size() > 1) {
                ComboBox<LoggerChannelUnitOption> units = new ComboBox<>();
                units.setUserData(channel.getParameterId());
                units.setAccessibleText("Units for " + channel.getName());
                units.setMaxWidth(Double.MAX_VALUE);
                units.setConverter(new StringConverter<>() {
                    @Override public String toString(LoggerChannelUnitOption option) {
                        return option == null ? "" : option.getLabel();
                    }
                    @Override public LoggerChannelUnitOption fromString(String text) {
                        throw new UnsupportedOperationException("Non-editable unit selector");
                    }
                });
                units.getItems().setAll(channel.getUnitOptions());
                channel.getUnitOptions().stream().filter(LoggerChannelUnitOption::isSelected)
                        .findFirst().ifPresent(units::setValue);
                units.setDisable(recording);
                units.setTooltip(new Tooltip("Stop recording before changing units"));
                units.valueProperty().addListener((property, oldValue, newValue) -> {
                    if (!recording && !recordingNow.getAsBoolean() && newValue != null) {
                        service.setUnitOption(channel.getParameterId(), newValue.getId());
                    }
                });
                row.getChildren().add(units);
            } else if (!channel.getUnits().isBlank()) {
                row.getChildren().add(new Label(channel.getUnits()));
            }
            rows.getChildren().add(row);
        }
        count.setText(shown + " shown / " + total + " in category · "
                + selectedInCategory + " selected");
        clearCategory.setDisable(scope == Category.ALL || selectedInCategory == 0);
        clearAll.setDisable(snapshot.stream().noneMatch(LoggerChannel::isSelected));
        if (shown == 0) rows.getChildren().add(new Label("No matching channels"));
    }
}
