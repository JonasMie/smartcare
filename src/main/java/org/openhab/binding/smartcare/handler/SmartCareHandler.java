/**
 * Copyright (c) 2014-2015 openHAB UG (haftungsbeschraenkt) and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.smartcare.handler;

import static org.openhab.binding.smartcare.SmartCareBindingConstants.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.math.BigDecimal;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.smarthome.config.core.Configuration;
import org.eclipse.smarthome.core.library.types.HSBType;
import org.eclipse.smarthome.core.library.types.OnOffType;
import org.eclipse.smarthome.core.library.types.PercentType;
import org.eclipse.smarthome.core.library.types.PlayPauseType;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingStatus;
import org.eclipse.smarthome.core.thing.binding.BaseThingHandler;
import org.eclipse.smarthome.core.types.Command;
import org.eclipse.smarthome.core.types.RefreshType;
import org.eclipse.smarthome.core.types.State;
import org.eclipse.smarthome.core.types.TypeParser;
import org.eclipse.smarthome.core.types.UnDefType;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link SmartCareHandler} is responsible for handling commands, which are
 * sent to one of the channels.
 *
 * @author Jonas Miederer - Initial contribution
 */
public class SmartCareHandler extends BaseThingHandler {

    private Logger logger = LoggerFactory.getLogger(SmartCareHandler.class);
    private JSONArray deviceData = null;
    ScheduledFuture<?> refreshJob;
    private BigDecimal refresh;

    @Override
    public void initialize() {
        logger.debug("Initializing SmartCare handler.");
        super.initialize();

        Configuration config = getThing().getConfiguration();

        try {
            refresh = (BigDecimal) config.get("refresh");
        } catch (Exception e) {
            logger.debug("Cannot set refresh parameter.", e);
        }

        if (refresh == null) {
            // let's go for the default
            refresh = new BigDecimal(10);
        }

        startAutomaticRefresh();
    }

    public SmartCareHandler(Thing thing) {
        super(thing);
    }

    @Override
    public void dispose() {
        refreshJob.cancel(true);
    }

    private void startAutomaticRefresh() {
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                try {
                    boolean success = updateDeviceData();
                    if (success) {
                        updateState(new ChannelUID(getThing().getUID(), CHANNEL_SONOS),
                                getState(deviceIds.get(CHANNEL_SONOS)));
                        updateState(new ChannelUID(getThing().getUID(), CHANNEL_HUE),
                                getState(deviceIds.get(CHANNEL_HUE)));
                    }
                } catch (Exception e) {
                    logger.debug("Exception occurred during execution: {}", e.getMessage(), e);
                }
            }
        };

        refreshJob = scheduler.scheduleAtFixedRate(runnable, 0, refresh.intValue(), TimeUnit.SECONDS);
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        if (command instanceof RefreshType) {
            boolean success = updateDeviceData();
            if (success) {
                switch (channelUID.getId()) {
                    case CHANNEL_SONOS:
                        updateState(channelUID, getState(deviceIds.get(channelUID.getId())));
                        break;
                    case CHANNEL_HUE:
                        break;
                }
            }
        }
    }

    private synchronized boolean updateDeviceData() {

        try {
            deviceData = getDeviceData();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        if (deviceData != null) {
            updateStatus(ThingStatus.ONLINE);
            return true;
        }
        return false;
    }

    private JSONArray getDeviceData() throws IOException {
        return readJsonFromUrl(API_URL);
    }

    public JSONArray readJsonFromUrl(String url) throws IOException {
        InputStream is = new URL(url).openStream();
        try {
            BufferedReader rd = new BufferedReader(new InputStreamReader(is, Charset.forName("UTF-8")));
            String jsonText = readAll(rd);
            JSONArray json = new JSONArray(jsonText);
            return json;
        } catch (JSONException e) {
            logger.error(e.getMessage());
            return null;
        } finally {
            is.close();
        }
    }

    private static String readAll(Reader rd) throws IOException {
        StringBuilder sb = new StringBuilder();
        int cp;
        while ((cp = rd.read()) != -1) {
            sb.append((char) cp);
        }
        return sb.toString();
    }

    private State getState(int deviceId) {
        if (deviceData != null) {
            for (int i = 0; i < deviceData.length(); i++) {
                JSONObject patientDevice = deviceData.getJSONObject(i);
                if (deviceId == patientDevice.getInt("deviceId")) {
                    return TypeParser.parseState(new ArrayList<Class<? extends State>>() {
                        {
                            add(HSBType.class);
                            add(OnOffType.class);
                            add(PercentType.class);
                            add(PlayPauseType.class);
                        }
                    }, patientDevice.getString("state"));
                }
            }
        }
        return UnDefType.UNDEF;
    }
}
