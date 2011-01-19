package org.glassfish.grizzly.comet;

import java.io.IOException;

import org.glassfish.grizzly.http.server.Request;
import org.glassfish.grizzly.http.server.Response;

public class CountDownHttpHandler extends CometHttpHandler {
    public CountDownHttpHandler(CometContext<String> cometContext, boolean resume) {
        super(cometContext, resume);
    }

    @Override
    public void service(Request request, Response response) throws IOException {
        response.setContentType("text/html");
        super.service(request, response);
        response.flush();
    }

    @Override
    public DefaultCometHandler createHandler(Response response) {
        return new CountDownCometHandler(cometContext, response);
    }

}