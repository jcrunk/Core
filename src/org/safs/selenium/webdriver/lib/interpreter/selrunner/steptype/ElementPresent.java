/** 
 * Copyright (C) SAS Institute, All rights reserved.
 * General Public License: http://www.opensource.org/licenses/gpl-license.php
 **/
package org.safs.selenium.webdriver.lib.interpreter.selrunner.steptype;

import org.safs.selenium.webdriver.lib.interpreter.WDLocator;
import org.safs.selenium.webdriver.lib.interpreter.selrunner.SRUtilities;
import org.safs.selenium.webdriver.lib.interpreter.selrunner.SRunnerType;

import com.sebuilder.interpreter.Getter;
import com.sebuilder.interpreter.Locator;
import com.sebuilder.interpreter.Step;
import com.sebuilder.interpreter.StepType;
import com.sebuilder.interpreter.TestRun;

public class ElementPresent extends com.sebuilder.interpreter.steptype.ElementPresent implements SRunnerType {

	@Override
	public void processParams(Step step, String[] params) {
		SRUtilities.setLocatorParam(step, params[1]);
		try{ 
			if(params[2].length() > 0){
				step.stringParams.put("variable", params[2]);
			}
		}catch(Throwable ignore){}
	}
}
