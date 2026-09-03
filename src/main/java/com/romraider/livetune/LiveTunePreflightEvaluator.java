/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.livetune;

import java.util.ArrayList;
import java.util.List;

import com.romraider.io.transport.EcuIdentity;
import com.romraider.platform.DimeModState;
import com.romraider.platform.VehicleModule;
import com.romraider.platform.VehiclePlatform;

/** Evaluates every required gate without opening or writing a transport. */
public final class LiveTunePreflightEvaluator {
    private LiveTunePreflightEvaluator() {
    }

    public static LiveTunePreflight evaluate(LiveTunePlan plan,
            LiveTuneSafetyContext context) {
        if (plan == null || context == null) {
            throw new IllegalArgumentException(
                    "Live-tune plan and safety context are required");
        }
        List<LiveTunePreflightCheck> checks =
                new ArrayList<LiveTunePreflightCheck>();
        add(checks, "platform", "Vehicle platform",
                context.getPlatform() == VehiclePlatform.SUBARU,
                "Live tuning is currently scoped to Subaru");
        add(checks, "module", "Control module",
                context.getModule() == VehicleModule.ENGINE_ECU,
                "An engine ECU connection is required");
        add(checks, "dimemod", "DimeMod runtime",
                context.getDimeModState() == DimeModState.ACTIVE,
                "DimeMod must be verified during this ECU session");
        add(checks, "definition", "Definition mapping",
                context.isDefinitionMapped(),
                "The loaded definition must map RAM Tune tables");
        add(checks, "runtime-feature", "RAM Tune feature",
                context.isRuntimeRamTuneAvailable(),
                "The connected DimeMod runtime must advertise RAM Tune");
        add(checks, "runtime-metadata", "RAM Tune metadata",
                context.isRuntimeMetadataQualified(),
                "The current session must provide a valid signature and lookup table");
        EcuIdentity connected = context.getConnectedIdentity();
        boolean identityMatches = connected != null
                && plan.getExpectedIdentity().equals(connected);
        add(checks, "identity", "ECU and ROM identity", identityMatches,
                "The connected ECU and ROM IDs must exactly match the plan");
        return new LiveTunePreflight(checks);
    }

    private static void add(List<LiveTunePreflightCheck> checks, String id,
            String label, boolean passes, String requirement) {
        checks.add(new LiveTunePreflightCheck(id, label,
                passes ? LiveTuneCheckStatus.PASS : LiveTuneCheckStatus.FAIL,
                passes ? "Verified" : requirement));
    }
}
