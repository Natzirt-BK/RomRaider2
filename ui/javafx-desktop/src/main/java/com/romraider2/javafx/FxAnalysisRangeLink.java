/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider2.javafx;

import com.romraider.logger.analysis.LogDataset;
import com.romraider.logger.analysis.LogRange;
import java.util.function.BooleanSupplier;

/** Explicit three-workspace range sharing. All commands run on the JavaFX thread. */
final class FxAnalysisRangeLink implements AutoCloseable {
    record Draft(String first, String last) {
        LogRange range(LogDataset data) {
            try { return LogRange.of(Integer.parseInt(first.trim()) - 1, Integer.parseInt(last.trim()), data.getRowCount()); }
            catch (IllegalArgumentException failure) { throw new IllegalArgumentException("Enter a valid inclusive sample range before applying or linking."); }
        }
    }
    private final FxLogAnalysisPane log;
    private final FxFuelAnalysisPane maf, injector;
    private final LogDataset dataset;
    private boolean linked, updating, closed, applied;
    private Draft shared;

    FxAnalysisRangeLink(FxLogAnalysisPane log, FxFuelAnalysisPane maf, FxFuelAnalysisPane injector) {
        this.log = log; this.maf = maf; this.injector = injector; dataset = log.dataset();
        requireCurrent(); log.attachRangeLink(this); maf.attachRangeLink(this); injector.attachRangeLink(this);
    }
    boolean isLinked() { return linked && !closed; }
    boolean isUpdating() { return updating; }
    boolean isApplied() { return applied; }
    void enable(BooleanSupplier confirm) {
        requireCurrent(); Draft draft = log.rangeDraft(); draft.range(dataset);
        long logRevision = log.rangeRevision(), mafRevision = maf.inputRevision(), injectorRevision = injector.inputRevision();
        try {
            if (!confirm.getAsBoolean()) return;
            requireCurrent();
            if (logRevision != log.rangeRevision() || mafRevision != maf.inputRevision() || injectorRevision != injector.inputRevision())
                throw new IllegalArgumentException("A range, setup or analysis input changed during review. Review the link again.");
            linked = true; apply(draft);
        } finally { refresh(); }
    }
    void draftChanged(Object source) {
        if (!isLinked() || updating) return;
        requireCurrent(); Draft draft;
        if (source == log) draft = log.rangeDraft();
        else if (source == maf) draft = maf.rangeDraft();
        else if (source == injector) draft = injector.rangeDraft();
        else throw new IllegalArgumentException("Unknown range source");
        if (draft.equals(shared)) return;
        updating = true;
        try {
            shared = draft; applied = false;
            maf.copySharedRange(draft); injector.copySharedRange(draft);
            log.copySharedRange(draft); log.invalidateSharedRange();
        } finally { updating = false; refresh(); }
    }
    void commit() {
        if (!isLinked()) throw new IllegalArgumentException("The sample-range link is not active.");
        requireCurrent(); apply(shared);
    }
    void select(LogRange range) {
        if (!isLinked()) throw new IllegalArgumentException("The sample-range link is not active.");
        requireCurrent();
        apply(new Draft(Integer.toString(range.getStartInclusive() + 1), Integer.toString(range.getEndExclusive())));
    }
    private void apply(Draft draft) {
        LogRange range = draft.range(dataset);
        // Canonicalize the inclusive fields in all three workspaces together.
        Draft canonical = new Draft(Integer.toString(range.getStartInclusive() + 1), Integer.toString(range.getEndExclusive()));
        updating = true; applied = false;
        try {
            shared = canonical; maf.copySharedRange(canonical); injector.copySharedRange(canonical);
            log.copySharedRange(canonical); log.selectRange(range); applied = true;
        } finally { updating = false; refresh(); }
    }
    void requireApplied() {
        if (isLinked() && !applied) throw new IllegalArgumentException("Apply the shared sample range first, then confirm the analysis units.");
    }
    void disconnect() { linked = false; refresh(); }
    private void requireCurrent() {
        if (closed || !log.rangeAvailable() || !maf.conditionsAvailable() || !injector.conditionsAvailable()
                || log.dataset() != dataset || maf.conditionsDraft().dataset() != dataset || injector.conditionsDraft().dataset() != dataset)
            throw new IllegalArgumentException("Range sharing requires the same open CSV in Log Analysis, MAF and Injector.");
    }
    private void refresh() {
        log.showRangeLink(isLinked(), applied); maf.showRangeLink(isLinked(), applied); injector.showRangeLink(isLinked(), applied);
    }
    @Override public void close() {
        if (closed) return;
        disconnect(); closed = true; log.attachRangeLink(null); maf.attachRangeLink(null); injector.attachRangeLink(null);
    }
}
