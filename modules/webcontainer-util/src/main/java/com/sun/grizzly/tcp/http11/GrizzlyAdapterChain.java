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
 * [mapping of copyright owner]"
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

package com.sun.grizzly.tcp.http11;

import com.sun.grizzly.tcp.Request;
import com.sun.grizzly.util.buf.ByteChunk;
import com.sun.grizzly.util.buf.MessageBytes;
import com.sun.grizzly.util.buf.UDecoder;
import com.sun.grizzly.util.http.HttpRequestURIDecoder;
import com.sun.grizzly.util.http.mapper.Mapper;
import com.sun.grizzly.util.http.mapper.MappingData;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * The GrizzlyAdapterChain class allow the invokation of multiple {@link GrizzlyAdapter}
 * every time a new HTTP requests is ready to be handled. Requests are mapped
 * to their associated {@link GrizzlyAdapter} at runtime using the mapping
 * information configured when invoking the {@link GrizzlyAdapterChain#addGrizzlyAdapter
 * (com.sun.grizzly.tcp.http11.GrizzlyAdapter, java.lang.String[])
 *
 * Below is a simple example using two {@link Servlet}
 * <pre><code>
 *
        GrizzlyWebServer ws = new GrizzlyWebServer(path);
        ServletAdapter sa = new ServletAdapter();
        sa.setRootFolder(".");
        sa.setServletInstance(new ServletTest("Adapter-1"));
        ws.addGrizzlyAdapter(sa, new String[]{"/Adapter-1"});

        ServletAdapter sa2 = new ServletAdapter();
        sa2.setRootFolder("/tmp");
        sa2.setServletInstance(new ServletTest("Adapter-2"));
        ws.addGrizzlyAdapter(sa2, new String[]{"/Adapter-2"});

        System.out.println("Grizzly WebServer listening on port 8080");
        ws.start();
 * 
 * </code></pre>
 * 
 * Note: This class is <strong>NOT</strong> thread-safe, so make sure you synchronize
 *       when dynamically adding and removing {@link GrizzlyAdapter}
 *  
 * @author Jeanfrancois Arcand
 */
public class GrizzlyAdapterChain extends GrizzlyAdapter{
    
    private UDecoder urlDecoder = new UDecoder();
    protected final static int MAPPING_DATA = 12;
    protected final static int MAPPED_ADAPTER = 13;
    
    /**
     * The list of {@link GrizzlyAdapter} instance.
     */
    private ConcurrentHashMap<GrizzlyAdapter, String[]> adapters = 
            new ConcurrentHashMap<GrizzlyAdapter, String[]>();

    /**
     * Internal {@link Mapper} used to Map request to their associated {@link GrizzlyAdapter}
     */
    private Mapper mapper = new Mapper();


    /**
     * The default host.
     */
    private final static String LOCAL_HOST="localhost";

    /**
     * Use the deprecated mechanism.
     */
    private boolean oldMappingAlgorithm = false;

    
    public GrizzlyAdapterChain(){
        mapper.setDefaultHostName(LOCAL_HOST);
        setHandleStaticResources(false);
        // We will decode it
        setDecodeUrl(false);
    }
    
    
    @Override
    public void start(){
         for (Entry<GrizzlyAdapter,String[]> entry: adapters.entrySet()){
            entry.getKey().start();
        }        
    }

    /**
     * Map the {@link GrizzlyRequest} to the proper {@link GrizzlyAdapter}
     * @param request The {@link GrizzlyRequest}
     * @param response The {@link GrizzlyResponse}
     */
    @Override
    public void service(GrizzlyRequest request, GrizzlyResponse response) {
        // For backward compatibility.
        if (oldMappingAlgorithm){
            int i = 0;
            int size = adapters.size();
            for (Entry<GrizzlyAdapter,String[]> entry: adapters.entrySet()){
                entry.getKey().service(request, response);
                if (response.getStatus() == 404 && i != size -1){
                    // Reset the
                    response.setStatus(200, "OK");
                } else {
                    return;
                }
            } 
        } else {
            Request req = request.getRequest();
            MappingData mappingData = null;
            try{
                MessageBytes decodedURI = req.decodedURI();
                decodedURI.duplicate(req.requestURI());
                mappingData = (MappingData)req.getNote(MAPPING_DATA);
                if (mappingData == null){
                    mappingData = new MappingData();
                    req.setNote(MAPPING_DATA, mappingData);
                } else {
                    mappingData.recycle();
                }

                // Map the request without any trailling.
                ByteChunk uriBB = decodedURI.getByteChunk();
                int semicolon = uriBB.indexOf(';',0);
                if (semicolon > 0){
                    decodedURI.setBytes(uriBB.getBuffer(), uriBB.getStart(), semicolon);
                }

                HttpRequestURIDecoder.decode(decodedURI,urlDecoder,null,null);
                if (mappingData==null) {
                    mappingData = (MappingData)req.getNote(MAPPING_DATA);
                }

                // Map the request to its Adapter
                mapper.map(req.serverName(), decodedURI, mappingData);
               
                GrizzlyAdapter adapter = null;
                if (mappingData.context != null && mappingData.context instanceof GrizzlyAdapter) {
                    if (mappingData.wrapper != null){
                        adapter = (GrizzlyAdapter)mappingData.wrapper;
                    } else {
                        adapter = (GrizzlyAdapter)mappingData.context;
                    }
                    // We already decoded the URL.
                    adapter.setDecodeUrl(false);
                    adapter.service(request.getRequest(), response.getResponse());
                } else {
                    response.getResponse().setStatus(404);
                    customizedErrorPage(req,response.getResponse());
                }
            } catch (Throwable t){
               try{
                    response.setStatus(404);
                    if (logger.isLoggable(Level.FINE)) {
                        logger.log(Level.FINE, "Invalid URL: " + req.decodedURI(), t);
                    }
                    customizedErrorPage(req,response.getResponse());
                } catch (Exception ex2){
                    if (logger.isLoggable(Level.WARNING)) {
                        logger.log(Level.WARNING, "Unable to error page", ex2);
                    }
                }
            }
        }
    }

    
    /**
     * Add a {@link GrizzlyAdapter} to the chain.
     * @param {@link GrizzlyAdapter} to the chain.
     * @deprecated - uses {@link GrizzlyAdapterChain#addGrizzlyAdapter(com.sun.grizzly.tcp.http11.GrizzlyAdapter, java.lang.String[])}
     */
    public void addGrizzlyAdapter(GrizzlyAdapter adapter){
        oldMappingAlgorithm = true;
        adapters.put(adapter,new String[]{""});
    }

    /**
     * Add a {@link GrizzlyAdapter} and its assciated array of mapping. The mapping
     * data will be used to map incoming request to its associated {@link GrizzlyAdapter}.
     * @param adapter {@link GrizzlyAdapter} instance
     * @param mappings an array of mapping.
     */
    public void addGrizzlyAdapter(GrizzlyAdapter adapter, String[] mappings){
        if (oldMappingAlgorithm){
            throw new IllegalStateException("Cannot mix addGrizzlyAdapter(GrizzlyAdapter) " +
                    "and addGrizzlyAdapter(GrizzlyAdapter,String[]");
        }

        if (mappings.length == 0){
            addGrizzlyAdapter(adapter);
        } else {
            adapters.put(adapter,mappings);
            for(String mapping: mappings){   
                String ctx = getContextPath(mapping);
                mapper.addContext(LOCAL_HOST, ctx, adapter,
                        new String[] {"index.html", "index.htm"}, null);
                mapper.addWrapper(LOCAL_HOST, ctx,mapping.substring(ctx.length()), adapter);
            }
        }
    }

    private String getContextPath(String mapping){
       String ctx = "";
        int slash = mapping.indexOf("/",1);
        if (slash != -1) {
            ctx = mapping.substring(0,slash);
        } else {
            ctx = mapping;
        }
        
        if (ctx.startsWith("/*")){
            ctx= "";
        }
        
        // Special case for the root context
        if (ctx.equals("/")) {
            ctx = "";
        }
        return ctx;
    }
    
    @Override
    public void destroy(){
        for (Entry<GrizzlyAdapter,String[]> adapter: adapters.entrySet()){
            adapter.getKey().destroy();
        }
    }
    
    /**
     * Remove a {@link GrizzlyAdapter}
     * @return <tt>true</tt> if removed
     */
    public boolean removeAdapter(GrizzlyAdapter adapter){
        if (adapter == null) throw new IllegalStateException();
        String[] mappings = adapters.remove(adapter);
        if (mappings != null){
            for (String mapping : mappings){
                String ctx = getContextPath(mapping);
                mapper.removeContext(LOCAL_HOST,ctx); 
            }
            adapter.destroy();
        }

        return (mappings != null);
    }
       
}
