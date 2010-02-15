package com.sun.grizzly.websocket;

import com.sun.grizzly.arp.AsyncProcessorTask;
import com.sun.grizzly.arp.AsyncTask;

import java.io.IOException;
import java.util.logging.Level;

public enum ContinuationType {
    BEFORE_REQUEST_PROCESSING {
        @Override
        public void executeServlet(AsyncProcessorTask apt) {
            apt.setStage(AsyncTask.PRE_EXECUTE);
            try {
                apt.doTask();
            } catch (IOException e) {
                WebSocketEngine.logger.log(Level.SEVERE,"executeServlet",e);
            }
        }},
    AFTER_SERVLET_PROCESSING {
        @Override
        public void executeServlet(AsyncProcessorTask apt) {
            apt.getAsyncExecutor().getProcessorTask().invokeAdapter();
        }},
    AFTER_RESPONSE_PROCESSING {
        @Override
        public void executeServlet(AsyncProcessorTask apt) {
            apt.setStage(AsyncTask.POST_EXECUTE);
            // Last step, execute directly from here.
            try {
                apt.doTask();
            } catch (IOException e) {
                WebSocketEngine.logger.log(Level.SEVERE,"executeServlet",e);
            }
        }
    };

    public abstract void executeServlet(AsyncProcessorTask apt);
}
