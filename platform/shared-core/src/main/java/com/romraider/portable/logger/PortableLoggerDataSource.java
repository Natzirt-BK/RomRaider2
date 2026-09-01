/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.portable.logger;

import java.io.IOException;

/** Read-only source for one planned group of logger addresses. */
public interface PortableLoggerDataSource {
    byte[] read(PortableLoggerQueryBatch batch) throws IOException;
}
