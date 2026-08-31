/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.editor.recovery;

import java.nio.file.Path;

/** One immutable crash-recovery artifact and its identifying metadata. */
public final class RecoverySnapshot {
    private final Path binaryPath;
    private final Path metadataPath;
    private final long createdAt;
    private final String sourceName;
    private final String sourcePath;
    private final String romId;
    private final int changedCells;

    RecoverySnapshot(Path binaryPath, Path metadataPath, long createdAt,
            String sourceName, String sourcePath, String romId,
            int changedCells) {
        this.binaryPath = binaryPath;
        this.metadataPath = metadataPath;
        this.createdAt = createdAt;
        this.sourceName = sourceName;
        this.sourcePath = sourcePath;
        this.romId = romId;
        this.changedCells = changedCells;
    }

    public Path getBinaryPath() { return binaryPath; }
    public Path getMetadataPath() { return metadataPath; }
    public long getCreatedAt() { return createdAt; }
    public String getSourceName() { return sourceName; }
    public String getSourcePath() { return sourcePath; }
    public String getRomId() { return romId; }
    public int getChangedCells() { return changedCells; }
}
