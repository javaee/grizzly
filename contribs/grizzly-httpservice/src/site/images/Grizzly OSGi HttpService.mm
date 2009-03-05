<map version="0.8.1">
<!-- To view this file, download free mind mapping software FreeMind from http://freemind.sourceforge.net -->
<node CREATED="1232405386243" ID="Freemind_Link_571091612" MODIFIED="1233497952379" TEXT="Grizzly OSGi HttpService">
<edge WIDTH="thin"/>
<node COLOR="#338800" CREATED="1233269393083" FOLDED="true" ID="Freemind_Link_1569408289" MODIFIED="1236288982226" POSITION="right" TEXT="Servlet Integration">
<edge WIDTH="thin"/>
<font BOLD="true" NAME="SansSerif" SIZE="12"/>
<node COLOR="#338800" CREATED="1233619205319" ID="_" MODIFIED="1233619610321" TEXT="ServletAdapter">
<node COLOR="#999999" CREATED="1233271398348" ID="Freemind_Link_817354511" MODIFIED="1233619679589" TEXT="Servlet when registering&#xa;needs to be started&#xa;needs to throw ServletException if init fails">
<edge WIDTH="thin"/>
<font NAME="SansSerif" SIZE="10"/>
</node>
<node COLOR="#999999" CREATED="1233619813990" ID="Freemind_Link_693537481" MODIFIED="1233619846337" TEXT="Use OSGiServletContext">
<font NAME="SansSerif" SIZE="10"/>
</node>
</node>
<node COLOR="#338800" CREATED="1233619213129" ID="Freemind_Link_1429330035" MODIFIED="1233619611237" TEXT="ServletContext">
<node COLOR="#999999" CREATED="1233271769806" ID="Freemind_Link_821457703" MODIFIED="1233498062543" TEXT="ServletContext methods getResource&#xa;and getResourceAsStream needs tu use&#xa;HttpContext.getResource">
<font NAME="SansSerif" SIZE="10"/>
</node>
<node COLOR="#999999" CREATED="1233619254919" ID="Freemind_Link_891518120" MODIFIED="1233620690849" TEXT="Override getMimeType&#xa;So it uses HttpContext.getMimeType&#xa;If HttpContext.getMimeType == null -&gt; try to determinate by our self">
<font NAME="SansSerif" SIZE="10"/>
</node>
</node>
<node COLOR="#338800" CREATED="1233619879747" ID="Freemind_Link_709214683" MODIFIED="1236288974532" TEXT="Review Thread Safety&#xa;&amp; data structures"/>
</node>
<node COLOR="#338800" CREATED="1233269430967" FOLDED="true" ID="Freemind_Link_17718760" MODIFIED="1236288985456" POSITION="right" TEXT="Resource Registration">
<font BOLD="true" NAME="SansSerif" SIZE="12"/>
<node COLOR="#338800" CREATED="1233619489842" ID="Freemind_Link_1314961618" MODIFIED="1233877871623" TEXT="ResourceAdapter">
<edge WIDTH="thin"/>
<font NAME="SansSerif" SIZE="12"/>
<node COLOR="#999999" CREATED="1233619500918" ID="Freemind_Link_247114518" MODIFIED="1233619708408" TEXT="Subclass GrizzlyAdapter">
<font NAME="SansSerif" SIZE="10"/>
</node>
<node COLOR="#999999" CREATED="1233619735141" ID="Freemind_Link_1062376546" MODIFIED="1233619753192" TEXT="Implement resource mapping">
<font NAME="SansSerif" SIZE="10"/>
</node>
<node COLOR="#999999" CREATED="1233619940413" ID="Freemind_Link_755999989" MODIFIED="1233620696054" TEXT="Use HttpContext.getMimeType&#xa;to determinate Content-Type to set&#xa;If HttpContext.getMimeType == null -&gt; try to determinate by our self">
<font NAME="SansSerif" SIZE="10"/>
</node>
<node COLOR="#999999" CREATED="1233619519475" ID="Freemind_Link_1989314954" MODIFIED="1233620101430" TEXT="Service method&#xa;Use HttpContext.getResource to provide data.&#xa;If HttpContext.getResource returns null -&gt; 404&#xa;Done after mapping (based on result of it)">
<font NAME="SansSerif" SIZE="10"/>
</node>
</node>
<node COLOR="#338800" CREATED="1233621359640" ID="Freemind_Link_1306972688" MODIFIED="1236288984565" TEXT="Review Thread Safety&#xa;&amp; data structures">
<edge WIDTH="thin"/>
<font NAME="SansSerif" SIZE="12"/>
</node>
</node>
<node COLOR="#990000" CREATED="1236288988635" ID="Freemind_Link_1579913728" MODIFIED="1236289000123" POSITION="right" TEXT="SSL">
<font BOLD="true" NAME="SansSerif" SIZE="12"/>
</node>
<node COLOR="#338800" CREATED="1233269195358" FOLDED="true" ID="Freemind_Link_1867536243" MODIFIED="1233269383162" POSITION="left" TEXT="&lt;html&gt;&lt;b&gt;GrizzlyWebServer&lt;/b&gt;&lt;br/&gt;&#xa;Dynamic add/remove GrizzlyAdapter&#xa;&lt;/html&gt;">
<node COLOR="#999999" CREATED="1233269267599" ID="Freemind_Link_530593684" LINK="https://grizzly.dev.java.net/issues/show_bug.cgi?id=417" MODIFIED="1233269386310" TEXT="Issue">
<font NAME="SansSerif" SIZE="10"/>
</node>
<node COLOR="#999999" CREATED="1233269355643" ID="Freemind_Link_703775678" MODIFIED="1233269386883" TEXT="Status: done">
<font NAME="SansSerif" SIZE="10"/>
</node>
</node>
<node COLOR="#338800" CREATED="1233270459443" FOLDED="true" ID="Freemind_Link_400020261" MODIFIED="1233428767917" POSITION="left" TEXT="Configuration">
<font BOLD="true" NAME="SansSerif" SIZE="12"/>
<node COLOR="#999999" CREATED="1233270477374" ID="Freemind_Link_520142533" MODIFIED="1233620306414" TEXT="In Activateor.start&#xa;Read configuration from&#xa;OSGi env as specified in 102.9">
<font NAME="SansSerif" SIZE="10"/>
</node>
</node>
<node COLOR="#338800" CREATED="1233269446655" FOLDED="true" ID="Freemind_Link_1291473624" MODIFIED="1233877934814" POSITION="left" TEXT="Http Authorization">
<font BOLD="true" NAME="SansSerif" SIZE="12"/>
<node COLOR="#338800" CREATED="1233620927199" ID="Freemind_Link_1823223585" MODIFIED="1233620948148" TEXT="Servlet">
<node COLOR="#338800" CREATED="1233620722395" FOLDED="true" ID="Freemind_Link_1922803863" MODIFIED="1233621243470" TEXT="Implement OSGiAuthFilter">
<node COLOR="#999999" CREATED="1233621182979" ID="Freemind_Link_1293758403" MODIFIED="1233621236994" TEXT="Call HttpContext.handleSecurity&#xa;If return true, proceed with next filter in chain,&#xa;else stop processing.">
<font NAME="SansSerif" SIZE="10"/>
</node>
</node>
</node>
<node COLOR="#338800" CREATED="1233620933558" ID="Freemind_Link_1552339935" MODIFIED="1233877933382" TEXT="Resource">
<node COLOR="#999999" CREATED="1233877908464" ID="Freemind_Link_414829636" MODIFIED="1233877929854" TEXT="Invoke HttpContext.handleSecurity">
<font NAME="SansSerif" SIZE="10"/>
</node>
</node>
</node>
<node COLOR="#338800" CREATED="1236288749522" FOLDED="true" ID="Freemind_Link_1815136621" MODIFIED="1236288795156" POSITION="left" TEXT="Complex Context">
<font BOLD="true" NAME="SansSerif" SIZE="12"/>
<node COLOR="#999999" CREATED="1236288801748" ID="Freemind_Link_771477405" MODIFIED="1236288959007" TEXT="Support for contexts like this&#xa;/a&#xa;/a/b&#xa;For request to /a/b/c first invoked is /a/b&#xa;if doesn&apos;t handle request (404) than /a">
<font NAME="SansSerif" SIZE="10"/>
</node>
</node>
<node COLOR="#990000" CREATED="1236289001834" ID="Freemind_Link_909337922" MODIFIED="1236289011689" POSITION="right" TEXT="Security">
<font BOLD="true" NAME="SansSerif" SIZE="12"/>
</node>
</node>
</map>
