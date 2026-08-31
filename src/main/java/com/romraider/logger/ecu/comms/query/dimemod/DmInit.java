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

package com.romraider.logger.ecu.comms.query.dimemod;

import com.romraider.Settings;
import com.romraider.logger.ecu.definition.*;
import com.romraider.logger.ecu.ui.handler.dash.GaugeMinMax;
import com.romraider.util.HexUtil;

import java.nio.ByteBuffer;
import java.util.*;

public class DmInit {
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
    private List<EcuParameter> params = new ArrayList<>();

    public DmInit(byte[] dmInitBytes) {
        this.dmInitBytes = dmInitBytes;
        ByteBuffer buf = ByteBuffer.wrap(dmInitBytes);
        buf.position(0);
        majorVer = buf.get() & 0xFF;
        minorVer = buf.get() & 0xFF;
        buildNum = buf.getShort() & 0xFFFF;
        if (majorVer > 2) {
            isDmInitReady = false; // unsupported version
        } else if (majorVer == 2) {
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
            int signature = buf.getInt();
            if (signature != 0xDEAD0001) {
                throw new IllegalStateException("DimeMod params reading failure at INPUTS_CONFIG");
            }
            currentErrorCodesAddress = buf.getInt();
            memorizedErrorCodesAddress = buf.getInt();
            activeFeaturesAddress = buf.getInt();
            activeInputsAddress = buf.getInt();
            afrAddress = buf.getInt();
            egtAddress = buf.getInt();
            fuelPressAddress = buf.getInt();
            fuelDiffPressAddress = buf.getInt();
            backPressAddress = buf.getInt();
            if (minorVer >= 3) {
                oilTempAddress = buf.getInt();
                oilPressAddress = buf.getInt();
            }
            ethanolContentAddress = buf.getInt();
            ffsTriggerStateAddress = buf.getInt();
            extFailsafeStateAddress = buf.getInt();

            afrVoltageAddress = buf.getInt();
            egtVoltageAddress = buf.getInt();
            fuelPressVoltageAddress = buf.getInt();
            backPressVoltageAddress = buf.getInt();
            if (minorVer >= 3) {
                oilTempVoltageAddress = buf.getInt();
                oilPressVoltageAddress = buf.getInt();
            }
            ethanolContentVoltageAddress = buf.getInt();
            ffsTriggerVoltageAddress = buf.getInt();
            extFailsafeVoltageAddress = buf.getInt();
            extMapSwitchVoltageAddress = buf.getInt();

            if (isRamTuneEnabled) {
                signature = buf.getInt();
                if (signature != 0xDEAD0020) {
                    throw new IllegalStateException("DimeMod params reading failure at RAM_TUNE");
                }
                ramTuneSignatureAddress = buf.getInt();
                ramTuneLutSize = buf.getInt();
            }

            if (isFailsafeEnabled) {
                signature = buf.getInt();
                if (signature != 0xDEAD0002) {
                    throw new IllegalStateException("DimeMod params reading failure at FAILSAFE");
                }
                failsafeStateAddress = buf.getInt();
                failsafeMemorizedStateAddress = buf.getInt();
                failsafeStageAddress = buf.getInt();
                failsafeReqTrqLimitAddress = buf.getInt();
            }

            if (isCelFlashEnabled) {
                signature = buf.getInt();
                if (signature != 0xDEAD0004) {
                    throw new IllegalStateException("DimeMod params reading failure at CEL_FLASH");
                }
                celOverrideStateAddress = buf.getInt();
            }

            if (isKsByCylsEnabled) {
                signature = buf.getInt();
                if (signature != 0xDEAD0006) {
                    throw new IllegalStateException("DimeMod params reading failure at KS_BY_CYLS");
                }
                knockSumCyl1Address = buf.getInt();
                knockSumCyl3Address = knockSumCyl1Address + 1;
                knockSumCyl2Address = knockSumCyl1Address + 2;
                knockSumCyl4Address = knockSumCyl1Address + 3;
            }

            if (isMapSwitchEnabled) {
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
                flexFuelBoostSetBlendAddress = buf.getInt();
                flexFuelFuelingSetBlendAddress = buf.getInt();
                flexFuelIgnitionSetBlendAddress = buf.getInt();
                flexFuelOtherSetBlendAddress = buf.getInt();
                flexFuelInjFlowValueAddress = buf.getInt();
                if (minorVer > 0 || buildNum > 1) {
                    diffPressureCompensationAddress = buf.getInt();
                    targetStoichAddress = buf.getInt();
                    stoichCompensationAddress = buf.getInt();
                    convertedAfrAddress = buf.getInt();
                    if ((minorVer == 1 && buildNum >= 300) ||
                            (minorVer == 3 && buildNum >= 100) ||
                            (minorVer > 3)) {
                        tipInMultiplierAddress = buf.getInt();
                        crankingMultiplierAddress = buf.getInt();
                    }
                }
            }

            if (isSpeedDensityEnabled) {
                signature = buf.getInt();
                if (signature != 0xDEAD0009) {
                    throw new IllegalStateException("DimeMod params reading failure at SPEED_DENSITY");
                }
                sdPortTempAddress = buf.getInt();
                sdIatCompensationAddress = buf.getInt();
                sdTipInCompensationAddress = buf.getInt();
                sdAtmPressCompensationAddress = buf.getInt();
                sdBlendingRatioAddress = buf.getInt();
                sdBaseVeAddress = buf.getInt();
                sdFinalVeAddress = buf.getInt();
                alphaNIatCompensationAddress = buf.getInt();
                alphaNAtmPressCompensationAddress = buf.getInt();
                alphaNBaseMassAirflowAddress = buf.getInt();
                alphaNFinalMassAirflowAddress = buf.getInt();
                sensorMassAirflowAddress = buf.getInt();
                if (minorVer > 0 || buildNum > 0) {
                    sdAtmPressAddress = buf.getInt();
                }
                int engineLoadSmoothingAAddress = buf.getInt();
                int engineLoadSmoothingBAddress = buf.getInt();
                int engineLoadSmoothingCAddress = buf.getInt();
                int engineLoadSmoothingDAddress = buf.getInt();
            }

            if (isVinLockEnabled) {
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
                signature = buf.getInt();
                if (signature != 0xDEAD000D) {
                    throw new IllegalStateException("DimeMod params reading failure at PWM_CONTROL");
                }
                pwmControlTargetDutyAddress = buf.getInt();
            }

            if (isAlsEnabled) {
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
                signature = buf.getInt();
                if (signature != 0xDEAD000E) {
                    throw new IllegalStateException("DimeMod params reading failure at VALET_MODE");
                }
                valetCurrentCodeAddress = buf.getInt();
            }
            isDmInitReady = true;
        } else if (majorVer == 1) {
            isDmInitReady = false; // unsupported version
        } else {
            isDmInitReady = false; // unsupported version
        }
    }

    public boolean updateRuntimeData(int activeFeatures, int activeInputs, int[] currentErrors, int[] memErrors) {
        boolean changed = false;
        if (activeFeatures != this.runtimeActiveFeatures || activeInputs != this.runtimeActiveInputs) {
            changed = true;
        }

        if (isDmInitReady) {
            this.runtimeActiveFeatures = activeFeatures;
            this.runtimeActiveInputs = activeInputs;
            this.runtimeCurrentErrors = currentErrors;
            this.runtimeMemErrors = memErrors;

            boolean isAfrEnabled = (activeInputs & 0x01) != 0;
            boolean isEgtEnabled = (activeInputs & 0x02) != 0;
            boolean isFuelPressureEnabled = (activeInputs & 0x04) != 0;
            boolean isBackPressureEnabled = (activeInputs & 0x08) != 0;
            boolean isFlexFuelEnabled = (activeInputs & 0x10) != 0;
            boolean isFfsExternalTriggerEnabled = (activeInputs & 0x20) != 0;
            boolean isMapSwitchExternalTriggerEnabled = (activeInputs & 0x40) != 0;
            boolean isFailsafeExternalTriggerEnabled = (activeInputs & 0x80) != 0;
            boolean isOilTempEnabled = (activeInputs & 0x100) != 0;
            boolean isOilPressEnabled = (activeInputs & 0x200) != 0;

            boolean isRamTuneEnabled = (runtimeActiveFeatures & 0x80000000) != 0;
            boolean isCruiseButtonImmediateHacksEnabled = (runtimeActiveFeatures & 0x40000000) != 0;
            boolean isCorrectionsByGearsEnabled = (runtimeActiveFeatures & 0x20000000) != 0;
            boolean isCelFlashEnabled = (runtimeActiveFeatures & 0x10000000) != 0;
            boolean isKnockLightEnabled = (runtimeActiveFeatures & 0x08000000) != 0;
            boolean isKsByCylsEnabled = (runtimeActiveFeatures & 0x04000000) != 0;
            boolean isMapSwitchEnabled = (runtimeActiveFeatures & 0x02000000) != 0;
            boolean isSparkCutEnabled = (runtimeActiveFeatures & 0x01000000) != 0;
            boolean isSpeedDensityEnabled = (runtimeActiveFeatures & 0x800000) != 0;
            boolean isAlsEnabled = (runtimeActiveFeatures & 0x400000) != 0;
            boolean isCanSenderEnabled = (runtimeActiveFeatures & 0x200000) != 0;
            boolean isVinLockEnabled = (runtimeActiveFeatures & 0x100000) != 0;
            boolean isPwmControlEnabled = (runtimeActiveFeatures & 0x080000) != 0;
            boolean isValetModeEnabled = (runtimeActiveFeatures & 0x040000) != 0;

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
                params.add(getFloatParameter("DM903", "DimeMod: AFR Voltage", "Voltage", afrVoltageAddress, "v", "x", 0f, 5f, 0.5f));
            }
            if (isEgtEnabled) {
                params.add(getFloatTempParameter("DM904", "DimeMod: EGT", "Exhaust Gas Temp", egtAddress));
                params.add(getFloatParameter("DM905", "DimeMod: EGT Voltage", "Voltage", egtVoltageAddress, "v", "x", 0f, 5f, 0.5f));
            }
            if (isFuelPressureEnabled) {
                params.add(getFloatMfPressureParameter("DM906", "DimeMod: Fuel Pressure", "Fuel Pressure", fuelPressAddress));
                params.add(getFloatParameter("DM907", "DimeMod: Fuel Pressure Voltage", "Voltage", fuelPressVoltageAddress, "v", "x", 0f, 5f, 0.5f));
                params.add(getFloatMfPressureParameter("DM908", "DimeMod: Fuel Differential Pressure", "Fuel Differential Pressure", fuelDiffPressAddress));
            }
            if (isBackPressureEnabled) {
                params.add(getFloatMfPressureParameter("DM909", "DimeMod: Backpressure", "BackPressure", backPressAddress));
                params.add(getFloatParameter("DM910", "DimeMod: Backpressure Voltage", "Voltage", backPressVoltageAddress, "v", "x", 0f, 5f, 0.5f));
            }
            if (isOilTempEnabled) {
                params.add(getFloatTempParameter("DM920", "DimeMod: Oil Temp", "Oil Temperature", oilTempAddress));
                params.add(getFloatParameter("DM921", "DimeMod: Oil Temp Voltage", "Voltage", oilTempVoltageAddress, "v", "x", 0f, 5f, 0.5f));
            }
            if (isOilPressEnabled) {
                params.add(getFloatMfPressureParameter("DM922", "DimeMod: Oil Press", "Oil Pressure", oilPressAddress));
                params.add(getFloatParameter("DM923", "DimeMod: Oil Press Voltage", "Voltage", oilPressVoltageAddress, "v", "x", 0f, 5f, 0.5f));
            }
            if (isFlexFuelEnabled) {
                params.add(getFloatParameter("DM911", "DimeMod: FlexFuel Ethanol Content", "Ethanol content", ethanolContentAddress, "%", "x", 0f, 100f, 5f));
                params.add(getFloatParameter("DM912", "DimeMod: FlexFuel Ethanol Content Voltage", "Voltage", ethanolContentVoltageAddress, "v", "x", 0f, 5f, 0.5f));
            }
            if (isFailsafeExternalTriggerEnabled) {
                params.add(getUInt8Parameter("DM913", "DimeMod: MapSwitch Failsafe Mode External Trigger", "Failsafe Trigger State", extFailsafeStateAddress, "state", "x"));
//            params.add(getUInt8Parameter("DM966", "DimeMod: MapSwitch Failsafe Mode External Trigger Timer", "Failsafe Trigger State", extFailsafeStateAddress + 2, "ticks", "x"));
                params.add(getFloatParameter("DM914", "DimeMod: MapSwitch Failsafe Mode External Trigger Voltage", "Voltage", extFailsafeVoltageAddress, "v", "x", 0f, 5f, 0.5f));
            }
            if (isMapSwitchExternalTriggerEnabled) {
                params.add(getFloatParameter("DM915", "DimeMod: MapSwitch External Trigger Voltage", "Voltage", extMapSwitchVoltageAddress, "v", "x", 0f, 5f, 0.5f));
            }
            if (isFailsafeExternalTriggerEnabled) {
                params.add(getUInt8Parameter("DM916", "DimeMod: FFS External Trigger", "FFS Trigger State", ffsTriggerStateAddress, "state", "x"));
                params.add(getFloatParameter("DM917", "DimeMod: FFS External Trigger Voltage", "Voltage", ffsTriggerVoltageAddress, "v", "x", 0f, 5f, 0.5f));
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
            params.add(getFloatParameter("DM017", "DimeMod: Injector Flow value", "Injector Flow Value", flexFuelInjFlowValueAddress, "cc/min", "2707090/x", 0, 4, 0.1f));
            if (minorVer > 0 || buildNum > 1) {
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
                if (buildNum > 0) {
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

    private EcuParameterImpl getUInt8Parameter(String id, String name, String description, int address, String units, String conversion) {
        return new EcuParameterImpl(id, name,
                description,
                new EcuAddressImpl("0x" + Integer.toHexString(address & 0xFFFFFF), 1, -1),
                null, null, null,
                new EcuDataConvertor[]{
                        new EcuParameterConvertorImpl(units, conversion, "0", -1, "uint8", Settings.Endian.BIG, new HashMap<>(), new GaugeMinMax(0, 255, 1))
                }
        );
    }
    private EcuParameterImpl getUInt16Parameter(String id, String name, String description, int address, String units, String conversion) {
        return new EcuParameterImpl(id, name,
                description,
                new EcuAddressImpl("0x" + Integer.toHexString(address & 0xFFFFFF), 2, -1),
                null, null, null,
                new EcuDataConvertor[]{
                        new EcuParameterConvertorImpl(units, conversion, "0", -1, "uint16", Settings.Endian.BIG, new HashMap<>(), new GaugeMinMax(0, 65535, 2000))
                }
        );
    }


    private EcuParameterImpl getUInt32Parameter(String id, String name, String description, int address, String units, String conversion) {
        return new EcuParameterImpl(id, name,
                description,
                new EcuAddressImpl("0x" + Integer.toHexString(address & 0xFFFFFF), 4, -1),
                null, null, null,
                new EcuDataConvertor[]{
                        new EcuParameterConvertorImpl(units, conversion, "0", -1, "uint32", Settings.Endian.BIG, new HashMap<>(), new GaugeMinMax(-1, 1, 1))
                }
        );
    }

    private EcuParameterImpl getFloatParameter(String id, String name, String description, int address, String units, float min, float max, float step) {
        return new EcuParameterImpl(id, name,
                description,
                new EcuAddressImpl(Integer.toHexString(address & 0xFFFFFF), 4, -1),
                null, null, null,
                new EcuDataConvertor[]{
                        new EcuParameterConvertorImpl(units, "x", "0.00", -1, "float", Settings.Endian.BIG, new HashMap<>(), new GaugeMinMax(min, max, step))
                }
        );
    }

    private EcuParameterImpl getFloatTempParameter(String id, String name, String description, int address) {
        return new EcuParameterImpl(id, name,
                description,
                new EcuAddressImpl(Integer.toHexString(address & 0xFFFFFF), 4, -1),
                null, null, null,
                new EcuDataConvertor[]{
                        new EcuParameterConvertorImpl("Degrees C", "x", "0.0", -1, "float", Settings.Endian.BIG, new HashMap<>(), new GaugeMinMax(-40, 120, 5)),
                        new EcuParameterConvertorImpl("Degrees F", "x*1.8+32", "0.0", -1, "float", Settings.Endian.BIG, new HashMap<>(), new GaugeMinMax(-50, 200, 5))
                }
        );
    }

    private EcuParameterImpl getFloatMfPressureParameter(String id, String name, String description, int address) {
        return new EcuParameterImpl(id, name,
                description,
                new EcuAddressImpl(Integer.toHexString(address & 0xFFFFFF), 4, -1),
                null, null, null,
                new EcuDataConvertor[]{
                        new EcuParameterConvertorImpl("bar", "x*.001333333333", "0.000", -1, "float", Settings.Endian.BIG, new HashMap<>(), new GaugeMinMax(0, 10, 1)),
                        new EcuParameterConvertorImpl("psi", "x*.0193384", "0.0", -1, "float", Settings.Endian.BIG, new HashMap<>(), new GaugeMinMax(0, 100, 10))
                }
        );
    }


    private EcuParameterImpl getFloatParameter(String id, String name, String description, int address, String units, String conversion, float min, float max, float step) {
        return new EcuParameterImpl(id, name,
                description,
                new EcuAddressImpl(Integer.toHexString(address & 0xFFFFFF), 4, -1),
                null, null, null,
                new EcuDataConvertor[]{
                        new EcuParameterConvertorImpl(units, conversion, "0.00", -1, "float", Settings.Endian.BIG, new HashMap<>(), new GaugeMinMax(min, max, step))
                }
        );
    }

    /**
     * Get the DimeMod ID string.
     *
     * @return ID string
     */
    public String getDimeModVersion() {
        return majorVer + "." + minorVer + " build " + String.format("%03d", buildNum);
    }

    public byte[] getDmInitBytes() {
        return dmInitBytes;
    }

    public Collection<? extends EcuParameter> getEcuParams() {
        return params;
    }

    public int getCurrentErrorCodesAddress() {
        return currentErrorCodesAddress;
    }

    public int getMemorizedErrorCodesAddress() {
        return memorizedErrorCodesAddress;
    }

    public int getActiveInputsAddress() {
        return activeInputsAddress;
    }

    public int[] getRuntimeCurrentErrors() {
        return runtimeCurrentErrors;
    }

    public int getRuntimeActiveInputs() {
        return runtimeActiveInputs;
    }

    public int[] getRuntimeMemErrors() {
        return runtimeMemErrors;
    }

    public int getActiveFeaturesAddress() {
        return activeFeaturesAddress;
    }

    public Set<String> decodeDmCurrentErrors() {
        if (minorVer == 0) {
            return decodeErrors20(runtimeCurrentErrors[0]);
        } else {
            return decodeErrors21(runtimeCurrentErrors);
        }
    }

    public Set<String> decodeDmMemorizedErrors() {
        if (minorVer == 0) {
            return decodeErrors20(runtimeMemErrors[0]);
        } else {
            return decodeErrors21(runtimeMemErrors);
        }
    }

    private Set<String> decodeErrors21(int[] errors) {
        Set<String> result = new TreeSet<>();
        for (int i : errors) {
            if (i == 0) break;

            short err = (short) i;
            if ((err == (short) 0x800F)) {
                result.add("DM3000: ECU Link License Error");
                continue;
            }
            if ((err == (short) 0x800E)) {
                result.add("DM9999: Internal Logic Error");
                continue;
            }
            if ((err == (short) 0x800D)) {
                result.add("DM0001: Table Metadata Buffer Overflow");
                continue;
            }
            if ((err == (short) 0x800C)) {
                result.add("DM0002: Table Metadata Wrong Table");
                continue;
            }

            // decoding errors
            String inputStr;
            String funcStr;
            String reasonStr;
            short inputType = (short) (err & 0x0F00);
            short funcType = (short) (err & 0x00F0);
            short reason = (short) (err & 0x000F);

            switch (inputType) {
                case 0x00:
                    inputStr = "";
                    break;
                case 0x100:
                    inputStr = "TGV Left";
                    break;
                case 0x200:
                    inputStr = "TGV Right";
                    break;
                case 0x300:
                    inputStr = "Rear O2";
                    break;
                case 0x400:
                    inputStr = "MAF Sensor Input";
                    break;
                case 0x500:
                    inputStr = "CAN ADC Channel 01";
                    break;
                case 0x600:
                    inputStr = "CAN ADC Channel 02";
                    break;
                case 0x700:
                    inputStr = "CAN ADC Channel 03";
                    break;
                case 0x800:
                    inputStr = "CAN ADC Channel 04";
                    break;
                case 0x900:
                    inputStr = "CAN ADC Channel 05";
                    break;
                case 0xA00:
                    inputStr = "CAN ADC Channel 06";
                    break;
                case 0xB00:
                    inputStr = "CAN ADC Channel 07";
                    break;
                case 0xC00:
                    inputStr = "CAN ADC Channel 08";
                    break;
                default:
                    inputStr = "Unknown input";
                    break;
            }

            switch (funcType) {
                case 0x00:
                    funcStr = "";
                    break;
                case 0x10:
                    funcStr = "AFR";
                    break;
                case 0x20:
                    funcStr = "EGT";
                    break;
                case 0x30:
                    funcStr = "Fuel Pressure";
                    break;
                case 0x40:
                    funcStr = "Backpressure";
                    break;
                case 0x50:
                    funcStr = "FlexFuel";
                    break;
                case 0x60:
                    funcStr = "FFS External Trigger";
                    break;
                case 0x70:
                    funcStr = "MapSwitch External Trigger";
                    break;
                case 0x80:
                    funcStr = "Custom Failsafe Trigger";
                    break;
                case 0x90:
                    funcStr = "Oil Temp";
                    break;
                case 0xA0:
                    funcStr = "Oil Press";
                    break;
                default:
                    funcStr = "Unknown function";
                    break;
            }

            switch (reason) {
                case 0x00:
                    reasonStr = "";
                    break;
                case 0x01:
                    reasonStr = "Voltage Too Low";
                    break;
                case 0x02:
                    reasonStr = "Voltage Too High";
                    break;
                case 0x03:
                    reasonStr = "Input is not available";
                    break;
                case 0x04:
                    reasonStr = "Input is already used";
                    break;
                case 0x05:
                    reasonStr = "Input is not assigned";
                    break;
                default:
                    reasonStr = "Unknown problem";
                    break;
            }
            result.add("DM" + HexUtil.intToHexString(err, 4) + " " + funcStr + ", " + reasonStr + (inputStr.isEmpty() ? "" : " (" + inputStr + ")"));
        }
        return result;
    }

    private Set<String> decodeErrors20(int errors) {
        Set<String> result = new TreeSet<>();
        if ((errors & 0x00000001) != 0) {
            result.add("DM0001: Table Metadata Buffer Overflow");
        }
        if ((errors & 0x00000002) != 0) {
            result.add("DM0002: Table Metadata Wrong Table");
        }
        if ((errors & 0x00000004) != 0) {
            result.add("DM0003: Voltage Too Low");
        }
        if ((errors & 0x00000008) != 0) {
            result.add("DM0004: Voltage Too High");
        }
        if ((errors & 0x00000010) != 0) {
            result.add("DM0010: TGV Left Input Configuration Problem");
        }
        if ((errors & 0x00000020) != 0) {
            result.add("DM0011: TGV Right Input Configuration Problem");
        }
        if ((errors & 0x00000040) != 0) {
            result.add("DM0012: Rear O2 Input Configuration Problem");
        }
        if ((errors & 0x00000080) != 0) {
            result.add("DM0013: MAF Input Configuration Problem");
        }
        /*
        if ((errors & 0x00000100) != 0) {
            result.add("DM0020: CAN ADC 01 Input Configuration Problem");
        }
        */
        if ((errors & 0x00010000) != 0) {
            result.add("DM0030: AFR Related Problem");
        }
        if ((errors & 0x00020000) != 0) {
            result.add("DM0031: EGT Related Problem");
        }
        if ((errors & 0x00040000) != 0) {
            result.add("DM0032: Fuel Pressure Related Problem");
        }
        if ((errors & 0x00080000) != 0) {
            result.add("DM0033: Backpressure Related Problem");
        }
        if ((errors & 0x00100000) != 0) {
            result.add("DM0034: FlexFuel Ethanol Content Sensor Related Problem");
        }
        if ((errors & 0x00200000) != 0) {
            result.add("DM0035: FFS External Trigger Related Problem");
        }
        if ((errors & 0x00400000) != 0) {
            result.add("DM0036: MapSwitch External Trigger Related Problem");
        }
        if ((errors & 0x00800000) != 0) {
            result.add("DM0037: Failsafe External Trigger Related Problem");
        }
        if ((errors & 0x00800000) != 0) {
            result.add("DM0037: Failsafe External Trigger Related Problem");
        }
        if ((errors & 0x10000000) != 0) {
            result.add("DM1000: Selected Custom Sensor is not configured");
        }
        if ((errors & 0x20000000) != 0) {
            result.add("DM2000: Selected Input is not available");
        }
        if ((errors & 0x40000000) != 0) {
            result.add("DM3000: ECU Link License Error");
        }
        if ((errors & 0x80000000) != 0) {
            result.add("DM9999: Internal Logic Error");
        }

        return result;
    }

    public int getMajorVer() {
        return majorVer;
    }

    public int getMinorVer() {
        return minorVer;
    }

    public int getBuildNum() {
        return buildNum;
    }
}
