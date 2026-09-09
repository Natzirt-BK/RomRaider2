/*
 * RomRaider Open-Source Tuning, Logging and Reflashing
 * Copyright (C) 2006-2021 RomRaider.com
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, write to the Free Software Foundation, Inc.,
 * 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 */

package com.romraider.portable.logger.dimemod;

import com.romraider.portable.logger.definition.*;
import java.nio.ByteBuffer;
import java.util.*;

/**
 * Portable adaptation of desktop DmInit's version 2.0–2.3 metadata and channel
 * conversions. No transport, RAM-tuning, error clearing or write capability.
 * Keep field order and channel expressions in parity with the desktop parser.
 */
public final class PortableDimeModMetadata {
    private final byte[] dmInitBytes;

    private final boolean isDmInitReady;
    private int failsafeReqTrqLimitAddress;
    private int failsafeStageAddress;
    private boolean isFailsafeEnabled;
    private boolean isRamTuneEnabled;
    private boolean isCorrectionsByGearsEnabled;
    private boolean isCelFlashEnabled;
    private boolean isKnockLightEnabled;
    private boolean isKsByCylsEnabled;
    private boolean isMapSwitchEnabled;
    private boolean isSparkCutEnabled;
    private boolean isSpeedDensityEnabled;
    private  boolean isAlsEnabled;
    private boolean isCanSenderEnabled;
    private boolean isVinLockEnabled;
    private boolean isPwmControlEnabled;
    private boolean isValetModeEnabled;
    private int ffsTriggerVoltageAddress;
    private int extFailsafeVoltageAddress;
    private int extMapSwitchVoltageAddress;
    private int valetCurrentCodeAddress;
    private int diffPressureCompensationAddress;
    private int targetStoichAddress;
    private int stoichCompensationAddress;
    private int convertedAfrAddress;
    private int tipInMultiplierAddress;
    private int crankingMultiplierAddress;
    private int sdAtmPressAddress;
    private int failsafeStateAddress;
    private int failsafeMemorizedStateAddress;
    private int ramTuneSignatureAddress;
    private int ramTuneLutSize;
    private int celOverrideStateAddress;
    private int knockSumCyl1Address;
    private int knockSumCyl3Address;
    private int knockSumCyl2Address;
    private int knockSumCyl4Address;
    private int msNumberOfSets;
    private int msCurrentSetNumberAddress;
    private int ffsTriggerStateAddress;
    private int extFailsafeStateAddress;
    private int flexFuelBoostSetBlendAddress;
    private int flexFuelFuelingSetBlendAddress;
    private int flexFuelIgnitionSetBlendAddress;
    private int flexFuelOtherSetBlendAddress;
    private int flexFuelInjFlowValueAddress;
    private int sdPortTempAddress;
    private int sdIatCompensationAddress;
    private int sdTipInCompensationAddress;
    private int sdAtmPressCompensationAddress;
    private int sdBlendingRatioAddress;
    private int sdBaseVeAddress;
    private int sdFinalVeAddress;
    private int alphaNIatCompensationAddress;
    private int alphaNAtmPressCompensationAddress;
    private int alphaNBaseMassAirflowAddress;
    private int alphaNFinalMassAirflowAddress;
    private int sensorMassAirflowAddress;
    private int pwmControlTargetDutyAddress;
    private int alsCounterCombustionCycles;
    private int alsCounterFuelCutCycles;
    private int alsCounterSparkCutCycles;
    private int alsCutMode;
    private int alsCutLevel;
    private int alsCutValue;
    private int currentErrorCodesAddress;
    private int memorizedErrorCodesAddress;
    private int activeFeaturesAddress;
    private int activeInputsAddress;
    private int afrAddress;
    private int egtAddress;
    private int fuelPressAddress;
    private int fuelDiffPressAddress;
    private int backPressAddress;
    private int oilTempAddress;
    private int oilPressAddress;
    private int ethanolContentAddress;
    private int afrVoltageAddress;
    private int egtVoltageAddress;
    private int fuelPressVoltageAddress;
    private int backPressVoltageAddress;
    private int oilTempVoltageAddress;
    private int oilPressVoltageAddress;
    private int ethanolContentVoltageAddress;
    private int majorVer;
    private int minorVer;
    private int buildNum;
    private int[] runtimeCurrentErrors;
    private int[] runtimeMemErrors;
    private int runtimeActiveFeatures;
    private int runtimeActiveInputs;
    private List<PortableLoggerParameter> params = new ArrayList<>();

    public PortableDimeModMetadata(byte[] dmInitBytes) {
        if (dmInitBytes == null || dmInitBytes.length < 4 || dmInitBytes.length > 0xFFFF) {
            throw new IllegalStateException("DimeMod metadata requires 4 to 65535 bytes");
        }
        this.dmInitBytes = dmInitBytes.clone();
        MetadataReader buf = new MetadataReader(this.dmInitBytes);
        majorVer = buf.get() & 0xFF;
        minorVer = buf.get() & 0xFF;
        buildNum = buf.getShort() & 0xFFFF;
        if (majorVer > 2 || minorVer > 3) {
            isDmInitReady = false; // unsupported version
        } else if (majorVer == 2) {
            buf.section("HEADER");
            int ramSize = buf.getInt();
            byte featuresConfig0 = buf.get();
            byte featuresConfig1 = buf.get();
            byte featuresConfig2 = buf.get();
            byte featuresConfig3 = buf.get();

            isRamTuneEnabled = (featuresConfig0 & 0x80) != 0;
            isFailsafeEnabled = (featuresConfig0 & 0x40) != 0;
            isCorrectionsByGearsEnabled = (featuresConfig0 & 0x20) != 0;
            isCelFlashEnabled = (featuresConfig0 & 0x10) != 0;
            isKnockLightEnabled = (featuresConfig0 & 0x08) != 0;
            isKsByCylsEnabled = (featuresConfig0 & 0x04) != 0;
            isMapSwitchEnabled = (featuresConfig0 & 0x02) != 0;
            isSparkCutEnabled = (featuresConfig0 & 0x01) != 0;
            isSpeedDensityEnabled = (featuresConfig1 & 0x80) != 0;
            isAlsEnabled = (featuresConfig1 & 0x40) != 0;
            isCanSenderEnabled = (featuresConfig1 & 0x20) != 0;
            isVinLockEnabled = (featuresConfig1 & 0x10) != 0;
            isPwmControlEnabled = (featuresConfig1 & 0x08) != 0;
            isValetModeEnabled = (featuresConfig1 & 0x04) != 0;

            // INPUTS_CONFIG
            buf.section("INPUTS_CONFIG");
            int signature = buf.getInt();
            if (signature != 0xDEAD0001) {
                throw new IllegalStateException("DimeMod params reading failure at INPUTS_CONFIG");
            }
            currentErrorCodesAddress = buf.getInt();
            memorizedErrorCodesAddress = buf.getInt();
            activeFeaturesAddress = buf.getInt();
            activeInputsAddress = buf.getInt();
            afrAddress = buf.getChannelAddress(4, "afrAddress");
            egtAddress = buf.getChannelAddress(4, "egtAddress");
            fuelPressAddress = buf.getChannelAddress(4, "fuelPressAddress");
            fuelDiffPressAddress = buf.getChannelAddress(4, "fuelDiffPressAddress");
            backPressAddress = buf.getChannelAddress(4, "backPressAddress");
            if (minorVer >= 3) {
                oilTempAddress = buf.getChannelAddress(4, "oilTempAddress");
                oilPressAddress = buf.getChannelAddress(4, "oilPressAddress");
            }
            ethanolContentAddress = buf.getChannelAddress(4, "ethanolContentAddress");
            ffsTriggerStateAddress = buf.getInt();
            extFailsafeStateAddress = buf.getInt();

            afrVoltageAddress = buf.getChannelAddress(4, "afrVoltageAddress");
            egtVoltageAddress = buf.getChannelAddress(4, "egtVoltageAddress");
            fuelPressVoltageAddress = buf.getChannelAddress(4, "fuelPressVoltageAddress");
            backPressVoltageAddress = buf.getChannelAddress(4, "backPressVoltageAddress");
            if (minorVer >= 3) {
                oilTempVoltageAddress = buf.getChannelAddress(4, "oilTempVoltageAddress");
                oilPressVoltageAddress = buf.getChannelAddress(4, "oilPressVoltageAddress");
            }
            ethanolContentVoltageAddress = buf.getChannelAddress(4, "ethanolContentVoltageAddress");
            ffsTriggerVoltageAddress = buf.getChannelAddress(4, "ffsTriggerVoltageAddress");
            extFailsafeVoltageAddress = buf.getChannelAddress(4, "extFailsafeVoltageAddress");
            extMapSwitchVoltageAddress = buf.getChannelAddress(4, "extMapSwitchVoltageAddress");

            if (isRamTuneEnabled) {
                buf.section("RAM_TUNE");
                signature = buf.getInt();
                if (signature != 0xDEAD0020) {
                    throw new IllegalStateException("DimeMod params reading failure at RAM_TUNE");
                }
                ramTuneSignatureAddress = buf.getInt();
                ramTuneLutSize = buf.getInt();
            }

            if (isFailsafeEnabled) {
                buf.section("FAILSAFE");
                signature = buf.getInt();
                if (signature != 0xDEAD0002) {
                    throw new IllegalStateException("DimeMod params reading failure at FAILSAFE");
                }
                failsafeStateAddress = buf.getInt();
                failsafeMemorizedStateAddress = buf.getInt();
                failsafeStageAddress = buf.getInt();
                failsafeReqTrqLimitAddress = buf.getChannelAddress(4, "failsafeReqTrqLimitAddress");
            }

            if (isCelFlashEnabled) {
                buf.section("CEL_FLASH");
                signature = buf.getInt();
                if (signature != 0xDEAD0004) {
                    throw new IllegalStateException("DimeMod params reading failure at CEL_FLASH");
                }
                celOverrideStateAddress = buf.getInt();
            }

            if (isKsByCylsEnabled) {
                buf.section("KS_BY_CYLS");
                signature = buf.getInt();
                if (signature != 0xDEAD0006) {
                    throw new IllegalStateException("DimeMod params reading failure at KS_BY_CYLS");
                }
                knockSumCyl1Address = buf.getChannelAddress(4, "knockSumCyl1Address");
                knockSumCyl3Address = knockSumCyl1Address + 1;
                knockSumCyl2Address = knockSumCyl1Address + 2;
                knockSumCyl4Address = knockSumCyl1Address + 3;
            }

            if (isMapSwitchEnabled) {
                buf.section("MAP_SWITCH");
                signature = buf.getInt();
                if (signature != 0xDEAD0007) {
                    throw new IllegalStateException("DimeMod params reading failure at MAP_SWITCH");
                }
                msNumberOfSets = buf.getInt();
                msCurrentSetNumberAddress = buf.getInt();
                if (majorVer == 2 && minorVer < 3) {
                    failsafeStateAddress = buf.getInt();
                    failsafeMemorizedStateAddress = buf.getInt();
                }
                flexFuelBoostSetBlendAddress = buf.getChannelAddress(4, "flexFuelBoostSetBlendAddress");
                flexFuelFuelingSetBlendAddress = buf.getChannelAddress(4, "flexFuelFuelingSetBlendAddress");
                flexFuelIgnitionSetBlendAddress = buf.getChannelAddress(4, "flexFuelIgnitionSetBlendAddress");
                flexFuelOtherSetBlendAddress = buf.getChannelAddress(4, "flexFuelOtherSetBlendAddress");
                flexFuelInjFlowValueAddress = buf.getChannelAddress(4, "flexFuelInjFlowValueAddress");
                if (minorVer > 0 || buildNum > 1) {
                    diffPressureCompensationAddress = buf.getChannelAddress(4, "diffPressureCompensationAddress");
                    targetStoichAddress = buf.getChannelAddress(4, "targetStoichAddress");
                    stoichCompensationAddress = buf.getChannelAddress(4, "stoichCompensationAddress");
                    convertedAfrAddress = buf.getChannelAddress(4, "convertedAfrAddress");
                    if ((minorVer == 1 && buildNum >= 300) ||
                            (minorVer == 3 && buildNum >= 100) ||
                            (minorVer > 3)) {
                        tipInMultiplierAddress = buf.getChannelAddress(4, "tipInMultiplierAddress");
                        crankingMultiplierAddress = buf.getChannelAddress(4, "crankingMultiplierAddress");
                    }
                }
            }

            if (isSpeedDensityEnabled) {
                buf.section("SPEED_DENSITY");
                signature = buf.getInt();
                if (signature != 0xDEAD0009) {
                    throw new IllegalStateException("DimeMod params reading failure at SPEED_DENSITY");
                }
                sdPortTempAddress = buf.getChannelAddress(4, "sdPortTempAddress");
                sdIatCompensationAddress = buf.getChannelAddress(4, "sdIatCompensationAddress");
                sdTipInCompensationAddress = buf.getChannelAddress(4, "sdTipInCompensationAddress");
                sdAtmPressCompensationAddress = buf.getChannelAddress(4, "sdAtmPressCompensationAddress");
                sdBlendingRatioAddress = buf.getChannelAddress(4, "sdBlendingRatioAddress");
                sdBaseVeAddress = buf.getChannelAddress(4, "sdBaseVeAddress");
                sdFinalVeAddress = buf.getChannelAddress(4, "sdFinalVeAddress");
                alphaNIatCompensationAddress = buf.getChannelAddress(4, "alphaNIatCompensationAddress");
                alphaNAtmPressCompensationAddress = buf.getChannelAddress(4, "alphaNAtmPressCompensationAddress");
                alphaNBaseMassAirflowAddress = buf.getChannelAddress(4, "alphaNBaseMassAirflowAddress");
                alphaNFinalMassAirflowAddress = buf.getChannelAddress(4, "alphaNFinalMassAirflowAddress");
                sensorMassAirflowAddress = buf.getChannelAddress(4, "sensorMassAirflowAddress");
                if (minorVer > 0 || buildNum > 0) {
                    sdAtmPressAddress = buf.getChannelAddress(4, "sdAtmPressAddress");
                }
                int engineLoadSmoothingAAddress = buf.getInt();
                int engineLoadSmoothingBAddress = buf.getInt();
                int engineLoadSmoothingCAddress = buf.getInt();
                int engineLoadSmoothingDAddress = buf.getInt();
            }

            if (isVinLockEnabled) {
                buf.section("VIN_LOCK");
                signature = buf.getInt();
                if (signature != 0xDEAD000C) {
                    throw new IllegalStateException("DimeMod params reading failure at VIN_LOCK");
                }
                buf.getInt();
                buf.getInt();
                buf.getInt();
                buf.getInt();
                buf.getInt();
                buf.getInt();
                buf.getInt();
                buf.getInt();
                buf.getInt();
                buf.getInt();
                buf.getInt();
            }

            if (isPwmControlEnabled) {
                buf.section("PWM_CONTROL");
                signature = buf.getInt();
                if (signature != 0xDEAD000D) {
                    throw new IllegalStateException("DimeMod params reading failure at PWM_CONTROL");
                }
                pwmControlTargetDutyAddress = buf.getInt();
            }

            if (isAlsEnabled) {
                buf.section("ALS");
                signature = buf.getInt();
                if (signature != 0xDEAD000A) {
                    throw new IllegalStateException("DimeMod params reading failure at ALS");
                }
                if (minorVer > 3 || ((minorVer == 3) && (buildNum > 0))) {
                    alsCounterCombustionCycles = buf.getInt();
                    alsCounterFuelCutCycles = buf.getInt();
                    alsCounterSparkCutCycles = buf.getInt();
                    alsCutMode = buf.getInt();
                    alsCutLevel = buf.getInt();
                    alsCutValue = buf.getInt();
                } else {
                    buf.getInt();
                }
            }

            if (isValetModeEnabled) {
                buf.section("VALET_MODE");
                signature = buf.getInt();
                if (signature != 0xDEAD000E) {
                    throw new IllegalStateException("DimeMod params reading failure at VALET_MODE");
                }
                valetCurrentCodeAddress = buf.getChannelAddress(2, "valetCurrentCodeAddress");
            }
            // Preserve the existing low-24-bit wire mapping (including SH
            // upper-byte aliases), but never let runtime reads or the derived
            // debug channels wrap into a different wire-address region.
            requireWireSpan(currentErrorCodesAddress, minorVer == 0 ? 13 : 38, "current errors/debug");
            requireWireSpan(memorizedErrorCodesAddress, minorVer == 0 ? 4 : 16, "memorized errors");
            requireWireSpan(activeFeaturesAddress, 4, "active features");
            requireWireSpan(activeInputsAddress, 2, "active inputs");
            isDmInitReady = true;
        } else if (majorVer == 1) {
            isDmInitReady = false; // unsupported version
        } else {
            isDmInitReady = false; // unsupported version
        }
    }

    private static void requireWireSpan(int address, int length, String field) {
        if ((long) (address & 0xFFFFFF) + length > 0x1000000L) {
            throw new IllegalStateException("DimeMod metadata " + field + " span wraps the 24-bit wire address space");
        }
    }

    private static final class MetadataReader {
        private final ByteBuffer bytes;
        private String section = "VERSION";

        MetadataReader(byte[] source) { bytes = ByteBuffer.wrap(source); }
        void section(String name) { section = name; }
        byte get() { require(1); return bytes.get(); }
        short getShort() { require(2); return bytes.getShort(); }
        int getInt() { require(4); return bytes.getInt(); }

        // Validate the entire published read before runtime flags can enable it.
        // The caller supplies the channel width, not the four-byte metadata field
        // width. Unpublished VIN/RAM-tune data does not acquire guessed semantics.
        int getChannelAddress(int length, String field) {
            int address = getInt();
            requireWireSpan(address, length, section + "/" + field);
            return address; // Preserve the established upper-byte address aliases.
        }

        private void require(int length) {
            if (bytes.remaining() < length) {
                throw new IllegalStateException("Truncated DimeMod metadata in " + section
                        + " at byte " + bytes.position() + ": need " + length
                        + ", remaining " + bytes.remaining());
            }
        }
    }

    private static int[] copyErrors(int[] errors) {
        return errors == null ? null : errors.clone();
    }

    public boolean updateRuntimeData(int activeFeatures, int activeInputs, int[] currentErrors, int[] memErrors) {
        boolean changed = false;
        if (activeFeatures != this.runtimeActiveFeatures || activeInputs != this.runtimeActiveInputs) {
            changed = true;
        }

        if (isDmInitReady) {
            this.runtimeActiveFeatures = activeFeatures;
            this.runtimeActiveInputs = activeInputs;
            this.runtimeCurrentErrors = copyErrors(currentErrors);
            this.runtimeMemErrors = copyErrors(memErrors);

            boolean isAfrEnabled = (activeInputs & 0x01) != 0;
            boolean isEgtEnabled = (activeInputs & 0x02) != 0;
            boolean isFuelPressureEnabled = (activeInputs & 0x04) != 0;
            boolean isBackPressureEnabled = (activeInputs & 0x08) != 0;
            boolean isFlexFuelEnabled = (activeInputs & 0x10) != 0;
            boolean isFfsExternalTriggerEnabled = (activeInputs & 0x20) != 0;
            boolean isMapSwitchExternalTriggerEnabled = (activeInputs & 0x40) != 0;
            boolean isFailsafeExternalTriggerEnabled = (activeInputs & 0x80) != 0;
            boolean isOilTempEnabled = minorVer >= 3 && (activeInputs & 0x100) != 0;
            boolean isOilPressEnabled = minorVer >= 3 && (activeInputs & 0x200) != 0;

            boolean isRamTuneEnabled = (runtimeActiveFeatures & 0x80000000) != 0;
            boolean isCruiseButtonImmediateHacksEnabled = (runtimeActiveFeatures & 0x40000000) != 0;
            boolean isCorrectionsByGearsEnabled = (runtimeActiveFeatures & 0x20000000) != 0;
            boolean isCelFlashEnabled = (runtimeActiveFeatures & 0x10000000) != 0;
            boolean isKnockLightEnabled = (runtimeActiveFeatures & 0x08000000) != 0;
            // Runtime flags cannot supply addresses from an absent discovery block.
            boolean isKsByCylsEnabled = this.isKsByCylsEnabled && (runtimeActiveFeatures & 0x04000000) != 0;
            boolean isMapSwitchEnabled = this.isMapSwitchEnabled && (runtimeActiveFeatures & 0x02000000) != 0;
            boolean isSparkCutEnabled = (runtimeActiveFeatures & 0x01000000) != 0;
            boolean isSpeedDensityEnabled = this.isSpeedDensityEnabled && (runtimeActiveFeatures & 0x800000) != 0;
            boolean isAlsEnabled = this.isAlsEnabled && (runtimeActiveFeatures & 0x400000) != 0;
            boolean isCanSenderEnabled = (runtimeActiveFeatures & 0x200000) != 0;
            boolean isVinLockEnabled = (runtimeActiveFeatures & 0x100000) != 0;
            boolean isPwmControlEnabled = (runtimeActiveFeatures & 0x080000) != 0;
            boolean isValetModeEnabled = this.isValetModeEnabled && (runtimeActiveFeatures & 0x040000) != 0;

            params.clear();
            if (minorVer > 0) {
                params.add(getUInt8Parameter("DM666", "DimeMod: Fatal Error", "DM666", currentErrorCodesAddress + 16 + 16 + 4, "n", "x"));
                params.add(getUInt8Parameter("DM667", "DimeMod: Timer: Main Loop (debug)", "DM667", currentErrorCodesAddress + 16 + 16 + 4 + 1, "n", "x/16"));
            } else {
                params.add(getUInt8Parameter("DM667", "DimeMod: Timer: Main Loop (debug)", "DM667", currentErrorCodesAddress + 12, "n", "x/16"));
            }
            params.add(getUInt32Parameter("DM900", "DimeMod: Errors present (current)", "Errors present if not zero", currentErrorCodesAddress, "n", "x!=0"));
            params.add(getUInt32Parameter("DM901", "DimeMod: Errors present (memorized)", "Errors present if not zero", memorizedErrorCodesAddress, "n", "x!=0"));

            if (isAfrEnabled) {
                params.add(getFloatParameter("DM902", "DimeMod: AFR", "Air-to-Fuel ratio", afrAddress, "AFR", "x", 8f, 20f, 2f));
                params.add(getFloatParameter("DM903", "DimeMod: AFR Voltage", "Voltage", afrVoltageAddress, "V", "x", 0f, 5f, 0.5f));
            }
            if (isEgtEnabled) {
                params.add(getFloatTempParameter("DM904", "DimeMod: EGT", "Exhaust Gas Temp", egtAddress));
                params.add(getFloatParameter("DM905", "DimeMod: EGT Voltage", "Voltage", egtVoltageAddress, "V", "x", 0f, 5f, 0.5f));
            }
            if (isFuelPressureEnabled) {
                params.add(getFloatMfPressureParameter("DM906", "DimeMod: Fuel Pressure", "Fuel Pressure", fuelPressAddress));
                params.add(getFloatParameter("DM907", "DimeMod: Fuel Pressure Voltage", "Voltage", fuelPressVoltageAddress, "V", "x", 0f, 5f, 0.5f));
                params.add(getFloatMfPressureParameter("DM908", "DimeMod: Fuel Differential Pressure", "Fuel Differential Pressure", fuelDiffPressAddress));
            }
            if (isBackPressureEnabled) {
                params.add(getFloatMfPressureParameter("DM909", "DimeMod: Backpressure", "BackPressure", backPressAddress));
                params.add(getFloatParameter("DM910", "DimeMod: Backpressure Voltage", "Voltage", backPressVoltageAddress, "V", "x", 0f, 5f, 0.5f));
            }
            if (isOilTempEnabled) {
                params.add(getFloatTempParameter("DM920", "DimeMod: Oil Temp", "Oil Temperature", oilTempAddress));
                params.add(getFloatParameter("DM921", "DimeMod: Oil Temp Voltage", "Voltage", oilTempVoltageAddress, "V", "x", 0f, 5f, 0.5f));
            }
            if (isOilPressEnabled) {
                params.add(getFloatMfPressureParameter("DM922", "DimeMod: Oil Press", "Oil Pressure", oilPressAddress));
                params.add(getFloatParameter("DM923", "DimeMod: Oil Press Voltage", "Voltage", oilPressVoltageAddress, "V", "x", 0f, 5f, 0.5f));
            }
            if (isFlexFuelEnabled) {
                params.add(getFloatParameter("DM911", "DimeMod: FlexFuel Ethanol Content", "Ethanol content", ethanolContentAddress, "%", "x", 0f, 100f, 5f));
                params.add(getFloatParameter("DM912", "DimeMod: FlexFuel Ethanol Content Voltage", "Voltage", ethanolContentVoltageAddress, "V", "x", 0f, 5f, 0.5f));
            }
            if (isFailsafeExternalTriggerEnabled) {
                params.add(getUInt8Parameter("DM913", "DimeMod: MapSwitch Failsafe Mode External Trigger", "Failsafe Trigger State", extFailsafeStateAddress, "state", "x"));
//            params.add(getUInt8Parameter("DM966", "DimeMod: MapSwitch Failsafe Mode External Trigger Timer", "Failsafe Trigger State", extFailsafeStateAddress + 2, "ticks", "x"));
                params.add(getFloatParameter("DM914", "DimeMod: MapSwitch Failsafe Mode External Trigger Voltage", "Voltage", extFailsafeVoltageAddress, "V", "x", 0f, 5f, 0.5f));
            }
            if (isMapSwitchExternalTriggerEnabled) {
                params.add(getFloatParameter("DM915", "DimeMod: MapSwitch External Trigger Voltage", "Voltage", extMapSwitchVoltageAddress, "V", "x", 0f, 5f, 0.5f));
            }
            if (isFfsExternalTriggerEnabled) {
                params.add(getUInt8Parameter("DM916", "DimeMod: FFS External Trigger", "FFS Trigger State", ffsTriggerStateAddress, "state", "x"));
                params.add(getFloatParameter("DM917", "DimeMod: FFS External Trigger Voltage", "Voltage", ffsTriggerVoltageAddress, "V", "x", 0f, 5f, 0.5f));
            }

            if (isKsByCylsEnabled) {
                params.add(
                        getUInt8Parameter("DM001", "DimeMod: Knock Sum Cylinder 1", "Knock count Cyl 1", knockSumCyl1Address, "n", "x"));
                params.add(
                        getUInt8Parameter("DM002", "DimeMod: Knock Sum Cylinder 2", "Knock count Cyl 2", knockSumCyl2Address, "n", "x"));
                params.add(
                        getUInt8Parameter("DM003", "DimeMod: Knock Sum Cylinder 3", "Knock count Cyl 3", knockSumCyl3Address, "n", "x"));
                params.add(
                        getUInt8Parameter("DM004", "DimeMod: Knock Sum Cylinder 4", "Knock count Cyl 4", knockSumCyl4Address, "n", "x"));
            }
            if (isFailsafeEnabled) {
                params.add(getUInt8Parameter("DM201", "DimeMod: Failsafe State", "Current MapSwitch failsafe state", failsafeStateAddress, "#", "x"));
                params.add(getUInt8Parameter("DM202", "DimeMod: Failsafe Memorized States", "Memorized MapSwitch failsafe states", failsafeMemorizedStateAddress, "#", "x"));
                params.add(getUInt8Parameter("DM203", "DimeMod: Failsafe Current Stage", "", failsafeStageAddress, "#", "x"));
                params.add(getFloatParameter("DM204", "DimeMod: Failsafe Requested Torque Limit", "", failsafeReqTrqLimitAddress, "Nm", "x", 0, 700, 10));
            }
            if (isMapSwitchEnabled) {
                params.add(getUInt8Parameter("DM010", "DimeMod: MapSwitch Selected Set", "Current MapSwitch set num", msCurrentSetNumberAddress, "set", "x+1"));
                if (majorVer == 2 && minorVer < 3) {
                    params.add(getUInt8Parameter("DM011", "DimeMod: Failsafe State", "Current MapSwitch failsafe state", failsafeStateAddress, "#", "x"));
                    params.add(getUInt8Parameter("DM012", "DimeMod: Failsafe Memorized States", "Memorized MapSwitch failsafe states", failsafeMemorizedStateAddress, "#", "x"));
                }
                if (isFlexFuelEnabled) {
                    params.add(getFloatParameter("DM013", "DimeMod: Flex Fuel blend value (Boost)", "Blend Value (Boost)", flexFuelBoostSetBlendAddress, "set", 0, 4, 0.1f));
                    params.add(getFloatParameter("DM014", "DimeMod: Flex Fuel blend value (Fuel)", "Blend Value (Fuel)", flexFuelFuelingSetBlendAddress, "set", 0, 4, 0.1f));
                    params.add(getFloatParameter("DM015", "DimeMod: Flex Fuel blend value (Ignition)", "Blend Value (Ignition)", flexFuelIgnitionSetBlendAddress, "set", 0, 4, 0.1f));
                    params.add(getFloatParameter("DM016", "DimeMod: Flex Fuel blend value (Other)", "Blend Value (Other)", flexFuelOtherSetBlendAddress, "set", 0, 4, 0.1f));
                }
            }
            // These addresses live in MAP_SWITCH metadata, even when switching
            // is not currently active. Do not expose default zero addresses.
            if (this.isMapSwitchEnabled)
                params.add(getFloatParameter("DM017", "DimeMod: Injector Flow value", "Injector Flow Value", flexFuelInjFlowValueAddress, "cc/min", "2707090/x", 0, 4, 0.1f));
            if (this.isMapSwitchEnabled && (minorVer > 0 || buildNum > 1)) {
                if (isFuelPressureEnabled) {
                    params.add(getFloatParameter("DM018", "DimeMod: IPW Diff Pressure Compensation", "IPW compensation in %", diffPressureCompensationAddress, "%", "(x-1)*100", -100, 100, 10f));
                }
                params.add(getFloatParameter("DM01A", "DimeMod: Target Stoichiometric AFR", "Target Stoich AFR", targetStoichAddress, "AFR", "x", 8, 18, 1f));
                params.add(getFloatParameter("DM019", "DimeMod: IPW Stoich Compensation", "IPW compensation in %", stoichCompensationAddress, "%", "(x-1)*100", -100, 100, 10f));
                if (isAfrEnabled) {
                    params.add(getFloatParameter("DM01B", "DimeMod: AFR Stoich Converted", "AFR with stoich compensation applied", convertedAfrAddress, "AFR", "x", 8, 18, 10f));
                }
                if ((minorVer == 1 && buildNum >= 300) ||
                        (minorVer == 3 && buildNum >= 100) ||
                        (minorVer > 3)) {
                    params.add(getFloatParameter("DM01C", "DimeMod: Tip-In IPW Total Multiplier", "", tipInMultiplierAddress, "n", "x", 0, 5, 0.5f));
                    params.add(getFloatParameter("DM01D", "DimeMod: Cranking IPW Total Multiplier", "", crankingMultiplierAddress, "n", "x", 0, 5, 0.5f));
                }
            }
            if (isSpeedDensityEnabled) {
                params.add(getFloatTempParameter("DM020", "DimeMod: SD Port Temp", "Estimated intake port temp", sdPortTempAddress));
                params.add(getFloatParameter("DM021", "DimeMod: SD IAT Compensation", "VE IAT Compensation", sdIatCompensationAddress, "%", "(x-1)*100", -50, 150, 25));
                params.add(getFloatParameter("DM022", "DimeMod: SD Tip-In Compensation", "VE Tip-In Compensation", sdTipInCompensationAddress, "%", "(x-1)*100", -50, 200, 25));
                params.add(getFloatParameter("DM023", "DimeMod: SD Atm. Press. Compensation", "VE Atmospheric Pressure Compensation", sdAtmPressCompensationAddress, "%", "(x-1)*100", 0, 300, 25));
                params.add(getFloatParameter("DM024", "DimeMod: SD Blending Ratio", "SD Blending Ratio", sdBlendingRatioAddress, "%", "x*100", 0, 100, 10));
                params.add(getFloatParameter("DM025", "DimeMod: SD VE Base", "Base VE (no compensations applied)", sdBaseVeAddress, "%", "x", 0, 100, 10));
                params.add(getFloatParameter("DM026", "DimeMod: SD VE Final", "Final VE (all compensations applied)", sdFinalVeAddress, "%", "x", 0, 100, 10));

                params.add(getFloatParameter("DM027", "DimeMod: AlphaN IAT Compensation", "AnphaN IAT Compensation", alphaNIatCompensationAddress, "%", "(x-1)*100", -50, 200, 25));
                params.add(getFloatParameter("DM028", "DimeMod: AlphaN Atm. Press. Compensation", "VE Atmospheric Pressure Compensation", alphaNAtmPressCompensationAddress, "%", "(x-1)*100", -50, 200, 25));
                params.add(getFloatParameter("DM029", "DimeMod: AlphaN Mass Airflow Base", "Base AlphaN Mass Airflow (no compensations applied)", alphaNBaseMassAirflowAddress, "g/s", "x", 0, 300, 50));
                params.add(getFloatParameter("DM02A", "DimeMod: AlphaN Mass Airflow Final", "Final AlphaN Mass Airflow (all compensations applied)", alphaNFinalMassAirflowAddress, "g/s", "x", 0, 300, 50));
                params.add(getFloatParameter("DM02B", "DimeMod: Mass Airflow (sensor-based))", "Mass Airflow calculated directly from MAF sensor", sensorMassAirflowAddress, "g/s", "x", 0, 500, 50));
                if (minorVer > 0 || buildNum > 0) {
                    params.add(getFloatMfPressureParameter("DM02C", "DimeMod: SD Atmospheric Pressure", "Atmospheric Pressure used in SD calculations", sdAtmPressAddress));
                }
            }
            if (minorVer > 3 || (minorVer == 3 && buildNum > 0)) {
                if (isAlsEnabled) {
                    params.add(getUInt8Parameter("DMA00", "DimeMod: ALS: Counter of total combustion cycles", "", alsCounterCombustionCycles, "#", "x"));
                    params.add(getUInt8Parameter("DMA01", "DimeMod: ALS: Counter of Fuel Cut cycles", "", alsCounterFuelCutCycles, "#", "x"));
                    params.add(getUInt8Parameter("DMA02", "DimeMod: ALS: Counter of Spark Cut cycles", "", alsCounterSparkCutCycles, "#", "x"));
                    params.add(getUInt8Parameter("DMA03", "DimeMod: ALS: Cut Mode", "", alsCutMode, "#", "x"));
                    params.add(getUInt8Parameter("DMA04", "DimeMod: ALS: Cut Level", "", alsCutLevel, "#", "x"));
                    params.add(getUInt8Parameter("DMA05", "DimeMod: ALS: Cut Randomizer Value", "", alsCutValue, "#", "x"));
                }
            }
            if (isValetModeEnabled) {
                params.add(getUInt16Parameter("DM030", "DimeMod: Valet Mode: Current entered Code", "Curently entered code", valetCurrentCodeAddress, "#", "x"));
            }
        }
        return changed;
    }


    public boolean supported() { return isDmInitReady; }
    public int activeInputsAddress() { return activeInputsAddress & 0xFFFFFF; }
    public int activeFeaturesAddress() { return activeFeaturesAddress & 0xFFFFFF; }
    public String version() { return majorVer + "." + minorVer + " build " + buildNum; }
    public List<PortableLoggerParameter> parameters() {
        return Collections.unmodifiableList(new ArrayList<>(params));
    }

    private PortableLoggerParameter parameter(String id, String name, String description,
            int address, int length, PortableLoggerConversion... conversions) {
        requireWireSpan(address, length, id);
        return new PortableLoggerParameter(id, name, description, 1,
                Collections.singletonMap(PortableLoggerParameter.ALL_ECUS,
                    Collections.singletonList(new PortableLoggerAddress(address & 0xFFFFFF, length))),
                Collections.emptyList(), Arrays.asList(conversions));
    }
    private PortableLoggerConversion conversion(String units, String expr, String format, String type) {
        return new PortableLoggerConversion(units, expr, format, type, "big");
    }
    private PortableLoggerParameter getUInt8Parameter(String id, String name, String desc, int addr, String units, String expr) {
        return parameter(id, name, desc, addr, 1, conversion(units, expr, "0", "uint8"));
    }
    private PortableLoggerParameter getUInt16Parameter(String id, String name, String desc, int addr, String units, String expr) {
        return parameter(id, name, desc, addr, 2, conversion(units, expr, "0", "uint16"));
    }
    private PortableLoggerParameter getUInt32Parameter(String id, String name, String desc, int addr, String units, String expr) {
        return parameter(id, name, desc, addr, 4, conversion(units, expr, "0", "uint32"));
    }
    private PortableLoggerParameter getFloatParameter(String id, String name, String desc, int addr, String units, float min, float max, float step) {
        return getFloatParameter(id, name, desc, addr, units, "x", min, max, step);
    }
    private PortableLoggerParameter getFloatParameter(String id, String name, String desc, int addr, String units, String expr, float min, float max, float step) {
        return parameter(id, name, desc, addr, 4, conversion(units, expr, "0.00", "float"));
    }
    private PortableLoggerParameter getFloatTempParameter(String id, String name, String desc, int addr) {
        return parameter(id, name, desc, addr, 4,
                conversion("Degrees C", "x", "0.0", "float"),
                conversion("Degrees F", "x*1.8+32", "0.0", "float"));
    }
    private PortableLoggerParameter getFloatMfPressureParameter(String id, String name, String desc, int addr) {
        return parameter(id, name, desc, addr, 4,
                conversion("bar", "x*.001333333333", "0.000", "float"),
                conversion("psi", "x*.0193384", "0.0", "float"));
    }
}

