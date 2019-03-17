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
package com.better.alarm.configuration;

import android.app.AlarmManager;
import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.media.RingtoneManager;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.view.ViewConfiguration;

import com.better.alarm.R;
import com.better.alarm.alert.BackgroundNotifications;
import com.better.alarm.background.AlertServicePusher;
import com.better.alarm.background.Event;
import com.better.alarm.logger.LogcatLogWriter;
import com.better.alarm.logger.Logger;
import com.better.alarm.logger.LoggingExceptionHandler;
import com.better.alarm.logger.StartupLogWriter;
import com.better.alarm.model.AlarmCore;
import com.better.alarm.model.AlarmCoreFactory;
import com.better.alarm.model.AlarmSetter;
import com.better.alarm.model.AlarmStateNotifier;
import com.better.alarm.model.AlarmValue;
import com.better.alarm.model.Alarms;
import com.better.alarm.model.AlarmsScheduler;
import com.better.alarm.model.Calendars;
import com.better.alarm.model.ImmediateHandlerFactory;
import com.better.alarm.persistance.DatabaseQuery;
import com.better.alarm.persistance.PersistingContainerFactory;
import com.better.alarm.presenter.DynamicThemeHandler;
import com.better.alarm.presenter.ScheduledReceiver;
import com.better.alarm.presenter.ToastPresenter;
import com.better.alarm.statemachine.HandlerFactory;
import com.better.alarm.util.Optional;
import com.f2prateek.rx.preferences2.Preference;
import com.f2prateek.rx.preferences2.RxSharedPreferences;

import org.acra.ReportField;
import org.acra.annotation.ReportsCrashes;

import java.io.File;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import io.reactivex.Maybe;
import io.reactivex.MaybeEmitter;
import io.reactivex.MaybeOnSubscribe;
import io.reactivex.Single;
import io.reactivex.annotations.NonNull;
import io.reactivex.functions.Consumer;
import io.reactivex.functions.Function;
import io.reactivex.subjects.BehaviorSubject;
import io.reactivex.subjects.PublishSubject;

@ReportsCrashes(
        mailTo = "yuriy.kulikov.87@gmail.com",
        applicationLogFileLines = 150,
        customReportContent = {
                ReportField.IS_SILENT,
                ReportField.APP_VERSION_CODE,
                ReportField.PHONE_MODEL,
                ReportField.ANDROID_VERSION,
                ReportField.CUSTOM_DATA,
                ReportField.STACK_TRACE,
                ReportField.SHARED_PREFERENCES,
        })
public class AlarmApplication extends Application {
    private static Container sContainer;
    private static DynamicThemeHandler sThemeHandler;

    public static Optional<Boolean> is24hoursFormatOverride = Optional.absent();
    private RxSharedPreferences rxPreferences;

    @Override
    public void onCreate() {
        // The following line triggers the initialization of ACRA
        // ACRA.init(this);
        sThemeHandler = new DynamicThemeHandler(this);
        setTheme(sThemeHandler.defaultTheme());

        try {
            ViewConfiguration config = ViewConfiguration.get(this);
            Field menuKeyField = ViewConfiguration.class.getDeclaredField("sHasPermanentMenuKey");
            if (menuKeyField != null) {
                menuKeyField.setAccessible(true);
                menuKeyField.setBoolean(config, false);
            }
        } catch (Exception ex) {
            // Ignore
        }

        final StartupLogWriter startupLogWriter = StartupLogWriter.create();
        final Logger logger = Logger.create();
        logger.addLogWriter(LogcatLogWriter.create());
        logger.addLogWriter(startupLogWriter);

        LoggingExceptionHandler.addLoggingExceptionHandlerToAllThreads(logger);

        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        rxPreferences = RxSharedPreferences.create(preferences);

        Function<String, Integer> parseInt = new Function<String, Integer>() {
            @Override
            public Integer apply(String s) throws Exception {
                return Integer.parseInt(s);
            }
        };

        final Single<Boolean> dateFormat = Maybe
                .create(new MaybeOnSubscribe<Boolean>() {
                    @Override
                    public void subscribe(@NonNull MaybeEmitter<Boolean> e) throws Exception {
                        if (is24hoursFormatOverride.isPresent()) {
                            e.onSuccess(is24hoursFormatOverride.get());
                        } else {
                            e.onComplete();
                        }
                    }
                })
                .switchIfEmpty(Maybe.create(new MaybeOnSubscribe<Boolean>() {
                    @Override
                    public void subscribe(@NonNull MaybeEmitter<Boolean> e) throws Exception {
                        e.onSuccess(android.text.format.DateFormat.is24HourFormat(getApplicationContext()));
                    }
                }))
                .toSingle();

        final Prefs prefs = new Prefs(dateFormat,
                rxPreferences.getString("prealarm_duration", "30").asObservable().map(parseInt),
                rxPreferences.getString("snooze_duration", "10").asObservable().map(parseInt),
                rxPreferences.getString("auto_silence", "10").asObservable().map(parseInt));

        final Store store = new Store(
                // alarmsSubject
                BehaviorSubject.<List<AlarmValue>>createDefault(new ArrayList<AlarmValue>()),
                // next
                BehaviorSubject.createDefault(Optional.<Store.Next>absent()),
                // sets
                PublishSubject.<Store.AlarmSet>create(),
                PublishSubject.<Event>create());

        store.alarms().subscribe(new Consumer<List<AlarmValue>>() {
            @Override
            public void accept(@NonNull List<AlarmValue> alarmValues) throws Exception {
                logger.d("###########################");
                for (AlarmValue alarmValue : alarmValues) {
                    logger.d("Store: " + alarmValue);
                }
            }
        });

        store.next().subscribe(new Consumer<Optional<Store.Next>>() {
            @Override
            public void accept(@NonNull Optional<Store.Next> next) throws Exception {
                logger.d("## Next: " + next);
            }
        });

        // ACRA.getErrorReporter().setExceptionHandlerInitializer(new ExceptionHandlerInitializer() {
        //     @Override
        //     public void initializeExceptionHandler(ErrorReporter reporter) {
        //         reporter.putCustomData("STARTUP_LOG", startupLogWriter.getMessagesAsString());
        //     }
        // });

        PreferenceManager.setDefaultValues(this, R.xml.preferences, false);

        final Preference<String> defaultAlert = rxPreferences.getString(Prefs.KEY_DEFAULT_RINGTONE, "");
        defaultAlert
                .asObservable()
                .firstOrError()
                .subscribe(new Consumer<String>() {
                    @Override
                    public void accept(@NonNull String s) throws Exception {
                        if (s.isEmpty()) {
                            Uri alert = RingtoneManager.getActualDefaultRingtoneUri(getApplicationContext(), RingtoneManager.TYPE_ALARM);
                            defaultAlert.set(alert.toString());
                        }
                    }
                });

        deleteLogs(getApplicationContext());
        AlarmManager alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);
        AlarmSetter.AlarmSetterImpl setter = new AlarmSetter.AlarmSetterImpl(logger, alarmManager, getApplicationContext());
        Calendars calendars = new Calendars() {
            @Override
            public Calendar now() {
                return Calendar.getInstance();
            }
        };

        AlarmsScheduler alarmsScheduler = new AlarmsScheduler(setter, logger, store, prefs, calendars);
        AlarmCore.IStateNotifier broadcaster = new AlarmStateNotifier(getApplicationContext(), store);
        HandlerFactory handlerFactory = new ImmediateHandlerFactory();
        PersistingContainerFactory containerFactory = new PersistingContainerFactory(calendars, getApplicationContext());
        Alarms alarms = new Alarms(alarmsScheduler, new DatabaseQuery(getContentResolver(), containerFactory), new AlarmCoreFactory(logger,
                alarmsScheduler,
                broadcaster,
                handlerFactory,
                prefs,
                store,
                calendars
        ), containerFactory);

        alarms.start();

        sContainer = new Container(
                getApplicationContext(),
                logger,
                preferences,
                rxPreferences,
                prefs,
                store,
                alarms);

        new ScheduledReceiver(store, getApplicationContext(), prefs, alarmManager).start();
        new ToastPresenter(store, getApplicationContext()).start();
        new AlertServicePusher(store, getApplicationContext());
        new BackgroundNotifications();

        logger.d("onCreate done");

        super.onCreate();
    }

    private void deleteLogs(Context context) {
        final File logFile = new File(context.getFilesDir(), "applog.log");
        if (logFile.exists()) {
            logFile.delete();
        }
    }

    @android.support.annotation.NonNull
    public static Container container() {
        return sContainer;
    }

    @android.support.annotation.NonNull
    public static DynamicThemeHandler themeHandler() {
        return sThemeHandler;
    }
}
