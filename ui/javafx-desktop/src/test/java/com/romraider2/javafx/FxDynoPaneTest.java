/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider2.javafx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.romraider.logger.api.LiveDataSample;
import com.romraider.logger.api.LoggerChannel;
import com.romraider.logger.api.LoggerChannelKind;

class FxDynoPaneTest {
    @Test void rejectsUnrelatedTimestampsAndUnsupportedUnits() {
        var rpm = List.of(sample("rpm", 2500, 10000), sample("rpm", 3000, 11000));
        var speed = List.of(sample("speed", 30, 0), sample("speed", 40, 1000));
        assertTrue(FxDynoPane.project(rpm, speed, "mph", 1600, .32, 2.2, .015, 0).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> FxDynoPane.project(rpm, speed, "V", 1600, .32, 2.2, .015, 0));
    }

    @Test void matchesTimestampsRatherThanListPositionsAndConvertsMetersPerSecond() {
        var rpm = List.of(sample("rpm", 1500, 0), sample("rpm", 2000, 500), sample("rpm", 2500, 1000));
        var mph = List.of(sample("speed", 30, 0), sample("speed", 40, 1000));
        var metric = mph.stream().map(s -> new LiveDataSample(s.getParameterId(), s.getName(), s.getRawValue() * .44704,
                "", "m/s", s.getTimestampMillis())).toList();
        var first = FxDynoPane.project(rpm, mph, "mph", 1600, .32, 2.2, .015, 0);
        var second = FxDynoPane.project(rpm, metric, "m/s", 1600, .32, 2.2, .015, 0);
        assertEquals(2500, first.getFirst().rpm());
        assertEquals(first.getFirst().hp(), second.getFirst().hp(), .00001);
    }

    @Test void separatedPullsAreNotMerged() {
        var rpm = new java.util.ArrayList<LiveDataSample>(); var speed = new java.util.ArrayList<LiveDataSample>();
        for (int i = 0; i < 4; i++) { rpm.add(sample("rpm", 2000 + i * 100, i * 1000)); speed.add(sample("speed", 20 + i, i * 1000)); }
        for (int i = 0; i < 4; i++) { rpm.add(sample("rpm", 3000 + i * 100, 10000 + i * 1000)); speed.add(sample("speed", 30 + i, 10000 + i * 1000)); }
        var result = FxDynoPane.project(rpm, speed, "mph", 1600, .32, 2.2, .015, 0);
        assertEquals(3, result.size()); assertEquals(3100, result.getFirst().rpm());
    }
    @Test
    void acceleratingRunProducesFiniteEstimatedPowerAndTorqueCurve() {
        List<LiveDataSample> rpm = List.of(
                sample("rpm", 2500, 0), sample("rpm", 3000, 1000),
                sample("rpm", 3500, 2000), sample("rpm", 4000, 3000));
        List<LiveDataSample> speed = List.of(
                sample("speed", 30, 0), sample("speed", 40, 1000),
                sample("speed", 50, 2000), sample("speed", 60, 3000));

        List<FxDynoPane.DynoPoint> curve = FxDynoPane.project(rpm, speed,
                "mph", 1600, .32, 2.2, .015, 0);

        assertEquals(3, curve.size());
        assertTrue(curve.stream().allMatch(point -> point.hp() > 0
                && point.torque() > 0
                && Double.isFinite(point.hp())
                && Double.isFinite(point.torque())));
    }

    @Test
    void stationaryOrDeceleratingSamplesAreExcluded() {
        List<LiveDataSample> rpm = List.of(
                sample("rpm", 3000, 0), sample("rpm", 2900, 1000));
        List<LiveDataSample> speed = List.of(
                sample("speed", 50, 0), sample("speed", 45, 1000));

        assertTrue(FxDynoPane.project(rpm, speed, "mph", 1600,
                .32, 2.2, .015, 0).isEmpty());
    }

    @Test
    void vehicleSpeedGuessDoesNotReuseEngineSpeed() {
        LoggerChannel engine = channel("rpm", "Engine Speed", "rpm");
        LoggerChannel vehicle = channel("speed", "Vehicle Speed", "km/h");

        assertEquals(vehicle,
                FxDynoPane.guessVehicleSpeed(List.of(engine, vehicle)));
        assertEquals("Vehicle Speed  [km/h]",
                FxDynoPane.channelText(vehicle));
    }

    private static LoggerChannel channel(String id, String name, String units) {
        return new LoggerChannel(id, name, units,
                LoggerChannelKind.PARAMETER, true);
    }

    private static LiveDataSample sample(String id, double value, long time) {
        return new LiveDataSample(id, id, value, Double.toString(value),
                id.equals("speed") ? "mph" : "rpm", time);
    }
}
