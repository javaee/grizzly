package org.glassfish.grizzly.comet;

import java.io.IOException;

import org.glassfish.grizzly.filterchain.BaseFilter;
import org.glassfish.grizzly.filterchain.FilterChain;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.filterchain.NextAction;
import org.glassfish.grizzly.http.server.Request;

public class CometFilter extends BaseFilter {
    @Override
    public void onAdded(final FilterChain filterChain) {
        CometEngine.getEngine().setCometSupported(true);
    }

    @Override
    public NextAction handleRead(final FilterChainContext ctx) throws IOException {
        updateThreadLocals(ctx);
        return super.handleRead(ctx);
    }

    private void updateThreadLocals(final FilterChainContext ctx) {
        Request.setConnection(ctx.getConnection());
    }
}