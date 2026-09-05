/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider2.javafx;

import javafx.event.Event;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;

/** Menu/host closes must honor the same veto as the native window close button. */
final class FxCloseRequest {
    private FxCloseRequest() { }

    static void request(Stage stage) {
        if (!stage.isShowing()) return;
        WindowEvent request = new WindowEvent(stage, WindowEvent.WINDOW_CLOSE_REQUEST);
        Event.fireEvent(stage, request);
        if (!request.isConsumed()) stage.close();
    }
}
