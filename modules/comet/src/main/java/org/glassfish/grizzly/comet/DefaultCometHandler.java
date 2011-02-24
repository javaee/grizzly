package org.glassfish.grizzly.comet;

import org.glassfish.grizzly.http.server.Response;

/**
 * A basic default implementation of CometHandler to take care of tracking the Response and CometContext.
 */
public abstract class DefaultCometHandler<E> implements CometHandler<E> {
    private Response response;
    private CometContext<E> cometContext;

    protected DefaultCometHandler(final CometContext<E> cometContext, final Response response) {
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
}
