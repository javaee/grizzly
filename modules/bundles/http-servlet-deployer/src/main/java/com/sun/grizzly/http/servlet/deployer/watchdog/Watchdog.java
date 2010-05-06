/**
 *
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER. *
 * Copyright 2007-2008 Sun Microsystems, Inc. All rights reserved. *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License. You can obtain
 * a copy of the License at https://glassfish.dev.java.net/public/CDDL+GPL.html
 * or glassfish/bootstrap/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at glassfish/bootstrap/legal/LICENSE.txt.
 * Sun designates this particular file as subject to the "Classpath" exception
 * as provided by Sun in the GPL Version 2 section of the License file that
 * accompanied this code.  If applicable, add the following below the License
 * Header, with the fields enclosed by brackets [] replaced by your own
 * identifying information: "Portions Copyrighted [year]
 * [name of copyright owner]"
 *
 * Contributor(s):
 *
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 *
 */
package com.sun.grizzly.http.servlet.deployer.watchdog;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.logging.Logger;

import com.sun.grizzly.http.servlet.deployer.GrizzlyWebServerDeployer;
import com.sun.grizzly.http.servlet.deployer.GrizzlyWebServerDeployer.DeployableFilter;
import com.sun.grizzly.http.servlet.deployer.conf.DeployableConfiguration;

/**
 * 
 * @author Sebastien Dionne
 *
 */
public class Watchdog implements Callable<Boolean>  {
	
	private static Logger logger = Logger.getLogger(Watchdog.class.getName());
	
	protected GrizzlyWebServerDeployer deployer;
	
	public Watchdog(GrizzlyWebServerDeployer deployer){
		this.deployer = deployer;
	}
	
	private void lookForNewFiles(String folder) throws Exception {
		
		Map<String, WatchedFile> contextMap = deployer.getWatchedFileMap();
		// reset the flag for the files,  if the flag Found is false at the end, that's mean that the file doesn't
		// exist anymore.
		
		for (WatchedFile watchedFile : contextMap.values()) {
			if(watchedFile!=null){
				watchedFile.resetFlag();
			}
		}
		
		File file = new File(folder);
		
		if(!file.exists()){
			throw new FileNotFoundException();
		}
		
		File files[] = file.listFiles(new DeployableFilter());
		
		for (File f : files) {
			String context = GrizzlyWebServerDeployer.getContext(f.getPath());
			
			System.out.println("context=" + context);
			
			if(contextMap.containsKey(context)){
				contextMap.get(context).setFound(true);
			} else {
				logger.info("Found a new file to deploy : " + f.getPath());
				deployer.deployApplication(new DeployableConfiguration(f.getPath()));
				contextMap.put(context, new WatchedFile(f.getPath()));
			}
			
		}
		
		List<String> contextToRemoveList = new ArrayList<String>();
		// undeploy file that are not found
		for (String context : contextMap.keySet()) {
			WatchedFile watchedFile = contextMap.get(context);
			
			if(watchedFile!=null && !watchedFile.isFound()){
				contextToRemoveList.add(context);
			}
		}
		
		// it's possible to undeploy because if the file doesn't exist, it's not locked
		if(!contextToRemoveList.isEmpty()){
			for (String context : contextToRemoveList) {
				logger.info("Application to undeploy : context= " + context);
				deployer.undeployApplication(context);
				contextMap.remove(context);
			}
		}
		
	}

	public Boolean call() throws Exception {
		if(deployer==null || deployer.getWatchDogFolder()==null){
			return false;
		}
		
		lookForNewFiles(deployer.getWatchDogFolder());
		
		return true;
	}


}
