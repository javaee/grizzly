/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2007-2010 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.grizzly.util.http;

import java.io.*;
import java.net.*;
import java.util.*;
import java.text.*;

/**
 * Util to process the "Accept-Language" header. Used by facade to implement
 * getLocale() and by StaticInterceptor.
 *
 * Not optimized - it's very slow.
 * 
 * @author James Duncan Davidson [duncan@eng.sun.com]
 * @author James Todd [gonzo@eng.sun.com]
 * @author Jason Hunter [jch@eng.sun.com]
 * @author Harish Prabandham
 * @author costin@eng.sun.com
 */
public class AcceptLanguage {

    public static Locale getLocale(String acceptLanguage) {
	if( acceptLanguage == null ) return Locale.getDefault();

        Hashtable<String, Vector<String>> languages =
            new Hashtable<String, Vector<String>>();
        Vector<Double> quality=new Vector<Double>();
        processAcceptLanguage(acceptLanguage, languages,quality);

        if (languages.size() == 0) return Locale.getDefault();

        Vector<Locale> l = new Vector<Locale>();
        extractLocales( languages,quality, l);

        return (Locale)l.elementAt(0);
    }

    public static Enumeration<Locale> getLocales(String acceptLanguage) {
    	// Short circuit with an empty enumeration if null header
        if (acceptLanguage == null) {
            Vector<Locale> v = new Vector<Locale>();
            v.addElement(Locale.getDefault());
            return v.elements();
        }
	
        Hashtable<String, Vector<String>> languages =
            new Hashtable<String, Vector<String>>();
        Vector<Double> quality=new Vector<Double>();
    	processAcceptLanguage(acceptLanguage, languages , quality);

        if (languages.size() == 0) {
            Vector<Locale> v = new Vector<Locale>();
            v.addElement(Locale.getDefault());
            return v.elements();
        }
    	Vector<Locale> l = new Vector<Locale>();
    	extractLocales( languages, quality , l);
    	return l.elements();
    }

    private static void processAcceptLanguage( String acceptLanguage,
            Hashtable<String, Vector<String>> languages, Vector<Double> q)
    {
        StringTokenizer languageTokenizer =
            new StringTokenizer(acceptLanguage, ",");

        while (languageTokenizer.hasMoreTokens()) {
            String language = languageTokenizer.nextToken().trim();
            int qValueIndex = language.indexOf(';');
            int qIndex = language.indexOf('q');
            int equalIndex = language.indexOf('=');
            Double qValue = new Double(1);

            if (qValueIndex > -1 &&
                    qValueIndex < qIndex &&
                    qIndex < equalIndex) {
    	        String qValueStr = language.substring(qValueIndex + 1);
                language = language.substring(0, qValueIndex);
                qValueStr = qValueStr.trim().toLowerCase(Locale.ENGLISH);
                qValueIndex = qValueStr.indexOf('=');
                qValue = new Double(0);
                if (qValueStr.startsWith("q") &&
                    qValueIndex > -1) {
                    qValueStr = qValueStr.substring(qValueIndex + 1);
                    try {
                        qValue = new Double(qValueStr.trim());
                    } catch (NumberFormatException nfe) {
                    }
                }
            }

            // XXX
            // may need to handle "*" at some point in time

            if (! language.equals("*")) {
                String key = qValue.toString();
                Vector<String> v;
                if (languages.containsKey(key)) {
                    v = languages.get(key) ;
                } else {
                    v= new Vector<String>();
                    q.addElement(qValue);
                }
                v.addElement(language);
                languages.put(key, v);
            }
        }
    }

    private static void extractLocales(
            Hashtable<String, Vector<String>> languages, Vector<Double> q,Vector<Locale> l)
    {
        // XXX We will need to order by q value Vector in the Future ?
        Enumeration<Double> e = q.elements();
        while (e.hasMoreElements()) {
            Vector<String> v= languages.get((e.nextElement()).toString());
            Enumeration<String> le = v.elements();
            while (le.hasMoreElements()) {
    	        String language = le.nextElement();
	        	String country = "";
        		int countryIndex = language.indexOf("-");
                if (countryIndex > -1) {
                    country = language.substring(countryIndex + 1).trim();
                    language = language.substring(0, countryIndex).trim();
                }
                l.addElement(new Locale(language, country));
            }
        }
    }


}
