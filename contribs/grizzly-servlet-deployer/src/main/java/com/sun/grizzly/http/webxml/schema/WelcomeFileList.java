package com.sun.grizzly.http.webxml.schema;

import java.util.List;

public class WelcomeFileList {
	public List<String> welcomeFile;
	
	public WelcomeFileList(){
		
	}
	
	public WelcomeFileList(List<String> welcomeFile){
		this.welcomeFile = welcomeFile;
	}

	public List<String> getWelcomeFile() {
		return welcomeFile;
	}

	public void setWelcomeFile(List<String> welcomeFile) {
		this.welcomeFile = welcomeFile;
	}

	public String toString() {
		
		StringBuffer buffer = new StringBuffer();
		buffer.append("<WelcomeFileList>").append("\n");
		
		if(welcomeFile!=null && welcomeFile.size()>0){
			for (String item : welcomeFile) {
				buffer.append("<welcomeFile>").append(item).append("</welcomeFile>").append("\n");
			}
		}
		
		buffer.append("</WelcomeFileList>");
		return buffer.toString();
		
	}
	
}
