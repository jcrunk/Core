/** Copyright (C) (SAS) All rights reserved.
 ** General Public License: http://www.opensource.org/licenses/gpl-license.php
 **/
package org.safs.model;

/**
 * Simple extension to handle Engine Commands of record type "E" 
 */
public class EngineCommand extends Command {

   /**
    * Create a Engine Command instance using the provided command name.
    * This is used by the org.safs.model.commands classes and is not typically 
    * used by the user unless they are building a custom Engine Command.
    * 
    * @param commandName -- cannot be null or zero-length
    * @throws IllegalArgumentException for null or zero-length command names
    */
   public EngineCommand(String commandName) {
      super(commandName, ENGINE_COMMAND_RECORD_TYPE);
   }
   
   private boolean _warningOK;
   private boolean _failureOK;
   
   /**
    * Indicates if a warning is acceptable for this EngineCommand.
    * <p>
    * @return <code>true</code> if a warning is acceptable, otherwise <code>false</code>
    * @see #setWarningOK(boolean)
    */
   public final boolean isWarningOK() {
      return _warningOK;
   }
   
   /**
    * Sets whether a warning is acceptable for this EngineCommand.
    * <p>
    * If a warning is set as acceptable, <code>setFailureOK(false)</code>
    * is called to turn off failures.
    * <p>
    * @param newValue <code>true</code> to indicate that warnings are acceptable, otherwise <code>false</code>
    * @see #isWarningOK()
    */
   public final void setWarningOK(boolean newValue) {
      if (isWarningOK() == newValue)
         return;
      _warningOK = newValue;
      if (newValue)
         setFailureOK(false);
   }
   
   /**
    * Indicates if a failure is acceptable for this EngineCommand.
    * <p>
    * @return <code>true</code> if a failure is acceptable, otherwise <code>false</code>
    * @see #setFailureOK(boolean)
    */
   public final boolean isFailureOK() {
      return _failureOK;
   }
   
   /**
    * Sets whether a failure is acceptable for this EngineCommand.
    * <p>
    * If a failure is set as acceptable, <code>setWarningOK(false)</code>
    * is called to turn off warnings.
    * <p>
    * @param newValue <code>true</code> to indicate that failures are acceptable, otherwise <code>false</code>
    * @see #isFailureOK()
    */
   public final void setFailureOK(boolean newValue) {
      if (isFailureOK() == newValue)
         return;
      _failureOK = newValue;
      if (newValue)
         setWarningOK(false);
   }

   /**
    * Returns the string record type for this command.  The set of values for EngineCommand
    * commands are:
    * <ul>
    * <li>"EF" - if <code>failureOK</code> is <code>true</code>
    * <li>"EW" - if <code>warningOK</code> is <code>true</code>
    * <li>"E" - if <code>failureOK</code> and <code>warningOK</code> are <code>false</code>
    * </ul>
    * <p>
    * @return the record type of this command
    */
   public String getTestRecordID() {
      if (isFailureOK())
         return ENGINE_COMMAND_FAILOK_RECORD_TYPE;
      if (isWarningOK())
         return ENGINE_COMMAND_WARNOK_RECORD_TYPE;
      return super.getTestRecordID();
   }
}