/** 
 * Copyright (C) SAS Institute, All rights reserved.
 * General Public License: http://www.opensource.org/licenses/gpl-license.php
 **/
/**
 * Developer Log:
 * JUL 28, 2015 SCNTAX Added SwitchToFrame and SwitchToFrameIndex support
 * MAR 01, 2016 CANAGL Added Support for SAFSVARS variable lookups if local Map has no reference.
 * SEP 27, 2016 SBJLWA Modified initRemoteWebDriver(): Launch selenium server set in the .ini configuration file.
 *                                                     Close the browser after getting RemoteDriver.
 */
package org.safs.selenium.webdriver.lib.interpreter;

import java.util.HashMap;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.safs.SAFSException;
import org.safs.STAFHelper;
import org.safs.StringUtils;
import org.safs.selenium.webdriver.WebDriverGUIUtilities;
import org.safs.selenium.webdriver.lib.SeleniumPlusException;
import org.safs.selenium.webdriver.lib.WDLibrary;

import com.sebuilder.interpreter.Locator;
import com.sebuilder.interpreter.Script;
import com.sebuilder.interpreter.Step;
import com.sebuilder.interpreter.StepType;
import com.sebuilder.interpreter.TestRun;
import com.sebuilder.interpreter.Verify;
import com.sebuilder.interpreter.steptype.ClickElement;
import com.sebuilder.interpreter.steptype.Get;
import com.sebuilder.interpreter.steptype.Store;
import com.sebuilder.interpreter.steptype.SwitchToFrame;
import com.sebuilder.interpreter.steptype.SwitchToFrameByIndex;
import com.sebuilder.interpreter.webdriverfactory.WebDriverFactory;

/**
 * The primary purpose of this class is to extend the TestRun support to include Step peeking, skipping, 
 * and Retry of specific Script Steps.
 * <p>
 * We also provide WDLocator instances using enhanced SearchObject search algorithms.
 * <p>
 * We've added the ability for embedded variable references in Scripts [ex: ${var}] to be handled sought 
 * in SAFSVARS if they are not found in the local variable Map.
 *    
 * @author canagl
 */
public class WDTestRun extends TestRun {

	public static final String VARREF_START = "${";
	public static final String VARREF_END   = "}";
	public static final String MAPREF_PREFIX   = "map:";
	
	public WDTestRun(Script script, int implicitlyWaitDriverTimeout,
			int pageLoadDriverTimeout, Map<String, String> initialVars) {
		super(script, implicitlyWaitDriverTimeout, pageLoadDriverTimeout, initialVars);
	}

	public WDTestRun(Script script, int implicitlyWaitDriverTimeout,
			int pageLoadDriverTimeout) {
		super(script, implicitlyWaitDriverTimeout, pageLoadDriverTimeout);
	}

	public WDTestRun(Script script, Log log, TestRun previousRun,
			int implicitlyWaitDriverTimeout, int pageLoadDriverTimeout,
			Map<String, String> initialVars) {
		super(script, log, previousRun, implicitlyWaitDriverTimeout,
				pageLoadDriverTimeout, initialVars);
	}

	public WDTestRun(Script script, Log log,
			WebDriverFactory webDriverFactory,
			HashMap<String, String> webDriverConfig,
			int implicitlyWaitDriverTimeout, int pageLoadDriverTimeout,
			Map<String, String> initialVars) {
		super(script, log, webDriverFactory, webDriverConfig,
				implicitlyWaitDriverTimeout, pageLoadDriverTimeout, initialVars);
	}

	public WDTestRun(Script script, Log log,
			WebDriverFactory webDriverFactory,
			HashMap<String, String> webDriverConfig,
			int implicitlyWaitDriverTimeout, int pageLoadDriverTimeout) {
		super(script, log, webDriverFactory, webDriverConfig,
				implicitlyWaitDriverTimeout, pageLoadDriverTimeout);
	}

	public WDTestRun(Script script, Log log,
			WebDriverFactory webDriverFactory,
			HashMap<String, String> webDriverConfig,
			Map<String, String> initialVars) {
		super(script, log, webDriverFactory, webDriverConfig, initialVars);
	}

	public WDTestRun(Script script, Log log,
			WebDriverFactory webDriverFactory,
			HashMap<String, String> webDriverConfig) {
		super(script, log, webDriverFactory, webDriverConfig);
	}

	public WDTestRun(Script script, Log log) {
		super(script, log);
	}

	public WDTestRun(Script script) {
		super(script);
	}

	/**
	 * @param map name of app map.  may be null.
	 * @param win name of app map section.  may be null.
	 * @param item item in app map from specified or default app map section.
	 * @return String value or null if no value exists.
	 */
	public static String getAppMapItem(String map, String win, String item){
		return WebDriverGUIUtilities._LASTINSTANCE.getSTAFHelper().getAppMapItem(map, win, item);
	}
	
	/**
	 * @param varName variable value to retrieve
	 * @return String value or null if no value exists.
	 */
	public static String getVariableValue(String varName){
		try {
			return WebDriverGUIUtilities._LASTINSTANCE.getSTAFHelper().getVariable(varName);
		} catch (SAFSException e) {}
		return null;
	}
	
	/**
	 * Process any ${var} references.
	 * Lookup potential variable values stored in the local Java Map object and, if not present there, 
	 * see if it is available via SAFSVARS or SAFSMAPS.
	 * <p>
     * Valid possible App Map Reference formats:
     * <p><pre>
     *   ${Map:ConstantName}              (default App Map)
     *   ${Map:WindowName:CompName}       (default App Map)
     *   ${Map:mapID:WindowName:CompName}
     *   </pre><p>
	 * @param value
	 * @return value with embedded variable references replaced.
	 * @see WebDriverGUIUtilities#_LASTINSTANCE
	 * @see STAFHelper#getVariable(String)
	 */
	public String replaceVariableReferences(String value){		
		int start = -1;
		int end   = -1;
		do{
		   start = value.indexOf(VARREF_START);
		   if(start > end){
			   // there must be a var name between the braces
			   end = value.indexOf(VARREF_END, start + VARREF_START.length() +1);
			   if(end > start){
				   String key = value.substring(start+VARREF_START.length(), end);				   
				   String val = null;
				   getLog().debug("WDTestRun seeking embedded variable reference '"+ key+"'.");
				   try{ 
					   if(vars().containsKey(key)){
						   val = vars().get(key);
					   }else{
						   // Valid possible App Map References:
						   // ${Map:ConstantName}          (default App Map)
						   // ${Map:WindowName:CompName} (default App Map)
						   // ${Map:mapID:WindowName:CompName}
						   if(key.toLowerCase().startsWith(MAPREF_PREFIX)){
							   String map = key.substring(MAPREF_PREFIX.length());
							   String win = null;
							   String comp = null;
							   int col = map.lastIndexOf(':');
							   if(col < 0){ 
								   //only a COMP (or constant) name is provided
								   comp = map;
								   map = null;
							   }else{
								   // extract explicit comp name
								   comp = map.substring(col+1);
								   map = map.substring(0, col);
								   col = map.lastIndexOf(':');
								   if(col < 0){
									   win = map;
									   map = null;
								   }else{
									   win = map.substring(col+1);
									   map = map.substring(0, col);
								   }
							   }
							   // lookup App Map Reference mapid and winname may be null
							   val = getAppMapItem(map, win, comp);
						   }else{
							   val = getVariableValue(key);
						   }
					   }
				   }catch(Exception ignore){}
				   value = val == null ? 
						   value       : 
						   value.replace(VARREF_START + key + VARREF_END, val);
				   getLog().debug("WDTestRun replacing variable reference '"+ key+"' as '"+ value +"'.");
			   }
		   }
		}while(start > -1 && end > start);		
		return value;
	}
	
	/**
	 * Fetches a Locator parameter from the current step.
	 * @param paramName The parameter's name.
	 * @return The Locator with any variable references in its value replaced.
	 * @see #replaceVariableReferences(String)
	 */
	@Override
	public Locator locator(String paramName) {
		if(paramName == null)
			throw new RuntimeException("WDTestRun.locator() paramName is null!");		
		Step step = currentStep();
		if(step == null)
			throw new RuntimeException("WDTestRun.locator() currentStep() #" +
					(stepIndex + 1) + " is coming back null!");		
		Locator loc = step.locatorParams.get(paramName);
		if(loc == null)
			throw new RuntimeException("WDTestRun.locator() step.locatorParams is coming back null!");

		WDLocator l = (loc instanceof WDLocator ) ?
				      (WDLocator)loc              :
				      new WDLocator(loc.type.name(), loc.value);
	    step.locatorParams.put(paramName, l);
		getLog().info("");
		try{ getLog().info("Locator class: "+ l.getClass().getName());}catch(Exception x){}
		try{ getLog().info("Locator Type: "+ l.wdtype);}catch(Exception x){}
		try{ getLog().info("Locator Value: "+ l.value);}catch(Exception x){}
		l.value = replaceVariableReferences(l.value);
		return l;
	}

	/**
	 * @return the value of the current Step's named string parameter with any variable references replaced.
	 * @see #replaceVariableReferences(String)
	 */
	@Override
	public String string(String paramName) {
		String s = currentStep().stringParams.get(paramName);
		if (s == null) {
			throw new RuntimeException("Missing parameter \"" + paramName + "\" at step #" +
					(stepIndex + 1) + ".");
		}
		return replaceVariableReferences(s);
	}
	

	/** 
	 * Retrieve the next Step that would be executed by this TestRun without incrementing the 
	 * stepIndex counter.
	 * <p>
	 * Does NOT use hasNext()--which can prematurely shutdown the WebDriver.
	 * <p>
	 * If avoiding hasNext() the user should make sure cleanup() is called when finished to perform 
	 * the normal cleanup operations done by hasNext() when it detects there are no more Steps to 
	 * execute.
	 * @return Step -- null if there are no more steps. 
	 */
	public Step peekNext(){
		try{
			getLog().debug("WDTestRun Peeking step " + (stepIndex + 2) + ":" +
			         getScript().steps.get(stepIndex + 1).type.getClass().getSimpleName());
		}catch(Exception x){
			getLog().debug("WDTestRun: There are no more Steps to Peek. null.");
			return null;
		}
		return getScript().steps.get(stepIndex + 1); 
	}
	
	/**
	 * Increment the stepIndex counter and return the Step that can be skipped or executed by this TestRun.
	 * Uses peekNext() to check for a valid next Step.  
	 * <p>
	 * Does NOT use hasNext()--which can prematurely shutdown the WebDriver.
	 * <p>
	 * If avoiding hasNext() the user should make sure cleanup() is called when finished to perform 
	 * the normal cleanup operations done by hasNext() when it detects there are no more Steps to 
	 * execute.
	 * @return Step -- null if there are no more steps.
	 * @see #cleanup()
	 */
	public Step popNext(){
		Step step = peekNext();
		if(step ==null){
			getLog().debug("WDTestRun: There are no more Steps to retrieve. null.");
			return null;
		}
		stepIndex++;
		getLog().debug("WDTestRun: retrieved step "+ (stepIndex + 1));
		return step;
	}
	
	/**
	 * Perform the normal cleanup done at the end of script execution.
	 * Essentially, calls hasNext() with stepIndex indicating all steps have been executed.
	 */
	public void cleanup(){
		getLog().debug("WDTestRun.cleanup invoked after step "+ (stepIndex + 1));
		stepIndex = getScript().steps.size();
		if (!hasNext() && (getDriver()!= null) && (getScript().closeDriver)) {
			getLog().debug("WDTestRun.cleanup closing RemoteWebDriver.");
			try{ WDLibrary.closeBrowser(); }
			catch(Exception x){
				getLog().debug("WDTestRun.cleanup error closing RemoteWebDriver: "+ x.getMessage());
			}
		}
	}
	
	/** @return True if there is another step to execute. */
	public boolean hasNext() {
		return stepIndex < getScript().steps.size() - 1;
	}
		
	/**
	 * Execute the action represented in the Step.
	 * This does NOT change or increment any counters of what Step is being executed 
	 * in the Script.
	 * <p>
	 * If the StepType is an instanceof ClickElement we will use our own WDClickElement 
	 * which uses our enhanced WDLibrary to perform the click.<br>
	 * 
	 * @param step
	 * @return
	 */
	public boolean runStep(Step step){

		initRemoteWebDriver();
		
		try {
			StepType type = step.type;
			if(type instanceof ClickElement){
				type = new WDClickElement();
			}else if(type instanceof Get){
				type = new WDGet();
			} else if(type instanceof SwitchToFrame) {
				type = new WDSwitchToFrame();
			} else if (type instanceof SwitchToFrameByIndex) {
				type = new WDSwitchToFrameByIndex();
			} else if (type instanceof Store){
				String varname = this.string("variable");
				String text = this.string("text");
				WebDriverGUIUtilities._LASTINSTANCE.getSTAFHelper().setVariable(varname, text);
			}
			return type.run(this);
		} catch (Throwable e) {
			
			this.log().debug("WDTestRun.runStep() "+ e.getClass().getName()+": "+ e.getMessage(),e); 
			RuntimeException t = new RuntimeException(currentStep() + " failed.", e);
			t.fillInStackTrace();
			throw t;
		}
	}
	
	/**
	 * Executes the next step.  
	 * Extracts the Step and executes via runStep for enhanced 
	 * handling of some StepTypes.
	 * @return True on success.
	 * @see #runStep(Step)
	 */
	@Override
	public boolean next() {
		if (stepIndex == -1) {
			getLog().debug("Starting test run.");
		}

		initRemoteWebDriver();

		getLog().debug("Running step " + (stepIndex + 2) + ":" +
		getScript().steps.get(stepIndex + 1).type.getClass().getSimpleName() + " step.");
		boolean result = runStep(getScript().steps.get(++stepIndex));
		if (!result) {
			// If a verify failed, we just note this but continue.
			if (currentStep().type instanceof Verify) {
				getLog().error(currentStep() + " failed.");
				return false;
			}
			// In all other cases, we throw an exception to stop the run.
			RuntimeException e = new RuntimeException(currentStep() + " failed.");
			e.fillInStackTrace();
			throw e; // continue?
		} else {
			return true;
		}
	}

	/* (non-Javadoc)
	 * @see com.sebuilder.interpreter.TestRun#getDriver()
	 */
	@Override
	public RemoteWebDriver getDriver() {
		getLog().info("WDTestRun.getDriver() invoked.");
		try{
			WebDriver webDriver = WDLibrary.getWebDriver();
			if(webDriver == null) {
				getLog().debug("WDTestRun.getDriver() is null.");
			}else{
				return (RemoteWebDriver) webDriver;
			}
		}catch(Exception x){
			getLog().debug("WDTestRun.getDriver() "+ x.getMessage());
		}
		return null;
	}

	/* (non-Javadoc)
	 * @see com.sebuilder.interpreter.TestRun#driver()
	 */
	@Override
	public RemoteWebDriver driver() {
		getLog().info("WDTestRun.driver() invoked. Calling getDriver();");
		// watch out for recursive loop!
		return getDriver();
	}

	/* (non-Javadoc)
	 * CANAGL -- this seems to be called prior to EVERY single Step in the script.
	 * So only initialize IF we don't already have one.
	 * @see com.sebuilder.interpreter.TestRun#initRemoteWebDriver()
	 */
	@Override
	public void initRemoteWebDriver() {
		getLog().info("WDTestRun.initRemoteWebDriver invoked.");
		if(getDriver() != null) {
			getLog().info("WDTestRun.initRemoteWebDriver detected session already exists...");
			return;
		}
		int timeout = 30; // TODO get from parameters in this object!
		String browserID = "sebuilder_run"+System.currentTimeMillis();
		try{ WDLibrary.startBrowser(null, null, browserID, timeout, true);}
		catch(Throwable th){			
			try {
				WebDriverGUIUtilities.launchSeleniumServers();
				try{
					WDLibrary.startBrowser(null, null, browserID, timeout, true);
					getLog().info("WDTestRun.initRemoteWebDriver successful starting browser session.");
				}catch(Throwable th2){
					getLog().debug("WDTestRun.initRemoteWebDriver failed to start a new WebDriver session:", th2);
				}
			} catch (SeleniumPlusException e) {
				getLog().info("WDTestRun.initRemoteWebDriver detected the expected RemoteServer is not running and cannot be started: "+StringUtils.debugmsg(e));
			}
		}
	}
	
}
