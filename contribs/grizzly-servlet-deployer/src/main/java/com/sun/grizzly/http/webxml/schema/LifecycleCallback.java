package com.sun.grizzly.http.webxml.schema;

public class LifecycleCallback {
	public String lifecycleCallbackClass;
	public String lifecycleCallbackMethod;
	
	public String getLifecycleCallbackClass() {
		return lifecycleCallbackClass;
	}
	public void setLifecycleCallbackClass(String lifecycleCallbackClass) {
		this.lifecycleCallbackClass = lifecycleCallbackClass;
	}
	public String getLifecycleCallbackMethod() {
		return lifecycleCallbackMethod;
	}
	public void setLifecycleCallbackMethod(String lifecycleCallbackMethod) {
		this.lifecycleCallbackMethod = lifecycleCallbackMethod;
	}

	public String toString() {
		StringBuffer buffer = new StringBuffer();
		buffer.append("<LifecycleCallback>").append("\n");
		buffer.append("<lifecycleCallbackClass>").append(lifecycleCallbackClass).append("</lifecycleCallbackClass>").append("\n");
		buffer.append("<lifecycleCallbackMethod>").append(lifecycleCallbackMethod).append("</lifecycleCallbackMethod>").append("\n");
		buffer.append("</LifecycleCallback>");
		return buffer.toString();
	}

}
