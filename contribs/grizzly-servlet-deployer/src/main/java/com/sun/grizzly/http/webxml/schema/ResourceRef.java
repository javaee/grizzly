package com.sun.grizzly.http.webxml.schema;

import java.util.List;

public class ResourceRef {
	public List<String> description;  
    public String resRefName;
    public String resType;
    public String resAuth;
    public String resSharingScope;
    public String mappedName;
    public List<InjectionTarget> injectionTarget;
    
	public List<String> getDescription() {
		return description;
	}
	public void setDescription(List<String> description) {
		this.description = description;
	}
	public String getResRefName() {
		return resRefName;
	}
	public void setResRefName(String resRefName) {
		this.resRefName = resRefName;
	}
	public String getResType() {
		return resType;
	}
	public void setResType(String resType) {
		this.resType = resType;
	}
	public String getResAuth() {
		return resAuth;
	}
	public void setResAuth(String resAuth) {
		this.resAuth = resAuth;
	}
	public String getResSharingScope() {
		return resSharingScope;
	}
	public void setResSharingScope(String resSharingScope) {
		this.resSharingScope = resSharingScope;
	}
    
	public String getMappedName() {
		return mappedName;
	}
	public void setMappedName(String mappedName) {
		this.mappedName = mappedName;
	}
	public List<InjectionTarget> getInjectionTarget() {
		return injectionTarget;
	}
	public void setInjectionTarget(List<InjectionTarget> injectionTarget) {
		this.injectionTarget = injectionTarget;
	}
	public String toString() {
		StringBuffer buffer = new StringBuffer();
		buffer.append("<ResourceRef>").append("\n");
		if(description!=null && description.size()>0){
			List<String> list = description;
			
			for (String item : list) {
				buffer.append("<description>").append(item).append("</description>").append("\n");
			}
		} 
		buffer.append("<mappedName>").append(mappedName).append("</mappedName>").append("\n");
		
		if(injectionTarget!=null && injectionTarget.size()>0){
			List<InjectionTarget> list = injectionTarget;
			
			for (InjectionTarget item : list) {
				buffer.append(item).append("\n");
			}
		} 
		buffer.append("<resAuth>").append(resAuth).append("</resAuth>").append("\n");
		buffer.append("<resRefName>").append(resRefName).append("</resRefName>").append("\n");
		buffer.append("<resSharingScope>").append(resSharingScope).append("</resSharingScope>").append("\n");
		buffer.append("<resType>").append(resType).append("</resType>").append("\n");
		buffer.append("</ResourceRef>");
		return buffer.toString();
	}
}
