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

package org.glassfish.grizzly;

import java.util.concurrent.ExecutorService;
import org.glassfish.grizzly.attributes.AttributeHolder;
import org.glassfish.grizzly.attributes.IndexedAttributeHolder;
import org.glassfish.grizzly.util.ObjectPool;
import org.glassfish.grizzly.util.PoolableObject;

/**
 *
 * @author Alexey Stashok
 */
public class Context implements PoolableObject {
    private Connection connection;
    
    private IOEvent ioEvent;
    
    private Processor processor;

    private PostProcessor postProcessor;

    private AttributeHolder attributes;
    
    private ExecutorService processorExecutor;

    private ProcessorRunnable processorRunnable;
    
    private final ObjectPool<Context> parentPool;

    public Context() {
        parentPool = null;
    }

    public Context(ObjectPool parentPool) {
        this.parentPool = parentPool;
    }
    
    public IOEvent getIoEvent() {
        return ioEvent;
    }

    public void setIoEvent(IOEvent ioEvent) {
        this.ioEvent = ioEvent;
    }

    public Connection getConnection() {
        return connection;
    }

    public void setConnection(Connection connection) {
        this.connection = connection;
    }

    public ExecutorService getProcessorExecutor() {
        return processorExecutor;
    }

    public void setProcessorExecutor(
            ExecutorService processorExecutor) {
        this.processorExecutor = processorExecutor;
    }

    public ProcessorRunnable getProcessorRunnable() {
        return processorRunnable;
    }

    public void setProcessorRunnable(ProcessorRunnable processorRunnable) {
        this.processorRunnable = processorRunnable;
    }

    public Processor getProcessor() {
        return processor;
    }

    public void setProcessor(Processor processor) {
        this.processor = processor;
    }   

    public PostProcessor getPostProcessor() {
        return postProcessor;
    }

    public void setPostProcessor(
            PostProcessor ioEventPostProcessor) {
        this.postProcessor = ioEventPostProcessor;
    }

    public AttributeHolder getAttributes() {
        return attributes;
    }

    public AttributeHolder obtainAttributes() {
        if (attributes == null) {
            if (connection == null) {
                throw new IllegalStateException(
                        "Can not obtain an attributes. " +
                        "Connection is not set for this context object!");
            }

            attributes = initializeAttributeHolder();
        }


        return attributes;
    }

    protected AttributeHolder initializeAttributeHolder() {
        return new IndexedAttributeHolder(
                connection.getTransport().getAttributeBuilder());
    }

    protected void setAttributes(AttributeHolder attributes) {
        this.attributes = attributes;
    }

    public void prepare() {
        if (attributes == null) {
            attributes = 
                    new IndexedAttributeHolder(Grizzly.DEFAULT_ATTRIBUTE_BUILDER);
        }
    }

    public void release() {
        if (attributes != null) {
            attributes.clear();
        }
        
        processor = null;
        processorExecutor = null;
        postProcessor = null;
        connection = null;
        ioEvent = IOEvent.NONE;
    }

    /**
     * Return this {@link IOEventContext} to the {@link ObjectPool} it was
     * taken from
     */
    public void offerToPool() {
        if (parentPool != null) {
            parentPool.offer(this);
        }
    }
}
