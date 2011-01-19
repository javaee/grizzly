package org.glassfish.grizzly.comet;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;

import org.glassfish.grizzly.http.server.Response;

public class CountDownCometHandler extends DefaultCometHandler {
    public final CountDownLatch onEvent;
    public final CountDownLatch onInitialize;
    public final CountDownLatch onInterrupt;
    public final CountDownLatch onTerminate;

    public CountDownCometHandler(CometContext<String> cometContext, Response response) {
        super(cometContext, response);
        onEvent = new CountDownLatch(1);
        onInitialize = new CountDownLatch(1);
        onInterrupt = new CountDownLatch(1);
        onTerminate = new CountDownLatch(1);
    }

    @Override
    public void onInterrupt(CometEvent event) throws IOException {
        super.onInterrupt(event);
        onInterrupt.countDown();
    }

    @Override
    public void onTerminate(CometEvent event) throws IOException {
        super.onTerminate(event);
        onTerminate.countDown();
    }

    @Override
    public void onEvent(CometEvent event) throws IOException {
        super.onEvent(event);
        onEvent.countDown();
    }

    @Override
    public void onInitialize(CometEvent event) throws IOException {
        super.onInitialize(event);
        onInitialize.countDown();
    }
}
