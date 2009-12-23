package com.sun.grizzly.http;

import com.sun.grizzly.tcp.Request;
import com.sun.grizzly.util.net.SSLSupport;
import java.nio.channels.SelectionKey;

/**
 *
 * @author gustav trede
 */
public abstract class TemporaryInterceptor {

    public abstract boolean checkForUpgrade(Request request);

    public abstract boolean doUpgrade(SelectionKey key, Request request, SSLSupport sslSupport);
}
