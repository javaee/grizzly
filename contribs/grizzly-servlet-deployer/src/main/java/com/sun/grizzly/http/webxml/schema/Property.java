package com.sun.grizzly.http.webxml.schema;

public class Property {
	public String name;
	public String value;
	
	public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
	}
	public String getValue() {
		return value;
	}
	public void setValue(String value) {
		this.value = value;
	}
	
	public String toString() {
		StringBuffer buffer = new StringBuffer();
		buffer.append("<Property>").append("\n");
		buffer.append("<name>").append(name).append("</name>").append("\n");
		buffer.append("<value>").append(value).append("</value>").append("\n");
		buffer.append("</Property>");
		return buffer.toString();
	}
}
