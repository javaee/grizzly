/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.glassfish.grizzly.asyncqueue;

/**
 *
 * @author oleksiys
 */
public abstract class PushBackContext {
    protected final AsyncWriteQueueRecord queueRecord;
    
    public PushBackContext(final AsyncWriteQueueRecord queueRecord) {
        this.queueRecord = queueRecord;
    }
    
    public PushBackHandler getPushBackHandler() {
        return queueRecord.getPushBackHandler();
    }
    
    public final int size() {
        return queueRecord.remaining();
    }
    
    public abstract void retryWhenPossible();
    
    public abstract void retryNow();
    
    public abstract void cancel();
    
    
}
