/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.logger.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Test;

public class LoggerChannelServiceTest {
    @Test
    public void preservesDefinitionOrderAndPublishesImmutableSnapshots() {
        AtomicInteger updates = new AtomicInteger();
        AtomicReference<List<LoggerChannel>> latest =
                new AtomicReference<List<LoggerChannel>>();
        LoggerChannelService service = new LoggerChannelService(
                (id, selected) -> { }, failure -> { throw failure; });
        service.addListener(channels -> {
            updates.incrementAndGet();
            latest.set(channels);
        });

        service.replaceChannels(Arrays.asList(
                channel("rpm", "Engine Speed", true),
                channel("boost", "Boost", false)));

        assertEquals(2, updates.get());
        assertEquals("rpm", latest.get().get(0).getParameterId());
        assertTrue(latest.get().get(0).isSelected());
        assertFalse(latest.get().get(1).isSelected());
        try {
            latest.get().clear();
        } catch (UnsupportedOperationException expected) {
            return;
        }
        throw new AssertionError("channel snapshots must be immutable");
    }

    @Test
    public void commandsOnlyKnownChannelsWhoseSelectionChanges() {
        AtomicInteger commands = new AtomicInteger();
        AtomicReference<String> selectedId = new AtomicReference<String>();
        LoggerChannelService service = new LoggerChannelService(
                (id, selected) -> {
                    commands.incrementAndGet();
                    selectedId.set(id + ":" + selected);
                }, failure -> { throw failure; });
        service.replaceChannels(Arrays.asList(
                channel("rpm", "Engine Speed", false)));

        service.setSelected("missing", true);
        service.setSelected("rpm", false);
        service.setSelected("rpm", true);

        assertEquals(1, commands.get());
        assertEquals("rpm:true", selectedId.get());
    }

    private static LoggerChannel channel(String id, String name,
            boolean selected) {
        return new LoggerChannel(id, name, "rpm",
                LoggerChannelKind.PARAMETER, selected);
    }
}
