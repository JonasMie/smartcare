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
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.smarthome.config.core.Configuration;
import org.eclipse.smarthome.core.library.types.HSBType;
import org.eclipse.smarthome.core.library.types.IncreaseDecreaseType;
import org.eclipse.smarthome.core.library.types.OnOffType;
import org.eclipse.smarthome.core.library.types.OpenClosedType;
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
    private JSONArray sleepData = null;
    ScheduledFuture<?> refreshJob;
    private BigDecimal refresh;

    private SimpleDateFormat parserSDF;

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
                    boolean success = updateData();
                    if (success) {
                        // Update Sonos state
                        updateState(new ChannelUID(getThing().getUID(), CHANNEL_SONOS),
                                getState(deviceIds.get(CHANNEL_SONOS)));
                        // Update Hue state
                        updateState(new ChannelUID(getThing().getUID(), CHANNEL_HUE),
                                getState(deviceIds.get(CHANNEL_HUE)));
                        // Update door state
                        updateState(new ChannelUID(getThing().getUID(), CHANNEL_DOOR),
                                getState(deviceIds.get(CHANNEL_DOOR)));
                        // Update rollershutter state
                        updateState(new ChannelUID(getThing().getUID(), CHANNEL_ROLLERSHUTTER),
                                getState(deviceIds.get(CHANNEL_ROLLERSHUTTER)));
                        // Update Sleeping state
                        postCommand(new ChannelUID(getThing().getUID(), CHANNEL_SLEEP), getSleepState());
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
            boolean success = updateData();
            if (success) {
                updateState(channelUID, getState(deviceIds.get(channelUID.getId())));
            }
        }
    }

    private synchronized boolean updateData() {

        try {
            deviceData = getDeviceData();
            sleepData = getSleepData();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        if (deviceData != null && sleepData != null) {
            updateStatus(ThingStatus.ONLINE);
            return true;
        }
        return false;
    }

    private JSONArray getDeviceData() throws IOException {
        return readJsonFromUrl(API_URL);
    }

    private JSONArray getSleepData() throws IOException {
        return readJsonFromUrl(SLEEP_URL);
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
                            add(OpenClosedType.class);
                        }
                    }, patientDevice.getString("state"));
                }
            }
        }
        return UnDefType.UNDEF;
    }

    private Command getSleepState() {
        parserSDF = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        if (sleepData != null) {
            for (int i = 0; i < sleepData.length(); i++) {
                JSONObject sleepcycle = sleepData.getJSONObject(i);
                Date from;
                Date to;
                Date now = new Date();
                try {
                    from = parserSDF.parse(sleepcycle.getString("datefrom"));
                    if (sleepcycle.isNull("dateto") && now.after(from)) {
                        // person is sleeping
                        return IncreaseDecreaseType.DECREASE;
                    } else {
                        to = parserSDF.parse(sleepcycle.getString("dateto"));
                        if (now.after(from) && now.before(to)) {
                            // person is sleeping

                        }
                    }
                } catch (JSONException | ParseException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
        }
        return IncreaseDecreaseType.INCREASE;
    }
}
