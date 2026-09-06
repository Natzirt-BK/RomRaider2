/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider2.javafx;

import java.util.List;
import java.util.function.BooleanSupplier;
import com.romraider.logger.analysis.LogChannel;
import com.romraider.logger.analysis.LogDataset;
import com.romraider.logger.analysis.LogRange;

/** Window-local, explicitly enabled condition linking. All calls run on the FX thread. */
final class FxFuelAnalysisLink {
    record FilterDraft(LogChannel channel, String minimum, String maximum) { }
    record Draft(LogDataset dataset, String first, String last, List<FilterDraft> filters) {
        Draft { filters = List.copyOf(filters); }
        void validate() {
            if (dataset == null) throw new IllegalArgumentException("Open the same CSV in both analysis tabs first.");
            try { LogRange.of(Integer.parseInt(first.trim()) - 1, Integer.parseInt(last.trim()), dataset.getRowCount()); }
            catch (IllegalArgumentException failure) { throw new IllegalArgumentException("Enter a valid inclusive sample range before linking."); }
            for (FilterDraft filter : filters) {
                if (filter.channel() == null) {
                    if (!filter.minimum().isBlank() || !filter.maximum().isBlank()) {
                        throw new IllegalArgumentException("Resolve each filter channel or clear its limits before linking.");
                    }
                    continue;
                }
                int index = filter.channel().getIndex();
                if (index < 0 || index >= dataset.getChannels().size()
                        || dataset.getChannels().get(index) != filter.channel() || filter.channel().isTimeChannel()) {
                    throw new IllegalArgumentException("A filter does not belong to the current dataset.");
                }
                try {
                    double minimum = Double.parseDouble(filter.minimum().trim()), maximum = Double.parseDouble(filter.maximum().trim());
                    if (!Double.isFinite(minimum) || !Double.isFinite(maximum) || minimum > maximum) throw new NumberFormatException();
                } catch (NumberFormatException failure) {
                    throw new IllegalArgumentException("Filter limits must be finite and ordered before linking.");
                }
            }
        }
        String summary() {
            StringBuilder text = new StringBuilder("Samples ").append(first).append(" through ").append(last).append(" (inclusive)");
            int count = 0;
            for (FilterDraft filter : filters) if (filter.channel() != null) {
                count++;
                text.append("\nColumn ").append(filter.channel().getIndex() + 1).append(": ")
                        .append(filter.channel().getLabel()).append(" · ").append(filter.minimum()).append(" to ").append(filter.maximum());
            }
            if (count == 0) text.append("\nNo numeric filters");
            return text.toString();
        }
    }

    private final FxFuelAnalysisPane first, second;
    private boolean linked, updating;

    FxFuelAnalysisLink(FxFuelAnalysisPane first, FxFuelAnalysisPane second) {
        if (first == second) throw new IllegalArgumentException("Two distinct analysis panes are required");
        this.first = first; this.second = second;
        first.attachConditionsLink(this); second.attachConditionsLink(this);
    }

    boolean isLinked() { return linked; }
    boolean available() {
        return first.conditionsAvailable() && second.conditionsAvailable()
                && first.conditionsDraft().dataset() == second.conditionsDraft().dataset();
    }

    void enable(FxFuelAnalysisPane source, BooleanSupplier confirm) {
        FxFuelAnalysisPane target = other(source);
        Draft draft = source.conditionsDraft();
        draft.validate();
        requireSameDataset(source, target);
        long sourceRevision = source.inputRevision(), targetRevision = target.inputRevision();
        try {
            if (!confirm.getAsBoolean()) return;
            requireSameDataset(source, target);
            if (sourceRevision != source.inputRevision() || targetRevision != target.inputRevision()) {
                throw new IllegalArgumentException("Analysis inputs changed during review. Review the current conditions again.");
            }
            linked = true;
            updating = true;
            try { target.applyConditionsDraft(draft); source.invalidateConditions(); }
            finally { updating = false; }
        } finally { refresh(); }
    }

    void changed(FxFuelAnalysisPane source) {
        if (!linked || updating) return;
        FxFuelAnalysisPane target = other(source);
        try { requireSameDataset(source, target); }
        catch (IllegalArgumentException failure) { disconnect(); return; }
        updating = true;
        try {
            // Mirror even an incomplete edit. Both panes must visibly reject the
            // same draft, never retain different last-valid calculation inputs.
            target.applyConditionsDraft(source.conditionsDraft());
        } finally { updating = false; }
    }

    void disconnect() { linked = false; refresh(); }

    private FxFuelAnalysisPane other(FxFuelAnalysisPane source) {
        if (source == first) return second;
        if (source == second) return first;
        throw new IllegalArgumentException("Unknown analysis pane");
    }
    private void requireSameDataset(FxFuelAnalysisPane source, FxFuelAnalysisPane target) {
        if (!source.conditionsAvailable() || !target.conditionsAvailable()
                || source.conditionsDraft().dataset() != target.conditionsDraft().dataset()) {
            throw new IllegalArgumentException("Conditions can only be linked for the same loaded dataset.");
        }
    }
    void refresh() {
        first.showConditionsLink(linked); second.showConditionsLink(linked);
    }
}
