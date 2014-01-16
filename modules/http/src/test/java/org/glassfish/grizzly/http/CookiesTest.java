/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2010-2014 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.grizzly.http;

import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.http.util.CookieUtils;
import org.glassfish.grizzly.memory.Buffers;
import org.glassfish.grizzly.memory.MemoryManager;
import org.glassfish.grizzly.utils.Charsets;
import org.glassfish.grizzly.utils.Pair;

import java.nio.ByteBuffer;
import java.text.ParseException;
import java.util.Date;
import junit.framework.TestCase;

/**
 * Cookie serialization/parsing test
 * 
 * @author Alexey Stashok
 */
public class CookiesTest extends TestCase {

    private static Pair[] createClientTestCaseCookie() {
        return new Pair[]{
                    new Pair<String, Checker[]>("CUSTOMER=WILE_E_COYOTE", new Checker[]{
                        new Checker(0, "CUSTOMER", CheckValue.NAME),
                        new Checker(0, "WILE_E_COYOTE", CheckValue.VALUE),
                        new Checker(0, 0, CheckValue.VERSION)
                    }),
                    new Pair<String, Checker[]>("CUSTOMER=WILE_E_COYOTE; PART_NUMBER=ROCKET_LAUNCHER_0001", new Checker[]{
                        new Checker(0, "CUSTOMER", CheckValue.NAME),
                        new Checker(0, "WILE_E_COYOTE", CheckValue.VALUE),
                        new Checker(0, 0, CheckValue.VERSION),
                        new Checker(1, "PART_NUMBER", CheckValue.NAME),
                        new Checker(1, "ROCKET_LAUNCHER_0001", CheckValue.VALUE),
                        new Checker(1, 0, CheckValue.VERSION)
                    }),
                    new Pair<String, Checker[]>("$Version=\"1\"; Customer=\"WILE_E_COYOTE\"; $Path=\"/acme\"", new Checker[]{
                        new Checker(0, "Customer", CheckValue.NAME),
                        new Checker(0, "WILE_E_COYOTE", CheckValue.VALUE),
                        new Checker(0, "/acme", CheckValue.PATH),
                        new Checker(0, 1, CheckValue.VERSION)
                    }),
                    new Pair<String, Checker[]>("$Version=\"1\"; Customer=\"WILE_E_COYOTE\"; $Path=\"/acme\"; $Domain=\"mydomain.com\"; Part_Number=\"Rocket_Launcher_0001\"; $Path=\"/acme\"", new Checker[]{
                        new Checker(0, "Customer", CheckValue.NAME),
                        new Checker(0, "WILE_E_COYOTE", CheckValue.VALUE),
                        new Checker(0, "/acme", CheckValue.PATH),
                        new Checker(0, "mydomain.com", CheckValue.DOMAIN),
                        new Checker(0, 1, CheckValue.VERSION),
                        new Checker(1, "Part_Number", CheckValue.NAME),
                        new Checker(1, "Rocket_Launcher_0001", CheckValue.VALUE),
                        new Checker(1, "/acme", CheckValue.PATH),
                        new Checker(1, 1, CheckValue.VERSION)
                    }),
                    new Pair<String, Checker[]>("$Version=\"1\"; Part_Number=\"Riding_Rocket_0023\"; $Path=\"/acme/ammo\"; Part_Number=\"Rocket_Launcher_0001\"; $Path=\"/acme\"", new Checker[]{
                        new Checker(0, "Part_Number", CheckValue.NAME),
                        new Checker(0, "Riding_Rocket_0023", CheckValue.VALUE),
                        new Checker(0, "/acme/ammo", CheckValue.PATH),
                        new Checker(0, 1, CheckValue.VERSION),
                        new Checker(1, "Part_Number", CheckValue.NAME),
                        new Checker(1, "Rocket_Launcher_0001", CheckValue.VALUE),
                        new Checker(1, "/acme", CheckValue.PATH),
                        new Checker(1, 1, CheckValue.VERSION)
                    })
                };
    }

    private static final long IN_HOUR = System.currentTimeMillis() + 1000 * 60 * 60;
    //ex. Wednesday, 09-Nov-99 23:12:40 GMT
    private static final String expiresStr = CookieUtils.OLD_COOKIE_FORMAT.get().format(new Date(IN_HOUR));

    private static Pair[] createServerTestCaseCookie() {
        return new Pair[]{
                    new Pair<String, Checker[]>("CUSTOMER=WILE_E_COYOTE; path=/; expires=" + expiresStr, new Checker[]{
                        new Checker(0, "CUSTOMER", CheckValue.NAME),
                        new Checker(0, "WILE_E_COYOTE", CheckValue.VALUE),
                        new Checker(0, expire2MaxAge(expiresStr), CheckValue.MAX_AGE),
                        new Checker(0, 0, CheckValue.VERSION)
                    }),
                    new Pair<String, Checker[]>("Part_Number=\"Rocket_Launcher_0001\"; Version=\"1\"; Path=\"/acme\"", new Checker[]{
                        new Checker(0, "Part_Number", CheckValue.NAME),
                        new Checker(0, "Rocket_Launcher_0001", CheckValue.VALUE),
                        new Checker(0, "/acme", CheckValue.PATH),
                        new Checker(0, 1, CheckValue.VERSION)
                    }),
                    new Pair<String, Checker[]>("Part_Number=\"Rocket_Launcher_0001\"; Version=\"1\"; Path=\"/acme\", Customer=\"WILE_E_COYOTE\"; Version=\"1\"; Path=\"/acme/path\"", new Checker[]{
                        new Checker(0, "Part_Number", CheckValue.NAME),
                        new Checker(0, "Rocket_Launcher_0001", CheckValue.VALUE),
                        new Checker(0, "/acme", CheckValue.PATH),
                        new Checker(0, 1, CheckValue.VERSION),
                        new Checker(1, "Customer", CheckValue.NAME),
                        new Checker(1, "WILE_E_COYOTE", CheckValue.VALUE),
                        new Checker(1, "/acme/path", CheckValue.PATH),
                        new Checker(1, 1, CheckValue.VERSION)
                    }),};
    }

    @SuppressWarnings({"unchecked"})
    public void testClientCookie() {
        for (Pair<String, Checker[]> testCase : createClientTestCaseCookie()) {
            final String cookieString = testCase.getFirst();
            final Checker[] checkers = testCase.getSecond();

            Cookie[] cookies =
                    CookiesBuilder.client().parse(cookieString).build().get();
            validateClientCookies(testCase, cookies, checkers);

            ByteBuffer b = ByteBuffer.allocateDirect(cookieString.length());
            b.put(cookieString.getBytes(Charsets.ASCII_CHARSET));
            b.flip();
            Buffer nonArrayBackedBuffer = Buffers.wrap(MemoryManager.DEFAULT_MEMORY_MANAGER, b);
            cookies = CookiesBuilder.client().parse(nonArrayBackedBuffer).build().get();
            validateClientCookies(testCase, cookies, checkers);

            Buffer byteBasedBuffer = Buffers.wrap(MemoryManager.DEFAULT_MEMORY_MANAGER, cookieString.getBytes(Charsets.ASCII_CHARSET));
            cookies = CookiesBuilder.client().parse(byteBasedBuffer).build().get();
            validateClientCookies(testCase, cookies, checkers);
        }
    }

    @SuppressWarnings({"unchecked"})
    public void testServerCookie() {
        for (Pair<String, Checker[]> testCase : createServerTestCaseCookie()) {
            final String cookieString = testCase.getFirst();
            final Checker[] checkers = testCase.getSecond();

            Cookie[] cookies =
                    CookiesBuilder.server().parse(cookieString).build().get();
            validateServerCookies(testCase, cookies, checkers);

            ByteBuffer b = ByteBuffer.allocateDirect(cookieString.length());
            b.put(cookieString.getBytes(Charsets.ASCII_CHARSET));
            b.flip();
            Buffer nonArrayBackedBuffer = Buffers.wrap(MemoryManager.DEFAULT_MEMORY_MANAGER, b);
            cookies = CookiesBuilder.server().parse(nonArrayBackedBuffer).build().get();
            validateServerCookies(testCase, cookies, checkers);

            Buffer byteBasedBuffer = Buffers.wrap(MemoryManager.DEFAULT_MEMORY_MANAGER, cookieString.getBytes(Charsets.ASCII_CHARSET));
            cookies = CookiesBuilder.server().parse(byteBasedBuffer).build().get();
            validateServerCookies(testCase, cookies, checkers);
        }
    }

    private void validateServerCookies(Pair<String, Checker[]> testCase, Cookie[] cookies, Checker[] checkers) {
        for (Checker checker : checkers) {
            final Cookie cookie = cookies[checker.getCookieIdx()];
            assertTrue("Mismatch. Checker=" + checker.getCheckValue() +
                    " expected=" + checker.getExpected() +
                    " value=" + checker.getCheckValue().get(cookie),
                    checker.check(cookie));
        }

        for (Cookie cookie : cookies) {
            final String serializedString = cookie.asServerCookieString();
            Cookie[] parsedCookies = CookiesBuilder.server().parse(serializedString).build().get();
            assertEquals(testCase.toString(), 1, parsedCookies.length);

            Cookie parsedCookie = parsedCookies[0];

            assertTrue(equalsCookies(cookie, parsedCookie));

            Buffer serializedBuffer = cookie.asServerCookieBuffer();
            parsedCookies = CookiesBuilder.server().parse(serializedBuffer).build().get();
            assertEquals(testCase.toString(), 1, parsedCookies.length);

            parsedCookie = parsedCookies[0];

            assertTrue(testCase.toString(), equalsCookies(cookie, parsedCookie));
        }
    }

    private void validateClientCookies(Pair<String, Checker[]> testCase, Cookie[] cookies, Checker[] checkers) {
        for (Checker checker : checkers) {
            final Cookie cookie = cookies[checker.getCookieIdx()];
            assertTrue("Mismatch. Checker=" + checker.getCheckValue() +
                    " expected=" + checker.getExpected() +
                    " value=" + checker.getCheckValue().get(cookie),
                    checker.check(cookie));
        }

        for (Cookie cookie : cookies) {
            final String serializedString = cookie.asClientCookieString();
            Cookie[] parsedCookies = CookiesBuilder.client().parse(serializedString).build().get();
            assertEquals(testCase.toString(), 1, parsedCookies.length);

            Cookie parsedCookie = parsedCookies[0];

            assertTrue(testCase.toString(), equalsCookies(cookie, parsedCookie));

            Buffer serializedBuffer = cookie.asClientCookieBuffer();
            parsedCookies = CookiesBuilder.client().parse(serializedBuffer).build().get();
            assertEquals(1, parsedCookies.length);

            parsedCookie = parsedCookies[0];

            assertTrue(testCase.toString(), equalsCookies(cookie, parsedCookie));
        }
    }
    
    private boolean equalsCookies(Cookie expected, Cookie got) {
        return equalsObjects("Name", expected.getName(), got.getName()) ||
                equalsObjects("Value", expected.getValue(), got.getValue()) ||
                equalsObjects("Comment", expected.getComment(), got.getComment()) ||
                equalsObjects("Domain", expected.getDomain(), got.getDomain()) ||
                equalsObjects("Max-Age", expected.getMaxAge(), got.getMaxAge()) ||
                equalsObjects("Path", expected.getPath(), got.getPath()) ||
                equalsObjects("Version", expected.getVersion(), got.getVersion()) ||
                equalsObjects("HttpOnly", expected.isHttpOnly(), got.isHttpOnly()) ||
                equalsObjects("Secure", expected.isSecure(), got.isSecure());
    }

    private boolean equalsObjects(String cmpValue, Object o1, Object o2) {
        final boolean result = (o1 == null && o2 == null) || (o1 != null && o1.equals(o2)) || (o2 != null && o2.equals(o1));
        if (!result) {
            fail("Mismatch property=" + cmpValue + " expected=" + o1 + " got=" + o2);
        }

        return true;
    }

    public static class Checker {
        private final int cookieIdx;
        private final Object expected;
        private final CheckValue checkValue;

        public Checker(int cookieIdx, Object expected, CheckValue checkValue) {
            this.cookieIdx = cookieIdx;
            this.expected = expected;
            this.checkValue = checkValue;
        }

        public int getCookieIdx() {
            return cookieIdx;
        }

        public Object getExpected() {
            return expected;
        }

        public CheckValue getCheckValue() {
            return checkValue;
        }

        public boolean check(Cookie cookie) {
            return checkValue.check(expected, cookie);
        }
    }
    
    public enum CheckValue {
        NAME() {
            @Override
            public Object get(Cookie cookie) {
                return cookie.getName();
            }
        }, VALUE() {
            @Override
            public Object get(Cookie cookie) {
                return cookie.getValue();
            }
        }, PATH() {
            @Override
            public Object get(Cookie cookie) {
                return cookie.getPath();
            }
        }, DOMAIN() {
            @Override
            public Object get(Cookie cookie) {
                return cookie.getDomain();
            }
        }, COMMENT() {
            @Override
            public Object get(Cookie cookie) {
                return cookie.getComment();
            }
        }, VERSION() {
            @Override
            public Object get(Cookie cookie) {
                return cookie.getVersion();
            }
        }, HTTP_ONLY() {
            @Override
            public Object get(Cookie cookie) {
                return cookie.isHttpOnly();
            }
        }, SECURE() {
            @Override
            public Object get(Cookie cookie) {
                return cookie.isSecure();
            }
        }, MAX_AGE() {
            @Override
            public Object get(Cookie cookie) {
                return cookie.getMaxAge();
            }
            @Override
            public boolean check(Object pattern, Cookie cookie) {
                // In the tests we allow max-age to have 15sec precision.
                return Math.abs((Integer) pattern - cookie.getMaxAge()) < 15;
            }
        };

        public abstract Object get(Cookie cookie);

        public boolean check(Object pattern, Cookie cookie) {
            return pattern.equals(get(cookie));
        }
    }

    private static int expire2MaxAge(String expire) {
        try {
            return (int) (CookieUtils.OLD_COOKIE_FORMAT.get().parse(expire).getTime() - System.currentTimeMillis()) / 1000;
        } catch (ParseException ex) {
            throw new IllegalArgumentException("Illegal expire value: " + expire);
        }
    }
}
