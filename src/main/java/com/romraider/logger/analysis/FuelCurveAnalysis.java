/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.logger.analysis;

import java.util.List;

/** Read-only curve review. No ROM, transport, extrapolation or automatic calibration API. */
public final class FuelCurveAnalysis {
    public static final int MAX_DEGREE = 20;
    private static final long MAX_WORK = 100_000_000L;
    private FuelCurveAnalysis() { }

    /** Coefficients are ascending powers of z=(x-center)/scale, with y normalized by yScale. */
    public static final class Polynomial {
        private final double[] coefficients;
        private final double minimumX, maximumX, center, scale, yScale;
        private final double rmse, residualDeviation, rSquared;
        private final int samples;
        private Polynomial(double[] coefficients, Bounds bounds, double center, double scale,
                double yScale, double rmse, double residualDeviation, double rSquared) {
            this.coefficients = coefficients.clone(); this.minimumX = bounds.minimum; this.maximumX = bounds.maximum;
            this.center = center; this.scale = scale; this.yScale = yScale; this.samples = bounds.count;
            this.rmse = rmse; this.residualDeviation = residualDeviation; this.rSquared = rSquared;
        }
        public int getDegree() { return coefficients.length - 1; }
        public int getSamples() { return samples; }
        public double getMinimumX() { return minimumX; }
        public double getMaximumX() { return maximumX; }
        public double getRmse() { return rmse; }
        public double getResidualDeviation() { return residualDeviation; }
        public double getRSquared() { return rSquared; }
        public double getCenter() { return center; }
        public double getScale() { return scale; }
        public double getYScale() { return yScale; }
        public double[] getNormalizedCoefficients() { return coefficients.clone(); }
        public double predict(double x) {
            if (!Double.isFinite(x) || x < minimumX || x > maximumX) return Double.NaN;
            return finite(evaluate(coefficients, (x - center) / scale) * yScale);
        }
        /** Descriptive fitted-line parameters, not measured injector characteristics. */
        public double linearSlope() { return getDegree() == 1 ? finite(coefficients[1] * yScale / scale) : Double.NaN; }
        public double linearIntercept() { return getDegree() == 1 ? finite(coefficients[0] * yScale - linearSlope() * center) : Double.NaN; }
    }

    public static Polynomial fit(FuelLogAnalysis.Result data, int degree) {
        if (data == null || degree < 1 || degree > MAX_DEGREE) throw new IllegalArgumentException("Choose polynomial degree 1–20.");
        int columns = degree + 1;
        if (data.getAccepted() < columns) throw new IllegalArgumentException("Not enough accepted samples for this polynomial degree.");
        if ((long) data.getAccepted() * columns * columns > MAX_WORK) {
            throw new IllegalArgumentException("Fit work limit exceeded; reduce polynomial degree or narrow the sample range.");
        }
        Bounds bounds = new Bounds(); data.forEachAccepted(bounds::add);
        if (bounds.count != data.getAccepted()) throw new IllegalArgumentException("Accepted sample replay changed.");
        double center = bounds.minimum / 2 + bounds.maximum / 2;
        double scale = bounds.maximum / 2 - bounds.minimum / 2;
        double yScale = bounds.yScale == 0 ? 1 : bounds.yScale;
        if (!Double.isFinite(scale) || scale <= 0) throw new IllegalArgumentException("Fit requires a finite, nonzero input span.");
        double[][] triangular = new double[columns][columns + 1];
        double[] row = new double[columns + 1];
        data.forEachAccepted((x, y) -> {
            double z = (x - center) / scale;
            row[0] = 1;
            for (int j = 1; j < columns; j++) row[j] = row[j - 1] * z;
            row[columns] = y / yScale;
            // Incremental Givens QR: retain only the small triangular factor.
            // Every accepted raw sample receives equal weight; no bin-center fit.
            for (int j = 0; j < columns; j++) {
                double norm = Math.hypot(triangular[j][j], row[j]);
                if (norm == 0) continue;
                double cosine = triangular[j][j] / norm, sine = row[j] / norm;
                triangular[j][j] = norm;
                for (int k = j + 1; k <= columns; k++) {
                    double old = triangular[j][k];
                    triangular[j][k] = cosine * old + sine * row[k];
                    row[k] = -sine * old + cosine * row[k];
                }
            }
        });
        double largest = 0;
        for (int j = 0; j < columns; j++) largest = Math.max(largest, Math.abs(triangular[j][j]));
        double[] coefficients = new double[columns];
        for (int j = columns - 1; j >= 0; j--) {
            // A conservative numerical-rank guard, not a condition-number estimate.
            if (!Double.isFinite(triangular[j][j]) || Math.abs(triangular[j][j]) <= largest * 1e-10) {
                throw new IllegalArgumentException("Fit is rank-deficient or poorly resolved; reduce degree or improve input coverage.");
            }
            double value = triangular[j][columns];
            for (int k = j + 1; k < columns; k++) value -= triangular[j][k] * coefficients[k];
            coefficients[j] = value / triangular[j][j];
            if (!Double.isFinite(coefficients[j])) throw new IllegalArgumentException("Fit produced nonfinite coefficients.");
        }
        double[] errors = {0, 0, 0, 0}; // residual sum of squares, normalized mean, total sum of squares, count
        data.forEachAccepted((x, y) -> {
            double normalized = y / yScale, prediction = evaluate(coefficients, (x - center) / scale);
            double residual = normalized - prediction;
            errors[0] += residual * residual;
            double delta = normalized - errors[1];
            errors[1] += delta / ++errors[3];
            errors[2] += delta * (normalized - errors[1]);
        });
        double rmse = Math.sqrt(errors[0] / bounds.count) * yScale;
        if (!Double.isFinite(rmse)) throw new IllegalArgumentException("Fit residuals are not finite.");
        double residualDeviation = bounds.count > columns ? Math.sqrt(errors[0] / (bounds.count - columns)) * yScale : Double.NaN;
        double rSquared = errors[2] > 0 ? 1 - errors[0] / errors[2] : Double.NaN;
        return new Polynomial(coefficients, bounds, center, scale, yScale, rmse, finite(residualDeviation), finite(rSquared));
    }

    /** Piecewise linear interpolation of observed bin means; empty-bin gaps stay unavailable. */
    public static double interpolate(FuelLogAnalysis.Result data, double x) {
        if (data == null || !Double.isFinite(x)) return Double.NaN;
        List<FuelLogAnalysis.Bin> bins = data.getBins();
        if (bins.isEmpty() || x < bins.get(0).getMeanX() || x > bins.get(bins.size() - 1).getMeanX()) return Double.NaN;
        int low = 0, high = bins.size() - 1;
        while (low <= high) {
            int middle = (low + high) >>> 1;
            double point = bins.get(middle).getMeanX();
            if (x == point) return finite(bins.get(middle).getMean());
            if (x < point) high = middle - 1; else low = middle + 1;
        }
        if (high < 0 || low >= bins.size()) return Double.NaN;
        FuelLogAnalysis.Bin left = bins.get(high), right = bins.get(low);
        double tolerance = 2 * Math.max(Math.ulp(left.getUpper()), Math.ulp(right.getLower()));
        if (right.getLower() - left.getUpper() > tolerance) return Double.NaN;
        double span = right.getMeanX() - left.getMeanX();
        if (span <= 0 || !Double.isFinite(span)) return Double.NaN;
        double fraction = (x - left.getMeanX()) / span;
        return finite(left.getMean() * (1 - fraction) + right.getMean() * fraction);
    }

    private static double evaluate(double[] coefficients, double z) {
        double value = coefficients[coefficients.length - 1];
        for (int j = coefficients.length - 2; j >= 0; j--) value = value * z + coefficients[j];
        return value;
    }
    private static double finite(double value) { return Double.isFinite(value) ? value : Double.NaN; }
    private static final class Bounds {
        int count;
        double minimum = Double.POSITIVE_INFINITY, maximum = Double.NEGATIVE_INFINITY, yScale;
        void add(double x, double y) {
            if (!Double.isFinite(x) || !Double.isFinite(y)) throw new IllegalArgumentException("Accepted point is not finite.");
            minimum = Math.min(minimum, x); maximum = Math.max(maximum, x); yScale = Math.max(yScale, Math.abs(y)); count++;
        }
    }
}
