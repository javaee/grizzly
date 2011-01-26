package org.glassfish.grizzly.comet;

import java.io.IOException;

import org.glassfish.grizzly.filterchain.BaseFilter;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.filterchain.NextAction;

public class CometFilter extends BaseFilter {
    private CometEngine engine;

    public CometFilter() {
        engine = new CometEngine();
        engine.setCometSupported(true);
        CometEngine.setEngine(engine);
    }

    @Override
    public NextAction handleRead(final FilterChainContext ctx) throws IOException {
        updateThreadLocals(ctx);
        return super.handleRead(ctx);
    }

    private void updateThreadLocals(final FilterChainContext ctx) {
        CometEngine.setConnection(ctx.getConnection());
        CometEngine.setEngine(engine);
    }
}
