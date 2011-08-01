package org.glassfish.grizzly.websockets;

import org.glassfish.grizzly.CompletionHandler;

public class FrameCompletionHandler implements CompletionHandler<DataFrame> {
    private DataFrame result;

    @Override
    public void cancelled() {
    }

    @Override
    public void failed(Throwable throwable) {
    }

    @Override
    public void completed(DataFrame result) {
        this.result = result;
    }

    @Override
    public void updated(DataFrame result) {
    }

    public DataFrame getResult() {
        return result;
    }
}
