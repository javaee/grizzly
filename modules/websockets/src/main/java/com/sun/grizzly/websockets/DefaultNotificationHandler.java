package com.sun.grizzly.websockets;

import com.sun.grizzly.util.ExtendedThreadPool;

public class DefaultNotificationHandler implements NotificationHandler {
    private ExtendedThreadPool pool;

    public void setThreadPool(ExtendedThreadPool pool) {
        this.pool = pool;
    }
}
