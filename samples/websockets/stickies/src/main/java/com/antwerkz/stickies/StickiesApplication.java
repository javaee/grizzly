/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2010 Oracle and/or its affiliates. All rights reserved.
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

package com.antwerkz.stickies;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.sun.grizzly.websockets.DataFrame;
import com.sun.grizzly.websockets.WebSocket;
import com.sun.grizzly.websockets.WebSocketApplication;
import com.sun.grizzly.websockets.WebSocketEngine;

public class StickiesApplication extends WebSocketApplication {
    static final Logger logger = Logger.getLogger(WebSocketEngine.WEBSOCKET);
    public static final AtomicInteger ids = new AtomicInteger(0);
    private Map<String, Note> notes = new HashMap<String, Note>();

    public void onMessage(WebSocket socket, DataFrame frame) throws IOException {
        final String data = frame.getTextPayload();
        final String[] bits = data.split("-");
        Operations.valueOf(bits[0].toUpperCase()).accept(this, socket, bits);
    }

    @Override
    public void onConnect(WebSocket socket) {
        super.onConnect(socket);
        for (Note note : notes.values()) {
            try {
                socket.send("create-" + note.toString());
            } catch (IOException e) {
                logger.fine(e.getMessage());
            }
        }
    }

    private void broadcast(WebSocket original, String text) throws IOException {
        for (WebSocket webSocket : getWebSockets()) {
            if (!webSocket.equals(original)) {
                send(webSocket, text);
            }
        }

    }

    private void send(WebSocket socket, String text) throws IOException {
        try {
            socket.send(text);
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Removing client: " + e.getMessage(), e);
            onClose(socket);
        }
    }

    private void createNote(WebSocket socket, String[] params) throws IOException {
        Note note = new Note();
        notes.put(note.getId(), note);
        broadcast(null, "create-" + note.toString());
    }

    private void saveNote(WebSocket socket, String[] params) throws IOException {
        String[] pieces = params[1].split(",");
        Map<String, String> map = new HashMap<String, String>();
        for (String s : pieces) {
            String[] data = s.split(":");
            map.put(data[0], data.length == 2 ? data[1] : "");
        }
        Note note = notes.get(map.get("id"));
        note.setText(map.get("text"));
        note.setTimestamp(String.valueOf(System.currentTimeMillis()));
        note.setLeft(map.get("left"));
        note.setTop(map.get("top"));
        note.setzIndex(map.get("zIndex"));
        broadcast(socket, "save-" + note.toString());
    }

    private void deleteNote(WebSocket socket, String[] params) throws IOException {
        notes.remove(params[1]);
        broadcast(socket, "delete-" + params[1]);
    }

    enum Operations {
        CREATE {
            @Override
            void accept(StickiesApplication app, WebSocket socket, String[] params) throws IOException {
                app.createNote(socket, params);
            }
        },
        SAVE {
            @Override
            void accept(StickiesApplication app, WebSocket socket, String[] params) throws IOException {
                app.saveNote(socket, params);
            }
        },
        DELETE {
            @Override
            void accept(StickiesApplication app, WebSocket socket, String[] params) throws IOException {
                app.deleteNote(socket, params);
            }
        };

        abstract void accept(StickiesApplication app, WebSocket socket, String[] params) throws IOException;
    }
}
