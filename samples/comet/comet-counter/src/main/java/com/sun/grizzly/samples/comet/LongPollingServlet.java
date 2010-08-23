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

package com.sun.grizzly.samples.comet;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.concurrent.atomic.AtomicInteger;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.sun.grizzly.comet.CometContext;
import com.sun.grizzly.comet.CometEngine;
import com.sun.grizzly.comet.CometEvent;
import com.sun.grizzly.comet.CometHandler;

public class LongPollingServlet extends HttpServlet {
    
    
    private final AtomicInteger counter = new AtomicInteger();
    
    
    private class CounterHandler implements CometHandler<HttpServletResponse> {
        
        private HttpServletResponse response;
        
        public void onEvent(CometEvent event) throws IOException {
            if (CometEvent.NOTIFY == event.getType()) {
                int count = counter.get();
                response.addHeader("X-JSON", "{\"counter\":" + count + " }");
                
                PrintWriter writer = response.getWriter();
                writer.write("success");
                writer.flush();
                
                event.getCometContext().resumeCometHandler(this);
            }
        }
        
        
        public void onInitialize(CometEvent event) throws IOException {
        }
        
        
        public void onInterrupt(CometEvent event) throws IOException {
            int count = counter.get();
            response.addHeader("X-JSON", "{\"counter\":" + count + " }");
            
            PrintWriter writer = response.getWriter();
            writer.write("success");
            writer.flush();
        }
        
        public void onTerminate(CometEvent event) throws IOException {
        }
        
        public void attach(HttpServletResponse attachment) {
            this.response = attachment;
        }
        
    }
    
    private static final long serialVersionUID = 1L;
    
    private String contextPath = null;
    
    @Override
    public void init(ServletConfig config) throws ServletException {
        super.init(config);

        ServletContext context = config.getServletContext();
        contextPath = context.getContextPath() + "/long_polling";
        
        CometEngine engine = CometEngine.getEngine();
        CometContext cometContext = engine.register(contextPath);
        cometContext.setExpirationDelay(5 * 30 * 1000);
    }
    
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res)
    throws ServletException, IOException {
        
        CounterHandler handler = new CounterHandler();
        handler.attach(res);
        
        CometEngine engine = CometEngine.getEngine();
        CometContext context = engine.getCometContext(contextPath);
        
        context.addCometHandler(handler);
    }
    
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse res)
    throws ServletException, IOException {
        counter.incrementAndGet();
        
        CometEngine engine = CometEngine.getEngine();
        CometContext<?> context = engine.getCometContext(contextPath);
        context.notify(null);
        
        PrintWriter writer = res.getWriter();
        writer.write("success");
        writer.flush();
    }
    
}
