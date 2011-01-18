package com.sun.grizzly.comet;

import java.io.IOException;

import org.glassfish.grizzly.comet.CometContext;
import org.glassfish.grizzly.comet.CometEvent;
import org.glassfish.grizzly.comet.CometHandler;
import org.glassfish.grizzly.http.server.Response;
import org.glassfish.grizzly.utils.Utils;

class DefaultCometHandler implements CometHandler<String> {
    private final boolean resume;
    private String attachment;
    private Response response;
    volatile String wasInterrupt = "";
    private CometContext<String> cometContext;

    public DefaultCometHandler(final CometContext<String> cometContext, Response response, boolean resume) {
        this.cometContext = cometContext;
        this.response = response;
        this.resume = resume;
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
        this.attachment = attachment;
    }

    public void onEvent(CometEvent event) throws IOException {
        Utils.dumpOut("     -> onEvent Handler:" + hashCode());
        response.addHeader("onEvent", event.attachment().toString());
        response.getWriter().write("onEvent");
        if (resume) {
            event.getCometContext().resumeCometHandler(this);
        }
    }

    public void onInitialize(CometEvent event) throws IOException {
        Utils.dumpOut("     -> onInitialize Handler:" + hashCode());
        String test = (String) event.attachment();
        if (test == null) {
            test = BasicCometTest.onInitialize;
        }
        response.addHeader(BasicCometTest.onInitialize, test);
    }

    public void onTerminate(CometEvent event) throws IOException {
        Utils.dumpOut("    -> onTerminate Handler:" + hashCode());
        response.addHeader(BasicCometTest.onTerminate, event.attachment().toString());
        response.getWriter().write(BasicCometTest.onTerminate);
    }

    public void onInterrupt(CometEvent event) throws IOException {
        Utils.dumpOut("    -> onInterrupt Handler:" + hashCode());
        wasInterrupt = BasicCometTest.onInterrupt;
        String test = (String) event.attachment();
        if (test == null) {
            test = BasicCometTest.onInterrupt;
        }
        response.addHeader(BasicCometTest.onInterrupt, test);
        response.getWriter().write(BasicCometTest.onInterrupt);
    }
}
