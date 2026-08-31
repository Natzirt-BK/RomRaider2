/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.maps.history;

import com.romraider.maps.Rom;

public interface EditHistoryListener {
    void historyChanged(Rom rom);
}
