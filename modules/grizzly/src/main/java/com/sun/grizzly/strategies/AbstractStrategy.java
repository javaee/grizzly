/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.sun.grizzly.strategies;

import com.sun.grizzly.Context;
import com.sun.grizzly.IOEvent;
import com.sun.grizzly.PostProcessor;
import com.sun.grizzly.ProcessorResult.Status;
import com.sun.grizzly.Strategy;
import com.sun.grizzly.nio.NIOConnection;
import java.io.IOException;

/**
 *
 * @author oleksiys
 */
public abstract class AbstractStrategy implements Strategy {
    private final static boolean[] isRegisterMap = {true, false, true, false};

    protected final static PostProcessor enableInterestPostProcessor =
            new EnableInterestPostProcessor();


    private static class EnableInterestPostProcessor implements PostProcessor {
        @Override
        public void process(Context context, Status status) throws IOException {
            if (isRegisterMap[status.ordinal()]) {
                final IOEvent ioEvent = context.getIoEvent();
                final NIOConnection nioConnection = (NIOConnection) context.getConnection();
                nioConnection.enableIOEvent(ioEvent);
            }
        }
    }
}
