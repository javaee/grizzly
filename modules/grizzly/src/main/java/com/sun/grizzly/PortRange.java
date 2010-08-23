/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2009-2010 Oracle and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * https://glassfish.dev.java.net/public/CDDL+GPL_1_1.html
 * or packager/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at packager/legal/LICENSE.txt.
 *
 * GPL Classpath Exception:
 * Oracle designates this particular file as subject to the "Classpath"
 * exception as provided by Oracle in the GPL Version 2 section of the License
 * file that accompanied this code.
 *
 * Modifications:
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyright [year] [name of copyright owner]"
 *
 * Contributor(s):
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */

package com.sun.grizzly;

import java.io.IOException;
import java.net.BindException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Random;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

/**
 * Immutable class representing a port range.
 * @author: Gerd Behrmann
 * @author: Tigran Mkrtchyan
 */
public class PortRange
{
    /** Pattern matching <PORT>[:<PORT>] */
    private final static Pattern FORMAT =
        Pattern.compile("(\\d+)(?:(?:,|:)(\\d+))?");

    private final int lower;
    private final int upper;
    /**
     * Random number generator used when binding sockets.
     */
    private final static Random _random = new Random();

    /**
     * Creates a port range with the given bounds (both inclusive).
     *
     * @throws IllegalArgumentException is either bound is not between
     *         0 and 65535, or if <code>high</code> is lower than
     *         <code>low</code>.
     */
    public PortRange(int low, int high)
    {
        if (low < 0 || high < low || 65535 < high) {
            throw new IllegalArgumentException("Invalid range");
        }
        lower = low;
        upper = high;
    }

    /**
     * Creates a port range containing a single port.
     */
    public PortRange(int port)
    {
        this(port, port);
    }

    /**
     * Parse a port range. A port range consists of either a single
     * integer, or two integers separated by either a comma or a
     * colon.
     *
     * The bounds must be between 0 and 65535, both inclusive.
     *
     * @return The port range represented by <code>s</code>. Returns
     * the range [0,0] if <code>s</code> is null or empty.
     */
    public static PortRange valueOf(String s)
        throws IllegalArgumentException
    {
        try {
            Matcher m = FORMAT.matcher(s);

            if (!m.matches()) {
                throw new IllegalArgumentException("Invalid range: " + s);
            }

            int low = Integer.parseInt(m.group(1));
            int high =
                (m.groupCount() == 1) ? low : Integer.parseInt(m.group(2));

            return new PortRange(low, high);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid range: " + s);
        }
    }

    /**
     * Returns a random port within the range.
     */
    public int random()
    {
        return _random.nextInt(upper - lower + 1) + lower;
    }

    /**
     * Returns the successor of a port within the range, wrapping
     * around to the lowest port if necessary.
     */
    public int succ(int port)
    {
        return (port < upper ? port + 1 : (int) lower);
    }

    public int getLower() {
        return lower;
    }

    public int getUpper() {
        return upper;
    }
    /**
     * Binds <code>socket</socket> to <code>endpoint</code>. If the
     * port in <code>endpoint</code> is zero, then a port is chosen
     * from this port range. If the port range is [0,0], then a free
     * port is chosen by the OS.
     *
     * @throws IOException if the bind operation fails, or if the
     * socket is already bound.
     */
    public void bind(ServerSocket socket, InetSocketAddress endpoint, int backLog)
        throws IOException
    {
        int port = endpoint.getPort();
        PortRange range = (port > 0) ? new PortRange(port) : this;
        range.bind(socket, endpoint.getAddress(), backLog);
    }

    /**
     * Binds <code>socket</socket> to <code>endpoint</code>. If the
     * port in <code>endpoint</code> is zero, then a port is chosen
     * from this port range. If the port range is [0,0], then a free
     * port is chosen by the OS.
     *
     * @throws IOException if the bind operation fails, or if the
     * socket is already bound.
     */
    public void bind(Socket socket, InetSocketAddress endpoint)
        throws IOException
    {
        int port = endpoint.getPort();
        PortRange range = (port > 0) ? new PortRange(port) : this;
        range.bind(socket, endpoint.getAddress());
    }

    /**
     * Binds <code>socket</socket> to <code>address</code>. A port is
     * chosen from this port range. If the port range is [0,0], then a
     * free port is chosen by the OS.
     *
     * @throws IOException if the bind operation fails, or if the
     * socket is already bound.
     */
    public void bind(ServerSocket socket, InetAddress address, int backLog)
        throws IOException
    {
        int start = random();
        int port = start;
        do {
            try {
                socket.bind(new InetSocketAddress(address, port), backLog);
                return;
            } catch (BindException e) {
            }
            port = succ(port);
        } while (port != start);

        throw new BindException("No free port within range");
    }

    /**
     * Binds <code>socket</socket> to <code>address</code>. A port is
     * chosen from this port range. If the port range is [0,0], then a
     * free port is chosen by the OS.
     *
     * @throws IOException if the bind operation fails, or if the
     * socket is already bound.
     */
    public void bind(Socket socket, InetAddress address)
        throws IOException
    {
        int start = random();
        int port = start;
        do {
            try {
                socket.bind(new InetSocketAddress(address, port));
                return;
            } catch (BindException e) {
            }
            port = succ(port);
        } while (port != start);

        throw new BindException("No free port within range");
    }

    /**
     * Binds <code>socket</socket> to <code>address</code>. A port is
     * chosen from this port range. If the port range is [0,0], then a
     * free port is chosen by the OS.
     *
     * @throws IOException if the bind operation fails, or if the
     * socket is already bound.
     */
    public void bind(DatagramSocket socket, InetAddress address)
            throws IOException {
        int start = random();
        int port = start;
        do {
            try {
                socket.bind(new InetSocketAddress(address, port));
                return;
            } catch (BindException e) {
            }
            port = succ(port);
        } while (port != start);

        throw new BindException("No free port within range");
    }

    /**
     * Binds <code>socket</socket> to the wildcard
     * <code>address</code>. A port is chosen from this port range. If
     * the port range is [0,0], then a free port is chosen by the OS.
     *
     * @throws IOException if the bind operation fails, or if the
     * socket is already bound.
     */
    public void bind(DatagramSocket socket)
            throws IOException {
        bind(socket, (InetAddress) null);
    }

    /**
     * Binds <code>socket</socket> to the wildcard
     * <code>address</code>. A port is chosen from this port range. If
     * the port range is [0,0], then a free port is chosen by the OS.
     *
     * @throws IOException if the bind operation fails, or if the
     * socket is already bound.
     */
    public void bind(ServerSocket socket, int backLog)
        throws IOException
    {
        bind(socket, (InetAddress) null, 50);
    }

    /**
     * Binds <code>socket</socket> to the wildcard
     * <code>address</code>. A port is chosen from this port range. If
     * the port range is [0,0], then a free port is chosen by the OS.
     *
     * @throws IOException if the bind operation fails, or if the
     * socket is already bound.
     */
    public void bind(Socket socket)
        throws IOException
    {
        bind(socket, (InetAddress) null);
    }


    @Override
    public String toString()
    {
        return String.format("%d:%d", lower, upper);
    }
}
