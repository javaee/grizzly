

/*
 * The contents of this file are subject to the terms
 * of the Common Development and Distribution License
 * (the "License").  You may not use this file except
 * in compliance with the License.
 *
 * You can obtain a copy of the license at
 * glassfish/bootstrap/legal/CDDLv1.0.txt or
 * https://glassfish.dev.java.net/public/CDDLv1.0.html.
 * See the License for the specific language governing
 * permissions and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL
 * HEADER in each file and include the License file at
 * glassfish/bootstrap/legal/CDDLv1.0.txt.  If applicable,
 * add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your
 * own identifying information: Portions Copyright [yyyy]
 * [name of copyright owner]
 *
 * Copyright 2005 Sun Microsystems, Inc. All rights reserved.
 *
 * Portions Copyright Apache Software Foundation.
 */ 

package com.sun.grizzly.util.http;

import com.sun.grizzly.util.res.StringManager;

import java.io.*;
import java.net.*;
import java.util.*;
import java.text.*;

/**
 * Handle (internationalized) HTTP messages.
 * 
 * @author James Duncan Davidson [duncan@eng.sun.com]
 * @author James Todd [gonzo@eng.sun.com]
 * @author Jason Hunter [jch@eng.sun.com]
 * @author Harish Prabandham
 * @author costin@eng.sun.com
 */
public class HttpMessages {
    // XXX move message resources in this package
    protected static final StringManager sm =
        StringManager.getManager("com.sun.grizzly.util.http.res");
	
    static String st_200=null;
    static String st_302=null;
    static String st_400=null;
    static String st_404=null;
    
    /** Get the status string associated with a status code.
     *  No I18N - return the messages defined in the HTTP spec.
     *  ( the user isn't supposed to see them, this is the last
     *  thing to translate)
     *
     *  Common messages are cached.
     *
     */
    public static String getMessage( int status ) {
	// method from Response.
	
	// Does HTTP requires/allow international messages or
	// are pre-defined? The user doesn't see them most of the time
	switch( status ) {
	case 200:
	    if( st_200==null ) st_200=sm.getString( "sc.200");
	    return st_200;
	case 302:
	    if( st_302==null ) st_302=sm.getString( "sc.302");
	    return st_302;
	case 400:
	    if( st_400==null ) st_400=sm.getString( "sc.400");
	    return st_400;
	case 404:
	    if( st_404==null ) st_404=sm.getString( "sc.404");
	    return st_404;
	}
	return sm.getString("sc."+ status);
    }

    /**
     * Filter the specified message string for characters that are sensitive
     * in HTML.  This avoids potential attacks caused by including JavaScript
     * codes in the request URL that is often reported in error messages.
     *
     * @param message The message string to be filtered
     */
    public static String filter(String message) {

	if (message == null)
	    return (null);

	char content[] = new char[message.length()];
	message.getChars(0, message.length(), content, 0);
	StringBuffer result = new StringBuffer(content.length + 50);
	for (int i = 0; i < content.length; i++) {
	    switch (content[i]) {
	    case '<':
		result.append("&lt;");
		break;
	    case '>':
		result.append("&gt;");
		break;
	    case '&':
		result.append("&amp;");
		break;
	    case '"':
		result.append("&quot;");
		break;
	    default:
		result.append(content[i]);
	    }
	}
	return (result.toString());
    }

}
