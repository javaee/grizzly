/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.sun.grizzly;

import java.io.IOException;

/**
 *
 * @author oleksiys
 */
public interface SelectorHandlerTask {
    public void run(Context context) throws IOException;
}
