/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2014 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.grizzly.http.server.accesslog;

import static java.util.concurrent.TimeUnit.MICROSECONDS;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.glassfish.grizzly.http.Method.GET;
import static org.glassfish.grizzly.http.Protocol.HTTP_1_1;
import static org.junit.Assert.assertEquals;

import java.util.Date;
import java.util.TimeZone;

import org.glassfish.grizzly.http.Cookie;
import org.glassfish.grizzly.http.HttpRequestPacket;
import org.glassfish.grizzly.http.HttpResponsePacket;
import org.glassfish.grizzly.http.server.Request;
import org.glassfish.grizzly.http.server.Response;
import org.glassfish.grizzly.http.util.MimeHeaders;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.internal.stubbing.answers.ThrowsException;

/**
 * Test for {@link ApacheLogFormat}
 *
 * @author <a href="mailto:pier@usrz.com">Pier Fumagalli</a>
 * @author <a href="http://www.usrz.com/">USRZ.com</a>
 */
public class ApacheLogFormatTest {

    private static final Date date = new Date(1389829512345L); //1389797112345L + 32400000L); // Jan 15th, 2014 @ 23:45:12.345 UTC
    private static final long nanos = 1234567890;

    private static final String SERVER_IP = "1.2.3.4";
    private static final String CLIENT_IP = "4.3.2.1";
    private static final int SERVER_PORT = 1234;
    private static final int CLIENT_PORT = 4321;
    private static final int STATUS = 210;
    private static final long CONTENT_LENGTH = 1234567L;

    private Response mockSimpleResponse() {

        /* By default throw a "non-mocked" exception */
        final ThrowsException exception = new ThrowsException(new IllegalStateException("Not-mocked"));

        /* Request fake headers (and HttpRequestPacket) */
        final MimeHeaders requestHeaders = new MimeHeaders();
        requestHeaders.addValue("user-agent").setString("Test-User-Agent");
        requestHeaders.addValue("referer").setString("http://referer/");
        requestHeaders.addValue("multi-request").setString("request-value-1");
        requestHeaders.addValue("multi-request").setString("request-value-2");

        final HttpRequestPacket requestPacket = Mockito.mock(HttpRequestPacket.class, exception);
        Mockito.doReturn(requestHeaders).when(requestPacket).getHeaders();

        /* Response fake headers (and HttpResponsePacket) */
        final MimeHeaders responseHeaders = new MimeHeaders();
        responseHeaders.addValue("content-type").setString("x-test/test");
        responseHeaders.addValue("multi-response").setString("response-value-1");
        responseHeaders.addValue("multi-response").setString("response-value-2");

        final HttpResponsePacket responsePacket = Mockito.mock(HttpResponsePacket.class, exception);
        Mockito.doReturn(responseHeaders).when(responsePacket).getHeaders();

        /* Fake cookies */
        final Cookie[] cookies = { new Cookie("Test-Cookie", "Test-Cookie-Value") };

        /* Mock request */
        final Request request = Mockito.mock(Request.class, exception);
        Mockito.doReturn(requestPacket).when(request).getRequest();
        Mockito.doReturn("server-name").when(request).getServerName();
        Mockito.doReturn("remote-host").when(request).getRemoteHost();
        Mockito.doReturn("remote-user").when(request).getRemoteUser();
        Mockito.doReturn("local-host").when(request).getLocalName();
        Mockito.doReturn(GET).when(request).getMethod();
        Mockito.doReturn("/test/path").when(request).getRequestURI();
        Mockito.doReturn("testing=true").when(request).getQueryString();
        Mockito.doReturn(HTTP_1_1).when(request).getProtocol();
        Mockito.doReturn(CLIENT_IP).when(request).getRemoteAddr();
        Mockito.doReturn(SERVER_IP).when(request).getLocalAddr();
        Mockito.doReturn(CLIENT_PORT).when(request).getRemotePort();
        Mockito.doReturn(SERVER_PORT).when(request).getLocalPort();
        Mockito.doReturn(cookies).when(request).getCookies();

        /* Mock response */
        final Response response = Mockito.mock(Response.class, exception);
        Mockito.doReturn(responsePacket).when(response).getResponse();
        Mockito.doReturn(request).when(response).getRequest();
        Mockito.doReturn(STATUS).when(response).getStatus();
        Mockito.doReturn(CONTENT_LENGTH).when(response).getContentLengthLong();

        /* All done */
        return response;
    }

    private Response mockEmptyResponse() {

        final HttpRequestPacket requestPacket = Mockito.mock(HttpRequestPacket.class);
        final HttpResponsePacket responsePacket = Mockito.mock(HttpResponsePacket.class);

        Mockito.doReturn(new MimeHeaders()).when(requestPacket).getHeaders();
        Mockito.doReturn(new MimeHeaders()).when(responsePacket).getHeaders();

        final Request request = Mockito.mock(Request.class);
        final Response response = Mockito.mock(Response.class);

        Mockito.doReturn(requestPacket).when(request).getRequest();
        Mockito.doReturn(responsePacket).when(response).getResponse();

        Mockito.doReturn(request).when(response).getRequest();

        return response;
    }

    @Test
    public void testBasicFormats() {
        final Response response = mockSimpleResponse();

        assertEquals(ApacheLogFormat.COMMON_UTC        .unsafeFormat(response, date, nanos), "remote-host - remote-user [2014/Jan/15:23:45:12 +0000] \"GET /test/path?testing=true HTTP/1.1\" 210 1234567");
        assertEquals(ApacheLogFormat.COMBINED_UTC      .unsafeFormat(response, date, nanos), "remote-host - remote-user [2014/Jan/15:23:45:12 +0000] \"GET /test/path?testing=true HTTP/1.1\" 210 1234567 \"http://referer/\" \"Test-User-Agent\"");
        assertEquals(ApacheLogFormat.VHOST_COMMON_UTC  .unsafeFormat(response, date, nanos), "server-name remote-host - remote-user [2014/Jan/15:23:45:12 +0000] \"GET /test/path?testing=true HTTP/1.1\" 210 1234567");
        assertEquals(ApacheLogFormat.VHOST_COMBINED_UTC.unsafeFormat(response, date, nanos), "server-name remote-host - remote-user [2014/Jan/15:23:45:12 +0000] \"GET /test/path?testing=true HTTP/1.1\" 210 1234567 \"http://referer/\" \"Test-User-Agent\"");
        assertEquals(ApacheLogFormat.REFERER_UTC       .unsafeFormat(response, date, nanos), "http://referer/ -> /test/path");
        assertEquals(ApacheLogFormat.AGENT_UTC         .unsafeFormat(response, date, nanos), "Test-User-Agent");
    }

    @Test
    public void testBasicFormatsEmptyResponse() {
        final Response response = mockEmptyResponse();

        assertEquals(ApacheLogFormat.COMMON_UTC        .unsafeFormat(response, date, nanos), "- - - [2014/Jan/15:23:45:12 +0000] \"- - -\" 000 -");
        assertEquals(ApacheLogFormat.COMBINED_UTC      .unsafeFormat(response, date, nanos), "- - - [2014/Jan/15:23:45:12 +0000] \"- - -\" 000 - \"\" \"\"");
        assertEquals(ApacheLogFormat.VHOST_COMMON_UTC  .unsafeFormat(response, date, nanos), "- - - - [2014/Jan/15:23:45:12 +0000] \"- - -\" 000 -");
        assertEquals(ApacheLogFormat.VHOST_COMBINED_UTC.unsafeFormat(response, date, nanos), "- - - - [2014/Jan/15:23:45:12 +0000] \"- - -\" 000 - \"\" \"\"");
        assertEquals(ApacheLogFormat.REFERER_UTC       .unsafeFormat(response, date, nanos), " -> -");
        assertEquals(ApacheLogFormat.AGENT_UTC         .unsafeFormat(response, date, nanos), "");
    }

    @Test
    public void testEscapes() {
        final Response response = mockSimpleResponse();
        assertEquals(new ApacheLogFormat("%%\\t\\b\\n\\r\\f\\%").unsafeFormat(response, date, nanos), "%\t\b\n\r\f%");
    }

    @Test
    public void testPatterns() {
        final Response response = mockSimpleResponse();

        assertEquals(new ApacheLogFormat("%a").unsafeFormat(response, date, nanos), CLIENT_IP);
        assertEquals(new ApacheLogFormat("%A").unsafeFormat(response, date, nanos), SERVER_IP);

        assertEquals(new ApacheLogFormat("%b").unsafeFormat(response, date, nanos), Long.toString(CONTENT_LENGTH));
        assertEquals(new ApacheLogFormat("%B").unsafeFormat(response, date, nanos), Long.toString(CONTENT_LENGTH));

        assertEquals(new ApacheLogFormat("%{test-cookie}C").unsafeFormat(response, date, nanos), "Test-Cookie-Value");

        assertEquals(new ApacheLogFormat("%D").unsafeFormat(response, date, nanos), Long.toString(MICROSECONDS.convert(nanos, NANOSECONDS)));

        assertEquals(new ApacheLogFormat("%h").unsafeFormat(response, date, nanos), "remote-host");
        assertEquals(new ApacheLogFormat("%{remote}h").unsafeFormat(response, date, nanos), "remote-host");
        assertEquals(new ApacheLogFormat("%{local}h").unsafeFormat(response, date, nanos), "local-host");

        assertEquals(new ApacheLogFormat("%H").unsafeFormat(response, date, nanos), "HTTP/1.1");

        assertEquals(new ApacheLogFormat("%{User-Agent}i").unsafeFormat(response, date, nanos), "Test-User-Agent");
        assertEquals(new ApacheLogFormat("%{Referer}i").unsafeFormat(response, date, nanos), "http://referer/");
        assertEquals(new ApacheLogFormat("%{Multi-Request}i").unsafeFormat(response, date, nanos), "request-value-1; request-value-2");

        assertEquals(new ApacheLogFormat("%m").unsafeFormat(response, date, nanos), "GET");

        assertEquals(new ApacheLogFormat("%{Content-Type}o").unsafeFormat(response, date, nanos), "x-test/test");
        assertEquals(new ApacheLogFormat("%{Multi-Response}o").unsafeFormat(response, date, nanos), "response-value-1; response-value-2");

        assertEquals(new ApacheLogFormat("%p").unsafeFormat(response, date, nanos), Integer.toString(SERVER_PORT));
        assertEquals(new ApacheLogFormat("%{local}p").unsafeFormat(response, date, nanos), Integer.toString(SERVER_PORT));
        assertEquals(new ApacheLogFormat("%{remote}p").unsafeFormat(response, date, nanos), Integer.toString(CLIENT_PORT));

        assertEquals(new ApacheLogFormat("%q").unsafeFormat(response, date, nanos), "?testing=true");

        assertEquals(new ApacheLogFormat("%r").unsafeFormat(response, date, nanos), "GET /test/path?testing=true HTTP/1.1");

        assertEquals(new ApacheLogFormat("%s").unsafeFormat(response, date, nanos), Integer.toString(STATUS));

        assertEquals(new ApacheLogFormat("%T")         .unsafeFormat(response, date, nanos), Long.toString(SECONDS.convert(nanos, NANOSECONDS)));
        assertEquals(new ApacheLogFormat("%{s}T")      .unsafeFormat(response, date, nanos), Long.toString(SECONDS.convert(nanos, NANOSECONDS)));
        assertEquals(new ApacheLogFormat("%{sec}T")    .unsafeFormat(response, date, nanos), Long.toString(SECONDS.convert(nanos, NANOSECONDS)));
        assertEquals(new ApacheLogFormat("%{secs}T")   .unsafeFormat(response, date, nanos), Long.toString(SECONDS.convert(nanos, NANOSECONDS)));
        assertEquals(new ApacheLogFormat("%{second}T") .unsafeFormat(response, date, nanos), Long.toString(SECONDS.convert(nanos, NANOSECONDS)));
        assertEquals(new ApacheLogFormat("%{seconds}T").unsafeFormat(response, date, nanos), Long.toString(SECONDS.convert(nanos, NANOSECONDS)));

        assertEquals(new ApacheLogFormat("%{m}T")           .unsafeFormat(response, date, nanos), Long.toString(MILLISECONDS.convert(nanos, NANOSECONDS)));
        assertEquals(new ApacheLogFormat("%{milli}T")       .unsafeFormat(response, date, nanos), Long.toString(MILLISECONDS.convert(nanos, NANOSECONDS)));
        assertEquals(new ApacheLogFormat("%{millis}T")      .unsafeFormat(response, date, nanos), Long.toString(MILLISECONDS.convert(nanos, NANOSECONDS)));
        assertEquals(new ApacheLogFormat("%{millisec}T")    .unsafeFormat(response, date, nanos), Long.toString(MILLISECONDS.convert(nanos, NANOSECONDS)));
        assertEquals(new ApacheLogFormat("%{millisecs}T")   .unsafeFormat(response, date, nanos), Long.toString(MILLISECONDS.convert(nanos, NANOSECONDS)));
        assertEquals(new ApacheLogFormat("%{millisecond}T") .unsafeFormat(response, date, nanos), Long.toString(MILLISECONDS.convert(nanos, NANOSECONDS)));
        assertEquals(new ApacheLogFormat("%{milliseconds}T").unsafeFormat(response, date, nanos), Long.toString(MILLISECONDS.convert(nanos, NANOSECONDS)));

        assertEquals(new ApacheLogFormat("%{micro}T")       .unsafeFormat(response, date, nanos), Long.toString(MICROSECONDS.convert(nanos, NANOSECONDS)));
        assertEquals(new ApacheLogFormat("%{micros}T")      .unsafeFormat(response, date, nanos), Long.toString(MICROSECONDS.convert(nanos, NANOSECONDS)));
        assertEquals(new ApacheLogFormat("%{microsec}T")    .unsafeFormat(response, date, nanos), Long.toString(MICROSECONDS.convert(nanos, NANOSECONDS)));
        assertEquals(new ApacheLogFormat("%{microsecs}T")   .unsafeFormat(response, date, nanos), Long.toString(MICROSECONDS.convert(nanos, NANOSECONDS)));
        assertEquals(new ApacheLogFormat("%{microsecond}T") .unsafeFormat(response, date, nanos), Long.toString(MICROSECONDS.convert(nanos, NANOSECONDS)));
        assertEquals(new ApacheLogFormat("%{microseconds}T").unsafeFormat(response, date, nanos), Long.toString(MICROSECONDS.convert(nanos, NANOSECONDS)));

        assertEquals(new ApacheLogFormat("%{n}T")          .unsafeFormat(response, date, nanos), Long.toString(nanos));
        assertEquals(new ApacheLogFormat("%{nano}T")       .unsafeFormat(response, date, nanos), Long.toString(nanos));
        assertEquals(new ApacheLogFormat("%{nanos}T")      .unsafeFormat(response, date, nanos), Long.toString(nanos));
        assertEquals(new ApacheLogFormat("%{nanosec}T")    .unsafeFormat(response, date, nanos), Long.toString(nanos));
        assertEquals(new ApacheLogFormat("%{nanosecs}T")   .unsafeFormat(response, date, nanos), Long.toString(nanos));
        assertEquals(new ApacheLogFormat("%{nanosecond}T") .unsafeFormat(response, date, nanos), Long.toString(nanos));
        assertEquals(new ApacheLogFormat("%{nanoseconds}T").unsafeFormat(response, date, nanos), Long.toString(nanos));

        assertEquals(new ApacheLogFormat("%u").unsafeFormat(response, date, nanos), "remote-user");

        assertEquals(new ApacheLogFormat("%U").unsafeFormat(response, date, nanos), "/test/path");

        assertEquals(new ApacheLogFormat("%v").unsafeFormat(response, date, nanos), "server-name");
    }

    @Test
    public void testPatternsEmptyResponse() {
        final Response response = mockEmptyResponse();

        assertEquals("remote-ip", "-", new ApacheLogFormat("%a").unsafeFormat(response, date, -1));
        assertEquals("local-ip", "-", new ApacheLogFormat("%A").unsafeFormat(response, date, -1));

        assertEquals("response bytes", "-", new ApacheLogFormat("%b").unsafeFormat(response, date, -1));
        assertEquals("response bytes", "0", new ApacheLogFormat("%B").unsafeFormat(response, date, -1));

        assertEquals("cookie", "", new ApacheLogFormat("%{test-cookie}C").unsafeFormat(response, date, -1));

        assertEquals("response-time", "-", new ApacheLogFormat("%D").unsafeFormat(response, date, -1));

        assertEquals("remote-host", "-", new ApacheLogFormat("%h").unsafeFormat(response, date, -1));
        assertEquals("remote-host", "-", new ApacheLogFormat("%{remote}h").unsafeFormat(response, date, -1));
        assertEquals("local-host", "-", new ApacheLogFormat("%{local}h").unsafeFormat(response, date, -1));

        assertEquals("protocol", "-", new ApacheLogFormat("%H").unsafeFormat(response, date, -1));

        assertEquals("request header", "", new ApacheLogFormat("%{header}i").unsafeFormat(response, date, -1));

        assertEquals("method", "-", new ApacheLogFormat("%m").unsafeFormat(response, date, -1));

        assertEquals("response header", "", new ApacheLogFormat("%{header}o").unsafeFormat(response, date, -1));

        assertEquals("server port", "-", new ApacheLogFormat("%p").unsafeFormat(response, date, -1));
        assertEquals("server port", "-", new ApacheLogFormat("%{local}p").unsafeFormat(response, date, -1));
        assertEquals("client port" , "-", new ApacheLogFormat("%{remote}p").unsafeFormat(response, date, -1));

        assertEquals("query", "", new ApacheLogFormat("%q").unsafeFormat(response, date, -1));

        assertEquals("first line", "- - -", new ApacheLogFormat("%r").unsafeFormat(response, date, -1));

        assertEquals("status", "000", new ApacheLogFormat("%s").unsafeFormat(response, date, -1));

        assertEquals("response time", "-", new ApacheLogFormat("%T")         .unsafeFormat(response, date, -1));
        assertEquals("response time", "-", new ApacheLogFormat("%{s}T")      .unsafeFormat(response, date, -1));
        assertEquals("response time", "-", new ApacheLogFormat("%{sec}T")    .unsafeFormat(response, date, -1));
        assertEquals("response time", "-", new ApacheLogFormat("%{secs}T")   .unsafeFormat(response, date, -1));
        assertEquals("response time", "-", new ApacheLogFormat("%{second}T") .unsafeFormat(response, date, -1));
        assertEquals("response time", "-", new ApacheLogFormat("%{seconds}T").unsafeFormat(response, date, -1));

        assertEquals("response time", "-", new ApacheLogFormat("%{m}T")           .unsafeFormat(response, date, -1));
        assertEquals("response time", "-", new ApacheLogFormat("%{milli}T")       .unsafeFormat(response, date, -1));
        assertEquals("response time", "-", new ApacheLogFormat("%{millis}T")      .unsafeFormat(response, date, -1));
        assertEquals("response time", "-", new ApacheLogFormat("%{millisec}T")    .unsafeFormat(response, date, -1));
        assertEquals("response time", "-", new ApacheLogFormat("%{millisecs}T")   .unsafeFormat(response, date, -1));
        assertEquals("response time", "-", new ApacheLogFormat("%{millisecond}T") .unsafeFormat(response, date, -1));
        assertEquals("response time", "-", new ApacheLogFormat("%{milliseconds}T").unsafeFormat(response, date, -1));

        assertEquals("response time", "-", new ApacheLogFormat("%{micro}T")       .unsafeFormat(response, date, -1));
        assertEquals("response time", "-", new ApacheLogFormat("%{micros}T")      .unsafeFormat(response, date, -1));
        assertEquals("response time", "-", new ApacheLogFormat("%{microsec}T")    .unsafeFormat(response, date, -1));
        assertEquals("response time", "-", new ApacheLogFormat("%{microsecs}T")   .unsafeFormat(response, date, -1));
        assertEquals("response time", "-", new ApacheLogFormat("%{microsecond}T") .unsafeFormat(response, date, -1));
        assertEquals("response time", "-", new ApacheLogFormat("%{microseconds}T").unsafeFormat(response, date, -1));

        assertEquals("response time", "-", new ApacheLogFormat("%{n}T")          .unsafeFormat(response, date, -1));
        assertEquals("response time", "-", new ApacheLogFormat("%{nano}T")       .unsafeFormat(response, date, -1));
        assertEquals("response time", "-", new ApacheLogFormat("%{nanos}T")      .unsafeFormat(response, date, -1));
        assertEquals("response time", "-", new ApacheLogFormat("%{nanosec}T")    .unsafeFormat(response, date, -1));
        assertEquals("response time", "-", new ApacheLogFormat("%{nanosecs}T")   .unsafeFormat(response, date, -1));
        assertEquals("response time", "-", new ApacheLogFormat("%{nanosecond}T") .unsafeFormat(response, date, -1));
        assertEquals("response time", "-", new ApacheLogFormat("%{nanoseconds}T").unsafeFormat(response, date, -1));

        assertEquals("remote-user", "-", new ApacheLogFormat("%u").unsafeFormat(response, date, -1));

        assertEquals("request-uri", "-", new ApacheLogFormat("%U").unsafeFormat(response, date, -1));

        assertEquals("server-name", "-", new ApacheLogFormat("%v").unsafeFormat(response, date, -1));
    }

    @Test
    public void testDates() {
        final TimeZone utc = TimeZone.getTimeZone("UTC");
        final TimeZone jst = TimeZone.getTimeZone("JST");
        final Response response = mockEmptyResponse();

        assertEquals(new ApacheLogFormat(utc, "%t")              .unsafeFormat(response, date, nanos), "[2014/Jan/15:23:45:12 +0000]");
        assertEquals(new ApacheLogFormat(jst, "%t")              .unsafeFormat(response, date, nanos), "[2014/Jan/16:08:45:12 +0900]");

        assertEquals(new ApacheLogFormat(utc, "%{HH:mm:ss.SSS}t").unsafeFormat(response, date, nanos), "23:45:12.345");
        assertEquals(new ApacheLogFormat(jst, "%{HH:mm:ss.SSS}t").unsafeFormat(response, date, nanos), "08:45:12.345");

        assertEquals(new ApacheLogFormat(utc, "%{@@HH:mm:ss}t").unsafeFormat(response, date, nanos), "@23:45:12");
        assertEquals(new ApacheLogFormat(jst, "%{@@HH:mm:ss}t").unsafeFormat(response, date, nanos), "@08:45:12");

        assertEquals(new ApacheLogFormat(utc, "%{@PST}t").unsafeFormat(response, date, nanos), "[2014/Jan/15:15:45:12 -0800]");
        assertEquals(new ApacheLogFormat(jst, "%{@PST}t").unsafeFormat(response, date, nanos), "[2014/Jan/15:15:45:12 -0800]");

        assertEquals(new ApacheLogFormat(utc, "%{HH:mm:ss.SSS@PST}t").unsafeFormat(response, date, nanos), "15:45:12.345");
        assertEquals(new ApacheLogFormat(jst, "%{HH:mm:ss.SSS@PST}t").unsafeFormat(response, date, nanos), "15:45:12.345");

        assertEquals(new ApacheLogFormat(utc, "%{@@HH:mm:ss@PST}t").unsafeFormat(response, date, nanos), "@15:45:12");
        assertEquals(new ApacheLogFormat(jst, "%{@@HH:mm:ss@PST}t").unsafeFormat(response, date, nanos), "@15:45:12");
    }

    @Test
    public void testGetFormatStandard() {
        assertStandardFormat(ApacheLogFormat.COMMON_FORMAT);
        assertStandardFormat(ApacheLogFormat.COMBINED_FORMAT);
        assertStandardFormat(ApacheLogFormat.VHOST_COMMON_FORMAT);
        assertStandardFormat(ApacheLogFormat.VHOST_COMBINED_FORMAT);
        assertStandardFormat(ApacheLogFormat.REFERER_FORMAT);
        assertStandardFormat(ApacheLogFormat.AGENT_FORMAT);
    }

    @Test
    public void testGetFormat() {
        assertFormat("%a");
        assertFormat("%A");
        assertFormat("%b");
        assertFormat("%B");
        assertFormat("%{Test-Cookie}C", "%{test-cookie}C"); // ignore case
        assertFormat("%D");
        assertFormat("%h");
        assertFormat("%{remote}h", "%h"); // normalize
        assertFormat("%{local}h");
        assertFormat("%H");
        assertFormat("%{User-Agent}i", "%{user-agent}i"); // ignore case
        assertFormat("%m");
        assertFormat("%{Content-Type}o", "%{content-type}o"); // ignore case
        assertFormat("%p");
        assertFormat("%{local}p", "%p"); // normalize
        assertFormat("%{remote}p");
        assertFormat("%q");
        assertFormat("%r", "%m %U%q %H"); // expand
        assertFormat("%s");
        assertFormat("%T", "%T"); // normalize seconds
        assertFormat("%{s}T", "%T"); // normalize seconds
        assertFormat("%{sec}T", "%T"); // normalize seconds
        assertFormat("%{secs}T", "%T"); // normalize seconds
        assertFormat("%{second}T", "%T"); // normalize seconds
        assertFormat("%{seconds}T", "%T"); // normalize seconds
        assertFormat("%{m}T", "%{m}T"); // normalize milliseconds
        assertFormat("%{milli}T", "%{m}T"); // normalize milliseconds
        assertFormat("%{millis}T", "%{m}T"); // normalize milliseconds
        assertFormat("%{millisec}T", "%{m}T"); // normalize milliseconds
        assertFormat("%{millisecs}T", "%{m}T"); // normalize milliseconds
        assertFormat("%{millisecond}T", "%{m}T"); // normalize milliseconds
        assertFormat("%{milliseconds}T", "%{m}T"); // normalize milliseconds
        assertFormat("%{micro}T", "%D"); // normalize microseconds
        assertFormat("%{micros}T", "%D"); // normalize microseconds
        assertFormat("%{microsec}T", "%D"); // normalize microseconds
        assertFormat("%{microsecs}T", "%D"); // normalize microseconds
        assertFormat("%{microsecond}T", "%D"); // normalize microseconds
        assertFormat("%{microseconds}T", "%D"); // normalize microseconds
        assertFormat("%{n}T", "%{n}T"); // normalize nanoseconds
        assertFormat("%{nano}T", "%{n}T"); // normalize nanoseconds
        assertFormat("%{nanos}T", "%{n}T"); // normalize nanoseconds
        assertFormat("%{nanosec}T", "%{n}T"); // normalize nanoseconds
        assertFormat("%{nanosecs}T", "%{n}T"); // normalize nanoseconds
        assertFormat("%{nanosecond}T", "%{n}T"); // normalize nanoseconds
        assertFormat("%{nanoseconds}T", "%{n}T"); // normalize nanoseconds
        assertFormat("%u");
        assertFormat("%U");
        assertFormat("%v");
        assertFormat("%t");
        assertFormat("%{HH:mm:ss.SSS}t");
        assertFormat("%{@@HH:mm:ss}t");
        assertFormat("%{@PST}t");
        assertFormat("%{HH:mm:ss.SSS@PST}t");
        assertFormat("%{@@HH:mm:ss@PST}t");
    }

    private void assertStandardFormat(String format) {
        this.assertFormat(format, format.replace("%r", "%m %U%q %H")
                                        .replace("Referer", "referer")
                                        .replace("User-agent", "user-agent"));
    }

    private void assertFormat(String format) {
        this.assertFormat(format, format);
    }

    private void assertFormat(String format, String expected) {
        final ApacheLogFormat first = new ApacheLogFormat(format);
        assertEquals(first.getFormat(), expected);
        final ApacheLogFormat second = new ApacheLogFormat(first.getFormat());
        assertEquals(second.getFormat(), expected);
    }
}
