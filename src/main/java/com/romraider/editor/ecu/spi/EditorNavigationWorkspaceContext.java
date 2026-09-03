/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.editor.ecu.spi;

import java.util.List;
import java.util.Map;

import com.romraider.editor.document.EditorDocument;
import com.romraider.editor.document.EditorDocumentSession;
import com.romraider.editor.document.EditorDocumentSnapshot;
import com.romraider.editor.workspace.EditorWorkspaceService;
import com.romraider.editor.workspace.TableLocation;
import com.romraider.maps.Rom;
import com.romraider.maps.Table;

/** Shared state and commands used by replacement editor navigation views. */
public final class EditorNavigationWorkspaceContext {
    public interface Opener {
        void open(TableLocation location);
    }

    private final EditorDocumentSession session;
    private final EditorWorkspaceService workspace;
    private final Opener opener;

    public EditorNavigationWorkspaceContext(EditorDocumentSession session,
            Opener opener) {
        if (session == null || opener == null) {
            throw new IllegalArgumentException("Session and opener are required");
        }
        this.session = session;
        this.workspace = EditorWorkspaceService.getInstance();
        this.opener = opener;
    }

    public EditorDocumentSnapshot getSnapshot() {
        return session.snapshot();
    }

    public List<TableLocation> getFavorites() {
        return workspace.preferences().getFavorites();
    }

    public List<TableLocation> getRecent() {
        return workspace.preferences().getRecent();
    }

    public Map<TableLocation, Integer> getChangedTables() {
        EditorDocumentSnapshot snapshot = session.snapshot();
        java.util.ArrayList<Rom> roms = new java.util.ArrayList<Rom>();
        for (EditorDocument document : snapshot.getDocuments()) {
            roms.add(document.getRom());
        }
        return workspace.changedTables(roms);
    }

    public boolean isFavorite(Rom rom, Table table) {
        return rom != null && table != null && workspace.isFavorite(rom, table);
    }

    public void toggleFavorite(Rom rom, Table table) {
        if (rom != null && table != null) workspace.toggleFavorite(rom, table);
    }

    public void removeFavorite(TableLocation location) {
        if (location != null) workspace.removeFavorite(location);
    }

    public void open(TableLocation location) {
        if (location != null) opener.open(location);
    }

    public void open(Rom rom, Table table) {
        if (rom == null || table == null) return;
        opener.open(new TableLocation(EditorWorkspaceService.romIdentity(rom),
                table.getName()));
    }

    public boolean canGoBack() { return workspace.navigation().canGoBack(); }
    public boolean canGoForward() { return workspace.navigation().canGoForward(); }
    public void goBack() { open(workspace.navigation().back()); }
    public void goForward() { open(workspace.navigation().forward()); }

    public void addSessionListener(EditorDocumentSession.Listener listener) {
        session.addListener(listener);
    }

    public void removeSessionListener(EditorDocumentSession.Listener listener) {
        session.removeListener(listener);
    }
}
