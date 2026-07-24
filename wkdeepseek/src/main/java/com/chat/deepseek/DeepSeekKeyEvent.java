package com.chat.deepseek;

public final class DeepSeekKeyEvent {
    public long time;
    public String event;

    public DeepSeekKeyEvent(long time, String event) {
        this.time = time;
        this.event = event == null ? "" : event;
    }
}
