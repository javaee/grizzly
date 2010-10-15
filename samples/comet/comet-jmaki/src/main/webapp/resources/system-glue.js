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

jmaki.namespace("jmaki.listeners");
   
jmaki.listeners.geocoderListener = function(coordinates) {
        var keys = jmaki.attributes.keys();
        // scan the widgets for all yahoo maps
        for (var l = 0; l < keys.length; l++) {
            if (jmaki.widgets.yahoo &&  jmaki.widgets.yahoo.map &&
                jmaki.widgets.yahoo.map.Widget &&
                jmaki.attributes.get(keys[l]) instanceof jmaki.widgets.yahoo.map.Widget) {
                var _map = jmaki.attributes.get(keys[l]).map;
                var centerPoint = new YGeoPoint(coordinates[0].latitude,coordinates[0].longitude);
                var marker = new YMarker(centerPoint);
                var txt = '<div style="width:160px;height:50px;"><b>' + coordinates[0].address + ' ' +
                    coordinates[0].city + ' ' +  coordinates[0].state + '</b></div>';
                marker.addAutoExpand(txt);
                _map.addOverlay(marker);
                _map.drawZoomAndCenter(centerPoint);
            } else if (typeof GLatLng != 'undefined' &&
                       jmaki.widgets.google &&
                       jmaki.widgets.google.map &&
                       jmaki.widgets.google.map.Widget &&
                       jmaki.attributes.get(keys[l]) instanceof jmaki.widgets.google.map.Widget) {
                // set the google map
                var _map = jmaki.attributes.get(keys[l]).map;
                var centerPoint = new GLatLng(coordinates[0].latitude,coordinates[0].longitude);
                _map.setCenter(centerPoint);
                var marker = new GMarker(centerPoint);
                _map.addOverlay(marker);
                var txt = '<div style="width:160px;height:50px;"><b>' + coordinates[0].address + ' ' +
                    coordinates[0].city + ' ' +  coordinates[0].state + '</b></div>';
                marker.openInfoWindowHtml(txt);               
            } 
        }
}
// add listerner mapping for geocoder
jmaki.subscribe(new RegExp("/jmaki/plotmap$"), "jmaki.listeners.geocoderListener");
// add listerner mapping for backward compatibility
jmaki.subscribe(new RegExp("/yahoo/geocoder$"), "jmaki.listeners.geocoderListener");


// sytem level filters
jmaki.namespace("jmaki.filters");

// convert an rss feed to the jMaki table format
jmaki.filters.tableFilter = function(input) {

    var _columns = [
            {title: 'Title'},
            //{title: 'URL'},
            {title: 'Date'},
            {title: 'Description'}
            ];
    var _rows = [];

    for (var _i=0; _i < input.channel.items.length;_i++) {
      var row = [
         input.channel.items[_i].title,
        // input.channel.items[_i].link,
         input.channel.items[_i].date,
         input.channel.items[_i].description
      ];
      _rows.push(row);
    }
    return {columns : _columns, rows : _rows};
}

// convert an rss feed to the jMaki Table Model format
jmaki.filters.tableModelFilter = function(input) {
    var _columns = [
            {label: 'Title', id : 'title'},
            //{lbael: 'URL' id : 'url'},
            {label: 'Date', id : 'date'},
            {label: 'Description', id : 'description'}
            ];
    var _rows = [];

    for (var _i=0; _i < input.channel.items.length;_i++) {
      var row = {
         title : input.channel.items[_i].title,
        //url : input.channel.items[_i].link,
         date : input.channel.items[_i].date,
         description : input.channel.items[_i].description
      };
      _rows.push(row);
    }
    return {type : 'jmakiModelData', columns : _columns, rows : _rows};
}

// convert an rss feed to the jMaki accordion format
jmaki.filters.accordionFilter = function(input) {

    var _rows = [];

    for (var _i=0; _i < input.channel.items.length;_i++) {
      var row = {
         label : input.channel.items[_i].title,
        // input.channel.items[_i].link,
        // input.channel.items[_i].date,
        content : input.channel.items[_i].description
      }
      _rows.push(row);
    }
    jmaki.log("rows count=" + _rows.length);
    return {rows : _rows};
}
