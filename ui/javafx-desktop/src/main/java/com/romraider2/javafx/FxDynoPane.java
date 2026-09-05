/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider2.javafx;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.romraider.logger.api.LiveDataSample;
import com.romraider.logger.api.LoggerChannel;
import com.romraider.logger.ecu.ui.spi.LoggerWorkspaceContext;

import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/** Modern road-dyno projection from synchronized RPM and speed samples. */
final class FxDynoPane extends BorderPane {
    private static final double AIR_DENSITY = 1.225;
    private static final double GRAVITY = 9.80665;
    private final LoggerWorkspaceContext context;
    private final ComboBox<LoggerChannel> rpm = new ComboBox<>();
    private final ComboBox<LoggerChannel> speed = new ComboBox<>();
    private final TextField mass = new TextField("1600");
    private final TextField drag = new TextField("0.32");
    private final TextField area = new TextField("2.20");
    private final TextField rolling = new TextField("0.015");
    private final TextField loss = new TextField("0");
    private final Label peakPower = metric("— PEAK EST. HP");
    private final Label peakTorque = metric("— PEAK EST. LB-FT");
    private final Label runStatus = new Label(
            "Select engine speed and vehicle speed channels.");
    private final LineChart<Number, Number> chart;
    private List<LoggerChannel> available = List.of();

    FxDynoPane(LoggerWorkspaceContext context) {
        this.context = context;
        configureChannelChoice(rpm);
        configureChannelChoice(speed);
        NumberAxis x = new NumberAxis();
        x.setLabel("Engine speed (RPM)");
        NumberAxis y = new NumberAxis();
        y.setLabel("Estimated engine output");
        chart = new LineChart<>(x, y);
        chart.setAnimated(false);
        chart.setCreateSymbols(false);
        chart.setTitle("Road Dyno · estimated engine power and torque");
        setTop(header());
        ScrollPane setupScroll = new ScrollPane(setup());
        setupScroll.setFitToWidth(true);
        setupScroll.setPrefViewportWidth(365);
        setupScroll.setMinHeight(0);
        setLeft(setupScroll);
        chart.setMinSize(0, 0);
        setCenter(chart);
        BorderPane.setMargin(chart, new Insets(12));
    }

    private VBox header() {
        Label kicker = new Label("ROAD DYNO");
        kicker.getStyleClass().add("section-kicker");
        Label title = new Label("Calculate a pull from live Logger history");
        title.getStyleClass().add("title");
        title.setWrapText(true);
        Label detail = new Label("Uses vehicle speed acceleration, mass, "
                + "rolling resistance, and aerodynamic drag. Results are "
                + "estimates for tuning comparison, not certified measurements.");
        detail.getStyleClass().add("muted");
        detail.setWrapText(true);
        FlowPane metrics = new FlowPane(8, 6, peakPower, peakTorque);
        VBox box = new VBox(8, new VBox(2, kicker, title, detail), metrics);
        box.setPadding(new Insets(16));
        box.getStyleClass().add("command-deck");
        return box;
    }

    private VBox setup() {
        GridPane form = new GridPane();
        form.setHgap(8);
        form.setVgap(9);
        form.addRow(0, new Label("Engine speed"), rpm);
        form.addRow(1, new Label("Vehicle speed"), speed);
        form.addRow(2, new Label("Vehicle + driver (kg)"), mass);
        form.addRow(3, new Label("Drag coefficient"), drag);
        form.addRow(4, new Label("Frontal area (m²)"), area);
        form.addRow(5, new Label("Rolling coefficient"), rolling);
        form.addRow(6, new Label("Drivetrain loss (%)"), loss);
        rpm.setMaxWidth(Double.MAX_VALUE);
        speed.setMaxWidth(Double.MAX_VALUE);
        Button calculate = new Button("Calculate current run");
        calculate.setDefaultButton(true);
        calculate.setMaxWidth(Double.MAX_VALUE);
        calculate.setOnAction(event -> calculate());
        runStatus.setWrapText(true);
        runStatus.getStyleClass().add("muted");
        VBox box = new VBox(12, form, calculate, runStatus);
        box.setPrefWidth(365);
        box.setMinWidth(340);
        box.setPadding(new Insets(16));
        box.getStyleClass().add("nav-pane");
        return box;
    }

    private static void configureChannelChoice(ComboBox<LoggerChannel> choice) {
        choice.setPrefWidth(190);
        choice.setCellFactory(ignored -> channelCell());
        choice.setButtonCell(channelCell());
    }

    private static ListCell<LoggerChannel> channelCell() {
        return new ListCell<>() {
            @Override protected void updateItem(LoggerChannel item,
                    boolean empty) {
                super.updateItem(item, empty);
                String text = empty || item == null ? null : channelText(item);
                setText(text);
                setTooltip(text == null ? null : new Tooltip(text));
            }
        };
    }

    static String channelText(LoggerChannel channel) {
        return channel.getUnits().isBlank() ? channel.getName()
                : channel.getName() + "  [" + channel.getUnits() + "]";
    }

    static LoggerChannel guessVehicleSpeed(List<LoggerChannel> channels) {
        LoggerChannel unitMatch = null;
        for (LoggerChannel channel : channels) {
            String name = channel.getName().toLowerCase(Locale.ROOT);
            if (name.contains("vehicle speed") || name.contains("road speed")) {
                return channel;
            }
            String units = channel.getUnits().toLowerCase(Locale.ROOT);
            if (unitMatch == null && (units.contains("km/h")
                    || units.contains("kph") || units.contains("mph"))) {
                unitMatch = channel;
            }
        }
        return unitMatch;
    }

    void refresh(List<LoggerChannel> channels) {
        List<LoggerChannel> selected = channels == null ? List.of()
                : channels.stream().filter(LoggerChannel::isSelected).toList();
        if (sameChannels(selected, available)) return;
        LoggerChannel priorRpm = rpm.getValue();
        LoggerChannel priorSpeed = speed.getValue();
        available = new ArrayList<>(selected);
        rpm.setItems(FXCollections.observableArrayList(available));
        speed.setItems(FXCollections.observableArrayList(available));
        rpm.setValue(findPrior(priorRpm, available));
        speed.setValue(findPrior(priorSpeed, available));
        if (rpm.getValue() == null) rpm.setValue(guess(available,
                "rpm", "engine speed"));
        LoggerChannel vehicleSpeed = guessVehicleSpeed(available);
        if (speed.getValue() == null || rpm.getValue() != null
                && speed.getValue().getParameterId().equals(
                        rpm.getValue().getParameterId())) {
            speed.setValue(vehicleSpeed);
        }
    }

    private void calculate() {
        try {
            LoggerChannel rpmChannel = required(rpm.getValue(), "engine speed");
            LoggerChannel speedChannel = required(speed.getValue(), "vehicle speed");
            double vehicleMass = positive(mass, "Vehicle mass");
            double cd = nonNegative(drag, "Drag coefficient");
            double frontalArea = positive(area, "Frontal area");
            double crr = nonNegative(rolling, "Rolling coefficient");
            double drivetrainLoss = nonNegative(loss, "Drivetrain loss") / 100.0;
            if (drivetrainLoss >= .80) {
                throw new IllegalArgumentException(
                        "Drivetrain loss must be below 80 percent");
            }
            Map<String, List<LiveDataSample>> history =
                    context.getLiveData().getRecentSamples();
            List<LiveDataSample> rpms = history.getOrDefault(
                    rpmChannel.getParameterId(), List.of());
            List<LiveDataSample> speeds = history.getOrDefault(
                    speedChannel.getParameterId(), List.of());
            List<DynoPoint> points = project(rpms, speeds, speedChannel.getUnits(),
                    vehicleMass, cd, frontalArea, crr, drivetrainLoss);
            if (points.size() < 3) {
                throw new IllegalArgumentException(
                        "At least three synchronized RPM and speed samples are required");
            }
            points.sort(Comparator.comparingDouble(DynoPoint::rpm));
            XYChart.Series<Number, Number> power = new XYChart.Series<>();
            power.setName("Estimated engine horsepower");
            XYChart.Series<Number, Number> torque = new XYChart.Series<>();
            torque.setName("Estimated engine torque (lb-ft)");
            double maxPower = 0;
            double maxTorque = 0;
            double powerRpm = 0;
            double torqueRpm = 0;
            for (DynoPoint point : points) {
                power.getData().add(new XYChart.Data<>(point.rpm(), point.hp()));
                torque.getData().add(new XYChart.Data<>(point.rpm(), point.torque()));
                if (point.hp() > maxPower) {
                    maxPower = point.hp();
                    powerRpm = point.rpm();
                }
                if (point.torque() > maxTorque) {
                    maxTorque = point.torque();
                    torqueRpm = point.rpm();
                }
            }
            chart.getData().setAll(power, torque);
            peakPower.setText(String.format(Locale.ROOT, "%.1f HP @ %.0f",
                    maxPower, powerRpm));
            peakTorque.setText(String.format(Locale.ROOT, "%.1f LB-FT @ %.0f",
                    maxTorque, torqueRpm));
            runStatus.setText(points.size() + " calculated points · "
                    + "Use the same road, gear, and vehicle settings when "
                    + "comparing runs.");
            runStatus.getStyleClass().remove("danger");
        } catch (RuntimeException failure) {
            runStatus.setText(FxDialogs.rootMessage(failure));
            if (!runStatus.getStyleClass().contains("danger")) {
                runStatus.getStyleClass().add("danger");
            }
        }
    }

    static List<DynoPoint> project(List<LiveDataSample> rpms,
            List<LiveDataSample> speeds, String speedUnits, double mass,
            double cd, double area, double crr, double drivetrainLoss) {
        int count = Math.min(rpms.size(), speeds.size());
        List<DynoPoint> result = new ArrayList<>();
        for (int index = 1; index < count; index++) {
            LiveDataSample before = speeds.get(speeds.size() - count + index - 1);
            LiveDataSample current = speeds.get(speeds.size() - count + index);
            LiveDataSample currentRpm = rpms.get(rpms.size() - count + index);
            double seconds = (current.getTimestampMillis()
                    - before.getTimestampMillis()) / 1000.0;
            if (seconds <= 0 || seconds > 2.0) continue;
            double previousSpeed = metersPerSecond(before.getRawValue(), speedUnits);
            double velocity = metersPerSecond(current.getRawValue(), speedUnits);
            double acceleration = (velocity - previousSpeed) / seconds;
            double engineSpeed = currentRpm.getRawValue();
            if (velocity <= 0 || acceleration <= 0 || engineSpeed <= 0) continue;
            double force = mass * acceleration + mass * GRAVITY * crr
                    + .5 * AIR_DENSITY * cd * area * velocity * velocity;
            double wheelHp = Math.max(0, force * velocity / 745.699872);
            double estimatedCrank = wheelHp / (1.0 - drivetrainLoss);
            double torque = estimatedCrank * 5252.113 / engineSpeed;
            if (Double.isFinite(estimatedCrank) && Double.isFinite(torque)) {
                result.add(new DynoPoint(engineSpeed, estimatedCrank, torque));
            }
        }
        return result;
    }

    private static double metersPerSecond(double speed, String units) {
        String normalized = units == null ? "" : units.toLowerCase(Locale.ROOT);
        if (normalized.contains("km") || normalized.contains("kph")) {
            return speed / 3.6;
        }
        return speed * 0.44704;
    }

    private static LoggerChannel guess(List<LoggerChannel> channels,
            String... names) {
        for (String name : names) {
            for (LoggerChannel channel : channels) {
                if (channel.getName().toLowerCase(Locale.ROOT).contains(name)) {
                    return channel;
                }
            }
        }
        return null;
    }

    private static LoggerChannel findPrior(LoggerChannel prior,
            List<LoggerChannel> channels) {
        if (prior == null) return null;
        return channels.stream().filter(channel -> channel.getParameterId()
                .equals(prior.getParameterId())).findFirst().orElse(null);
    }

    private static boolean sameChannels(List<LoggerChannel> first,
            List<LoggerChannel> second) {
        if (first.size() != second.size()) return false;
        for (int index = 0; index < first.size(); index++) {
            if (!first.get(index).getParameterId().equals(
                    second.get(index).getParameterId())) return false;
        }
        return true;
    }

    private static LoggerChannel required(LoggerChannel channel, String name) {
        if (channel == null) throw new IllegalArgumentException(
                "Select the " + name + " channel");
        return channel;
    }

    private static double positive(TextField field, String name) {
        double value = number(field, name);
        if (value <= 0) throw new IllegalArgumentException(name + " must be positive");
        return value;
    }

    private static double nonNegative(TextField field, String name) {
        double value = number(field, name);
        if (value < 0) throw new IllegalArgumentException(name + " cannot be negative");
        return value;
    }

    private static double number(TextField field, String name) {
        try {
            double value = Double.parseDouble(field.getText().trim());
            if (!Double.isFinite(value)) throw new NumberFormatException();
            return value;
        } catch (RuntimeException invalid) {
            throw new IllegalArgumentException(name + " is not a valid number");
        }
    }

    private static Label metric(String text) {
        Label label = new Label(text);
        label.getStyleClass().addAll("metric", "dyno-metric");
        return label;
    }

    record DynoPoint(double rpm, double hp, double torque) { }
}
