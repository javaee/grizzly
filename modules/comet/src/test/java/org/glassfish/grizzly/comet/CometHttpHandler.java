package org.glassfish.grizzly.comet;

import java.io.IOException;

import org.glassfish.grizzly.http.server.HttpHandler;
import org.glassfish.grizzly.http.server.Request;
import org.glassfish.grizzly.http.server.Response;

public class CometHttpHandler extends HttpHandler {
    final boolean resume;
    DefaultCometHandler cometHandler;
    CometContext<String> cometContext;

    public CometHttpHandler(CometContext<String> cometContext, boolean resume) {
        this.cometContext = cometContext;
        this.resume = resume;
    }

    @Override
    public void service(Request request, Response response) throws IOException {
        cometHandler = createHandler(response);
        cometContext.addCometHandler(cometHandler);
    }

    public DefaultCometHandler createHandler(Response response) {
        return new DefaultCometHandler(cometContext, response);
    }
}
