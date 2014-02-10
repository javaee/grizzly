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

import static java.util.logging.Level.WARNING;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.TimeZone;
import java.util.logging.Logger;

import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.http.Cookie;
import org.glassfish.grizzly.http.Method;
import org.glassfish.grizzly.http.Protocol;
import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.grizzly.http.server.Request;
import org.glassfish.grizzly.http.server.Response;
import org.glassfish.grizzly.http.util.MimeHeaders;

/**
 * An {@link AccessLogFormat} using a standard vaguely similar and heavily
 * influenced by <a href="http://httpd.apache.org/docs/2.2/mod/mod_log_config.html#formats">Apache's
 * own custom access log formats</a>.
 *
 * <p>As with Apache, the format string specified at
 * {@linkplain #ApacheLogFormat(String) construction} should be composed of
 * these tokens:</p>
 *
 * <table>
 * <tr><th><code>%%</code></th>
 *     <td>The literal percent sign (can also be escaped with back-slash, like "<code>\%</code>"</td></tr>
 * <tr><th><code>%a</code></th>
 *     <td>Remote IP-address</td></tr>
 * <tr><th><code>%A</code></th>
 *     <td>Local IP-address</td></tr>
 * <tr><th><code>%b</code></th>
 *     <td>Size of response in bytes, excluding HTTP headers, using "<code>-</code>" (a <em>dash</em> character) rather than a "<code>0</code>" (<em>zero</em>) when no bytes are sent</td></tr>
 * <tr><th><code>%B</code></th>
 *     <td>Size of response in bytes, excluding HTTP headers</td></tr>
 * <tr><th><code>%{Foobar}C</code></th>
 *     <td>The contents of cookie "<code>Foobar</code>" in the request sent to the server</td></tr>
 * <tr><th><code>%D</code></th>
 *     <td>The time taken to serve the request, in microseconds</td></tr>
 * <tr><th><code>%h</code></th>
 *     <td>Remote host name</td></tr>
 * <tr><th><code>%{local|remote}h</code></th>
 *     <td>Host name, either "<code>local</code>" or "<code>remote</code>"</td></tr>
 * <tr><th><code>%H</code></th>
 *     <td>The request protocol</td></tr>
 * <tr><th><code>%{Foobar}i</code></th>
 *     <td>The contents of the "<code>Foobar: ...</code>" header in the request</td></tr>
 * <tr><th><code>%m</code></th>
 *     <td>The request method</td></tr>
 * <tr><th><code>%{Foobar}o</code></th>
 *     <td>The contents of the "<code>Foobar: ...</code>" header in the response</td></tr>
 * <tr><th><code>%p</code></th>
 *     <td>Local port number</td></tr>
 * <tr><th><code>%{local|remote}p</code></th>
 *     <td>The port number, either "<code>local</code>" or "<code>remote</code>"</td></tr>
 * <tr><th><code>%q</code></th>
 *     <td>The query string, prepended with a "<code>?</code>" (<em>question mark</em>) if a query string exists, otherwise an empty string</td></tr>
 * <tr><th><code>%r</code></th>
 *     <td>First line of request, an alias to "<code>%m %U%q %H</code>"</td></tr>
 * <tr><th><code>%s</code></th>
 *     <td>Status code</td></tr>
 * <tr><th><code>%t</code></th>
 *     <td>The time the request was received, in standard <em>English</em> format (like "<code>[09/Feb/2014:12:00:34 +0900]</code>")</td></tr>
 * <tr><th><code>%{[format][@timezone]}t</code></th>
 *     <td>The time the request was received. Both <em>format</em> and <em>timezone</em> are optional<br>
 *         <ul><li>When "<code>format</code>" is left unspecified, the default <code>%t</code> format <code>[yyyy/MMM/dd:HH:mm:ss Z]</code> is used</li>
 *             <li>When "<code>format</code>" is specified, the given pattern <b>must</b> be a valid {@link SimpleDateFormat} pattern.</li>
 *             <li>When "<code>@timezone</code>" is left unspecified, the {@linkplain TimeZone#getDefault() default time zone} is used.</li>
 *             <li>When "<code>@timezone</code>" is specified, the time zone will be looked up by {@linkplain TimeZone#getTimeZone(String) time zone identifier}
 *                 (note that this will default to <em>GMT</em> if the specified identifier was not recognized).</li>
 *         </ul>
 *         When the "<code>@</code>" character needs to be used in the <em>format</em>, it <b>must</b> be escaped as "<em>@@</em>"</td></tr>
 * <tr><th><code>%T</code></th>
 *     <td>The time taken to serve the request, in seconds</td></tr>
 * <tr><th><code>%{...}T</code></th>
 *     <td>The time taken to serve the request. The parameter can be a time unit like:
 *         <ul><li>"<code>n</code>",  "<code>nano<em>[s]<em></code>",  "<code>nanosec<em>[s]<em></code>",  "<code>nanosecond<em>[s]<em></code>"</li>
 *             <li>                  "<code>micro<em>[s]<em></code>", "<code>microsec<em>[s]<em></code>", "<code>microsecond<em>[s]<em></code>"</li>
 *             <li>"<code>m</code>", "<code>milli<em>[s]<em></code>", "<code>millisec<em>[s]<em></code>", "<code>millisecond<em>[s]<em></code>"</li>
 *             <li>"<code>s</code>",                                       "<code>sec<em>[s]<em></code>",      "<code>second<em>[s]<em></code>"</li></ul></td></tr>
 * <tr><th><code>%u</code></th>
 *     <td>The remote user name</td></tr>
 * <tr><th><code>%U</code></th>
 *     <td>The URL path requested, not including any query string</td></tr>
 * <tr><th><code>%v</code></th>
 *     <td>The name of the server which served the request</td></tr>
 * </table>
 *
 * @author <a href="mailto:pier@usrz.com">Pier Fumagalli</a>
 * @author <a href="http://www.usrz.com/">USRZ.com</a>
 */
public class ApacheLogFormat implements AccessLogFormat {

    /* The UTC time zone */
    private static final TimeZone UTC = TimeZone.getTimeZone("UTC");

    /** A {@link String} representing our version of Apache's <em>common</em> format. */
    public static final String COMMON_FORMAT = "%h - %u %t \"%r\" %s %b";
    /** A {@link String} representing our version of Apache's <em>combined</em> format. */
    public static final String COMBINED_FORMAT = "%h - %u %t \"%r\" %s %b \"%{Referer}i\" \"%{User-agent}i\"";
    /** A {@link String} representing our version of Apache's <em>common with virtual-hosts</em> format. */
    public static final String VHOST_COMMON_FORMAT = "%v %h - %u %t \"%r\" %s %b";
    /** A {@link String} representing our version of Apache's <em>combined with virtual-hosts</em> format. */
    public static final String VHOST_COMBINED_FORMAT = "%v %h - %u %t \"%r\" %s %b \"%{Referer}i\" \"%{User-agent}i\"";
    /** A {@link String} representing our version of Apache's <em>referer</em> format. */
    public static final String REFERER_FORMAT = "%{Referer}i -> %U";
    /** A {@link String} representing our version of Apache's <em>user-agent</em> format. */
    public static final String AGENT_FORMAT = "%{User-agent}i";

    /** A {@linkplain ApacheLogFormat format} compatible with Apache's <em>common</em> format. */
    public static final ApacheLogFormat COMMON = new ApacheLogFormat(COMMON_FORMAT);
    /** A {@linkplain ApacheLogFormat format} compatible with Apache's <em>combined</em> format. */
    public static final ApacheLogFormat COMBINED = new ApacheLogFormat(COMBINED_FORMAT);
    /** A {@linkplain ApacheLogFormat format} compatible with Apache's <em>common with virtual-hosts</em> format. */
    public static final ApacheLogFormat VHOST_COMMON = new ApacheLogFormat(VHOST_COMMON_FORMAT);
    /** A {@linkplain ApacheLogFormat format} compatible with Apache's <em>combined with virtual-hosts</em> format. */
    public static final ApacheLogFormat VHOST_COMBINED = new ApacheLogFormat(VHOST_COMBINED_FORMAT);
    /** A {@linkplain ApacheLogFormat format} compatible with Apache's <em>referer</em> format. */
    public static final ApacheLogFormat REFERER = new ApacheLogFormat(REFERER_FORMAT);
    /** A {@linkplain ApacheLogFormat format} compatible with Apache's <em>user-agent</em> format. */
    public static final ApacheLogFormat AGENT = new ApacheLogFormat(AGENT_FORMAT);

    /** A {@linkplain ApacheLogFormat format} compatible with Apache's <em>common</em> format set to use the <em>UTC</em> {@linkplain TimeZone time zone}. */
    public static final ApacheLogFormat COMMON_UTC = new ApacheLogFormat(UTC, COMMON_FORMAT);
    /** A {@linkplain ApacheLogFormat format} compatible with Apache's <em>combined</em> format set to use the <em>UTC</em> {@linkplain TimeZone time zone}. */
    public static final ApacheLogFormat COMBINED_UTC = new ApacheLogFormat(UTC, COMBINED_FORMAT);
    /** A {@linkplain ApacheLogFormat format} compatible with Apache's <em>common with virtual-hosts</em> format set to use the <em>UTC</em> {@linkplain TimeZone time zone}. */
    public static final ApacheLogFormat VHOST_COMMON_UTC = new ApacheLogFormat(UTC, VHOST_COMMON_FORMAT);
    /** A {@linkplain ApacheLogFormat format} compatible with Apache's <em>combined with virtual-hosts</em> format set to use the <em>UTC</em> {@linkplain TimeZone time zone}. */
    public static final ApacheLogFormat VHOST_COMBINED_UTC = new ApacheLogFormat(UTC, VHOST_COMBINED_FORMAT);
    /** A {@linkplain ApacheLogFormat format} compatible with Apache's <em>referer</em> format set to use the <em>UTC</em> {@linkplain TimeZone time zone}. */
    public static final ApacheLogFormat REFERER_UTC = new ApacheLogFormat(UTC, REFERER_FORMAT);
    /** A {@linkplain ApacheLogFormat format} compatible with Apache's <em>user-agent</em> format set to use the <em>UTC</em> {@linkplain TimeZone time zone}. */
    public static final ApacheLogFormat AGENT_UTC = new ApacheLogFormat(UTC, AGENT_FORMAT);

    /* Log log log, never enough */
    private static final Logger LOGGER = Grizzly.logger(HttpServer.class);

    /* Our list of fields for formatting */
    private final List<Field> fields;

    /* Our timezone */
    private final TimeZone timeZone;

    /**
     * Create a new {@link ApacheLogFormat} instance by parsing the format
     * from the specified {@link String}.
     */
    public ApacheLogFormat(String format) {
        this(TimeZone.getDefault(), format);
    }

    /**
     * Create a new {@link ApacheLogFormat} instance by parsing the format
     * from the specified {@link String}.
     */
    public ApacheLogFormat(TimeZone timeZone, String format) {
        if (timeZone == null) throw new NullPointerException("Null time zone");
        fields = new ArrayList<Field>();
        this.timeZone = timeZone;
        parse(format);
    }

    @Override
    public String format(Response response, Date timeStamp, long responseNanos) {
        final StringBuilder builder = new StringBuilder();
        final Request request = response.getRequest();
        for (Field field: fields) try {
            field.format(builder, request, response, timeStamp, responseNanos);
        } catch (Exception exception) {
            LOGGER.log(WARNING, "Exception formatting access log entry", exception);
            builder.append('-');
        }
        return builder.toString();
    }

    String unsafeFormat(Response response, Date timeStamp, long responseNanos) {
        final StringBuilder builder = new StringBuilder();
        final Request request = response.getRequest();
        for (Field field: fields) {
            field.format(builder, request, response, timeStamp, responseNanos);
        }
        return builder.toString();
    }

    /**
     * Return the <em>normalized</em> format associated with this instance.
     */
    public String getFormat() {
        final StringBuilder builder = new StringBuilder();
        for (Field field: fields) builder.append(field.toString());
        return builder.toString();
    }

    /* ====================================================================== */
    /* PARSING OF FORMAT STRINGS                                              */
    /* ====================================================================== */

    private void parse(String format) {
        for (int x = 0; x < format.length(); x ++) {
            switch (format.charAt(x)) {
                case '\\': x = parseEscape(format, x); break;
                case '%':  x = parseFormat(format, null, x); break;
                default: addLiteral(format.charAt(x));
            }
        }
    }

    private int parseFormat(String format, String parameter, int position) {
        if (++position < format.length()) {
            final char field = format.charAt(position);

            /* Initial check to see if parameter is supposed to be there */
            if (parameter != null) switch (field) {
                case 'C': break; // parameter is cookie name
                case 'h': break; // parameter for host ("local" or "remote")
                case 'i': break; // parameter is request header name
                case 'o': break; // parameter is response header name
                case 'p': break; // parameter for port ("local" or "remote")
                case 't': break; // parameter is simple date format's format
                case 'T': break; // parameter is scale ("nano", "micro", "milli" or number)
                default: throw new IllegalArgumentException("Unsupported parameter \"" + parameter + "\" for field '" + field + "' in [" + format + "] at character " + position);
            }


            switch (field) {
                case '{': return parseParameter(format, position);
                case '%': addLiteral('%'); break; // The percent sign
                case 'a': fields.add(new RemoteAddressField()); break; // Remote IP-address
                case 'A': fields.add(new LocalAddressField()); break; // Local IP-address
                case 'b': fields.add(new ResponseSizeField(false)); break; // Size of response in bytes in CLF format
                case 'B': fields.add(new ResponseSizeField(true)); break; // Size of response in bytes with zeroes
          /* */ case 'C': fields.add(new RequestCookieField(parameter)); break; // The contents of a cookie
                case 'D': fields.add(new ResponseTimeField("micro", format, position)); break; // The time taken to serve the request, in microseconds.
          /* */ case 'h':  // Remote (or local if parameterized) host
                    fields.add(parseLocal(parameter, false, field, format, position) ?
                                   new LocalHostField() :
                                   new RemoteHostField());
                    break;
                case 'H': fields.add(new RequestProtocolField()); break; // The request protocol
          /* */ case 'i': fields.add(new RequestHeaderField(parameter)); break; // A request header
                case 'm': fields.add(new RequestMethodField()); break; // The request method
          /* */ case 'o': fields.add(new ResponseHeaderField(parameter)); break; // A response header
          /* */ case 'p': // Local (or remote if parameterized) port
                    fields.add(parseLocal(parameter, true, field, format, position) ?
                                   new LocalPortField() :
                                   new RemotePortField());
                    break;
                case 'q': fields.add(new RequestQueryField()); break; // The query string (prepended with a ?)
                case 'r': // First line of request, alias to "%m %U%q %H"
                    fields.add(new RequestMethodField());
                    addLiteral(' ');
                    fields.add(new RequestURIField());
                    fields.add(new RequestQueryField());
                    addLiteral(' ');
                    fields.add(new RequestProtocolField());
                    break;

                case 's': fields.add(new ResponseStatusField()); break; // Status.
          /* */ case 't': fields.add(new RequestTimeField(parameter, timeZone)); break; // The time, in the form given by format, which should be in strftime(3) format. (potentially localized)
          /* */ case 'T': fields.add(new ResponseTimeField(parameter, format, position)); break; // The time taken to serve the request, in the scale specified.
                case 'u': fields.add(new RequestUserField()); break; // The URL path requested, not including any query string.
                case 'U': fields.add(new RequestURIField()); break; // The URL path requested, not including any query string.
                case 'v': fields.add(new ServerNameField()); break; // The canonical ServerName of the server serving the request.
                default: throw new IllegalArgumentException("Unsupported field '" + field + "' in [" + format + "] at character " + position);
            }
            return position;
        }
        throw new IllegalArgumentException("Unterminated field declaration in [" + format + "] at character " + position);
    }

    private boolean parseLocal(String parameter, boolean defaultValue, char field, String format, int position) {
        if (parameter == null) return defaultValue;
        final String p = parameter.trim().toLowerCase();
        if (p.equals("local")) {
            return true;
        } else if (p.equals("remote")) {
            return false;
        } else {
            throw new IllegalArgumentException("Unsupported parameter \"" + parameter + "\" for field '" + field + "' in [" + format + "] at character " + position);
        }
    }

    private int parseParameter(String format, int position) {
        if (++position < format.length()) {
            final int end = format.indexOf('}', position);
            if (end == position) {
                return parseFormat(format, null, end);
            } else if (end > position) {
                return parseFormat(format, format.substring(position, end), end);
            }
        }
        throw new IllegalArgumentException("Unterminated format parameter in [" + format + "] at character " + position);
    }

    private int parseEscape(String format, int position) {
        if (++position < format.length()) {
            final char escaped = format.charAt(position);
            switch (escaped) {
                case 't': addLiteral('\t'); break;
                case 'b': addLiteral('\b'); break;
                case 'n': addLiteral('\n'); break;
                case 'r': addLiteral('\r'); break;
                case 'f': addLiteral('\f'); break;
                default:  addLiteral(escaped);
            }
            return position;
        }
        throw new IllegalArgumentException("Unterminated escape sequence in [" + format + "] at character " + position);
    }

    /* ====================================================================== */

    private void addLiteral(char c) {
        /* See if we can add to the previuos literal field */
        if (!fields.isEmpty()) {
            final Field last = fields.get(fields.size() - 1);
            if (last instanceof LiteralField) {
                ((LiteralField) last).append(c);
                return;
            }
        }

        /* List empty or last field was not a literal, add a new one */
        fields.add(new LiteralField(c));
    }

    /* ====================================================================== */
    /* FIELD DECLARATIONS                                                     */
    /* ====================================================================== */

    private static abstract class Field {

        abstract StringBuilder format(StringBuilder builder, Request request, Response response, Date timeStamp, long responseNanos);

        @Override
        public abstract String toString();

    }

    private static abstract class AbstractField extends Field {
        private final char format;
        private final String parameter;

        protected AbstractField(char format) {
            this(format, null);
        }

        protected AbstractField(char format, String parameter) {
            this.format = format;
            this.parameter = parameter;
        }

        @Override
        public final String toString() {
            final StringBuilder builder = new StringBuilder().append('%');
            if (parameter != null) builder.append('{').append(parameter).append('}');
            return builder.append(format).toString();
        }
    }

    /* ====================================================================== */

    private abstract static class HeaderField extends AbstractField {

        final String name;

        HeaderField(char format, String name) {
            super(format, name.trim().toLowerCase());
            this.name = name.trim().toLowerCase();
        }

        StringBuilder format(StringBuilder builder, MimeHeaders headers) {
            final Iterator<String> iterator = headers.values(name).iterator();
            if (iterator.hasNext()) builder.append(iterator.next());
            while (iterator.hasNext()) builder.append("; ").append(iterator.next());
            return builder;
        }
    }

    /* ====================================================================== */

    private static class LiteralField extends Field {

        final StringBuilder contents;

        LiteralField(char character) {
            contents = new StringBuilder().append(character);
        }

        void append(char character) {
            contents.append(character);
        }

        @Override
        StringBuilder format(StringBuilder builder, Request request, Response response, Date timeStamp, long responseNanos) {
            return builder.append(contents);
        }

        @Override
        public String toString() {
            final StringBuilder builder = new StringBuilder();
            for (int x = 0; x < contents.length(); x ++) {
                final char character = contents.charAt(x);
                /* Escape \t \b \n \r \f and %% */
                switch(character) {
                    case 't': case 'b': case 'n': case 'r': case 'f':
                        builder.append('\\'); break;
                    case '%':
                        builder.append('%'); break;
                }
                builder.append(character);
            }
            return builder.toString();
        }
    }

    /* ====================================================================== */

    private static class ServerNameField extends AbstractField {

        ServerNameField() {
            super('v');
        }

        @Override
        StringBuilder format(StringBuilder builder, Request request, Response response, Date timeStamp, long responseNanos) {
            final String name = request.getServerName();
            return builder.append(name == null ? "-" : name);
        }
    }

    /* ====================================================================== */

    private static class LocalHostField extends AbstractField {

        LocalHostField() {
            super('h', "local");
        }

        @Override
        StringBuilder format(StringBuilder builder, Request request, Response response, Date timeStamp, long responseNanos) {
            final String host = request.getLocalName();
            return builder.append(host == null ? "-" : host);
        }
    }

    /* ====================================================================== */

    private static class LocalAddressField extends AbstractField {

        LocalAddressField() {
            super('A');
        }

        @Override
        StringBuilder format(StringBuilder builder, Request request, Response response, Date timeStamp, long responseNanos) {
            final String address = request.getLocalAddr();
            return builder.append(address == null ? "-" : address);
        }
    }

    /* ====================================================================== */

    private static class LocalPortField extends AbstractField {

        LocalPortField() {
            super('p');
        }

        @Override
        StringBuilder format(StringBuilder builder, Request request, Response response, Date timeStamp, long responseNanos) {
            final int port = request.getLocalPort();
            return builder.append(port < 1 ? "-" : port);
        }
    }

    /* ====================================================================== */

    private static class RemoteHostField extends AbstractField {

        RemoteHostField() {
            super('h');
        }

        @Override
        StringBuilder format(StringBuilder builder, Request request, Response response, Date timeStamp, long responseNanos) {
            final String host = request.getRemoteHost();
            return builder.append(host == null ? "-" : host);
        }
    }

    /* ====================================================================== */

    private static class RemoteAddressField extends AbstractField {

        RemoteAddressField() {
            super('a');
        }

        @Override
        StringBuilder format(StringBuilder builder, Request request, Response response, Date timeStamp, long responseNanos) {
            final String address = request.getRemoteAddr();
            return builder.append(address == null ? "-" : address);
        }
    }

    /* ====================================================================== */

    private static class RemotePortField extends AbstractField {

        RemotePortField() {
            super('p', "remote");
        }

        @Override
        StringBuilder format(StringBuilder builder, Request request, Response response, Date timeStamp, long responseNanos) {
            final int port = request.getRemotePort();
            return builder.append(port < 1 ? "-" : port);
        }
    }

    /* ====================================================================== */

    private static class RequestTimeField extends Field {

        private static final String DEFAULT_PATTERN = "[yyyy/MMM/dd:HH:mm:ss Z]";
        private final SimpleDateFormatThreadLocal simpleDateFormat;
        private final TimeZone timeZone;
        private final String pattern;
        private final String format;

        RequestTimeField(String format, TimeZone zone) {
            this.format = format;
            if (format == null) {
                pattern = DEFAULT_PATTERN;
                timeZone = zone;
            } else {
                /* Check for timezone separation */
                final int pos = format.lastIndexOf('@');

                if ((pos < 0) || ((pos > 0) && (format.charAt(pos - 1) == '@'))) {
                    /* There is no '@' or the last '@' is actually an '@@' */
                    pattern = format.replace("@@", "@");
                    timeZone = zone;

                } else if (pos == 0) {
                    /* We have *ONLY* a time zone specified */
                    pattern = DEFAULT_PATTERN;
                    timeZone = TimeZone.getTimeZone(format.substring(1));

                } else {
                    /* We have both format and time zone */
                    pattern = format.substring(0, pos).replace("@@", "@");
                    timeZone = TimeZone.getTimeZone(format.substring(pos + 1));
                }
            }

            /* Get our simple date format */
            simpleDateFormat = new SimpleDateFormatThreadLocal(pattern);
        }

        @Override
        StringBuilder format(StringBuilder builder, Request request, Response response, Date timeStamp, long responseNanos) {
            if (timeStamp == null) return builder.append('-');

            final SimpleDateFormat format = simpleDateFormat.get();
            format.setTimeZone(timeZone);
            return builder.append(format.format(timeStamp));
        }

        @Override
        public String toString() {
            return format == null ? "%t" : "%{" + format + "}t";
        }
    }

    /* ====================================================================== */

    private static class RequestMethodField extends AbstractField {

        RequestMethodField() {
            super('m');
        }

        @Override
        StringBuilder format(StringBuilder builder, Request request, Response response, Date timeStamp, long responseNanos) {
            final Method method = request.getMethod();
            return builder.append(method == null ? "-" : method.toString());
        }
    }

    /* ====================================================================== */

    private static class RequestUserField extends AbstractField {

        RequestUserField() {
            super('u');
        }

        @Override
        StringBuilder format(StringBuilder builder, Request request, Response response, Date timeStamp, long responseNanos) {
            final String user = request.getRemoteUser();
            return builder.append(user == null ? "-" : user);
        }
    }

    /* ====================================================================== */

    private static class RequestURIField extends AbstractField {

        RequestURIField() {
            super('U');
        }

        @Override
        StringBuilder format(StringBuilder builder, Request request, Response response, Date timeStamp, long responseNanos) {
            final String uri = request.getRequestURI();
            return builder.append(uri == null ? "-" : uri);
        }
    }

    /* ====================================================================== */

    private static class RequestQueryField extends AbstractField {

        RequestQueryField() {
            super('q');
        }

        @Override
        StringBuilder format(StringBuilder builder, Request request, Response response, Date timeStamp, long responseNanos) {
            final String query = request.getQueryString();
            if (query != null) builder.append('?').append(query);
            return builder;
        }
    }

    /* ====================================================================== */

    private static class RequestProtocolField extends AbstractField {

        RequestProtocolField() {
            super('H');
        }

        @Override
        StringBuilder format(StringBuilder builder, Request request, Response response, Date timeStamp, long responseNanos) {
            final Protocol protocol = request.getProtocol();
            if (protocol == null) return builder.append("-");
            switch (protocol) {
                case HTTP_0_9: return builder.append("HTTP/0.9");
                case HTTP_1_0: return builder.append("HTTP/1.0");
                case HTTP_1_1: return builder.append("HTTP/1.1");
                default: return builder.append("-");
            }
        }
    }

    /* ====================================================================== */

    private static class RequestHeaderField extends HeaderField {

        RequestHeaderField(String name) {
            super('i', name);
        }

        @Override
        StringBuilder format(StringBuilder builder, Request request, Response response, Date timeStamp, long responseNanos) {
            return this.format(builder, request.getRequest().getHeaders());
        }
    }

    /* ====================================================================== */

    private static class RequestCookieField extends AbstractField {

        final String name;

        RequestCookieField(String name) {
            super('C', name.trim().toLowerCase());
            this.name = name.trim().toLowerCase();
        }

        @Override
        StringBuilder format(StringBuilder builder, Request request, Response response, Date timeStamp, long responseNanos) {
            final Cookie[] cookies = request.getCookies();
            if (cookies != null) for (Cookie cookie: cookies) {
                if (name.equals(cookie.getName().toLowerCase())) {
                    return builder.append(cookie.getValue());
                }
            }
            return builder;
        }
    }

    /* ====================================================================== */

    private static class ResponseStatusField extends AbstractField {

        ResponseStatusField() {
            super('s');
        }

        @Override
        StringBuilder format(StringBuilder builder, Request request, Response response, Date timeStamp, long responseNanos) {
            final int status = response.getStatus();
            if (status < 10) builder.append('0');
            if (status < 100) builder.append('0');
            return builder.append(status);
        }
    }

    /* ====================================================================== */

    private static class ResponseSizeField extends AbstractField {

        final String zero;

        ResponseSizeField(boolean zero) {
            super(zero ? 'B' : 'b');
            this.zero = zero ? "0" : "-";
        }

        @Override
        StringBuilder format(StringBuilder builder, Request request, Response response, Date timeStamp, long responseNanos) {
            final long size = response.getContentLengthLong();
            return builder.append(size < 1 ? zero : Long.toString(size));
        }
    }

    /* ====================================================================== */

    private static class ResponseTimeField extends Field {

        private final long scale;

        ResponseTimeField(String unit, String format, int position) {
            /* Figure out the scale */
            if (unit == null) {
                scale = 1000000000;
                return;
            }

            String s = unit.trim().toLowerCase();
            if (s.equals("n") || s.equals("nano") || s.equals(
                    "nanos") || s.equals("nanosec") || s.equals(
                    "nanosecs") || s.equals("nanosecond") || s.equals(
                    "nanoseconds")) {
                scale = 1;
            } else if (s.equals("micro") || s.equals("micros") || s.equals(
                    "microsec") || s.equals("microsecs") || s.equals(
                    "microsecond") || s.equals("microseconds")) {
                scale = 1000;
            } else if (s.equals("m") || s.equals("milli") || s.equals(
                    "millis") || s.equals("millisec") || s.equals(
                    "millisecs") || s.equals("millisecond") || s.equals(
                    "milliseconds")) {
                scale = 1000000;
            } else if (s.equals("s") || s.equals("sec") || s.equals(
                    "secs") || s.equals("second") || s.equals("seconds")) {
                scale = 1000000000;
            } else {
                throw new IllegalArgumentException(
                        "Unsupported time unit \"" + unit + "\" for field 'T' in [" + format + "] at character " + position);
            }
        }

        @Override
        StringBuilder format(StringBuilder builder, Request request, Response response, Date timeStamp, long responseNanos) {
            if (responseNanos < 0) return builder.append('-');
            return builder.append(responseNanos / scale);
        }

        @Override
        public String toString() {
            final StringBuilder string = new StringBuilder().append('%');
            if      (scale          == 1) string.append("{n}T");
            else if (scale       == 1000) string.append('D');
            else if (scale    == 1000000) string.append("{m}T");
            else if (scale == 1000000000) string.append('T');
            else string.append('{').append(scale).append("}T");
            return string.toString();
        }
    }

    /* ====================================================================== */

    private static class ResponseHeaderField extends HeaderField {

        ResponseHeaderField(String name) {
            super('o', name);
        }

        @Override
        StringBuilder format(StringBuilder builder, Request request, Response response, Date timeStamp, long responseNanos) {
            return this.format(builder, response.getResponse().getHeaders());
        }
    }
}
