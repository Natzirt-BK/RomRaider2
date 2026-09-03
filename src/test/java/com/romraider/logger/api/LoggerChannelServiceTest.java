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

    @Test
    public void bulkSelectionUsesOneCommandWithOnlyChannelsThatChange() {
        AtomicInteger commands = new AtomicInteger();
        AtomicReference<List<String>> selectedIds =
                new AtomicReference<List<String>>();
        LoggerChannelService service = new LoggerChannelService(
                (ids, selected) -> {
                    commands.incrementAndGet();
                    selectedIds.set(Arrays.asList(ids.toArray(new String[0])));
                    assertFalse(selected);
                }, (id, option) -> { }, failure -> { throw failure; });
        service.replaceChannels(Arrays.asList(
                channel("rpm", "Engine Speed", true),
                channel("boost", "Boost", false),
                channel("load", "Engine Load", true)));

        service.setSelected(Arrays.asList("rpm", "boost", "missing", "load"),
                false);

        assertEquals(1, commands.get());
        assertEquals(Arrays.asList("rpm", "load"), selectedIds.get());
    }

    @Test
    public void unitCommandsUseNeutralOptionIdsAndIgnoreCurrentChoice() {
        AtomicInteger commands = new AtomicInteger();
        AtomicReference<String> choice = new AtomicReference<String>();
        LoggerChannelService service = new LoggerChannelService(
                (ids, selected) -> { },
                (id, option) -> {
                    commands.incrementAndGet();
                    choice.set(id + ":" + option);
                }, failure -> { throw failure; });
        LoggerChannel channel = new LoggerChannel(
                "temperature", "Coolant Temperature", "C",
                LoggerChannelKind.PARAMETER, true, Arrays.asList(
                        new LoggerChannelUnitOption("0", "C", true),
                        new LoggerChannelUnitOption("1", "F", false)));
        service.replaceChannels(Arrays.asList(channel));

        service.setUnitOption("temperature", "0");
        service.setUnitOption("temperature", "1");

        assertEquals(1, commands.get());
        assertEquals("temperature:1", choice.get());
    }

    private static LoggerChannel channel(String id, String name,
            boolean selected) {
        return new LoggerChannel(id, name, "rpm",
                LoggerChannelKind.PARAMETER, selected);
    }
}
