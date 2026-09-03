/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.editor.document;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.romraider.maps.Rom;
import com.romraider.maps.Table;

/** Immutable snapshot of the desktop editor document session. */
public final class EditorDocumentSnapshot {
    private final List<EditorDocument> documents;
    private final Rom activeRom;
    private final Table activeTable;
    private final long revision;

    EditorDocumentSnapshot(List<EditorDocument> documents, Rom activeRom,
            Table activeTable, long revision) {
        this.documents = Collections.unmodifiableList(
                new ArrayList<EditorDocument>(documents));
        this.activeRom = activeRom;
        this.activeTable = activeTable;
        this.revision = revision;
    }

    public List<EditorDocument> getDocuments() { return documents; }
    public Rom getActiveRom() { return activeRom; }
    public Table getActiveTable() { return activeTable; }
    public long getRevision() { return revision; }
    public boolean isEmpty() { return documents.isEmpty(); }

    public EditorDocument getActiveDocument() {
        for (EditorDocument document : documents) {
            if (document.getRom() == activeRom) return document;
        }
        return null;
    }
}
