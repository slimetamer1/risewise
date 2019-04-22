package com.better.alarm.model

import android.content.Context
import com.better.alarm.background.Event
import com.better.alarm.configuration.Store
import com.better.alarm.interfaces.Intents
import com.better.alarm.model.AlarmCore.IStateNotifier

/**
 * Broadcasts alarm state with an intent
 *
 * @author Yuriy
 */
class AlarmStateNotifier(private val mContext: Context, private val store: Store) : IStateNotifier {
    override fun broadcastAlarmState(id: Int, action: String) {
        val event = when (action) {
            Intents.ALARM_ALERT_ACTION -> Event.AlarmEvent(id)
            Intents.ALARM_PREALARM_ACTION -> Event.PrealarmEvent(id)
            Intents.ACTION_MUTE -> Event.MuteEvent()
            Intents.ACTION_DEMUTE -> Event.DemuteEvent()
            Intents.ACTION_SOUND_EXPIRED -> Event.Autosilenced(id)
            Intents.ALARM_SNOOZE_ACTION -> Event.SnoozedEvent(id)
            Intents.ACTION_CANCEL_SNOOZE -> Event.CancelSnoozedEvent(id)
            Intents.ALARM_DISMISS_ACTION -> Event.DismissEvent(id)
            else -> throw RuntimeException("Unknown action $action")
        }
        store.events.onNext(event)
    }
}
