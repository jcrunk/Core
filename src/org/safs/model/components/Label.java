
/******************************************************************************
 * Label.java
 *
 * Copyright (c) by SAS Institute Inc., Cary, NC 27513
 * General Public License: http://www.opensource.org/licenses/gpl-license.php
 *
 * !!! DO NOT EDIT THIS FILE !!!
 * This file is automatically generated from XML source.  Any changes you make 
 * here will be erased the next time the file is generated.
 *
 * The following assets are needed to generate this file:
 *
 *   LabelFunctions.xml
 *   keyword_library.dtd
 *   XSLJavaCommonFunctions.xsl
 *   XSLJavaComponentModel.xsl
 *
 * Example invocation to generate:
 *
 *   msxsl.exe LabelFunctions.xml XSLJavaComponentModel.xsl -o Label.java
 *
 ******************************************************************************/ 
package org.safs.model.components;

import org.safs.model.commands.LabelFunctions;
import org.safs.model.ComponentFunction;
import org.safs.model.components.UIComponent;
import org.safs.model.StepTestTable;

public class Label extends GenericObject {

    /*****************
    Constructor 

    Create an instance of pseudo-component representing 
    a specific component in a specific window.
    
    @param window  Optional:NO 
           Specifies which Window this component is 'in'.
    @param compname Optional:NO 
           Specifies the AppMap name of the component in the Window.
    ****************/
    public Label(Window window, String compname) {

        super(window, compname);
    }

    /*****************
    Constructor 

    Create an instance of pseudo-component representing 
    a specific component in a specific window.
    
    This convenience routine will create the requisite Window component.
    
    @param winname  Optional:NO 
           Specifies the AppMap name of the window.
    @param compname Optional:NO 
           Specifies the AppMap name of the component in the Window.
    ****************/
    public Label(String winname, String compname) {

        this(new Window(winname), compname);
    }

    protected Label(String compname) {

        super(compname);
    }



    /*********** <pre> 
                 Attempts to perform a standard Click on the Label
               
                Attempts to perform a standard Click on the Object.  
                  Attempts to click on the Label that is indicated in the map file by the 
                  Step command. This should provide for a click on any type of Label. 
                  </pre>    Supporting Engines:
    <P/><UL>
    <LI>Mercury Interactive WinRunner</LI>
    </UL>

     **********/
    public ComponentFunction click( ) {

        return LabelFunctions.click(getWindow().getName(), getName());
    }

    /*********** <pre> 
                 Attempts to perform a standard Click on the Label
               
                Attempts to perform a standard Click on the Object.  
                  Attempts to click on the Label that is indicated in the map file by the 
                  Step command. This should provide for a click on any type of Label. 
                  </pre>    Supporting Engines:
    <P/><UL>
    <LI>Mercury Interactive WinRunner</LI>
    </UL>

     @param table  Optional:NO
            The table to add the record to.
     **********/
    public void click(StepTestTable table ) {

        if ( table == null ) throw new IllegalArgumentException ( "click.table = null");

        table.add( LabelFunctions.click(getWindow().getName(), getName()));
    }

}
