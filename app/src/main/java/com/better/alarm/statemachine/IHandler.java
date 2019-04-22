package com.better.alarm.statemachine;

/**
 * Created by Yuriy on 07.03.2017.
 */
public interface IHandler {
    void sendMessageAtFrontOfQueue(Message message);

    void sendMessage(Message what);

    Message obtainMessage(int what, Object obj);

    Message obtainMessage(int what);
}
