package com.better.alarm.logger;

import com.better.alarm.logger.Logger.LogLevel;
import com.better.alarm.logger.Logger.LogWriter;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.CopyOnWriteArrayList;

public class StartupLogWriter implements LogWriter {
    private static final int STARTUP_BUFFER_SIZE = 100;

    private final DateFormat dtf;
    private final CopyOnWriteArrayList<String> messages;

    public static StartupLogWriter create() {
        return new StartupLogWriter();
    }

    private StartupLogWriter() {
        dtf = new SimpleDateFormat("dd-MM HH:mm:ss");
        messages = new CopyOnWriteArrayList<String>();
    }

    public CopyOnWriteArrayList<String> getMessages() {
        return messages;
    }

    public String getMessagesAsString() {
        StringBuilder sb = new StringBuilder(messages.size() * 120);
        for (String message : messages) {
            sb.append(message).append('\n');
        }
        return sb.toString();
    }

    @Override
    public void write(LogLevel level, String tag, String message, Throwable throwable) {
        if (messages.size() < STARTUP_BUFFER_SIZE) {
            // BufferedWriter for performance, true to set append to file flag
            final StringBuilder buf = new StringBuilder();
            final Date timeStamp = new Date(System.currentTimeMillis());
            buf.append(dtf.format(timeStamp));
            buf.append(" ");
            buf.append(level.name());
            buf.append(" ");
            buf.append(tag);
            buf.append(" ");
            buf.append(message);
            if (throwable != null) {
                buf.append(throwable.toString());
            }
            messages.add(buf.toString());
        }
    }
}
