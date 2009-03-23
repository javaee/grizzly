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
	/**
		 * 
		 * @return 
		 * @author 
		 */
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
