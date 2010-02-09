/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 1997-2008 Sun Microsystems, Inc. All rights reserved.
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
 *
 * Copyright 2004 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.sun.grizzly.http;

import java.util.Locale;



/**
 * Response object.
 * 
 * @author James Duncan Davidson [duncan@eng.sun.com]
 * @author Jason Hunter [jch@eng.sun.com]
 * @author James Todd [gonzo@eng.sun.com]
 * @author Harish Prabandham
 * @author Hans Bergsten <hans@gefionsoftware.com>
 * @author Remy Maucherat
 */
public class HttpResponseImpl extends HttpPacketImpl implements HttpResponse {


    // ----------------------------------------------------------- Constructors


    public HttpResponseImpl() {
        characterEncoding = Constants.DEFAULT_CHARACTER_ENCODING;
    }


    // ----------------------------------------------------- Class Variables

    /**
     * Default locale as mandated by the spec.
     */
    private static Locale DEFAULT_LOCALE = Locale.getDefault();
    
    // ----------------------------------------------------- Instance Variables

    /**
     * Status code.
     */
    protected int status = 200;


    /**
     * Status message.
     */
    protected String message = null;


    /**
     * HTTP specific fields.
     */
    protected String contentLanguage = null;
    private String quotedCharsetValue = characterEncoding;
    private Locale locale = DEFAULT_LOCALE;

    /**
     * Has the charset been explicitly set.
     */
    protected boolean charsetSet = false;

    protected HttpRequest request;

    // ------------------------------------------------------------- Properties

    @Override
    public HttpRequest getRequest() {
        return request;
    }

    @Override
    public void setRequest(HttpRequest req) {
        this.request=req;
    }

    public MimeHeaders getMimeHeaders() {
        return headers;
    }


    // -------------------- State --------------------


    @Override
    public int getStatus() {
        return status;
    }
    
    /** 
     * Set the response status 
     */ 
    @Override
    public void setStatus( int status ) {
        this.status = status;
    }


    /**
     * Get the status message.
     */
    @Override
    public String getMessage() {
        return message;
    }


    /**
     * Set the status message.
     */
    @Override
    public void setMessage(String message) {
        this.message = message;
    }

    // -------------------- Methods --------------------
    
    
    public void reset() {
        
        // Reset the headers only if this is the main request,
        // not for included
        contentType = null;
        locale = DEFAULT_LOCALE;
        contentLanguage = null;
        characterEncoding = Constants.DEFAULT_CHARACTER_ENCODING;
        // START SJSAS 6316254
        quotedCharsetValue = characterEncoding;
        // END SJSAS 6316254
        contentLength = -1;
        charsetSet = false;

        status = 200;
        message = null;
        headers.clear();
    }

    // -------------------- Headers --------------------
    public boolean containsHeader(String name) {
        return headers.getHeader(name) != null;
    }


    public void setHeader(String name, String value) {
        char cc=name.charAt(0);
        if( cc=='C' || cc=='c' ) {
            if( checkSpecialHeader(name, value) )
            return;
        }
        headers.setValue(name).setString( value);
    }


    public void addHeader(String name, String value) {
        char cc=name.charAt(0);
        if( cc=='C' || cc=='c' ) {
            if( checkSpecialHeader(name, value) )
            return;
        }
        headers.addValue(name).setString( value );
    }

    
    /** 
     * Set internal fields for special header names. 
     * Called from set/addHeader.
     * Return true if the header is special, no need to set the header.
     */
    private boolean checkSpecialHeader( String name, String value) {
        // XXX Eliminate redundant fields !!!
        // ( both header and in special fields )
        if( name.equalsIgnoreCase( "Content-Type" ) ) {
            setContentType( value );
            return true;
        }
        if( name.equalsIgnoreCase( "Content-Length" ) ) {
            try {
                int cL=Integer.parseInt( value );
                setContentLength( cL );
                return true;
            } catch( NumberFormatException ex ) {
                // Do nothing - the spec doesn't have any "throws" 
                // and the user might know what he's doing
                return false;
            }
        }
        if( name.equalsIgnoreCase( "Content-Language" ) ) {
            // XXX XXX Need to construct Locale or something else
        }
        return false;
    }

    // -------------------- I18N --------------------


    public Locale getLocale() {
        return locale;
    }

    /**
     * Called explicitely by user to set the Content-Language and
     * the default encoding
     */
    public void setLocale(Locale locale) {

        if (locale == null) {
            return;  // throw an exception?
        }

        // Save the locale for use by getLocale()
        this.locale = locale;

        // Set the contentLanguage for header output
        contentLanguage = locale.getLanguage();
        if ((contentLanguage != null) && (contentLanguage.length() > 0)) {
            String country = locale.getCountry();
            StringBuilder value = new StringBuilder(contentLanguage);
            if ((country != null) && (country.length() > 0)) {
                value.append('-');
                value.append(country);
            }
            contentLanguage = value.toString();
        }

    }

    /**
     * Return the content language.
     */
    @Override
    public String getContentLanguage() {
        return contentLanguage;
    }

    /*
     * Overrides the name of the character encoding used in the body
     * of the response. This method must be called prior to writing output
     * using getWriter().
     *
     * @param charset String containing the name of the chararacter encoding.
     */
    @Override
    public void setCharacterEncoding(String charset) {

        if (charset == null)
            return;

        characterEncoding = charset;
        // START SJSAS 6316254
        quotedCharsetValue = charset;
        // END SJSAS 6316254
        charsetSet=true;
    }

    /**
     * Sets the content type.
     *
     * This method must preserve any response charset that may already have 
     * been set via a call to response.setContentType(), response.setLocale(),
     * or response.setCharacterEncoding().
     *
     * @param type the content type
     */
    @Override
    public void setContentType(String contentType) {

        int semicolonIndex = -1;

        if (contentType == null) {
            this.contentType = null;
            return;
        }

        /*
         * Remove the charset param (if any) from the Content-Type, and use it
         * to set the response encoding.
         * The most recent response encoding setting will be appended to the
         * response's Content-Type (as its charset param) by getContentType();
         */
        boolean hasCharset = false;
        int len = contentType.length();
        int index = contentType.indexOf(';');
        while (index != -1) {
            semicolonIndex = index;
            index++;
            while (index < len && Character.isSpace(contentType.charAt(index))) {
                index++;
            }
            if (index+8 < len
                    && contentType.charAt(index) == 'c'
                    && contentType.charAt(index+1) == 'h'
                    && contentType.charAt(index+2) == 'a'
                    && contentType.charAt(index+3) == 'r'
                    && contentType.charAt(index+4) == 's'
                    && contentType.charAt(index+5) == 'e'
                    && contentType.charAt(index+6) == 't'
                    && contentType.charAt(index+7) == '=') {
                hasCharset = true;
                break;
            }
            index = contentType.indexOf(';', index);
        }

        if (!hasCharset) {
            this.contentType = contentType;
            return;
        }

        this.contentType = contentType.substring(0, semicolonIndex);
        String tail = contentType.substring(index+8);
        int nextParam = tail.indexOf(';');
        String charsetValue = null;
        if (nextParam != -1) {
            this.contentType += tail.substring(nextParam);
            charsetValue = tail.substring(0, nextParam);
        } else {
            charsetValue = tail;
        }

        // The charset value may be quoted, but must not contain any quotes.
        if (charsetValue != null && charsetValue.length() > 0) {
            charsetSet=true;
            // START SJSAS 6316254
            this.quotedCharsetValue = charsetValue;
            // END SJSAS 6316254
            this.characterEncoding = charsetValue.replace('"', ' ').trim();
        }
    }

    @Override
    public String getContentType() {

        String ret = contentType;

        if (ret != null 
                /* SJSAS 6316254
                && characterEncoding != null
                */
                // START SJSAS 6316254
                && quotedCharsetValue != null
                // END SJSAS 6316254
                && charsetSet) {
            /* SJSAS 6316254
            ret = ret + ";charset=" + characterEncoding;   
            */
            // START SJSAS 6316254
            ret = ret + ";charset=" + quotedCharsetValue;
            // END SJSAS 6316254
        }

        return ret;
    }

    // --------------------
    
    @Override
    public void recycle() {
        super.recycle();
        
        contentLanguage = null;
        locale = DEFAULT_LOCALE;
        // START SJSAS 6316254
        quotedCharsetValue = characterEncoding;
        // END SJSAS 6316254
        charsetSet = false;
        status = 200;
        message = null;
    }
}
