package com.sun.grizzly.http.webxml.schema;

public class Taglib {
	public String taglibUri;
    public String taglibLocation;
    
    public Taglib(){
    	
    }
    
    public Taglib(String taglibUri, String taglibLocation){
    	this.taglibUri = taglibUri;
    	this.taglibLocation = taglibLocation;
    }
    
	public String getTaglibUri() {
		return taglibUri;
	}
	public void setTaglibUri(String taglibUri) {
		this.taglibUri = taglibUri;
	}
	public String getTaglibLocation() {
		return taglibLocation;
	}
	public void setTaglibLocation(String taglibLocation) {
		this.taglibLocation = taglibLocation;
	}

	public String toString() {
		StringBuffer buffer = new StringBuffer();
		buffer.append("<Taglib>").append("\n");
		buffer.append("<taglibLocation>").append(taglibLocation).append("</taglibLocation>").append("\n");
		buffer.append("<taglibUri>").append(taglibUri).append("</taglibUri>").append("\n");
		buffer.append("</Taglib>");
		return buffer.toString();
	}
}
