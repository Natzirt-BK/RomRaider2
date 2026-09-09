/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider2.javafx;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.romraider.io.BinaryFileIO;
import com.romraider.util.XmlSecurity;
import org.w3c.dom.Element;

/** Definition metadata only: never constructs a protocol or opens an adapter. */
final class FxLoggerConnectionChoices {
    private final Map<String, Map<String, List<String>>> choices;
    private final java.util.Set<String> fastPolling = new java.util.HashSet<>();

    private FxLoggerConnectionChoices(Map<String, Map<String, List<String>>> choices) {
        this.choices = choices;
    }

    static FxLoggerConnectionChoices empty() {
        return new FxLoggerConnectionChoices(Map.of());
    }

    static FxLoggerConnectionChoices read(String path) throws Exception {
        if (path == null || path.isBlank()) throw new IOException("Choose a logger definition first.");
        byte[] bytes = BinaryFileIO.read(new File(path), 32L * 1024 * 1024);
        var factory = XmlSecurity.newDocumentBuilderFactory();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        Element root = factory.newDocumentBuilder().parse(new ByteArrayInputStream(bytes)).getDocumentElement();
        if (!"logger".equals(root.getTagName())) throw new IOException("Choose a logger XML, not an editor definition.");
        Map<String, Map<String, List<String>>> choices = new LinkedHashMap<>();
        java.util.Set<String> fastPolling = new java.util.HashSet<>();
        for (Element group : children(root, "protocols")) {
            for (Element protocol : children(group, "protocol")) {
                String id = protocol.getAttribute("id");
                Map<String, List<String>> transports = new LinkedHashMap<>();
                for (Element transportGroup : children(protocol, "transports")) {
                    for (Element transport : children(transportGroup, "transport")) {
                        String transportId = transport.getAttribute("id").toUpperCase(Locale.ROOT);
                        if (!implemented(id, transportId)) continue;
                        for (Element module : children(transport, "module")) {
                            if ("true".equalsIgnoreCase(module.getAttribute("fastpoll")))
                                fastPolling.add(id + "/" + transportId + "/" + module.getAttribute("id"));
                        }
                        List<String> modules = children(transport, "module").stream()
                                .map(module -> module.getAttribute("id"))
                                .filter(module -> !module.isBlank()).distinct().toList();
                        if (!modules.isEmpty()) transports.putIfAbsent(transportId, modules);
                    }
                }
                if (!transports.isEmpty()) choices.putIfAbsent(id, transports);
            }
        }
        if (choices.isEmpty()) throw new IOException("No supported protocol/transport combinations in this definition.");
        FxLoggerConnectionChoices result = new FxLoggerConnectionChoices(choices);
        result.fastPolling.addAll(fastPolling);
        return result;
    }

    boolean supportsFastPolling(String protocol, String transport, String module) {
        return fastPolling.contains(protocol + "/" + transport + "/" + module);
    }

    List<String> protocols() { return List.copyOf(choices.keySet()); }

    List<String> transports(String protocol) {
        if (protocol == null) return List.of();
        return List.copyOf(choices.getOrDefault(protocol, Map.of()).keySet());
    }

    List<String> modules(String protocol, String transport) {
        if (protocol == null || transport == null) return List.of();
        return choices.getOrDefault(protocol, Map.of()).getOrDefault(transport, List.of());
    }

    private static boolean implemented(String protocol, String transport) {
        if (!protocol.matches("[A-Z][A-Z0-9]*") || !transport.matches("[A-Z][A-Z0-9]*")) return false;
        // Match the existing reflection factories without initializing any classes.
        String prefix = "/com/romraider/";
        return FxLoggerConnectionChoices.class.getResource(prefix + "io/protocol/"
                + protocol.toLowerCase(Locale.ROOT) + "/" + transport.toLowerCase(Locale.ROOT)
                + "/" + protocol + "LoggerProtocol.class") != null
                && FxLoggerConnectionChoices.class.getResource(prefix + "logger/ecu/comms/io/connection/"
                + protocol + "LoggerConnection.class") != null;
    }

    private static List<Element> children(Element parent, String name) {
        List<Element> result = new ArrayList<>();
        for (var node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node instanceof Element child && name.equals(child.getTagName())) result.add(child);
        }
        return result;
    }
}
