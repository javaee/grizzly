/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2010 Sun Microsystems, Inc. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License. You can obtain
 * a copy of the License at https://glassfish.dev.java.net/public/CDDL+GPL.html
 * or glassfish/bootstrap/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at glassfish/bootstrap/legal/LICENSE.txt.
 * Sun designates this particular file as subject to the "Classpath" exception
 * as provided by Sun in the GPL Version 2 section of the License file that
 * accompanied this code.  If applicable, add the following below the License
 * Header, with the fields enclosed by brackets [] replaced by your own
 * identifying information: "Portions Copyrighted [year]
 * [name of copyright owner]"
 *
 * Contributor(s):
 *
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
 *
 *
 * This file incorporates work covered by the following copyright and
 * permission notice:
 */

package com.sun.grizzly.http.core;

import com.sun.grizzly.Buffer;
import com.sun.grizzly.http.Cookie;
import com.sun.grizzly.http.CookiesBuilder;
import com.sun.grizzly.http.util.CookieUtils;
import com.sun.grizzly.utils.Pair;
import java.text.ParseException;
import java.util.List;
import junit.framework.TestCase;

/**
 * Cookie serialization/parsing test
 * 
 * @author Alexey Stashok
 */
public class CookiesTest extends TestCase {

    private static Pair[] TEST_CASE_CLIENT_COOKIE =
            new Pair[] {
        new Pair("CUSTOMER=WILE_E_COYOTE", new Checker[]{
            new Checker(0, "CUSTOMER", CheckValue.NAME),
            new Checker(0, "WILE_E_COYOTE", CheckValue.VALUE),
            new Checker(0, 0, CheckValue.VERSION)
        }),

        new Pair("CUSTOMER=WILE_E_COYOTE; PART_NUMBER=ROCKET_LAUNCHER_0001", new Checker[]{
            new Checker(0, "CUSTOMER", CheckValue.NAME),
            new Checker(0, "WILE_E_COYOTE", CheckValue.VALUE),
            new Checker(0, 0, CheckValue.VERSION),
            new Checker(1, "PART_NUMBER", CheckValue.NAME),
            new Checker(1, "ROCKET_LAUNCHER_0001", CheckValue.VALUE),
            new Checker(1, 0, CheckValue.VERSION)
        }),

        new Pair("$Version=\"1\"; Customer=\"WILE_E_COYOTE\"; $Path=\"/acme\"", new Checker[]{
            new Checker(0, "Customer", CheckValue.NAME),
            new Checker(0, "WILE_E_COYOTE", CheckValue.VALUE),
            new Checker(0, "/acme", CheckValue.PATH),
            new Checker(0, 1, CheckValue.VERSION)
        }),

        new Pair("$Version=\"1\"; Customer=\"WILE_E_COYOTE\"; $Path=\"/acme\"; $Domain=\"mydomain.com\"; Part_Number=\"Rocket_Launcher_0001\"; $Path=\"/acme\"", new Checker[]{
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

        new Pair("$Version=\"1\"; Part_Number=\"Riding_Rocket_0023\"; $Path=\"/acme/ammo\"; Part_Number=\"Rocket_Launcher_0001\"; $Path=\"/acme\"", new Checker[]{
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

    private static Pair[] TEST_CASE_SERVER_COOKIE =
            new Pair[] {
        new Pair("CUSTOMER=WILE_E_COYOTE; path=/; expires=Wednesday, 09-Nov-99 23:12:40 GMT", new Checker[]{
            new Checker(0, "CUSTOMER", CheckValue.NAME),
            new Checker(0, "WILE_E_COYOTE", CheckValue.VALUE),
            new Checker(0, expire2MaxAge("Wednesday, 09-Nov-99 23:12:40 GMT"), CheckValue.MAX_AGE),
            new Checker(0, 0, CheckValue.VERSION)
        }),

        new Pair("Part_Number=\"Rocket_Launcher_0001\"; Version=\"1\"; Path=\"/acme\"", new Checker[]{
            new Checker(0, "Part_Number", CheckValue.NAME),
            new Checker(0, "Rocket_Launcher_0001", CheckValue.VALUE),
            new Checker(0, "/acme", CheckValue.PATH),
            new Checker(0, 1, CheckValue.VERSION)
        }),

        new Pair("Part_Number=\"Rocket_Launcher_0001\"; Version=\"1\"; Path=\"/acme\", Customer=\"WILE_E_COYOTE\"; Version=\"1\"; Path=\"/acme/path\"", new Checker[]{
            new Checker(0, "Part_Number", CheckValue.NAME),
            new Checker(0, "Rocket_Launcher_0001", CheckValue.VALUE),
            new Checker(0, "/acme", CheckValue.PATH),
            new Checker(0, 1, CheckValue.VERSION),
            new Checker(1, "Customer", CheckValue.NAME),
            new Checker(1, "WILE_E_COYOTE", CheckValue.VALUE),
            new Checker(1, "/acme/path", CheckValue.PATH),
            new Checker(1, 1, CheckValue.VERSION)
        }),
    };
    
    public void testClientCookie() {
        for (Pair<String, Checker[]> testCase : TEST_CASE_CLIENT_COOKIE) {
            String cookieString = testCase.getFirst();
            
            final List<Cookie> cookies =
                    CookiesBuilder.client().parse(cookieString).build();

            final Checker[] checkers = testCase.getSecond();

            for (Checker checker : checkers) {
                final Cookie cookie = cookies.get(checker.getCookieIdx());
                assertTrue("Mismatch. Checker=" + checker.getCheckValue() +
                        " expected=" + checker.getExpected() +
                        " value=" + checker.getCheckValue().get(cookie),
                        checker.check(cookie));
            }

            for (Cookie cookie : cookies) {
                final String serializedString = cookie.asClientCookieString();
                List<Cookie> parsedCookies = CookiesBuilder.client().parse(serializedString).build();
                assertEquals(1, parsedCookies.size());
                
                Cookie parsedCookie = parsedCookies.get(0);

                assertTrue(equalsCookies(cookie, parsedCookie));

                Buffer serializedBuffer = cookie.asClientCookieBuffer();
                parsedCookies = CookiesBuilder.client().parse(serializedBuffer).build();
                assertEquals(1, parsedCookies.size());

                parsedCookie = parsedCookies.get(0);

                assertTrue(equalsCookies(cookie, parsedCookie));
            }
        }
    }

    public void testServerCookie() {
        for (Pair<String, Checker[]> testCase : TEST_CASE_SERVER_COOKIE) {
            String cookieString = testCase.getFirst();

            final List<Cookie> cookies =
                    CookiesBuilder.server().parse(cookieString).build();

            final Checker[] checkers = testCase.getSecond();

            for (Checker checker : checkers) {
                final Cookie cookie = cookies.get(checker.getCookieIdx());
                assertTrue("Mismatch. Checker=" + checker.getCheckValue() +
                        " expected=" + checker.getExpected() +
                        " value=" + checker.getCheckValue().get(cookie),
                        checker.check(cookie));
            }

            for (Cookie cookie : cookies) {
                final String serializedString = cookie.asServerCookieString();
                List<Cookie> parsedCookies = CookiesBuilder.server().parse(serializedString).build();
                assertEquals(1, parsedCookies.size());

                Cookie parsedCookie = parsedCookies.get(0);

                assertTrue(equalsCookies(cookie, parsedCookie));

                Buffer serializedBuffer = cookie.asServerCookieBuffer();
                parsedCookies = CookiesBuilder.server().parse(serializedBuffer).build();
                assertEquals(1, parsedCookies.size());

                parsedCookie = parsedCookies.get(0);

                assertTrue(equalsCookies(cookie, parsedCookie));
            }
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
                    // In the tests we allow max-age to have 5sec precision.
                return Math.abs((Integer) pattern - cookie.getMaxAge()) < 5000;
            }
        };

        public abstract Object get(Cookie cookie);

        public boolean check(Object pattern, Cookie cookie) {
            return pattern.equals(get(cookie));
        }
    }

    private static int expire2MaxAge(String expire) {
        try {
            return (int) (CookieUtils.OLD_COOKIE_FORMAT.get().parse(expire).getTime() - System.currentTimeMillis());
        } catch (ParseException ex) {
            throw new IllegalArgumentException("Illegal expire value: " + expire);
        }
    }
}
