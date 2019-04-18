/*
 * Copyright (C) 2017 Yuriy Kulikov yuriy.kulikov.87@gmail.com
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

package com.better.alarm.presenter

import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Bundle
import android.support.v4.app.Fragment
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import com.better.alarm.R
import com.better.alarm.configuration.AlarmApplication.container
import com.better.alarm.configuration.Store
import com.better.alarm.interfaces.AlarmEditor
import com.better.alarm.interfaces.IAlarmsManager
import com.better.alarm.interfaces.Intents
import com.better.alarm.logger.Logger
import com.better.alarm.lollipop
import com.better.alarm.util.Optional
import com.better.alarm.view.showDialog
import com.better.alarm.view.summary
import com.f2prateek.rx.preferences2.RxSharedPreferences
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable
import io.reactivex.disposables.Disposables
import io.reactivex.subjects.BehaviorSubject
import io.reactivex.subjects.Subject
import java.util.*

/**
 * Details activity allowing for fine-grained alarm modification
 *
 * TODO create - unknown ringtone
 */
class AlarmDetailsFragment : Fragment() {
    private val alarms: IAlarmsManager = container().alarms()
    private val logger: Logger = container().logger()
    private val rxSharedPreferences: RxSharedPreferences = container().rxPrefs()
    private var disposables = CompositeDisposable()

    private var backButtonSub: Disposable = Disposables.disposed()
    private var disposableDialog = Disposables.disposed()

    private val alarmsListActivity by lazy { activity as AlarmsListActivity }
    private val store: UiStore by lazy { AlarmsListActivity.uiStore(alarmsListActivity) }
    private val mLabel: EditText by lazy { fragmentView.findViewById(R.id.details_label) as EditText }
    private val rowHolder: RowHolder by lazy { RowHolder(fragmentView, alarmId) }
    private val mRingtoneRow by lazy { fragmentView.findViewById(R.id.details_ringtone_row) as LinearLayout }
    private val mRingtoneSummary by lazy { fragmentView.findViewById(R.id.details_ringtone_summary) as TextView }
    private val mRepeatRow by lazy { fragmentView.findViewById(R.id.details_repeat_row) as LinearLayout }
    private val mRepeatSummary by lazy { fragmentView.findViewById(R.id.details_repeat_summary) as TextView }
    private val mPreAlarmRow by lazy {
        fragmentView.findViewById(R.id.details_prealarm_row) as LinearLayout
    }
    private val mPreAlarmCheckBox by lazy {
        fragmentView.findViewById(R.id.details_prealarm_checkbox) as CheckBox
    }

    private val editor: Subject<AlarmEditor> = BehaviorSubject.create()

    private val isNewAlarm: Boolean by lazy { arguments.getBoolean(Store.IS_NEW_ALARM) }
    private val alarmId: Int by lazy { arguments.getInt(Intents.EXTRA_ID) }

    lateinit var fragmentView: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        editor.onNext(alarms.getAlarm(alarmId).edit())
    }

    override fun onCreateView(inflater: LayoutInflater?, container: ViewGroup?, savedInstanceState: Bundle?): View {
        logger.d("Inflating layout")
        editor.subscribe { logger.d("---- $it") }

        val view = inflater!!.inflate(R.layout.details_activity, container, false)
        this.fragmentView = view

        rowHolder.run {
            onOff().setOnClickListener {
                modify("onOff") { editor ->
                    editor.withIsEnabled(!editor.isEnabled)
                }
            }

            // detailsButton().visibility = View.INVISIBLE
            daysOfWeek().visibility = View.INVISIBLE
            label().visibility = View.INVISIBLE

            lollipop {
                digitalClock().transitionName = "clock$alarmId"
                container().transitionName = "onOff$alarmId"
                detailsButton().transitionName = "detailsButton$alarmId"
            }

            digitalClock().setLive(false)
            digitalClock().setOnClickListener {
                disposableDialog = TimePickerDialogFragment.showTimePicker(alarmsListActivity.supportFragmentManager).subscribe(pickerConsumer)
            }

            rowView().setOnClickListener {
                saveAlarm()
            }
        }

        view.findViewById<View>(R.id.details_activity_button_save).setOnClickListener { saveAlarm() }
        view.findViewById<View>(R.id.details_activity_button_revert).setOnClickListener { revert() }

        if (isNewAlarm) {
            disposableDialog = TimePickerDialogFragment.showTimePicker(alarmsListActivity.supportFragmentManager)
                    .subscribe(pickerConsumer)
        }

        //pre-alarm
        mPreAlarmRow.setOnClickListener {
            modify("Pre-alarm") { editor -> editor.with(isPrealarm = !editor.isPrealarm, enabled = true) }
        }

        mRepeatRow.setOnClickListener {
            editor.firstOrError()
                    .flatMap { editor -> editor.daysOfWeek.showDialog(context) }
                    .subscribe { daysOfWeek ->
                        modify("Repeat dialog") { prev -> prev.withDaysOfWeek(daysOfWeek).withIsEnabled(true) }
                    }
        }

        mRingtoneRow.setOnClickListener {
            editor.firstOrError().subscribe { editor ->
                startActivityForResult(Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                    // TODO corner cases: silent, undef
                    putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(editor.alertString))

                    putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                    putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))

                    putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
                    putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                }, 42)
            }
        }

        mLabel.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                editor.take(1)
                        .filter { it.label != s.toString() }
                        .subscribe {
                            modify("Label") { prev -> prev.withLabel(s.toString()).withIsEnabled(true) }
                        }
            }
        })

        return view
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (data != null && requestCode == 42) {
            // TODO proper silent alarms
            val alert = data.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)?.toString()
                    ?: "silent"

            logger.d("onActivityResult $alert")

            modify("Ringtone picker") { prev ->
                prev.with(alertString = alert, enabled = true)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        disposables = CompositeDisposable()

        // TODO split bindings
        disposables.add(editor
                .distinctUntilChanged()
                .subscribe { editor ->
                    rowHolder.digitalClock().updateTime(Calendar.getInstance().apply {
                        set(Calendar.HOUR_OF_DAY, editor.hour)
                        set(Calendar.MINUTE, editor.minutes)
                    })

                    rowHolder.onOff().isChecked = editor.isEnabled
                    mPreAlarmCheckBox.isChecked = editor.isPrealarm

                    mRepeatSummary.text = editor.daysOfWeek.summary(context)
                    mRingtoneSummary.text = RingtoneManager.getRingtone(context, Uri.parse(editor.alertString)).getTitle(context)

                    if (editor.label != mLabel.text.toString()) {
                        mLabel.setText(editor.label)
                    }
                })

        //pre-alarm duration, if set to "none", remove the option
        disposables.add(rxSharedPreferences.getString("prealarm_duration", "-1")
                .asObservable()
                .subscribe { value ->
                    mPreAlarmRow.visibility = if (value.toInt() == -1) View.GONE else View.VISIBLE
                })

        backButtonSub = store.onBackPressed().subscribe { saveAlarm() }
        store.transitioningToNewAlarmDetails().onNext(false)
    }

    override fun onPause() {
        super.onPause()
        disposableDialog.dispose()
        backButtonSub.dispose()
        disposables.dispose()
    }

    private fun saveAlarm() {
        editor.firstOrError().subscribe { editorToSave ->
            editorToSave.commit()
            store.hideDetails(rowHolder)
        }
    }

    private fun revert() {
        editor.firstOrError().subscribe { edited ->
            // "Revert" on a newly created alarm should delete it.
            if (isNewAlarm) {
                alarms.delete(edited)
            }
            // else do not save changes
            store.hideDetails(rowHolder)
        }
    }

    private val pickerConsumer = { picked: Optional<PickedTime> ->
        if (picked.isPresent()) {
            modify("Picker") { editor: AlarmEditor ->
                editor.with(hour = picked.get().hour,
                        minutes = picked.get().minute,
                        enabled = true)
            }
        }
    }

    private fun modify(reason: String, function: (AlarmEditor) -> AlarmEditor) {
        logger.d("Performing modification because of $reason")
        editor.firstOrError().subscribe { ed -> editor.onNext(function.invoke(ed)) }
    }
}