package com.sun.grizzly.http.webxml.schema;

public class InjectionTarget {
	public String injectionTargetClass;
    public String injectionTargetName;
    
	public String getInjectionTargetClass() {
		return injectionTargetClass;
	}
	public void setInjectionTargetClass(String injectionTargetClass) {
		this.injectionTargetClass = injectionTargetClass;
	}
	public String getInjectionTargetName() {
		return injectionTargetName;
	}
	public void setInjectionTargetName(String injectionTargetName) {
		this.injectionTargetName = injectionTargetName;
	}

	public String toString() {
		StringBuffer buffer = new StringBuffer();
		buffer.append("<InjectionTarget>").append("\n");
		buffer.append("<injectionTargetClass>").append(injectionTargetClass).append("</injectionTargetClass>").append("\n");
		buffer.append("<injectionTargetName>").append(injectionTargetName).append("</injectionTargetName>").append("\n");
		buffer.append("</InjectionTarget>");
		return buffer.toString();
	}
}
