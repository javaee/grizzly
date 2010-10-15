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

dojo.require("dojox.cometd");

var room = {
    _last: "",
    _username: null,

    join: function(name){
        
        if(name == null || name.length==0 ){
            alert('Please enter a username!');
        }else{
		
            dojox.cometd.init(new String(document.location).replace(/http:\/\/[^\/]*/,'').replace(/\/\/.*$/,'\/cometd')+"/cometd");
            this._username=name;
            dojo.byId('join').className='hidden';
            dojo.byId('joined').className='';
            dojo.byId('phrase').focus();

            // Really need to batch to avoid ordering issues
	    dojox.cometd.startBatch();
            dojox.cometd.subscribe("/chat/demo", room, "_chat");
            dojox.cometd.publish("/chat/demo", { user: room._username, join: true, chat : room._username+" has joined"});
	    dojox.cometd.endBatch();
        }
    },

    leave: function(){
        if (room._username==null)
            return;
	dojox.cometd.startBatch();
        dojox.cometd.publish("/chat/demo", { user: room._username, leave: true, chat : room._username+" has left"});
        dojox.cometd.unsubscribe("/chat/demo", room, "_chat");
	dojox.cometd.endBatch();

        // switch the input form
        dojo.byId('join').className='';
        dojo.byId('joined').className='hidden';
        dojo.byId('username').focus();
        room._username=null;
        dojox.cometd.disconnect();
    },
      
    chat: function(text){
        if(!text || !text.length){ return false; }
        dojox.cometd.publish("/chat/demo", { user: room._username, chat: text});
    },

    _chat: function(message){
        var chat=dojo.byId('chat');
        if(!message.data){
            alert("bad message format "+message);
            return;
        }
        var from=message.data.user;
        var special=message.data.join || message.data.leave;
        var text=message.data.chat;
        if(!text){ return; }

        if( !special && from == room._last ){
            from="...";
        }else{
            room._last=from;
            from+=":";
        }

        if(special){
            chat.innerHTML += "<span class=\"alert\"><span class=\"from\">"+from+"&nbsp;</span><span class=\"text\">"+text+"</span></span><br/>";
            room._last="";
        }else{
            chat.innerHTML += "<span class=\"from\">"+from+"&nbsp;</span><span class=\"text\">"+text+"</span><br/>";
        } 
        chat.scrollTop = chat.scrollHeight - chat.clientHeight;    
    },
  
  _init: function(){
        dojo.byId('join').className='';
        dojo.byId('joined').className='hidden';
        dojo.byId('username').focus();
	
        var element=dojo.byId('username');
        element.setAttribute("autocomplete","OFF"); 
        dojo.connect(element, "onkeyup", function(e){   
            if(e.keyCode == dojo.keys.ENTER){
                room.join(dojo.byId('username').value);
                return false;
            }
            return true;
	});
  
        element=dojo.byId('joinB');
        element.onclick = function(){
            room.join(dojo.byId('username').value);
            return false;
	}
  
        element=dojo.byId('phrase');
        element.setAttribute("autocomplete","OFF");
        dojo.connect(element, "onkeyup", function(e){   
            if(e.keyCode == dojo.keys.ENTER){
                room.chat(dojo.byId('phrase').value);
                dojo.byId('phrase').value='';
                return false;
            }
            return true;
	});
  
        element=dojo.byId('sendB');
        element.onclick = function(){
          room.chat(dojo.byId('phrase').value);
          dojo.byId('phrase').value='';
	}
  
        element=dojo.byId('leaveB');
        element.onclick = function(){
          room.leave();
	}
    } 
};

dojo.addOnLoad(room, "_init");
dojo.addOnUnload(room,"leave");
