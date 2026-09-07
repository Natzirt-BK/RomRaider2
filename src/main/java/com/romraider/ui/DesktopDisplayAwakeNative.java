/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.ui;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.win32.StdCallLibrary;
import java.util.Locale;

/** Lazy native linkage: unavailable desktop services cannot break logger startup. */
final class DesktopDisplayAwakeNative {
    private DesktopDisplayAwakeNative() { }

    static DesktopDisplayAwake.Lease acquire() throws Exception {
        if (Boolean.getBoolean("romraider2.displayAwake.disabled")) {
            throw new UnsupportedOperationException("Screen-awake requests disabled for this process");
        }
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.startsWith("windows")) return windows(Native.load("kernel32", Kernel32.class));
        if (os.startsWith("mac")) return mac(
                Native.load("/System/Library/Frameworks/CoreFoundation.framework/CoreFoundation", CoreFoundation.class),
                Native.load("/System/Library/Frameworks/IOKit.framework/IOKit", IOKit.class));
        if (os.startsWith("linux")) return LinuxDisplayAwake.acquire();
        throw new UnsupportedOperationException("No screen-awake provider for this desktop");
    }

    public interface Kernel32 extends StdCallLibrary { int SetThreadExecutionState(int flags); }
    static DesktopDisplayAwake.Lease windows(Kernel32 api) {
        int previous = api.SetThreadExecutionState(0x80000003); // CONTINUOUS | DISPLAY_REQUIRED | SYSTEM_REQUIRED
        if (previous == 0) throw new IllegalStateException("Windows rejected the screen-awake request");
        return new DesktopDisplayAwake.Lease() {
            private boolean released;
            @Override public void close() {
                if (released) return;
                if (api.SetThreadExecutionState(previous | 0x80000000) == 0)
                    throw new IllegalStateException("Windows did not clear the screen-awake request");
                released = true;
            }
        };
    }

    public interface CoreFoundation extends Library {
        Pointer CFStringCreateWithCString(Pointer allocator, String text, int encoding);
        void CFRelease(Pointer value);
    }
    public interface IOKit extends Library {
        int IOPMAssertionCreateWithName(Pointer type, int level, Pointer name, IntByReference id);
        int IOPMAssertionRelease(int id);
    }
    static DesktopDisplayAwake.Lease mac(CoreFoundation cf, IOKit io) {
        Pointer type = cf.CFStringCreateWithCString(null, "PreventUserIdleDisplaySleep", 0x08000100);
        Pointer reason = null;
        try {
            reason = cf.CFStringCreateWithCString(null, "RomRaider2 full-screen gauges", 0x08000100);
            if (type == null || reason == null) throw new IllegalStateException("Cannot create display-awake description");
            IntByReference id = new IntByReference();
            if (io.IOPMAssertionCreateWithName(type, 255, reason, id) != 0)
                throw new IllegalStateException("macOS rejected the screen-awake assertion");
            return new DesktopDisplayAwake.Lease() {
                private boolean released;
                @Override public void close() {
                    if (released) return;
                    if (io.IOPMAssertionRelease(id.getValue()) != 0)
                        throw new IllegalStateException("macOS did not release the screen-awake assertion");
                    released = true;
                }
            };
        } finally {
            if (reason != null) cf.CFRelease(reason);
            if (type != null) cf.CFRelease(type);
        }
    }
}
