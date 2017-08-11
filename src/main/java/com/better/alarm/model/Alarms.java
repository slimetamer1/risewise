/*
 * Copyright (C) 2012 Yuriy Kulikov yuriy.kulikov.87@gmail.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.better.alarm.model;

import android.annotation.SuppressLint;

import com.better.alarm.interfaces.Alarm;
import com.better.alarm.interfaces.IAlarmsManager;
import com.better.alarm.persistance.DatabaseQuery;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.reactivex.annotations.NonNull;
import io.reactivex.functions.Consumer;

/**
 * The Alarms implements application domain logic
 */
@SuppressLint("UseSparseArrays")
public class Alarms implements IAlarmsManager {
    private final IAlarmsScheduler mAlarmsScheduler;

    private final Map<Integer, AlarmCore> alarms;
    private DatabaseQuery query;
    private final AlarmCoreFactory factory;
    private final ContainerFactory containerFactory;

    public Alarms(IAlarmsScheduler alarmsScheduler, DatabaseQuery query, final AlarmCoreFactory factory, ContainerFactory containerFactory) {
        this.mAlarmsScheduler = alarmsScheduler;
        this.query = query;
        this.factory = factory;
        this.containerFactory = containerFactory;
        this.alarms = new HashMap<Integer, AlarmCore>();
    }

    public void start() {
        query.query().subscribe(new Consumer<List<AlarmContainer>>() {
            @Override
            public void accept(@NonNull List<AlarmContainer> alarmContainers) throws Exception {
                for (AlarmContainer container : alarmContainers) {
                    final AlarmCore a = factory.create(container);
                    alarms.put(a.getId(), a);
                    //TODO a.refresh();, but with a delay or something. We do not want to refresh the alarms that have just fired, right?
                }
            }
        });
    }

    public void refresh() {
        for (AlarmCore alarmCore : alarms.values()) {
            alarmCore.refresh();
        }
    }

    public void onTimeSet() {
        for (AlarmCore alarmCore : alarms.values()) {
            alarmCore.onTimeSet();
        }
    }

    @Override
    public AlarmCore getAlarm(int alarmId) {
        AlarmCore alarm = alarms.get(alarmId);
        if (alarm != null) return alarm;
        else throw new IllegalStateException("AlarmID " + alarmId + " could not be resolved");
    }

    @Override
    public Alarm createNewAlarm() {
        AlarmCore alarm = factory.create(containerFactory.create());
        alarms.put(alarm.getId(), alarm);
        return alarm;
    }

    @Override
    public void delete(AlarmValue alarm) {
        alarms.get(alarm.getId()).delete();
        alarms.remove(alarm.getId());
    }

    @Override
    public void delete(Alarm alarm) {
        alarm.delete();
        alarms.remove(alarm.getId());
    }

    public void onAlarmFired(AlarmCore alarm, CalendarType calendarType) {
        //TODO this should not be needed
        mAlarmsScheduler.onAlarmFired(alarm.getId());
        alarm.onAlarmFired(calendarType);
    }

    /**
     * A convenience method to enable or disable an alarm
     *
     * @param enable corresponds to the ENABLED column
     */
    @Override
    public void enable(Alarm alarm, boolean enable) {
        alarm.enable(enable);
    }

    @Override
    public void enable(AlarmValue alarm, boolean enable) {
        alarms.get(alarm.getId()).enable(enable);
    }

    @Override
    public void snooze(Alarm alarm) {
        alarm.snooze();
    }

    @Override
    public void dismiss(Alarm alarm) {
        alarm.dismiss();
    }
}
