/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.activity;

/** Toolkit-neutral progress target for bounded application work. */
public interface ProgressReporter {
    void update(String status, int percent);
}
