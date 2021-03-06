/** 
 * Copyright (C) SAS Institute, All rights reserved.
 * General Public License: http://www.opensource.org/licenses/gpl-license.php
 */

/**
 * Logs for developers, not published to API DOC.
 *
 * History:
 * MAY 12, 2016    (SBJLWA) Initial release.
 */
package org.safs.tools;

import org.safs.TestRecordData;

/**
 * <p>
 * In the driver/engine class (like JSAFSDriver TIDDriverFlowCommand), there is a shared class field
 * 'Test Record'. When executing a keyword, as only ONE instance of driver/engine is used, the class field
 * 'Test Record' will be replaced by a new one. There is no problem for the sequential keyword execution,
 * for example, like 'LogMessage' 'Expressions', there is no overlap; But for the reentrant keyword like
 * 'CallJUnit', there is a problem, if a JUnit-test is invoked by 'CallJUnit' and inside a JUnit-test 
 * the keyword 'LogMessage' (even 'CallJUnit') is attempted, then the class field 'Test Record' (for 'CallJUnit')
 * will be overwritten by that of 'LogMessage', after execution of 'LogMessage', we come back to the execution 
 * of 'CallJUnit', but class field 'Test Record' has been changed, we need to get it back. A FILO (Stack) is used
 * to store the class field 'Test Record', and try to retrieve the correct one from it.
 * <br>
 * The Stack to hold the 'test records' being processed. Such as
 * <pre>
 *   |  'LogMessage'  |
 *   |__'CallJUnit'___|
 * </pre> 
 *
 * This interface defines 2 methods {@link #pushTestRecord(TestRecordData)} and {@link #popTestRecord()}, and they
 * are used to push 'Test Record' before keyword execution and to pop 'Test Record' after the execution.
 * 
 * @author sbjlwa
 *
 */
public interface ITestRecordStackable {
	/** 
	 * Push the 'test record' into a FILO structure to cache it.
	 * This should be called after the 'Test Record' has been initialized and before execution of a keyword.
	 */
	public void pushTestRecord(TestRecordData trd);
	/** 
	 * Pop the 'test record' from the FILO structure, and this 'Test Record' should
	 * be used to replace the class field in the driver/engine class.
	 * This should be called after execution of a keyword.
	 */
	public TestRecordData popTestRecord();
}