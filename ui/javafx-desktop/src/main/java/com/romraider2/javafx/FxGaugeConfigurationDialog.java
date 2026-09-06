/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider2.javafx;

import com.romraider.logger.api.LoggerChannel;
import com.romraider.logger.api.LoggerGaugeConfiguration;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.stage.Window;

final class FxGaugeConfigurationDialog {
    record Result(LoggerGaugeConfiguration configuration) { }
    private FxGaugeConfigurationDialog() { }

    static Dialog<Result> create(Window owner, LoggerChannel channel,
            LoggerGaugeConfiguration existing, boolean inactive) {
        Dialog<Result> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.setTitle("Gauge limits · " + channel.getName());
        dialog.setHeaderText("Values in " + channel.getUnits() + ". No vehicle safety limits are guessed."
                + (inactive ? "\nPrevious limits are inactive: their conversion is unknown or different." : ""));
        TextField minimum = field(existing == null ? null : existing.getScaleMinimum());
        TextField maximum = field(existing == null ? null : existing.getScaleMaximum());
        TextField low = field(existing == null ? null : existing.getLowWarning());
        TextField high = field(existing == null ? null : existing.getHighWarning());
        TextField hysteresis = field(existing == null ? 0.0 : existing.getHysteresis());
        Label error = new Label();
        error.setWrapText(true);
        GridPane form = new GridPane();
        form.setHgap(12);
        form.setVgap(9);
        String[] labels = {"Scale minimum", "Scale maximum", "Low warning", "High warning", "Hysteresis"};
        TextField[] fields = {minimum, maximum, low, high, hysteresis};
        for (int i = 0; i < fields.length; i++) form.addRow(i, new Label(labels[i]), fields[i]);
        form.add(new Label("Blank warnings are off. Set both scale endpoints or leave both blank."), 0, 5, 2, 1);
        form.add(error, 0, 6, 2, 1);
        dialog.getDialogPane().setContent(form);
        ButtonType save = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        ButtonType clear = new ButtonType("Clear limits", ButtonBar.ButtonData.OTHER);
        dialog.getDialogPane().getButtonTypes().addAll(save, clear, ButtonType.CANCEL);
        Runnable validate = () -> {
            try {
                parse(minimum.getText(), maximum.getText(), low.getText(), high.getText(),
                        hysteresis.getText(), channel.getConversionIdentity());
                error.setText("");
                dialog.getDialogPane().lookupButton(save).setDisable(false);
            } catch (IllegalArgumentException failure) {
                error.setText(failure.getMessage());
                dialog.getDialogPane().lookupButton(save).setDisable(true);
            }
        };
        for (TextField field : fields) field.textProperty().addListener((value, old, next) -> validate.run());
        validate.run();
        dialog.setResultConverter(button -> button == clear ? new Result(null)
                : button == save ? new Result(parse(minimum.getText(), maximum.getText(), low.getText(),
                        high.getText(), hysteresis.getText(), channel.getConversionIdentity())) : null);
        return dialog;
    }

    static LoggerGaugeConfiguration parse(String minimum, String maximum, String low,
            String high, String hysteresis, String identity) {
        Double min = optional(minimum), max = optional(maximum), lo = optional(low), hi = optional(high);
        Double band = optional(hysteresis);
        LoggerGaugeConfiguration result = new LoggerGaugeConfiguration(min, max, lo, hi, band == null ? 0 : band)
                .forConversion(identity);
        return min == null && max == null && lo == null && hi == null ? null : result;
    }
    private static TextField field(Double value) { return new TextField(value == null ? "" : value.toString()); }
    private static Double optional(String text) {
        if (text == null || text.trim().isEmpty()) return null;
        try { return Double.valueOf(text.trim()); }
        catch (NumberFormatException failure) { throw new IllegalArgumentException("Enter numbers or leave optional limits blank."); }
    }
}
