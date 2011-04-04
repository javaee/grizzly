/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2010-2011 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.grizzly.samples.stickies;

import com.sun.grizzly.tcp.Request;
import com.sun.grizzly.websockets.WebSocket;
import com.sun.grizzly.websockets.WebSocketApplication;
import com.sun.grizzly.websockets.WebSocketEngine;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

public class StickiesApplication extends WebSocketApplication {
    static final Logger logger = Logger.getLogger(WebSocketEngine.WEBSOCKET);
    public static final AtomicInteger ids = new AtomicInteger(0);
    private Map<String, Note> notes = new HashMap<String, Note>();

    @Override
    public boolean isApplicationRequest(Request request) {
        return request.requestURI().equals("/");
    }

    @Override
    public void onMessage(WebSocket socket, String data) {
        final String[] bits = data.split("-");
        Operations.valueOf(bits[0].toUpperCase()).accept(this, socket, bits);
    }

    @Override
    public void onConnect(WebSocket socket) {
        super.onConnect(socket);
        for (Note note : notes.values()) {
            socket.send("create-" + note.toString());
        }
    }

    private void broadcast(WebSocket original, String text) {
        for (WebSocket webSocket : getWebSockets()) {
            if (!webSocket.equals(original)) {
                send(webSocket, text);
            }
        }

    }

    private void send(WebSocket socket, String text) {
        socket.send(text);
    }

    private void createNote(String[] params) {
        Note note = new Note();
        notes.put(note.getId(), note);
        broadcast(null, "create-" + note.toString());
    }

    private void saveNote(WebSocket socket, String[] params) {
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

    private void deleteNote(WebSocket socket, String[] params) {
        notes.remove(params[1]);
        broadcast(socket, "delete-" + params[1]);
    }

    enum Operations {
        CREATE {
            @Override
            void accept(StickiesApplication app, WebSocket socket, String[] params) {
                app.createNote(params);
            }
        },
        SAVE {
            @Override
            void accept(StickiesApplication app, WebSocket socket, String[] params) {
                app.saveNote(socket, params);
            }
        },
        DELETE {
            @Override
            void accept(StickiesApplication app, WebSocket socket, String[] params) {
                app.deleteNote(socket, params);
            }
        };

        abstract void accept(StickiesApplication app, WebSocket socket, String[] params);
    }
}
