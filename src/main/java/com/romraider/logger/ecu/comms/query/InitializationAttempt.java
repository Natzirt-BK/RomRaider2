/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.logger.ecu.comms.query;

import com.romraider.logger.ecu.comms.query.dimemod.DmInit;
import com.romraider.logger.ecu.comms.query.dimemod.DmInitCallback;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Lifetime token for one ECU/DimeMod initialization attempt, not a firmware
 * identity or permission to discover metadata. Closing never clears owner caches.
 */
public final class InitializationAttempt implements AutoCloseable {
    private final AtomicBoolean active = new AtomicBoolean(true);

    public boolean isActive() { return active.get(); }

    public void requireActive() {
        if (!isActive()) throw new IllegalStateException("Initialization attempt is no longer active");
    }

    @Override public void close() { active.set(false); }

    public EcuInitCallback bind(EcuInitCallback callback) {
        return next -> {
            if (isActive()) callback.callback(next, this);
        };
    }

    public DmInitCallback bind(DmInitCallback callback) {
        return new DmInitCallback() {
            public void invalidate() {
                if (isActive()) callback.invalidate(InitializationAttempt.this);
            }
            public void callback(DmInit next, boolean forceUpdate) {
                if (isActive()) callback.callback(next, forceUpdate, InitializationAttempt.this);
            }
            public boolean needToInit() {
                requireActive();
                return callback.needToInit(InitializationAttempt.this);
            }
            public DmInit getDmInit() {
                // A stale cache lookup must fail, never become a discovery miss.
                requireActive();
                return callback.getDmInit(InitializationAttempt.this);
            }
        };
    }
}
