/*
 * 
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 * 
 * Copyright 2007-2008 Sun Microsystems, Inc. All rights reserved.
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
 */
package com.sun.grizzly.grizzlet;

import com.sun.grizzly.container.GrizzletRequest;
import com.sun.grizzly.container.GrizzletResponse;
import java.io.IOException;


/**
 * Simple Flickr Magnet Demo.
 *
 * @author Jeanfrancois Arcand
 * @author Greg Murray
 */
public class JMakiGrizzlet implements Grizzlet {

    /**
     * Simple counter
     */
    private int counter;

    public void onRequest(AsyncConnection ac) throws IOException {
        GrizzletRequest req = ac.getRequest();
        GrizzletResponse res = ac.getResponse();

        String[] actionValues = req.getParameterValues("action");
        if (actionValues != null && actionValues[0] != null) {
            String action = req.getParameterValues("action")[0];
            if ("post".equals(action)) {
                String message = req.getParameterValues("message")[0];
                String callback = req.getParameterValues("callback")[0];
                if (callback == null) {
                    callback = "alert";
                }

                // Notify other registered CometHandler.
                ac.push("<script id='comet_" + counter++ + "'>" + "window.parent." 
                        + callback + "(" + message + ");</script>");
                res.write("ok");
                res.flush();
                return;
            } else if ("start".equals(action)) {
                res.setContentType("text/html");
                String callback = req.getParameterValues("callback")[0];
                if (callback == null) {
                    callback = "alert";
                }

                String message = "{ message : 'Welcome'}";
                res.write("<script id='comet_" + counter++ + "'>" + "window.parent."
                        + callback + "(" + message + ");</script>");
                res.write("<html><head><title>jMaki Grizzly Comet Words Sample</title></head><body bgcolor=\"#FFFFFF\">");
                res.flush();

                if (ac.isGet()) {
                    ac.suspend();
                }
                return;
            }
        }
    }

    public void onPush(AsyncConnection ac) throws IOException {
        GrizzletRequest req = ac.getRequest();
        GrizzletResponse res = ac.getResponse();

        if (ac.isResuming()) {
            res.write("jMaki Grizzly Comet Words Sample closed<br/>");
            res.write("</body></html>");
            res.flush();
            res.finish();
        } else if (ac.hasPushEvent()) {
            res.write(ac.getPushEvent().toString());
            res.flush();
        }
    }

}