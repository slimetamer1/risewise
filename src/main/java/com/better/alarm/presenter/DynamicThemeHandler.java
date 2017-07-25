package com.better.alarm.presenter;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import com.better.alarm.R;
import com.better.alarm.alert.AlarmAlertFullScreen;

import java.util.HashMap;
import java.util.Map;

public class DynamicThemeHandler {
    public static final String KEY_THEME = "theme";
    public static final String DEFAULT = "default";

    private static DynamicThemeHandler sInstance;
    private final Map<String, Map<String, Integer>> themes;
    private final SharedPreferences sp;

    private class HashMapWithDefault extends HashMap<String, Integer> {
        private static final long serialVersionUID = 6169875120194964563L;

        @Override
        public Integer get(Object key) {
            Object id = super.get(key);
            if (id == null) return super.get(DEFAULT);
            else return (Integer) id;
        }

        public HashMapWithDefault(Integer defaultValue) {
            super(5);
            put(DEFAULT, defaultValue);
        }
    }

    public int getIdForName(String name) {
        final String activeThemeName = sp.getString(KEY_THEME, "light");
        final String correctedTheme;
        if (!"light".equals(activeThemeName) && !"dark".equals(activeThemeName)) {
            sp.edit().putString(KEY_THEME, "light").commit();
            correctedTheme = "light";
        } else {
            correctedTheme = activeThemeName;
        }
        Map<String, Integer> activeThemeMap = themes.get(correctedTheme);

        Integer themeForName = activeThemeMap.get(name);
        return themeForName;
    }

    public static void init(Context context) {
        sInstance = new DynamicThemeHandler(context);
    }

    public static DynamicThemeHandler getInstance() {
        return sInstance;
    }

    private DynamicThemeHandler(Context context) {
        sp = PreferenceManager.getDefaultSharedPreferences(context);

        Map<String, Integer> darkThemes = new HashMapWithDefault(R.style.DefaultDarkTheme);
        darkThemes.put(AlarmAlertFullScreen.class.getName(), R.style.AlarmAlertFullScreenDarkTheme);
        darkThemes.put(TimePickerDialogFragment.class.getName(), R.style.TimePickerDialogFragmentDark);

        Map<String, Integer> lightThemes = new HashMapWithDefault(R.style.DefaultLightTheme);
        lightThemes.put(AlarmAlertFullScreen.class.getName(), R.style.AlarmAlertFullScreenLightTheme);
        lightThemes.put(TimePickerDialogFragment.class.getName(), R.style.TimePickerDialogFragmentLight);

        themes = new HashMap<String, Map<String, Integer>>(3);
        themes.put("light", lightThemes);
        themes.put("dark", darkThemes);
    }
}
