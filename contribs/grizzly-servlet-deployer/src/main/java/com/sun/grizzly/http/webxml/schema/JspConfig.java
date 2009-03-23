package com.sun.grizzly.http.webxml.schema;

import java.util.List;

public class JspConfig {
	public List<Taglib> taglib;
	public List<JspPropertyGroup> jspPropertyGroup;
	
	public List<Taglib> getTaglib() {
		return taglib;
	}
	public void setTaglib(List<Taglib> taglib) {
		this.taglib = taglib;
	}
	public List<JspPropertyGroup> getJspPropertyGroup() {
		return jspPropertyGroup;
	}
	public void setJspPropertyGroup(List<JspPropertyGroup> jspPropertyGroup) {
		this.jspPropertyGroup = jspPropertyGroup;
	}
	
	public String toString() {
		StringBuffer buffer = new StringBuffer();
		buffer.append("<JspConfig>").append("\n");
		
		if(taglib!=null && taglib.size()>0){
			List<Taglib> list = taglib;
			
			for (Taglib item : list) {
				buffer.append(item).append("\n");
			}
		} 
		
		if(jspPropertyGroup!=null && jspPropertyGroup.size()>0){
			List<JspPropertyGroup> list = jspPropertyGroup;
			
			for (JspPropertyGroup item : list) {
				buffer.append(item).append("\n");
			}
		} 
		
		buffer.append("</JspConfig>");
		return buffer.toString();
	}
}
