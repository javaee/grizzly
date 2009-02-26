/*
 * The contents of this file are subject to the terms
 * of the Common Development and Distribution License
 * (the License).  You may not use this file except in
 * compliance with the License.
 *
 * You can obtain a copy of the license at
 * https://glassfish.dev.java.net/public/CDDLv1.0.html or
 * glassfish/bootstrap/legal/CDDLv1.0.txt.
 * See the License for the specific language governing
 * permissions and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL
 * Header Notice in each file and include the License file
 * at glassfish/bootstrap/legal/CDDLv1.0.txt.
 * If applicable, add the following below the CDDL Header,
 * with the fields enclosed by brackets [] replaced by
 * you own identifying information:
 * "Portions Copyrighted [year] [name of copyright owner]"
 *
 * Copyright 2006 Sun Microsystems, Inc. All rights reserved.
 */
package com.sun.grizzly.jruby;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;

import org.jruby.Ruby;
import org.jruby.RubyException;
import org.jruby.RubyIO;
import org.jruby.ast.Node;
import org.jruby.exceptions.RaiseException;
import org.jruby.javasupport.JavaEmbedUtils;
import org.jruby.runtime.DynamicScope;
import org.jruby.runtime.builtin.IRubyObject;

import com.sun.grizzly.http.SelectorThread;
import com.sun.grizzly.standalone.DynamicContentAdapter;
import com.sun.grizzly.tcp.Adapter;
import com.sun.grizzly.tcp.Request;
import com.sun.grizzly.tcp.Response;
import com.sun.grizzly.tcp.http11.InternalOutputBuffer;

/**
 * Adapter implementation that bridge JRuby on Rails with Grizzly.
 *
 * @author TAKAI Naoto
 * @author Jean-Francois Arcand
 */
public class RailsAdapter extends DynamicContentAdapter
        implements Adapter{
    
    private static final int RAILS_TOKEN = 17;

    private RubyObjectPool pool = null;
    
    private RubyRuntimeAsyncFilter asyncFilter;
    
    public RailsAdapter( RubyObjectPool pool) {
        super(pool.getRailsRoot() + "/public");
        this.pool = pool;
        
    }

    public RailsAdapter(RubyObjectPool pool, RubyRuntimeAsyncFilter asyncFilter) {
        this(pool);
        this.asyncFilter = asyncFilter;
    }
    
    
    protected int getTokenID() {
        return RAILS_TOKEN;
    }    
        
    
    protected void serviceDynamicContent(Request req, Response res) throws IOException {
        Ruby runtime = null;
        DynamicContentAdapter.RequestTupple rt = (DynamicContentAdapter.RequestTupple)req.getNote(RAILS_TOKEN);
        if (rt == null){
            rt = new DynamicContentAdapter.RequestTupple();
        }
        rt.req = req;
        try {
            runtime = pool.borrowRuntime();
            if (runtime == null){
                throw new IllegalStateException();
            }
            
            req.doRead(rt.readChunk);
            ((InternalOutputBuffer)res.getOutputBuffer()).commit();
            res.setCommitted(true);

            IRubyObject reqObj = JavaEmbedUtils.javaToRuby(runtime, req);
            IRubyObject loggerObj = JavaEmbedUtils.javaToRuby(runtime, SelectorThread.logger());
            
            OutputStream os =
                    ((InternalOutputBuffer)res.getOutputBuffer()).getOutputStream();
            
            RubyIO iObj = new RubyIO(runtime, rt.inputStream);
            RubyIO oObj = new RubyIO(runtime, os);
            
            runtime.defineReadonlyVariable("$req", reqObj);
            runtime.defineReadonlyVariable("$stdin", iObj);
            runtime.defineReadonlyVariable("$stdout", oObj);
            runtime.defineReadonlyVariable("$logger", loggerObj);
            
            if (contextRoot!=null) {
                runtime.defineReadonlyVariable("$root", 
                        JavaEmbedUtils.javaToRuby(runtime, contextRoot));
            }
            (JavaEmbedUtils.newRuntimeAdapter()).eval(runtime, getDispatcherString());
        } catch (RaiseException e) {
            RubyException exception = e.getException();
            
            System.err.println(e.getMessage());
            exception.printBacktrace(System.err);
            
            throw e;
        } finally {
            rt.recycle();
            req.setNote(RAILS_TOKEN,rt);
            if (runtime != null) {
                pool.returnRuntime(runtime);
            }
        }
    }
    
    private String getDispatcherString() {        
        String str;
        StringBuffer completeText = new StringBuffer();
        try {
            InputStream is = getClass().getResourceAsStream("/dispatch.rb");
            BufferedReader br = new BufferedReader(new InputStreamReader(is));
            str = br.readLine();
            while (str != null) {
                completeText.append(str + "\n");
                str = br.readLine();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return completeText.toString();
    }

}
