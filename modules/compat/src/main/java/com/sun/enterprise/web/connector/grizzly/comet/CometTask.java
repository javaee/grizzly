package com.sun.enterprise.web.connector.grizzly.comet;

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



import com.sun.grizzly.http.SelectorThread;
import com.sun.grizzly.http.Task;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.logging.Logger;

/**
 * A {@link Task} implementation that allow Grizzly ARP to invokeCometHandler
 * {@link CometHandler} when new data (bytes) are available from the
 * {@link CometSelector}.
 *
 * @author Jeanfrancois Arcand
 * @author Gustav Trede
 */
@Deprecated
public class CometTask extends com.sun.grizzly.comet.CometTask{

    private static final Logger logger = SelectorThread.logger();

    /**
     * The {@link CometContext} associated with this instance.
     */
    protected final CometContext cometContext;

    /**
     * The {@link CometHandler} associated with this task.
     */
    protected final CometHandler cometHandler;

    /**
     *  true if run() should call cometcontext.interrupt0
     */
    protected boolean callInterrupt;

    /**
     *  true if interrupt should flushAPT
     */
    protected boolean interruptFlushAPT;

    /**
     * New {@link CometTask}.
     */
    public CometTask(CometContext cometContext, CometHandler cometHandler) {
        this.cometContext = cometContext;
        this.cometHandler = cometHandler;
    }

    /**
     * performs doTask() or cometContext.interrupt0
     */
    public void run(){        
        if (callInterrupt){
            cometContext.interrupt0(this, true, interruptFlushAPT, true);
        }else{
            try{
                doTask();
            } catch (IOException ex){
                throw new RuntimeException(ex);
            }                
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getIdleTimeoutDelay() {
        return cometContext.getExpirationDelay();
    }

    /**
     * this should never be called for for comet, due to we are nulling the attachment
     * and completely overriding the selector.select logic.<br>
     * called by grizzly when the selectionkey is canceled and its socket closed.<br>     
     *
     * @param selectionKey
     */
    @Override
    public void release(SelectionKey selectionKey) {
        //logger.warning("cometTask.release() :  isactive: "+cometContext.isActive(cometHandler)+"  attachment:"+selectionKey.attachment());
        //cometContext.interrupt(this, true, false,false, true);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean timedOut(SelectionKey key){
        //System.err.println("cometTask.timedout() :  isactive: "+cometContext.isActive(cometHandler)+"  attachment:"+key.attachment());
        cometContext.interrupt(this, true, true, true, true);
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void handleSelectedKey(SelectionKey selectionKey) {
        if (!selectionKey.isValid()){
            cometContext.interrupt(this, true, false,true, true);
            return;
        }
        if (cometHandlerIsAsyncRegistered){
            if (selectionKey.isReadable()){
                selectionKey.interestOps(selectionKey.interestOps() & (~SelectionKey.OP_READ));
                upcoming_op_isread = true;
            }
            if (selectionKey.isWritable()){
                selectionKey.interestOps(selectionKey.interestOps() & (~SelectionKey.OP_WRITE));
                upcoming_op_isread = false;
            }
            asyncProcessorTask.getThreadPool().execute(this);
        }            
        else{
           checkIfClientClosedConnection(selectionKey);
        }
    }

    /**
     * checks if client has closed the connection.
     * the check is done by trying to read 1 byte that is trown away.
     * only used for non async registered comethandler.
     * @param mainKey
     */
    private void checkIfClientClosedConnection(SelectionKey mainKey) {
        boolean connectionclosed = true;
        try {
            connectionclosed = ((SocketChannel)mainKey.channel()).
                read(ByteBuffer.allocate(1)) == -1;
        } catch (IOException ex) {
            
        }
        finally{
           if (connectionclosed){
               cometContext.interrupt(this, true, false,true, true);
           }else{
               //cometContext.interrupt(this, false, false, true,false, true);
               //System.err.println("**** ready key detected : "+mainKey.attachment() +" isactive:"+cometContext.isActive(cometHandler));
           }
        }
    }


   

 

    /**
     * Return the {@link CometContext} associated with this instance.
     * @return CometContext the {@link CometContext} associated with this
     *         instance.
     */
    public CometContext getCometContext() {
        return cometContext;
    }

    /**
     *  returns the {@link CometHandler }
     * @return {@link CometHandler }
     */
    public CometHandler getCometHandler() {
        return cometHandler;
    }

}
