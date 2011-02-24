package org.glassfish.grizzly.comet;

import java.io.IOException;

import org.glassfish.grizzly.http.server.Response;

/**
 * A basic default implementation of CometHandler to take care of tracking the Response and CometContext.
 */
public class DefaultCometHandler<E> implements CometHandler<E> {
    private Response response;
    private CometContext<E> cometContext;

    public DefaultCometHandler() {
    }

    public DefaultCometHandler(final CometContext<E> cometContext, final Response response) {
        this.cometContext = cometContext;
        this.response = response;
    }

    @Override
    public Response getResponse() {
        return response;
    }

    @Override
    public void setResponse(final Response response) {
        this.response = response;
    }

    @Override
    public CometContext<E> getCometContext() {
        return cometContext;
    }

    @Override
    public void setCometContext(final CometContext<E> cometContext) {
        this.cometContext = cometContext;
    }

    @Override
    public void onEvent(final CometEvent event) throws IOException {
    }

    @Override
    public void onInitialize(final CometEvent event) throws IOException {
    }

    @Override
    public void onTerminate(final CometEvent event) throws IOException {
    }

    @Override
    public void onInterrupt(final CometEvent event) throws IOException {
    }
}
