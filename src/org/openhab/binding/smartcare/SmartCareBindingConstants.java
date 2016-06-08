/**
 * Copyright (c) 2014-2015 openHAB UG (haftungsbeschraenkt) and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.smartcare;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.smarthome.core.thing.ThingTypeUID;

/**
 * The {@link SmartCareBinding} class defines common constants, which are
 * used across the whole binding.
 *
 * @author Jonas Miederer - Initial contribution
 */
public class SmartCareBindingConstants {

    public static final String BINDING_ID = "smartcare";

    // List of all Thing Type UIDs
    public final static ThingTypeUID THING_TYPE_SC = new ThingTypeUID(BINDING_ID, "sc");

    // API-Url
    public final static String API_URL_OLD = "http://www.smart-home-care.de/public/patientdevice.json";
    public final static String API_URL = "http://smartcare.mi.hdm-stuttgart.de/api/public/patientdevice.json";
    public final static String SLEEP_URL = "http://smartcare.mi.hdm-stuttgart.de/api/public/sleepcycle.json";

    // List of all Channel ids
    public final static String CHANNEL_SONOS = "sonos";
    public final static String CHANNEL_HUE = "hue";
    public final static String CHANNEL_DOOR = "door";
    public final static String CHANNEL_SLEEP = "sleep";
    public final static String CHANNEL_ROLLERSHUTTER = "rollershutter";

    public final static Map<String, Integer> deviceIds = new HashMap<String, Integer>() {

        {
            put(CHANNEL_SONOS, 4);
            put(CHANNEL_HUE, 3);
            put(CHANNEL_DOOR, 5);
            put(CHANNEL_ROLLERSHUTTER, 2);
        };
    };

}
