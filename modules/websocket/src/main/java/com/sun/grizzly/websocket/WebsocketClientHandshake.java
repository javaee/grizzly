/*
 *
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2007-2009 Sun Microsystems, Inc. All rights reserved.
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
package com.sun.grizzly.websocket;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** {@inheritDoc} */
class WebsocketClientHandshake extends WebsocketHandshake {

    private final static byte[] normresponse = Charset.forName("ASCII").
            encode("HTTP/1.1 101 Web Socket Protocol Handshake\r\n").array();
    private final static byte[] normupgrade = Charset.forName("ASCII").
            encode("Upgrade: WebSocket\r\nConnection: Upgrade\r\n").array();

   /**
     * Using enum here is not of a great value.
     * But its a decent state tracker.
     */
    private enum Handshakestate{
        PREPAREUPGRADEREQUEST{@Override
            public void handshake(WebsocketClientHandshake w) throws Exception {
                w.prepareUpgradeRequest();
            }},
        SENDUPGRADE{@Override
            public void handshake(WebsocketClientHandshake w) throws Exception {
                w.sendUpgradeRequest();
            }},
        READRESPONSEA{@Override
            public void handshake(WebsocketClientHandshake w) throws Exception {
                w.readResponseA();
            }},
        READRESPONSEB{@Override
            public void handshake(WebsocketClientHandshake w) throws Exception {
                w.readResponseB();
            }},
        READHEADERS{@Override
            public void handshake(WebsocketClientHandshake w) throws Exception {
                w.readResponseHeaders();
            }},
        VALIDATEHEADERS{@Override
            public void handshake(WebsocketClientHandshake w) throws Exception {
                w.validateResponseHeaders();
            }},
        COMPLETED{@Override
            public void handshake(WebsocketClientHandshake w) throws Exception {
                w.handshakeCOMPLETED();
            }};
        public abstract void handshake(WebsocketClientHandshake w)
                throws Exception;
        private final static Handshakestate[] allstates = values();
        public final Handshakestate nextState(){
            return (allstates[this.ordinal()+1]);
        }
    };

    private Handshakestate state = Handshakestate.allstates[0];

    private final List<WebsockHeader> headers = new ArrayList<WebsockHeader>();

    private WebsockHeader responseOrigin,responseLocation,responseProtocol;
    private int     hstart,hend,vstart;
    private boolean normalheader;
    private byte[]  upgradeRequestBytes;

    /**
   The |WebSocket-Location| header is used in the WebSocket handshake.
   It is sent from the server to the client to confirm the URL of the
   connection.  This enables the client to verify that the connection
   was established to the right server, port, and path, instead of
   relying on the server to verify that the requested host, port, and
   path are correct.

      For example, consider a server running on port 20000 of a shared
      virtual host, on behalf of the author of www.example.com, which is
      hosted on that server.  The author of the site
      hostile.example.net, also hosted on the same server, could write a
      script to connectAsync to port 20000 on hostile.example.net; if neither
      the client nor the server verified that all was well, this would
      connectAsync, and the author of the site hostile.example.net could then
      use the resources of www.example.com.

      With WebSocket, though, the server responds with a |WebSocket-
      Location| header in the handshake, explicitly saying that it is
      serving |ws://www.example.com:20000/|.  The client, expecting (in
      the case of its use by the hostile author) that the |WebSocket-
      Location| be |ws://hostile.example.net:20000/|, would abort the
      connection.
     */
    protected final String location;

    /**
       The |WebSocket-Protocol| header is used in the WebSocket handshake.
       It is sent from the client to the server and back from the server to
       the client to confirm the subprotocol of the connection.  This
       enables scripts to both select a subprotocol and be sure that the
       server agreed to serve that subprotocol.
    */
    protected final String protocol;

    protected final InetSocketAddress remoteAddress;

    protected final boolean secureonly;

    /**
      TODO:
   URI scheme syntax.
      In ABNF terms using the terminals from the URI specifications:
      [RFC5234] [RFC3986]

           "ws" ":" hier-part [ "?" query ]

      The path and query components form the resource name sent to the
      server to identify the kind of service desired.  Other components
      have the meanings described in RFC3986.

   URI scheme semantics.
      The only operation for this scheme is to open a connection using
      the Web Socket protocol.

   Encoding considerations.
      Characters in the host component that are excluded by the syntax
      defined above must be converted from Unicode to ASCII by applying
      the IDNA ToASCII algorithm to the Unicode host name, with both the
      AllowUnassigned and UseSTD3ASCIIRules flags set, and using the
      result of this algorithm as the host in the URI.  [RFC3490]

      Characters in other components that are excluded by the syntax
      defined above must be converted from Unicode to ASCII by first
      encoding the characters as UTF-8 and then replacing the
      corresponding bytes using their percent-encoded form as defined in
      the URI and IRI specification.  [RFC3986] [RFC3987]
     */
    public WebsocketClientHandshake(String url,String origin, String protocol)
            throws IOException{
        super(getOrigin(origin));
        if (url == null)
            throw new MalformedURLException("WebSocket URI is null");
        if (origin != null && origin.length() == 0)
            throw new MalformedURLException("origin length is 0");
        URI urls = null;
        try{
             urls = URI.create(url);
        }catch(Throwable ea){ throw new WebSocketImpl.IOExceptionWrap(ea); }
        String scheme = urls.getScheme();
        boolean secured  = "wss".equals(scheme);
        if ( !(secured || "ws".equals(scheme)))
            throw new MalformedURLException(
                    "WebSocket url must have a scheme (ws or wss)");
        String host = urls.getHost();
        if (host == null)
            throw new MalformedURLException("no host was specifed");
        host = host.toLowerCase(Locale.ENGLISH);
        int port = urls.getPort();
        if (port == -1)
            port = secured ? 443 : 80;
        String resource_ = urls.getPath();
        if (resource_ == null || resource_.length()==0 )
            resource_ = "/"; //0x002F
        final String hostandport =
                host+(secured?(port!=443?":"+port:""):(port!=80?":"+port:""));
        this.secureonly = secured;
        this.remoteAddress = new InetSocketAddress(host,port);
        this.location = scheme+"://"+hostandport+resource_;
        if (urls.getQuery() != null){
            resource_ += 0x003F + urls.getQuery();
        }
        if (protocol != null){
            if (protocol.length() == 0)
                throw new MalformedURLException(
                        "WebSocket protocol length is 0");
            for (int i=0;i<protocol.length();i++){
                char c = protocol.charAt(i);
                if (c < 0x0021 || c>0x007E )
                    throw new MalformedURLException(
                            "WebSocket protocol illegal character : "+c);
            }
        }
        this.protocol = protocol;
        String request =
        "GET "+resource_+" HTTP/1.1\r\n"+
        "Upgrade: WebSocket\r\n"+
        "Connection: Upgrade\r\n"+
        "Host: "+hostandport+
        "\r\nOrigin: "+this.origin;
        if (protocol != null){
            request+="\r\nWebSocket-Protocol: "+protocol;
        }
        //TODO: add cookies :
        //http://tools.ietf.org/html/draft-hixie-thewebsocketprotocol-54#page-15
        request+="\r\n\r\n";
        this.upgradeRequestBytes = request.getBytes("ASCII");
    }

    private static String getOrigin(String origin) throws UnknownHostException{
        return origin!=null?origin:
            InetAddress.getLocalHost().getCanonicalHostName();
    }

    public boolean doHandshake(ByteBuffer bb) throws Exception{
        if (state.ordinal() > Handshakestate.SENDUPGRADE.ordinal()){
            iohandler.read(bbf);
        }
        state.handshake(this);
        return state == Handshakestate.COMPLETED;
    }

    private void prepareUpgradeRequest() throws Exception{
        final ByteBuffer bb = bbf;
        bb.clear();
        if (upgradeRequestBytes.length > bb.capacity()){
            throw new MalformedURLException(
                "request length "+upgradeRequestBytes.length+">"+bb.capacity());
        }
        bb.put(upgradeRequestBytes);
        upgradeRequestBytes = null;
        bb.flip();
        (state = state.nextState()).handshake(this);
    }

    private void sendUpgradeRequest() throws Exception{
        final ByteBuffer bb = bbf;
        TCPIOhandler ioh = iohandler;        
        if (ioh.write(bb)){
            bb.clear();
            ioh.initialInterest(SelectionKey.OP_READ);
            state = state.nextState();
        }else{
            ioh.initialInterest(SelectionKey.OP_WRITE);
        }
    }

    private void readResponseA() throws Exception{
        final ByteBuffer bb = bbf;
        final byte[] ba = bb.array();
        int index = WebSocketImpl.indexOf(0,0x0A,ba,bb.limit());
        if (index == -1 ){
            //spec dont limit the number of bytes, but we do
            if (!bb.hasRemaining()){
                throw new HandShakeException("too long protocol handshake " +
                        "response >"+bb.position()+" bytes");
            }
        }else{
            if (index==0 || ba[index-1] != 0x0D ){
                throw new HandShakeException(
                        "invalid protocol handshake response");
            }
            normalheader = arrayEquals(ba, 0, 1+index, normresponse);
            if (normalheader){
                parsed = normresponse.length;
                (state = state.nextState()).handshake(this);
            }else{
                index = WebSocketImpl.indexOf(0,0x20,ba,bb.limit());
                if (index>-1 && ba[index+1]=='4'&&ba[index+2]=='0'
                        &&ba[index+3]=='7'&&ba[index+4]==0x20){
                    throw new HandShakeException(
                            "Proxy Authentication Required");
                }
                throw new HandShakeException("only normal mode implemented");
            }
        }
    }

    private void readResponseB() throws Exception{
        final ByteBuffer bb = bbf;    
        if (bb.position() - parsed >= normupgrade.length){
            byte[] ba = bb.array();
            if (!arrayEquals(ba, parsed, normupgrade.length, normupgrade)){
                throw new HandShakeException("invalid normalupgraderesponse");
            }
            parsed += normupgrade.length;
            hstart = parsed;
            (state = state.nextState()).handshake(this);
        }
    }

    private void readResponseHeaders() throws Exception{
        final ByteBuffer readBuffer = bbf;
        final byte[] ba = readBuffer.array();
        final int limit = readBuffer.position();
        while(parsed < limit){
            if (state == Handshakestate.VALIDATEHEADERS){
                state.handshake(this);
                return;
            }
            if (hend == 0){
                clientReadHeaderName(limit, ba);
                continue;
            }
            clientReadHeaderValue(limit, ba);
        }
    }

    private void clientReadHeaderName(int limit, byte[] ba) throws Exception{
        int i = parsed;
        for(;i<limit;i++){
            int b = ba[i];
            if (b>=0x41){
                if (b<=0x5A)  // A-Z
                    ba[i] = (byte) (b + 0x20); // to lowerchar
                continue;
            }
            if (b == 0x3A){ // :
                hend = i;
                parsed = ++i;
                return;
            }
            if (b == 0x0D){
                if (i == parsed){  // hend == 0
                    parsed = ++i;
                    state = state.nextState();
                    return;
                }
                throw new HandShakeException("invalid header name (0x0D)");
            }
            if (b == 0x0A){
                throw new HandShakeException("invalid header name (0x0A)");
            }
        }
        parsed = i;
    }

    private void clientReadHeaderValue(int limit, byte[] ba) throws Exception{
        int i = parsed;
        if (vstart == 0)
            vstart = ba[i]==0x20?++i:i;
        for(;i<limit;i++){
            int b = ba[i];
            if (b == 0x0D && i+1 < limit ){
                if (ba[i+1] == 0x0A){
                    createParsedHeader(ba,i);
                    return;
                }
                throw new HandShakeException(
                        "invalid header value end byte: not 0x0A");
            }
            if (b == 0x0A){
                throw new HandShakeException("invalid header value 0x0A");
            }
        }
        parsed = i;
    }

    private void createParsedHeader(byte[] ba, int index) throws Exception{
        int namelength = hend-hstart;
        if (namelength == 0)
            throw new HandShakeException("headername of 0 length");
        String headername  = new String(ba, hstart, namelength,   "UTF-8");
        String headervalue = new String(ba, vstart, index-vstart, "UTF-8");
        WebsockHeader newheader = new WebsockHeader(headername, headervalue);
        headers.add(newheader);
        validatehileparsing(newheader);
        hstart = parsed = index+2;
        hend   = 0;
        vstart = 0;
    }

    private void validatehileparsing(WebsockHeader newheader) throws Exception{
        if (newheader.name.equals("websocket-origin")){
            if (responseOrigin == null)
                responseOrigin = newheader;
            else{
                throw new HandShakeException(
                        newheader.name+" existed more then once in response");
            }
            return;
        }
        if (newheader.name.equals("websocket-location")){
            if (responseLocation == null)
                responseLocation = newheader;
            else{
                throw new HandShakeException(
                        newheader.name+" existed more then once in response");
            }
            return;
        }
        if (newheader.name.equals("websocket-protocol")){
            if (responseProtocol == null)
                responseProtocol = newheader;
            else{
                /*or if the /protocol/ was specified but
                there is not exactly one entry in the /headers/ list whose name
                is "websocket-protocol",  abort the connection*/
                if (protocol != null)
                    throw new HandShakeException(
                          newheader.name+" existed more then once in response");
                }
        }
    }

    private void validateResponseHeaders() throws Exception{
        final ByteBuffer readBuffer = bbf;
        final int available = readBuffer.position() - parsed;
        if (available > 0){
            byte[] ba = readBuffer.array();
            if (ba[parsed] != 0x0A){
                throw new HandShakeException("invalid response headers " +
                        "end byte. expected 0x0A got :"+(int)ba[parsed]);
            }
            if (normalheader){
                if (available==1){
                    parsed = 0;
                    readBuffer.clear();
                }else{
                    parsed++;
                }
                normalModeHeaderValidation();
                (state = state.nextState()).handshake(this);
            }else{
                throw new HandShakeException(
                        "only normal header mode is implemented");
            }
        }
    }

    private void normalModeHeaderValidation() throws Exception{
        if (responseOrigin == null){
            throw new HandShakeException("response missing originheader");
        }
        if (!responseOrigin.value.equalsIgnoreCase(origin)){
            throw new HandShakeException("response origin invalid"+
                responseOrigin.value+")!="+origin);
        }
        if (responseLocation == null){
            throw new HandShakeException("response missing locationheader");
        }
        if (!responseLocation.value.equals(location)){
            throw new HandShakeException("response locationheader invalid:"+
                    responseLocation.value+"!="+location);
        }
        if (protocol != null){
            if(responseProtocol == null)
                throw new HandShakeException("response missing protocolheader");
            if (!protocol.equals(responseProtocol.value)){
                throw new HandShakeException("response protocol invalid:"+
                        responseProtocol.value+"!="+protocol);
            }
        }
    }

    @Override
    public String toString() {
        return WebsocketClientHandshake.class.getSimpleName()+
                " "+state+
                " parsed:"+parsed+"\r\n"+
                " headers: "+headers
                ;
    }

}
