/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.logger.analysis;

/** Named legacy scalar gates; labels are not channel guesses or calibration defaults. */
public enum FuelOperatingCondition {
    LOOP_STATE("Closed-loop / open-loop state", Mode.EQUAL),
    AFR("Air/fuel ratio", Mode.RANGE),
    RPM("Engine speed", Mode.RANGE),
    MASS_FLOW("Mass airflow", Mode.RANGE),
    MAF_VOLTAGE("MAF voltage", Mode.RANGE),
    INTAKE_TEMPERATURE("Intake air temperature", Mode.MAXIMUM),
    COOLANT_TEMPERATURE("Coolant temperature", Mode.MINIMUM),
    TIP_IN("Tip-in throttle", Mode.EQUAL);

    public enum Mode { EQUAL, RANGE, MINIMUM, MAXIMUM }
    private final String label;
    private final Mode mode;
    FuelOperatingCondition(String label, Mode mode) { this.label = label; this.mode = mode; }
    public String label() { return label; }
    public Mode mode() { return mode; }

    public void validate(Double minimum, Double maximum) {
        if (minimum != null && !Double.isFinite(minimum) || maximum != null && !Double.isFinite(maximum))
            throw new IllegalArgumentException(label + " limits must be finite");
        boolean valid = switch (mode) {
            case EQUAL -> minimum != null && maximum != null && minimum.doubleValue() == maximum.doubleValue();
            case RANGE -> minimum != null && maximum != null && minimum <= maximum;
            case MINIMUM -> minimum != null && maximum == null;
            case MAXIMUM -> minimum == null && maximum != null;
        };
        if (!valid) throw new IllegalArgumentException("Invalid " + label + " condition: use its required equality or inclusive bounds");
    }
    public FuelLogAnalysis.Filter filter(int channel, Double minimum, Double maximum) {
        validate(minimum, maximum);
        return new FuelLogAnalysis.Filter(channel, minimum == null ? -Double.MAX_VALUE : minimum,
                maximum == null ? Double.MAX_VALUE : maximum);
    }
}
