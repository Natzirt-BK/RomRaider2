/*
 * RomRaider Open-Source Tuning, Logging and Reflashing
 * Copyright (C) 2006-2014 RomRaider.com
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, write to the Free Software Foundation, Inc.,
 * 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 */

package com.romraider.logger.ecu.profile.xml;

import com.romraider.logger.ecu.profile.UserProfile;
import com.romraider.logger.ecu.profile.UserProfileImpl;
import com.romraider.logger.ecu.profile.UserProfileItem;
import com.romraider.logger.ecu.profile.UserProfileItemImpl;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;
import java.util.LinkedHashMap;
import java.util.Map;

public final class UserProfileHandler extends DefaultHandler {
    private static final String SELECTED = "selected";
    private static final String TAG_PROFILE = "profile";
    private static final String TAG_PARAMETER = "parameter";
    private static final String TAG_SWITCH = "switch";
    private static final String TAG_EXTERNAL = "external";
    private static final String ATTR_PROTOCOL = "protocol";
    private static final String ATTR_ID = "id";
    private static final String ATTR_UNITS = "units";
    private static final String ATTR_LIVE_DATA = "livedata";
    private static final String ATTR_GRAPH = "graph";
    private static final String ATTR_DASH = "dash";
    private Map<String, UserProfileItem> params;
    private Map<String, UserProfileItem> switches;
    private Map<String, UserProfileItem> external;
    private String protocol;
    private boolean foundProfile;
    private final java.util.List<String> selectedIds = new java.util.ArrayList<>();
    private final java.util.Map<Integer, String> orderedIds = new java.util.TreeMap<>();

    public void startDocument() {
        params = new LinkedHashMap<String, UserProfileItem>();
        switches = new LinkedHashMap<String, UserProfileItem>();
        external = new LinkedHashMap<String, UserProfileItem>();
        protocol = null;
        foundProfile = false;
        selectedIds.clear(); orderedIds.clear();
    }

    public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
        if (!foundProfile && !TAG_PROFILE.equals(qName)) throw new SAXException("Logger profile root is missing");
        if (TAG_PROFILE.equals(qName)) {
            if (foundProfile) throw new SAXException("Nested logger profiles are not supported");
            foundProfile = true;
            protocol = attributes.getValue(ATTR_PROTOCOL);
        } else if (TAG_PARAMETER.equals(qName)) {
            add(params, attributes);
        } else if (TAG_SWITCH.equals(qName)) {
            add(switches, attributes);
        } else if (TAG_EXTERNAL.equals(qName)) {
            add(external, attributes);
        }
    }

    public UserProfile getUserProfile() {
        if (!foundProfile) throw new IllegalArgumentException("Logger profile is missing");
        if (!orderedIds.isEmpty() && (orderedIds.size() != selectedIds.size()
                || !orderedIds.keySet().equals(new java.util.HashSet<>(java.util.stream.IntStream.range(0, selectedIds.size()).boxed().toList()))))
            throw new IllegalArgumentException("Profile selection order is incomplete");
        return new UserProfileImpl(params, switches, external, protocol,
                orderedIds.isEmpty() ? selectedIds : new java.util.ArrayList<>(orderedIds.values()));
    }

    private void add(Map<String, UserProfileItem> group, Attributes attributes) throws SAXException {
        String id = attributes.getValue(ATTR_ID);
        UserProfileItem item = getUserProfileItem(attributes);
        if (id == null || id.isEmpty() || group.put(id, item) != null) throw new SAXException("Missing or duplicate profile ID");
        boolean selected = item.isLiveDataSelected() || item.isGraphSelected() || item.isDashSelected();
        if (selected) selectedIds.add(id);
        String order = attributes.getValue("rr2-order");
        if (order != null) {
            if (!selected || !order.matches("0|[1-9][0-9]{0,8}")) throw new SAXException("Invalid profile selection order");
            if (orderedIds.put(Integer.parseInt(order), id) != null) throw new SAXException("Duplicate profile selection order");
        }
    }

    private UserProfileItem getUserProfileItem(Attributes attributes) {
        return new UserProfileItemImpl(
                attributes.getValue(ATTR_UNITS),
                SELECTED.equalsIgnoreCase(attributes.getValue(ATTR_LIVE_DATA)),
                SELECTED.equalsIgnoreCase(attributes.getValue(ATTR_GRAPH)),
                SELECTED.equalsIgnoreCase(attributes.getValue(ATTR_DASH))
        );
    }

}
