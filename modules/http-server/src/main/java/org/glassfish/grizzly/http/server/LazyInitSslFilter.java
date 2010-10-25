package org.glassfish.grizzly.http.server;

import java.io.IOException;

import org.glassfish.grizzly.filterchain.BaseFilter;
import org.glassfish.grizzly.filterchain.Filter;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.filterchain.NextAction;
import org.glassfish.grizzly.ssl.SSLFilter;

public class LazyInitSslFilter extends BaseFilter {
    private SSLFilter filter;

    public LazyInitSslFilter(final SSLFilter filter) {
        this.filter = filter;
    }

    @Override
    public NextAction handleRead(final FilterChainContext ctx) throws IOException {
        ctx.getFilterChain().add(ctx.getFilterIdx(), filter);
        ctx.getFilterChain().remove(this);
        return filter.handleRead(ctx);
    }
}
