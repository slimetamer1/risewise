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
package com.better.alarm.model

import android.annotation.SuppressLint
import com.better.alarm.configuration.Prefs
import com.better.alarm.configuration.Store
import com.better.alarm.interfaces.Alarm
import com.better.alarm.interfaces.IAlarmsManager
import com.better.alarm.logger.Logger
import com.better.alarm.persistance.DatabaseQuery
import com.better.alarm.persistance.DatastoreMigration

/** The Alarms implements application domain logic */
@SuppressLint("UseSparseArrays")
class Alarms(
    private val prefs: Prefs,
    private val store: Store,
    private val calendars: Calendars,
    private val alarmsScheduler: IAlarmsScheduler,
    private val broadcaster: AlarmCore.IStateNotifier,
    private val alarmsRepository: AlarmsRepository,
    private val logger: Logger,
    private val databaseQuery: DatabaseQuery,
) : IAlarmsManager, DatastoreMigration {
  private val alarms: MutableMap<Int, AlarmCore> = mutableMapOf()

  fun start() {
    alarms.putAll(alarmsRepository.query().associate { store -> store.id to createAlarm(store) })
    alarms.values.forEach { it.start() }
    if (!alarmsRepository.initialized) {
      migrateDatabase()
      if (alarms.isEmpty()) {
        insertDefaultAlarms()
      }
    }
  }

  fun refresh() {
    alarms.values.forEach { alarmCore -> alarmCore.refresh() }
  }

  fun onTimeSet() {
    alarms.values.forEach { alarmCore -> alarmCore.onTimeSet() }
  }

  override fun getAlarm(alarmId: Int): AlarmCore? {
    return alarms[alarmId].also {
      if (it == null) {
        val exception =
            RuntimeException("Alarm with id $alarmId not found!").apply { fillInStackTrace() }
        logger.error(exception) { "Alarm with id $alarmId not found!" }
      }
    }
  }

  override fun createNewAlarm(): Alarm {
    val alarm = createAlarm(alarmsRepository.create())
    alarms[alarm.id] = alarm
    alarm.start()
    return alarm
  }

  fun onAlarmFired(alarm: AlarmCore, calendarType: CalendarType?) {
    // TODO this should not be needed
    alarmsScheduler.removeAlarm(alarm.id)
    alarm.onAlarmFired()
  }

  override fun enable(alarm: AlarmValue, enable: Boolean) {
    alarms.getValue(alarm.id).enable(enable)
  }

  private fun createAlarm(alarmStore: AlarmStore): AlarmCore {
    return AlarmCore(
        alarmStore,
        logger,
        alarmsScheduler,
        broadcaster,
        prefs,
        store,
        calendars,
        onDelete = { alarms.remove(it) },
    )
  }

  override fun drop() {
    alarms.values.toList().forEach { it.delete() }
    alarms.clear()
  }

  override fun insertDefaultAlarms() {
    createNewAlarm().edit {
      copy(
          daysOfWeek = DaysOfWeek(31),
          hour = 8,
          minutes = 30,
      )
    }
    createNewAlarm().edit {
      copy(
          daysOfWeek = DaysOfWeek(96),
          hour = 9,
          minutes = 0,
      )
    }
  }

  override fun migrateDatabase() {
    val alarmsInDatabase = databaseQuery.query()
    logger.warning {
      "migrateDatabase() found ${alarmsInDatabase.size} alarms in SQLite database..."
    }
    alarmsInDatabase.forEach { restored ->
      logger.warning { "Migrating $restored from SQLite to DataStore" }
      val alarmStore = alarmsRepository.create()
      alarmStore.modify { restored.copy(id = alarmStore.id) }
      val alarm = createAlarm(alarmStore)
      alarms[alarm.id] = alarm
      alarm.start()
      databaseQuery.delete(restored.id)
    }
  }
}
