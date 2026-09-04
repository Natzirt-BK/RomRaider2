/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider2.javafx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.romraider.logger.api.LiveDataSample;
import com.romraider.logger.api.LoggerChannel;
import com.romraider.logger.api.LoggerChannelKind;

class FxDynoPaneTest {
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
