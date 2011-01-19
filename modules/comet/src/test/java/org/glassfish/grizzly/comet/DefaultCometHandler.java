package org.glassfish.grizzly.comet;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

import org.glassfish.grizzly.http.server.Response;
import org.glassfish.grizzly.utils.Utils;

class DefaultCometHandler implements CometHandler<String>, Comparable<CometHandler> {
    private Response response;
    volatile AtomicBoolean onInitializeCalled = new AtomicBoolean(false);
    volatile AtomicBoolean onInterruptCalled = new AtomicBoolean(false);
    volatile AtomicBoolean onEventCalled = new AtomicBoolean(false);
    volatile AtomicBoolean onTerminateCalled = new AtomicBoolean(false);
    private CometContext<String> cometContext;

    public DefaultCometHandler(CometContext<String> cometContext, Response response) {
        this.cometContext = cometContext;
        this.response = response;
    }

    @Override
    public Response getResponse() {
        return response;
    }

    @Override
    public CometContext<String> getCometContext() {
        return cometContext;
    }

    public void attach(String attachment) {
    }

    public void onEvent(CometEvent event) throws IOException {
        Utils.dumpOut("     -> onEvent Handler:" + hashCode());
        onEventCalled.set(true);
    }

    public void onInitialize(CometEvent event) throws IOException {
        Utils.dumpOut("     -> onInitialize Handler:" + hashCode());
        response.addHeader(BasicCometTest.onInitialize,
            event.attachment() == null ? BasicCometTest.onInitialize : event.attachment().toString());
        onInitializeCalled.set(true);
    }

    public void onTerminate(CometEvent event) throws IOException {
        Utils.dumpOut("    -> onTerminate Handler:" + hashCode());
        onTerminateCalled.set(true);
        response.getWriter().write(BasicCometTest.onTerminate);
    }

    public void onInterrupt(CometEvent event) throws IOException {
        Utils.dumpOut("    -> onInterrupt Handler:" + hashCode());
        onInterruptCalled.set(true);
        response.getWriter().write(BasicCometTest.onInterrupt);
    }

    @Override
    public int compareTo(CometHandler o) {
        return hashCode() - o.hashCode();
    }
}
