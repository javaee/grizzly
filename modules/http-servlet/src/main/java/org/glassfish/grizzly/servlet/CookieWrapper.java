/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2008-2016 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.grizzly.servlet;

import org.glassfish.grizzly.http.Cookie;


/**
 * Simple Wrapper around {@link Cookie}.
 * @author jfarcand
 */
public class CookieWrapper extends Cookie {
    /**
     * Constructs a cookie with a specified name and value.
     *
     * <p>The name must conform to RFC 2109. That means it can contain 
     * only ASCII alphanumeric characters and cannot contain commas, 
     * semicolons, or white space or begin with a $ character. The cookie's
     * name cannot be changed after creation.
     *
     * <p>The value can be anything the server chooses to send. Its
     * value is probably of interest only to the server. The cookie's
     * value can be changed after creation with the
     * <code>setValue</code> method.
     *
     * <p>By default, cookies are created according to the Netscape
     * cookie specification. The version can be changed with the 
     * <code>setVersion</code> method.
     *
     *
     * @param name 			a <code>String</code> specifying the name of the cookie
     *
     * @param value			a <code>String</code> specifying the value of the cookie
     *
     * @throws IllegalArgumentException	if the cookie name contains illegal characters
     *					(for example, a comma, space, or semicolon)
     *					or it is one of the tokens reserved for use
     *					by the cookie protocol
     * @see #setValue
     * @see #setVersion
     *
     */
    public CookieWrapper(String name, String value) {
        super(name,value);
    }
    
    private javax.servlet.http.Cookie wrappedCookie = null;
            
    /**
     *
     * Specifies a comment that describes a cookie's purpose.
     * The comment is useful if the browser presents the cookie 
     * to the user. Comments
     * are not supported by Netscape Version 0 cookies.
     *
     * @param purpose		a <code>String</code> specifying the comment 
     *				to display to the user
     *
     * @see #getComment
     *
     */

    @Override
    public void setComment(String purpose) {
	wrappedCookie.setComment(purpose);
    }
    
    
    

    /**
     * Returns the comment describing the purpose of this cookie, or
     * <code>null</code> if the cookie has no comment.
     *
     * @return			a <code>String</code> containing the comment,
     *				or <code>null</code> if none
     *
     * @see #setComment
     *
     */ 

    @Override
    public String getComment() {
	return wrappedCookie.getComment();
    }
    
    
    


    /**
     *
     * Specifies the domain within which this cookie should be presented.
     *
     * <p>The form of the domain name is specified by RFC 2109. A domain
     * name begins with a dot (<code>.foo.com</code>) and means that
     * the cookie is visible to servers in a specified Domain Name System
     * (DNS) zone (for example, <code>www.foo.com</code>, but not 
     * <code>a.b.foo.com</code>). By default, cookies are only returned
     * to the server that sent them.
     *
     *
     * @param pattern		a <code>String</code> containing the domain name
     *				within which this cookie is visible;
     *				form is according to RFC 2109
     *
     * @see #getDomain
     *
     */

    @Override
    public void setDomain(String pattern) {
	wrappedCookie.setDomain(pattern);
    }
    
    
    
    

    /**
     * Returns the domain name set for this cookie. The form of 
     * the domain name is set by RFC 2109.
     *
     * @return			a <code>String</code> containing the domain name
     *
     * @see #setDomain
     *
     */ 

    @Override
    public String getDomain() {
	return wrappedCookie.getDomain();
    }




    /**
     * Sets the maximum age of the cookie in seconds.
     *
     * <p>A positive value indicates that the cookie will expire
     * after that many seconds have passed. Note that the value is
     * the <i>maximum</i> age when the cookie will expire, not the cookie's
     * current age.
     *
     * <p>A negative value means
     * that the cookie is not stored persistently and will be deleted
     * when the Web browser exits. A zero value causes the cookie
     * to be deleted.
     *
     * @param expiry		an integer specifying the maximum age of the
     * 				cookie in seconds; if negative, means
     *				the cookie is not stored; if zero, deletes
     *				the cookie
     *
     *
     * @see #getMaxAge
     *
     */

    @Override
    public void setMaxAge(int expiry) {
	wrappedCookie.setMaxAge(expiry);
    }




    /**
     * Returns the maximum age of the cookie, specified in seconds,
     * By default, <code>-1</code> indicating the cookie will persist
     * until browser shutdown.
     *
     *
     * @return			an integer specifying the maximum age of the
     *				cookie in seconds; if negative, means
     *				the cookie persists until browser shutdown
     *
     *
     * @see #setMaxAge
     *
     */

    @Override
    public int getMaxAge() {
	return wrappedCookie.getMaxAge();
    }
    
    
    

    /**
     * Specifies a path for the cookie
     * to which the client should return the cookie.
     *
     * <p>The cookie is visible to all the pages in the directory
     * you specify, and all the pages in that directory's subdirectories. 
     * A cookie's path must include the servlet that set the cookie,
     * for example, <i>/catalog</i>, which makes the cookie
     * visible to all directories on the server under <i>/catalog</i>.
     *
     * <p>Consult RFC 2109 (available on the Internet) for more
     * information on setting path names for cookies.
     *
     *
     * @param uri		a <code>String</code> specifying a path
     *
     *
     * @see #getPath
     *
     */

    @Override
    public void setPath(String uri) {
	wrappedCookie.setPath(uri);
    }




    /**
     * Returns the path on the server 
     * to which the browser returns this cookie. The
     * cookie is visible to all subpaths on the server.
     *
     *
     * @return		a <code>String</code> specifying a path that contains
     *			a servlet name, for example, <i>/catalog</i>
     *
     * @see #setPath
     *
     */ 

    @Override
    public String getPath() {
	return wrappedCookie.getPath();
    }





    /**
     * Indicates to the browser whether the cookie should only be sent
     * using a secure protocol, such as HTTPS or SSL.
     *
     * <p>The default value is <tt>false</tt>.
     *
     * @param flag	if <tt>true</tt>, sends the cookie from the browser
     *			to the server only when using a secure protocol;
     *			if <tt>false</tt>, sent on any protocol
     *
     * @see #isSecure()
     *
     */
 
    @Override
    public void setSecure(boolean flag) {
	wrappedCookie.setSecure(flag);
    }




    /**
     * Returns <tt>true</tt> if the browser is sending cookies
     * only over a secure protocol, or <tt>false</tt> if the
     * browser can send cookies using any protocol.
     *
     * @return		<tt>true</tt> if the browser uses a secure protocol;
     * 			 otherwise, <tt>true</tt>
     *
     * @see #setSecure
     *
     */

    @Override
    public boolean isSecure() {
	return wrappedCookie.getSecure();
    }





    /**
     * Returns the name of the cookie. The name cannot be changed after
     * creation.
     *
     * @return		a <code>String</code> specifying the cookie's name
     *
     */

    @Override
    public String getName() {
	return wrappedCookie.getName();
    }





    /**
     *
     * Assigns a new value to a cookie after the cookie is created.
     * If you use a binary value, you may want to use BASE64 encoding.
     *
     * <p>With Version 0 cookies, values should not contain white 
     * space, brackets, parentheses, equals signs, commas,
     * double quotes, slashes, question marks, at signs, colons,
     * and semicolons. Empty values may not behave the same way
     * on all browsers.
     *
     * @param newValue		a <code>String</code> specifying the new value 
     *
     *
     * @see #getValue
     * @see Cookie
     *
     */

    @Override
    public void setValue(String newValue) {	    
	wrappedCookie.setValue(newValue);
    }




    /**
     * Returns the value of the cookie.
     *
     * @return			a <code>String</code> containing the cookie's
     *				present value
     *
     * @see #setValue
     * @see Cookie
     *
     */

    @Override
    public String getValue() {
	return wrappedCookie.getValue();
    }




    /**
     * Returns the version of the protocol this cookie complies 
     * with. Version 1 complies with RFC 2109, 
     * and version 0 complies with the original
     * cookie specification drafted by Netscape. Cookies provided
     * by a browser use and identify the browser's cookie version.
     * 
     *
     * @return			0 if the cookie complies with the
     *				original Netscape specification; 1
     *				if the cookie complies with RFC 2109
     *
     * @see #setVersion
     *
     */

    @Override
    public int getVersion() {
	return wrappedCookie.getVersion();
    }




    /**
     * Sets the version of the cookie protocol this cookie complies
     * with. Version 0 complies with the original Netscape cookie
     * specification. Version 1 complies with RFC 2109.
     *
     * <p>Since RFC 2109 is still somewhat new, consider
     * version 1 as experimental; do not use it yet on production sites.
     *
     *
     * @param v			0 if the cookie should comply with 
     *				the original Netscape specification;
     *				1 if the cookie should comply with RFC 2109
     *
     * @see #getVersion
     *
     */

    @Override
    public void setVersion(int v) {
	wrappedCookie.setVersion(v);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isHttpOnly() {
        return wrappedCookie.isHttpOnly();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setHttpOnly(boolean isHttpOnly) {
        wrappedCookie.setHttpOnly(isHttpOnly);
    }

    @SuppressWarnings("UnusedDeclaration")
    public Object cloneCookie() {
        return wrappedCookie.clone();
    }

    public javax.servlet.http.Cookie getWrappedCookie() {
        return wrappedCookie;
    }

    
    public void setWrappedCookie(javax.servlet.http.Cookie wrappedCookie) {
        this.wrappedCookie = wrappedCookie;
    }
}
