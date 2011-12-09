/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.glassfish.grizzly.asyncqueue;

import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.Connection;

/**
 *
 * @author oleksiys
 */
public interface PushBackHandler {

    public void onAccept(Connection connection, Buffer buffer);

    public void onPushBack(Connection connection, Buffer buffer,
            PushBackContext pushBackContext);
    
}
