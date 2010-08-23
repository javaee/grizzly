/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2008-2010 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.grizzly.cometd.bayeux;

/**
 * Bayeux Deliver Response implementation.
 * This is used in server side.
 *
 * @author Shing Wai Chan
 */
public class DeliverResponse extends VerbBase {

    private boolean isFinished = false;

    public DeliverResponse() {
        type = Verb.Type.DELIVER;
    }

    public DeliverResponse(PublishRequest req) {
        this();
        channel = req.getChannel();
        data = req.getData();
        clientId = req.getClientId();
        id = req.getId();
        ext = req.getExt();
        advice = req.getAdvice();
        first = req.isFirst();
        follow = req.isFollow();
        last = req.isLast();
        isFinished = false;
    }


    /**
     * Set to <tt>true</tt> when the underlying connection needs to be resumed.
     * @param isFinished
     */
    public void setFinished(boolean isFinished){
        this.isFinished = isFinished;
    }

    
    /**
     * Return <tt>true</tt> if the underlying connection needs to be resumed.
     * @return
     */
    public boolean isFinished(){
        return isFinished;
    }

    @Override
    public boolean isValid() {
        return (channel != null && data != null);
    }

    public String toJSON() {
        StringBuilder sb = new StringBuilder(
                getJSONPrefix() + "{"
                + "\"channel\":\"" + channel + "\""
                );
        if (data != null) {
            sb.append(",").append(data.toJSON());
        }
        if (id != null) {
            sb.append(",\"id\":\"").append(id).append("\"");
        }
        if (clientId != null) {
            sb.append(",\"clientId\":\"").append(clientId).append("\"");
        }

        if (ext != null) {
            sb.append(",").append(ext.toJSON());
        }
        if (advice != null) {
            sb.append(",").append(advice.toJSON());
        }
        sb.append("}").append(getJSONPostfix());
        return sb.toString();
    }    
}
