package com.sun.grizzly.http.servlet.deployer.comparator;

import java.io.File;
import java.util.Comparator;

public class WarFileComparator implements Comparator<File> {

	@Override
	public int compare(File o1, File o2) {
		return o1.getName().compareToIgnoreCase(o2.getName());
	}

}
