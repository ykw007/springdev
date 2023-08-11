package com.example.demo;

import java.io.File;
import java.io.IOException;

import org.apache.commons.io.monitor.FileAlterationListener;
import org.apache.commons.io.monitor.FileAlterationListenerAdaptor;
import org.apache.commons.io.monitor.FileAlterationMonitor;
import org.apache.commons.io.monitor.FileAlterationObserver;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
 
@SpringBootApplication
public class DemoApplication {

	public static void main(String[] args) {
		SpringApplication.run(DemoApplication.class, args);
		
		
    	// A hardcoded path to a folder you are monitoring .
    	String FOLDER = 	        "\\\\TEST";


    	    // The monitor will perform polling on the folder every 5 seconds
    	    final long pollingInterval = 5 * 1000;


    	    File folder = new File(FOLDER);

    	    if (!folder.exists()) {
    	        // Test to see if monitored folder exists
    	        throw new RuntimeException("Directory not found: " + FOLDER);
    	    }

    	    FileAlterationObserver observer = new FileAlterationObserver(folder);
    	    FileAlterationMonitor monitor =
    	            new FileAlterationMonitor(pollingInterval);
    	    FileAlterationListener listener = new FileAlterationListenerAdaptor() {
    	        // Is triggered when a file is created in the monitored folder
    	        @Override
    	        public void onFileCreate(File file) {
    	            try {
    	                // "file" is the reference to the newly created file
    	                System.out.println("File created: "
    	                        + file.getCanonicalPath());



    	                if(file.getName().endsWith(".docx")){
    	                    System.out.println("Uploaded resource is of type docx, preparing solr for indexing.");
    	                }


    	            } catch (IOException e) {
    	                e.printStackTrace(System.err);
    	            }
    	        }

    	        @Override
    	        public void onFileChange(File file) {
    	            try {
    	                // "file" is the reference to the removed file
    	                System.out.println("File changed: "
    	                        + file.getCanonicalPath());
    	                // "file" does not exists anymore in the location
    	                System.out.println("File still exists in location: "
    	                        + file.exists());
    	            } catch (IOException e) {
    	                e.printStackTrace(System.err);
    	            }
    	        }
    	        
    	        // Is triggered when a file is deleted from the monitored folder
    	        @Override
    	        public void onFileDelete(File file) {
    	            try {
    	                // "file" is the reference to the removed file
    	                System.out.println("File removed: "
    	                        + file.getCanonicalPath());
    	                // "file" does not exists anymore in the location
    	                System.out.println("File still exists in location: "
    	                        + file.exists());
    	            } catch (IOException e) {
    	                e.printStackTrace(System.err);
    	            }
    	        }
    	    };

    	    observer.addListener(listener);
    	    monitor.addObserver(observer);
    	    System.out.println("Starting monitor service");
    	    try {
				monitor.start();
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

	}

}
