package com.sun.grizzly.http.webxml.schema;

public class CookieConfig {
	public String name;
	public String domain;
	public String path;
	public String comment;
	public boolean httpOnly;
	public boolean secure;
	
	public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
	}
	public String getDomain() {
		return domain;
	}
	public void setDomain(String domain) {
		this.domain = domain;
	}
	public String getPath() {
		return path;
	}
	public void setPath(String path) {
		this.path = path;
	}
	public String getComment() {
		return comment;
	}
	public void setComment(String comment) {
		this.comment = comment;
	}
	public boolean isHttpOnly() {
		return httpOnly;
	}
	public void setHttpOnly(boolean httpOnly) {
		this.httpOnly = httpOnly;
	}
	public boolean isSecure() {
		return secure;
	}
	public void setSecure(boolean secure) {
		this.secure = secure;
	}

	public String toString() {
		StringBuffer buffer = new StringBuffer();
		buffer.append("<CookieConfig>").append("\n");
		buffer.append("<comment>").append(comment).append("</comment>").append("\n");
		buffer.append("<domain>").append(domain).append("</domain>").append("\n");
		buffer.append("<httpOnly>").append(httpOnly).append("</httpOnly>").append("\n");
		buffer.append("<name>").append(name).append("</name>").append("\n");
		buffer.append("<path>").append(path).append("</path>").append("\n");
		buffer.append("<secure>").append(secure).append("</secure>").append("\n");
		buffer.append("</CookieConfig>");
		return buffer.toString();
	}

}
