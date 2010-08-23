/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2009-2010 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.grizzly.http.webxml.schema;

import java.util.List;

public class JspPropertyGroup {
	public List<String> description;
    public List<String> displayName;
    public List<Icon> icon;
    public List<String> urlPattern;
    public boolean elIgnored;
    public String pageEncoding;
    public boolean scriptingInvalid;
    public boolean isXml;
    public List<String> includePrelude;
    public List<String> includeCoda;
    public boolean deferredSyntaxAllowedAsLiteral;
    public boolean trimDirectiveWhitespaces;
    public String defaultContentType;
    public String buffer;
    public boolean errorOnUndeclaredNamespace;
    
	public List<String> getDescription() {
		return description;
	}
	public void setDescription(List<String> description) {
		this.description = description;
	}
	public List<String> getDisplayName() {
		return displayName;
	}
	public void setDisplayName(List<String> displayName) {
		this.displayName = displayName;
	}
	public List<Icon> getIcon() {
		return icon;
	}
	public void setIcon(List<Icon> icon) {
		this.icon = icon;
	}
	public List<String> getUrlPattern() {
		return urlPattern;
	}
	public void setUrlPattern(List<String> urlPattern) {
		this.urlPattern = urlPattern;
	}
	public boolean getElIgnored() {
		return elIgnored;
	}
	public void setElIgnored(boolean elIgnored) {
		this.elIgnored = elIgnored;
	}
	public String getPageEncoding() {
		return pageEncoding;
	}
	public void setPageEncoding(String pageEncoding) {
		this.pageEncoding = pageEncoding;
	}
	public boolean getScriptingInvalid() {
		return scriptingInvalid;
	}
	public void setScriptingInvalid(boolean scriptingInvalid) {
		this.scriptingInvalid = scriptingInvalid;
	}
	public boolean getIsXml() {
		return isXml;
	}
	public void setIsXml(boolean isXml) {
		this.isXml = isXml;
	}
	public List<String> getIncludePrelude() {
		return includePrelude;
	}
	public void setIncludePrelude(List<String> includePrelude) {
		this.includePrelude = includePrelude;
	}
	public List<String> getIncludeCoda() {
		return includeCoda;
	}
	public void setIncludeCoda(List<String> includeCoda) {
		this.includeCoda = includeCoda;
	}
	public boolean getDeferredSyntaxAllowedAsLiteral() {
		return deferredSyntaxAllowedAsLiteral;
	}
	public void setDeferredSyntaxAllowedAsLiteral(boolean deferredSyntaxAllowedAsLiteral) {
		this.deferredSyntaxAllowedAsLiteral = deferredSyntaxAllowedAsLiteral;
	}
	public boolean getTrimDirectiveWhitespaces() {
		return trimDirectiveWhitespaces;
	}
	public void setTrimDirectiveWhitespaces(boolean trimDirectiveWhitespaces) {
		this.trimDirectiveWhitespaces = trimDirectiveWhitespaces;
	}
	public String getDefaultContentType() {
		return defaultContentType;
	}
	public void setDefaultContentType(String defaultContentType) {
		this.defaultContentType = defaultContentType;
	}
	public String getBuffer() {
		return buffer;
	}
	public void setBuffer(String buffer) {
		this.buffer = buffer;
	}
	public boolean getErrorOnUndeclaredNamespace() {
		return errorOnUndeclaredNamespace;
	}
	public void setErrorOnUndeclaredNamespace(boolean errorOnUndeclaredNamespace) {
		this.errorOnUndeclaredNamespace = errorOnUndeclaredNamespace;
	}
	
		public String toString() {
			StringBuffer sb = new StringBuffer();
			sb.append("<JspPropertyGroup>").append("\n");
			sb.append("<buffer>").append(buffer).append("</buffer>").append("\n");
			sb.append("<defaultContentType>").append(defaultContentType).append("</defaultContentType>").append("\n");
			sb.append("<deferredSyntaxAllowedAsLiteral>").append(deferredSyntaxAllowedAsLiteral).append("</deferredSyntaxAllowedAsLiteral>").append("\n");
			if(description!=null && description.size()>0){
				List<String> list = description;
				
				for (String item : list) {
					sb.append("<description>").append(item).append("</description>").append("\n");
				}
			} 
			if(displayName!=null && displayName.size()>0){
				List<String> list = displayName;
				
				for (String item : list) {
					sb.append("<displayName>").append(item).append("</displayName>").append("\n");
				}
			}  
			sb.append("<elIgnored>").append(elIgnored).append("</elIgnored>").append("\n");
			sb.append("<errorOnUndeclaredNamespace>").append(errorOnUndeclaredNamespace).append("</errorOnUndeclaredNamespace>").append("\n");
			if(icon!=null && icon.size()>0){
				List<Icon> list = icon;
				
				for (Icon item : list) {
					sb.append(item).append("\n");
				}
			} 
			
			if(includeCoda!=null && includeCoda.size()>0){
				sb.append("<includeCoda>").append("\n");
				
				List<String> list = includeCoda;
				
				for (String item : list) {
					sb.append(item).append("\n");
				}
				sb.append("</includeCoda>").append("\n");
			} 
			
			if(includePrelude!=null && includePrelude.size()>0){
				sb.append("<includePrelude>").append("\n");
				
				List<String> list = includePrelude;
				
				for (String item : list) {
					sb.append(item).append("\n");
				}
				sb.append("</includePrelude>").append("\n");
			} 
			
			sb.append("<isXml>").append(isXml).append("</isXml>").append("\n");
			sb.append("<pageEncoding>").append(pageEncoding).append("</pageEncoding>").append("\n");
			sb.append("<scriptingInvalid>").append(scriptingInvalid).append("</scriptingInvalid>").append("\n");
			sb.append("<trimDirectiveWhitespaces>").append(trimDirectiveWhitespaces).append("</trimDirectiveWhitespaces>").append("\n");
			if(urlPattern!=null && urlPattern.size()>0){
				List<String> list = urlPattern;
				
				for (String item : list) {
					sb.append("<urlPattern>").append(item).append("</urlPattern>").append("\n");
				}
			} 
			sb.append("</JspPropertyGroup>");
			return sb.toString();
		}
    
    
}
