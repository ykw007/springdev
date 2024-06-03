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
	}

}
/*
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

public class FtpDownloadWithTimeoutExample {
    public static void main(String[] args) {
        String server = "ftp.example.com";
        int port = 21;
        String username = "your-username";
        String password = "your-password";
        String remoteFilePath = "/path/to/remote/file.txt";
        String localFilePath = "local-file.txt";

        try (FTPClient ftpClient = new FTPClient()) {
            // Set connection timeout (in milliseconds)
            ftpClient.setConnectTimeout(5000); // 5 seconds

            ftpClient.connect(server, port);
            ftpClient.login(username, password);
            ftpClient.enterLocalPassiveMode();
            ftpClient.setFileType(FTP.BINARY_FILE_TYPE);

            // Set control connection keep-alive timeout (in milliseconds)
            ftpClient.setControlKeepAliveTimeout(30000); // 30 seconds

            // 파일 존재 여부 확인
            FTPFile[] files = ftpClient.listFiles(remoteFilePath);
            if (files.length == 0) {
                System.err.println("File does not exist on the server.");
                return;
            }

            try (OutputStream outputStream = new FileOutputStream(localFilePath)) {
                boolean success = ftpClient.retrieveFile(remoteFilePath, outputStream);
                if (success) {
                    System.out.println("File downloaded successfully!");
                } else {
                    System.err.println("Error downloading file.");
                }
            } catch (IOException e) {
                System.err.println("Error writing to local file: " + e.getMessage());
            }
        } catch (IOException e) {
            System.err.println("Error connecting to FTP server: " + e.getMessage());
        }
    }
}
*/
