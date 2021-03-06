/** Copyright (C) (SAS) All rights reserved.
 ** General Public License: http://www.opensource.org/licenses/gpl-license.php
 **/
package org.safs.install;
/** 
 * History:
 * 
 *    JUL 03, 2015	(LeiWang)	Modify prepareTempSourceDirectory(): Move the download and unzip functionality out of this method.
 *                                                                   Call these functionalities in downloader thread directly, wait for unzip done before updateDiretory.
 *    JUL 15, 2015	(LeiWang)	Modify processArgs(): Catch Throwable to avoid infinite loop waiting.
 *    JAN 04, 2016	(LeiWang)	Add '-q' for quiet mode so that no dialog will prompt for confirmation.
 **/
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.nio.channels.FileChannel;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Set;

import javax.swing.JOptionPane;

import org.safs.text.FileUtilities;

/**
 * A generic customizable File directory updater for SAFS users wishing to update certain library assets 
 * with newer assets that might be available on the network in a central location.
 * <p>
 * For example, users can use this to grab centrally located build assets to update the SAFS\lib JAR files.
 * Or, it can be used to update TCAFS libraries located in TCAFS\TCAFS\Script.
 * <p>
 * The support for this is narrowly focused to SAFS assets at this time.
 * <p>
 * Specifically, only the following file types are supported:
 * <p>
 * <ul>
 * <li>.DAT
 * <li>.SVB
 * <li>.JAR
 * <li>.EXE
 * </ul>
 * <p>
 * In addition, only source assets dated newer than Aug 01, 2012 are considered for update.
 * <p>
 * The class supports backing up older files into any specified directory.  
 * It also supports directory recursion, and an option to override the prompting for file replacement.
 * <p>
 * There are no SAFS dependencies in this class and can be packaged stand-alone in an executable JAR file.
 * <p>
 * Command-line options:
 * <p><code><big><ul><table>
 * <tr><td style="vertical-align: top;white-space:nowrap;">-q           <td>(quiet mode, no prompt dialog), it should be put before other argument if present"
 * <tr><td style="vertical-align: top;white-space:nowrap;">"-prompt:titlebar" <td>(alternate prompt title bar message)<br>
 * This parameter should appear before other parameters (at this time) 
 * <tr ><td style="vertical-align: top;white-space:nowrap;">-b:backupdir <td>**(backup dir for replaced files)
 * <tr><td style="vertical-align: top;white-space:nowrap;">-s:sourcedir <td>* (source dir containing newer files, or an HTTP URL to a ZIP)<br>
 * Supports HTTP URL to a ZIP file that would be downloaded and extracted into a temporary directory 
 * to be used as the sourcedir for the update.<br>
 * Note: Any spaces in the URL should be retained as spaces--not URLEncoded.<br>
 * The code will handle URL encoding of spaces.<br>
 * <span  style="white-space:nowrap;">Ex: -s:http://sourceforge.net/projects/safsdev/files/SAFS Updates/SAFS.LIB Updates/SAFS.LIB.UPDATE.LATEST.ZIP</span>
 * <tr><td style="vertical-align: top;white-space:nowrap;">-t:targetdir <td>* (target dir receiving newer files)
 * <tr><td style="vertical-align: top;white-space:nowrap;">-f           <td>(force w/o prompting for each file)
 * <tr><td style="vertical-align: top;white-space:nowrap;">-r           <td>(recurse into sub-directories)
 * <tr><td style="vertical-align: top;white-space:nowrap;">-a           <td>(file of all types will be copy, default false)"
 * <tr><td style="vertical-align: top;white-space:nowrap;">-nob         <td>(no file backups required)
 * <tr><td> &nbsp;&nbsp; <td> &nbsp;&nbsp;   
 * <tr><td><td>*  - required parameter.
 * <tr><td><td>** - only required if -nob not provided.
 * </table></big></code></ul>
 * <p>
 * Entry points are:
 * <ul>
 * <li>Instance: {@link #processArgs(String[]) processArgs}
 * <li>  Static: {@link #main(String[]) main}
 * </ul>
 * @author canagl Original Draft SEP 18 2013
 *
 */
public class LibraryUpdate {

	public static final String _VERSION_ = "1.0";
	
	public static final String ARG_PREFIX_B = "-b:";
	public static final String ARG_PREFIX_NOB = "-nob";
	public static final String ARG_PREFIX_P = "-prompt:";
	public static final String ARG_PREFIX_T = "-t:";
	public static final String ARG_PREFIX_S = "-s:";
	public static final String ARG_PREFIX_R = "-r";
	public static final String ARG_PREFIX_F = "-f";
	public static final String ARG_PREFIX_A = "-a";
	public static final String ARG_PREFIX_Q = "-q";
    
    // do not attempt to copy/replace files older than this date
    public static final long OLDEST_DATE = Date.parse("01 Aug 2012");
    
	PrintStream console = System.out;
	PrintStream errors = System.err;
	
	String prompt = "SAFS Library Update "+ _VERSION_;
	boolean recurse = false;
	boolean force = false;
	/** quiet mode, if true then no dialog will prompt for confirmation, default is  'false'.*/
	boolean quiet = false;
	boolean dobackup = true;
	boolean allfilecopy = false;
	/** Gets set TRUE if the user cancelled the update from a dialog prompt. */
	public boolean canceled = false;
	public String canceledMSG = null;
	
	File targetdir = null;
	File sourcedir = null;
	File backupdir = null;
	int modifiedFiles = 0;
	int backupFiles = 0;
	
	/**
	 * If we need to download zip file from Internet and unzip it in a separate thread,<br>
	 * we need to set this field to false in the main thread. In the separate thread, when<br>
	 * unzip finishes, this field will be set to true. In the main thread, we can wait this<br>
	 * field becomes true before processing some actions like {@link #updateDirectory(File, File, File)}.<br>
	 * 
	 * @see #updateDirectory(File, File, File)
	 */
	boolean unzipDone = true;
	/**
	 * If we need to download file from Internet in a separate thread,<br>
	 * we need to set this field to false in the main thread.<br>
	 * 
	 * @see #processArgs(String[])
	 */
	boolean downloadDone = false;
	final ProgressIndicator progressor = new ProgressIndicator();	
    int progress = 0;
	
	/**
	 * Default constructor using standard System.out and System.err console streams.
	 */
	public LibraryUpdate(){
		this(System.out, System.err);
	}
	
	/**
	 * Alternate constructor provide different console and error output streams.
	 */
	public LibraryUpdate(PrintStream console, PrintStream errors){ 
		this.console = console;
		this.errors = errors;
		init();
	}
	
	protected void init(){
	    javax.swing.SwingUtilities.invokeLater(new Runnable() {
	        public void run() {
	        	progressor.createAndShowGUI();
	        }
	    });
	}
	
	/**
	 * Main entry point for an instance of the class not executing from the command-line.
	 * @param args command-line args
	 * @return true if the args processed OK and the updateDirectory process completed successfully.
	 * returns false if the user canceled the update or an error occurred in processing the args or 
	 * performing the update.  
	 * @throws Exception
	 */
	public boolean processArgs(String[] args)throws Exception{
		modifiedFiles = 0;
		backupFiles = 0;
		canceledMSG = null;
		String message = null;
		for(String arg: args){
			
			if(arg.startsWith(ARG_PREFIX_T)){
				targetdir = new File(arg.substring(ARG_PREFIX_T.length()));
				if(! targetdir.canWrite()) throw new Exception("Specified target directory '"+ targetdir.getAbsolutePath() +"' cannot be written.");
				progressor.setProgressInfo(progress+=10, "Using Target Directory: "+ targetdir);
			}
			else if(arg.startsWith(ARG_PREFIX_S)){
				String sarg = arg.substring(ARG_PREFIX_S.length());
				final String encodedURL = sarg.replaceAll(" ", "%20");
				if(sarg.toLowerCase().startsWith("http")){

					progressor.setProgressInfo(progress+=10, "Evaluating URL Source: "+ sarg);
					
					if(!quiet){
						message = "Preparing to download a library update.\n\n"
								+ "Source: "+ sarg +"\n"
								+ "\nThis may take a few minutes.\n"
								+ "\nDo you wish to proceed?\n";
						Object[] options = {
								"Proceed with Download", 
								"Exit without Download"
						};
						int selected = JOptionPane.showOptionDialog(null, 
								message, 
								prompt, 
								JOptionPane.YES_NO_OPTION,
								JOptionPane.QUESTION_MESSAGE,
								null,
								options,
								options[1]);
						if(selected == JOptionPane.CLOSED_OPTION || selected ==1){
							progressor.setProgressMessage("Download cancelled by User.");
							canceled = true;
							return false;
						}
					}
					
					Thread downloader = new Thread(new Runnable(){
						public void run(){
							File zipfile = null;
							String zipfileFullPath = null;
							try{
								sourcedir = prepareTempSourceDirectory();
								//Download zip file
								zipfile = new File(sourcedir, "tempzipfile");
								downloadURL(encodedURL, zipfile);
								//Unzip the downloaded zip file, it takes time
								zipfileFullPath = zipfile.getAbsolutePath();
								progressor.setProgressMessage("Unzipping '"+zipfileFullPath+"' to '"+sourcedir+"'...");
								FileUtilities.unzipFile(zipfileFullPath, sourcedir, false);
								zipfile.delete();
								unzipDone = true;
							}catch(MalformedURLException mf){
								errors.println("Invalid Download URL "+ encodedURL);
								errors.println(mf.getMessage());
								canceled = true;
								canceledMSG = mf.getMessage();
							}catch(FileNotFoundException nf){
								errors.println("Invalid storage location for URL '"+ encodedURL+"' or for ZIP FILE '"+zipfileFullPath+"'");
								errors.println(nf.getMessage());
								canceled = true;
								canceledMSG = nf.getMessage();
							}catch(IOException io){
								errors.println("Error in processing for URL '"+ encodedURL+"' or for ZIP FILE '"+zipfileFullPath+"'");
								errors.println(io.getClass().getName()+": "+io.getMessage());
								canceled = true;
								canceledMSG = io.getMessage();
							}catch(Throwable e){
								errors.println("During processing of URL '"+ encodedURL+"' or ZIP FILE '"+zipfileFullPath+"', Met ");
								errors.println(e.getClass().getName()+": "+e.getMessage());
								canceled = true;
								canceledMSG = e.getMessage();
							}
						}
					});
					unzipDone = false;
					downloadDone = false;
					canceled = false;
					downloader.start();
					progressor.setProgressInfo(progress+=10, "Download started and can take several minutes...");
					while(!canceled && !downloadDone){
						try{Thread.sleep(1000);}catch(Exception x){}
					}
					progress += 10;
					if(canceled){
						message = "Download of update content was not successful.";
						progressor.setProgressInfo(progress,message);
						message = "\n"+ message +"\n";
						if(canceledMSG != null) {
							canceledMSG = "Update was canceled due to "+canceledMSG;
							progressor.setProgressMessage(canceledMSG);
							message+= "\n"+ canceledMSG +"\n";
						}
						if(!quiet) JOptionPane.showMessageDialog(null, message, prompt, JOptionPane.ERROR_MESSAGE);
						return false;
					}
					progressor.setProgressInfo(progress, "Download complete. Evaluating content...");
					
					//Before updating files from sourcedir to targetdir, we DO need to wait
					//unzip finished in the sourcedir.
					//If some Exception happened during unzip, the field canceled will be set to true.
					if(!unzipDone){
						progressor.setProgressMessage("Waiting for files unzipping done ...");
						while(!canceled && !unzipDone){ 
							try{Thread.sleep(1000);}catch(Exception x){}
						}
					}
					progress = 70;
					if(canceled){
						message = "Unzip of update content was not successful.";
						progressor.setProgressInfo(progress,message);
						message = "\n"+ message +"\n";
						if(canceledMSG != null) {
							canceledMSG = "Update was canceled due to "+canceledMSG;
							progressor.setProgressMessage(canceledMSG);
							message+= "\n"+ canceledMSG +"\n";
						}
						if(!quiet) JOptionPane.showMessageDialog(null, message, prompt, JOptionPane.ERROR_MESSAGE);
						return false;
					}
					progressor.setProgressInfo(progress, "Unzipping is done");

					//We don't need to verify the validation of sourcedir, we have done it in prepareTempSourceDirectory().
//					if(sourcedir == null || !sourcedir.isDirectory()){
//						message = "Reference to temporary download directory is not valid.";
//						progressor.setProgressMessage(message);
//						JOptionPane.showMessageDialog(null, "\n"+message+"\n", prompt, JOptionPane.ERROR_MESSAGE);
//						return false;
//					}
				}else{
					sourcedir = new File(sarg);
				}
				if(! sourcedir.canRead()) {
					message = "Specified source directory '"+ sourcedir.getAbsolutePath() +"' cannot be read.";
					throw new Exception(message);
				}
			}
			else if(arg.startsWith(ARG_PREFIX_P)){
				prompt = arg.substring(ARG_PREFIX_P.length());
				progressor.setTitle(prompt);
			}
			else if(arg.startsWith(ARG_PREFIX_B)){
				backupdir = new File(arg.substring(ARG_PREFIX_B.length()));
				if(!backupdir.exists()){
					try{ backupdir.mkdir(); }catch(Exception ignore){}
				}
				if(! backupdir.canWrite()) throw new Exception("Specified backup directory '"+ backupdir.getAbsolutePath() +"' cannot be written.");
				progressor.setProgressInfo(progress+=10, "Using Backup Directory: "+ backupdir);
			}
			else if(arg.startsWith(ARG_PREFIX_F)){
				force = true;
				progressor.setProgressInfo(progress+=10, "Force Overwrite set: "+ force);
			}
			else if(arg.startsWith(ARG_PREFIX_R)){
				recurse = true;
				progressor.setProgressInfo(progress+=10, "Recurse Directories set: "+ recurse);
			}
			else if(arg.startsWith(ARG_PREFIX_NOB)){
				dobackup = false;
				progressor.setProgressInfo(progress+=10, "Do Backup set: "+ dobackup);
			}
			else if(arg.startsWith(ARG_PREFIX_A)){
				allfilecopy = true;
				progressor.setProgressInfo(progress+=10, "All files type copy: "+ allfilecopy);
			}
			else if(arg.startsWith(ARG_PREFIX_Q)){
				quiet = true;
				progressor.setProgressInfo(progress+=1, "Quiet Mode: "+ quiet);
			}
		}
		progressor.setProgressInfo(progress+=10, "Preparing to perform the update...");
		if ((targetdir != null && targetdir.canWrite()) && 
		   (sourcedir != null && sourcedir.canRead())  && 
		   (!dobackup || (backupdir != null && backupdir.canWrite()))){
			
			if(!quiet){
				message = "Preparing to perform a library update.\n\n"
						+ "Source: "+ sourcedir +"\n"
						+ "Target: "+ targetdir +"\n"
						+ "Recurse: "+ recurse +"\n"
						+ "Backup: "+ dobackup +"\n";
				if(dobackup) message += "Backups: "+ backupdir +"\n";
				message += "\nDo you wish to proceed?\n";
				
				Object[] options = {
						"Proceed with Update", 
						"Exit without Update"
				};
				int selected = JOptionPane.showOptionDialog(null, 
						message, 
						prompt, 
						JOptionPane.YES_NO_OPTION,
						JOptionPane.QUESTION_MESSAGE,
						null,
						options,
						options[1]);
				if(selected == JOptionPane.CLOSED_OPTION || selected ==1){
					progressor.setProgressMessage("User has cancelled the update.");
					canceled = true;
					return false;
				}
			}
			
			progressor.setProgressMessage("Updating...");
			updateDirectory(targetdir, sourcedir, backupdir);
			progressor.setProgressMessage("Update complete.");
			return !canceled;
		}
		return false;
	}

	public static final String help_string = "\n" 
			   + "SAFS LibraryUpdate "+ _VERSION_ +"\n" 
			   + "\n"
			   + "Command-line arguments: \n"
			   + "\n"
			   + "   -q                (quiet mode, no prompt dialog), it should be put before other argument if present\n"
			   + "   -b:backupdir    **(backup dir for replaced files)\n"
			   + "   -s:sourcedir    * (source dir containing newer files)\n"
			   + "   -t:targetdir    * (target dir receiving newer files)\n"
			   + "  \"-prompt:titlebar\" (alternate prompt titlebar msg)\n"
			   + "   -f                (force w/o prompting for each file)\n"
			   + "   -r                (recurse into subdirectories)\n"
			   + "   -nob              (no file backups required)\n\n"
			   + "   -a				   (file of all types will be copy, default false)\n\n"
			   + "*  - required parameter.\n"
			   + "** - only required if -nob not provided.\n\n";
	
	static void help(PrintStream out){
		out.print(help_string);
	}

	static final String LC_JAR = ".jar";
	static final String LC_SVB = ".svb";
	static final String LC_DAT = ".dat";
	static final String LC_EXE = ".exe";
	static final String LC_TSCRIPT = ".tscript";
	
	/**
	 * Include only .JAR, .SVB, and .DAT Files newer than OLDEST_DATE
	 * @param file
	 * @return true if file meets criteria
	 */
	boolean fileIncluded(File file){
		if(file.getName().length()==0) return false;
		String lcname = file.getName().trim().toLowerCase();
		return ((lcname.endsWith(LC_JAR) || 
		         lcname.endsWith(LC_SVB) ||	
		         lcname.endsWith(LC_EXE) ||	
		         lcname.endsWith(LC_DAT)) &&
		        (file.lastModified() > OLDEST_DATE));
	}
	
	/**
	 * Include all Files newer than OLDEST_DATE
	 * @param file
	 * @return true if file meets criteria
	 */
	boolean allFileIncluded(File file){
		if(file.getName().length()==0) return false;
		return ((file.lastModified() > OLDEST_DATE));
	}

	// storage in-case we have to back out changes
	// key = targetfile, value = backupfile
	HashMap backups = new HashMap();
	
	/**
	 * Uses copyFile to backup a targetfile to a backup directory.
	 * Retains a storage reference to every file backed up in the event 
	 * that an attempt is made to roll-back the update following some type of update failure.
	 * @param targetfile
	 * @param backupdir
	 * @throws Exception
	 */
	void backupFile(File targetfile, File backupdir) throws Exception{
		File backupfile = new File(backupdir, targetfile.getName());
		copyFile(targetfile, backupfile);
		backups.put(targetfile, backupfile);
		backupFiles++;
	}
	
	/**
	 * Copy a sourcefile to a targetfile location.
	 * Will delete an existing file if it already exists before creating the replacement file.
	 * @param sourcefile
	 * @param targetfile
	 * @throws Exception
	 */
	void copyFile(File sourcefile, File targetfile) throws Exception {
		if(targetfile.exists()) targetfile.delete();
		targetfile.createNewFile();
		FileChannel source = null;
		FileChannel target = null;
		try{
			source = new FileInputStream(sourcefile).getChannel();
			target = new FileOutputStream(targetfile).getChannel();
			target.transferFrom(source, 0, source.size());
		}
		finally{
			if(source!=null) source.close();
			if(target!=null) target.close();
		}
		targetfile.setLastModified(sourcefile.lastModified());
	}
	
	/**
	 * Re-entrant routine to process the provided directories.  If recurse is true, 
	 * the routine will call itself with each new source subdirectory encountered.
	 * @param targetdir
	 * @param sourcedir
	 * @param backupdir
	 * @throws Exception
	 */
	void updateDirectory(File targetdir, File sourcedir, File backupdir) throws Exception{
		ArrayList<File> sourcesubs = new ArrayList<File>();
		File[] sourcefiles = sourcedir.listFiles();
		File targetfile = null;
		String sourceinfo = null;
		String targetinfo = null;
		String promptMessage = "";
		Object[] promptOptions = {
			"Yes",
			"Yes to All",
			"Cancel"
		};
		
		SimpleDateFormat date = new SimpleDateFormat("MM/dd/yyyy hh:mm a");
		try{
			for(File file:sourcefiles){
				if(file.isDirectory()){
					if(recurse) sourcesubs.add(file);
					continue;
				}
				
				if (allfilecopy){
					if (! allFileIncluded(file)) continue;
				} else {
					if(! fileIncluded(file)) continue;
				}
				
				targetfile = new File(targetdir, file.getName());
				if(targetfile.exists()){
					if(targetfile.lastModified() >= file.lastModified()) continue;
				}
				if(!force && !quiet){
					sourceinfo = "Source: "+ file.getAbsolutePath()+ "\n"
							   + "TimeStamp: "+ date.format(new Date(file.lastModified()));
					if (file.lastModified() > targetfile.lastModified())
						sourceinfo += " (newer)";
					sourceinfo += "\nSize: "+ file.length();
					if( file.length() > targetfile.length())
						sourceinfo += " (larger)";
					
					targetinfo = "Target: "+ targetfile.getAbsolutePath()+ "\n"
							   + "TimeStamp: "+ date.format(new Date(targetfile.lastModified()));
					if (targetfile.lastModified() > file.lastModified())
						targetinfo += " (newer)";
					targetinfo += "\nSize: "+ targetfile.length();
					if( targetfile.length() > file.length())
						targetinfo += " (larger)";
					
					promptMessage = "\n Preparing to ";
					if(targetfile.exists() && dobackup)
						promptMessage += "Backup and ";
					promptMessage += "Copy/Replace:\n\n"
							       + sourceinfo
							       + "\n\n"
							       + targetinfo
							       + "\nDo you wish to proceed?\n\n";
					
					int selected = JOptionPane.showOptionDialog(null, 
							promptMessage, 
							prompt, 
							JOptionPane.YES_NO_CANCEL_OPTION,
							JOptionPane.QUESTION_MESSAGE,
							null,
							promptOptions,
							promptOptions[0]);
					if(selected == JOptionPane.CLOSED_OPTION || selected == 2){
						canceled = true;
						return;
					}
					if(selected == 1) force = true;
				}
				if( targetfile.exists() && dobackup) backupFile(targetfile, backupdir);				
				copyFile(file, targetfile);
				modifiedFiles++;
			}
			if (recurse){
				Iterator it = sourcesubs.iterator();
				while(it.hasNext()){
					File newsourcedir = (File)it.next();
					File newtargetdir = new File(targetdir, newsourcedir.getName());
					if(!newtargetdir.isDirectory()) newtargetdir.mkdir();
					File newbackupdir = null;
					if(dobackup) {
						newbackupdir = new File(backupdir, newsourcedir.getName());
						if(!newbackupdir.isDirectory()) newbackupdir.mkdir();
					}
					updateDirectory(newtargetdir, newsourcedir, newbackupdir);
				}
			}
		}catch(Exception x){
			// restore any backups, if we can!
			errors.println("Failed to update directory "+ targetdir.getAbsolutePath());
			errors.println("   due to "+ x.getClass().getSimpleName()+": "+x.getMessage());
			if(backups.size() > 0) {
				errors.println("Attempting to restore from backups at "+ backupdir.getAbsolutePath());
			}
			Set keys = backups.keySet();
			File target = null;
			File backup = null;
			for(Object key: keys){
				try{
					target = (File) key;
					backup = (File) backups.get(key);
					copyFile(backup, target);
				}catch(IOException y){
					errors.println("Unable to restore backup file "+ backup.getAbsolutePath()+" to "+ target.getAbsolutePath());
				}
			}
			throw x;
		}
	}
	
	/** Prepare temporary directory for holding files downloaded from Internet.*/
	File prepareTempSourceDirectory() throws FileNotFoundException, IOException{
		File tempfile = null;
		File tempdir = null;
		tempfile = File.createTempFile("temp", "Delete");
		tempdir = tempfile.getParentFile();
		tempfile.delete();
		tempdir = new File(tempdir, "SAFS_UPDATE");
		tempdir.mkdir();
		
		File[] files = tempdir.listFiles();
		if(files != null && files.length > 0){
			for(File file:files){
				if(file.isDirectory()) {
					try{ 
						FileUtilities.deleteDirectoryRecursively(file.getAbsolutePath(), false);
					}catch(Throwable x){
						// java.lang.NoClassDefFoundError
						errors.println(x.getClass().getName()+": "+x.getMessage());
					}
				}else{
					try{
						file.delete();
					}catch(Throwable x){
						errors.println(x.getClass().getName()+": "+x.getMessage());
					}
				}
			}
		}
		if(tempdir==null || !tempdir.isDirectory()) throw new IOException("Cannot deduce a root source directory for HTTP extraction.");
		return tempdir;
	}
	
	void downloadURL(String url, File outfile) throws MalformedURLException, IOException{
		InputStream in = null;
		BufferedOutputStream fout = null;
		HttpURLConnection con = null;
		downloadDone = false;
		try {
			con = (HttpURLConnection) URI.create(url).toURL().openConnection();
			int response = con.getResponseCode();
			if(response != con.HTTP_OK)
				throw new MalformedURLException("Bad Server Response ("+ response +") for "+ url);
			long conlength = con.getContentLengthLong();
			if(conlength < 1024)
				throw new MalformedURLException("Suspect content length ("+ conlength +") for "+ url);
			//System.out.println("Response: "+ response);
			//System.out.println("Length: "+ conlength);
			in = con.getInputStream();
			fout = new BufferedOutputStream(new FileOutputStream(outfile), 1000 * 1024);
			byte data[] = new byte[1000 * 1024];
			int count;
			while((count = in.read(data))!= -1){
				fout.write(data, 0, count);
			}
			fout.flush();
			downloadDone = true;
		} catch (MalformedURLException e) {
			errors.println("Unable to retrieve bad URL "+ url +" due to "+ e.getClass().getName()+": "+e.getMessage());
			throw e;
		} catch (IOException e) {
			errors.println("Unable to open URL InputStream for "+ url +" due to "+ e.getClass().getName()+": "+e.getMessage());
			throw e;
		}
		finally{
			if(con != null)try{ con.disconnect();}catch(Exception ignore){}
			if(in != null)try{ in.close();}catch(Exception ignore){}
			if(fout != null)try{ fout.close();}catch(Exception ignore){}
		}
	}

	public boolean isQuiet(){
		return quiet;
	}
	
	/**
	 * The main entry point for the application when invoked via the command-line.
	 * @param args 
	 * Command-line options:
	 * <p><code><big><ul><table>
	 * <tr><td style="vertical-align: top;white-space:nowrap;">-q           <td>(quiet mode, no prompt dialog), it should be put before other argument if present"
	 * <tr><td style="vertical-align: top;white-space:nowrap;">"-prompt:titlebar" <td>(alternate prompt title bar message)<br>
	 * This parameter should appear before other parameters (at this time) 
	 * <tr ><td style="vertical-align: top;white-space:nowrap;">-b:backupdir <td>**(backup dir for replaced files)
	 * <tr><td style="vertical-align: top;white-space:nowrap;">-s:sourcedir <td>* (source dir containing newer files, or an HTTP URL to a ZIP)<br>
	 * Supports HTTP URL to a ZIP file that would be downloaded and extracted into a temporary directory 
	 * to be used as the sourcedir for the update.<br>
	 * Note: Any spaces in the URL should be retained as spaces--not URLEncoded.<br>
	 * The code will handle URL encoding of spaces.<br>
	 * <span  style="white-space:nowrap;">Ex: -s:http://sourceforge.net/projects/safsdev/files/SAFS Updates/SAFS.LIB Updates/SAFS.LIB.UPDATE.LATEST.ZIP</span>
	 * <tr><td style="vertical-align: top;white-space:nowrap;">-t:targetdir <td>* (target dir receiving newer files)
	 * <tr><td style="vertical-align: top;white-space:nowrap;">-f           <td>(force w/o prompting for each file)
	 * <tr><td style="vertical-align: top;white-space:nowrap;">-r           <td>(recurse into sub-directories)
	 * <tr><td style="vertical-align: top;white-space:nowrap;">-a           <td>(file of all types will be copy, default false)"
	 * <tr><td style="vertical-align: top;white-space:nowrap;">-nob         <td>(no file backups required)
	 * <tr><td> &nbsp;&nbsp; <td> &nbsp;&nbsp;   
	 * <tr><td><td>*  - required parameter.
	 * <tr><td><td>** - only required if -nob not provided.
	 * </table></big></code></ul>
	 * @return exitcode<br> 
	 * -1 - user cancelled the request.<br>
	 * -2 - an error occurred during processing<br>
	 *  0 - +N The number of files that were modified.
	 */
	public static void main(String[] args) {	
		LibraryUpdate updater = null;
		int exitcode = 0;
		try{
			updater = new LibraryUpdate();
			//sufficent valid args provided
			if(!updater.processArgs(args)){
				if(! updater.canceled){
					String message = "Please provide all *required* parameters.";
					updater.console.println(message);
					help(updater.console);
					if(!updater.isQuiet()) JOptionPane.showMessageDialog(null, message +"\n"+ updater.help_string, updater.prompt, JOptionPane.WARNING_MESSAGE);
					exitcode = -1;
				}else{
					String message = "\nNo Download or Update will be attempted.";
					updater.console.println(message);
					if(!updater.isQuiet()) JOptionPane.showMessageDialog(null, message, updater.prompt, JOptionPane.INFORMATION_MESSAGE);
					exitcode = -1;
				}
			}else{
				String message = "Modified "+ updater.modifiedFiles +", creating "+ updater.backupFiles +" backups.";
				updater.progressor.setProgressInfo(100,message);
				if(!updater.isQuiet())  JOptionPane.showMessageDialog(null, message, updater.prompt, JOptionPane.INFORMATION_MESSAGE);
				exitcode = updater.modifiedFiles;
			}
		}catch(Exception x){
			updater.errors.println(x.getMessage());
			help(updater.errors);
			updater.progressor.setProgressInfo(100,x.getMessage());
			if(!updater.isQuiet())  JOptionPane.showMessageDialog(null, x.getMessage()+"\n"+ updater.help_string, updater.prompt, JOptionPane.ERROR_MESSAGE);
			exitcode = -2;
		}
		try{ 
			String mainclass = org.safs.tools.MainClass.deduceMainClass();
			if( LibraryUpdate.class.getName().equals(mainclass) ||
			    mainclass.endsWith("safsupdate.jar")	)
			     System.exit(exitcode);
		}catch(Throwable ignore){}
	}
}
