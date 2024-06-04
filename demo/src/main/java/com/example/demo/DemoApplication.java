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

// ...

public class FtpDownloadRetryExample {

    public static void main(String[] args) {
        String server = "ftp.example.com";
        int port = 21;
        String username = "your-ftp-username";
        String password = "your-ftp-password";
        String remoteFilePath = "/path/on/ftp/server/file.txt";
        String localFilePath = "path/to/local/file.txt";

        FTPClient ftpClient = new FTPClient();
        try {
            ftpClient.connect(server, port);
            ftpClient.login(username, password);
            ftpClient.setFileType(FTP.BINARY_FILE_TYPE);

            // 파일 다운로드 시도 (3회까지 재시도)
            int maxRetries = 3;
            int retryCount = 0;
            boolean success = false;
            while (!success && retryCount < maxRetries) {
                try {
                    success = ftpClient.retrieveFile(remoteFilePath, new FileOutputStream(localFilePath));
                } catch (IOException e) {
                    System.err.println("다운로드 실패: " + e.getMessage());
                    retryCount++;
                    Thread.sleep(1000); // 1초 대기 후 재시도
                }
            }

            if (success) {
                System.out.println("파일 다운로드 성공!");
            } else {
                System.out.println("파일 다운로드 실패 (재시도 횟수 초과)");
            }

            ftpClient.logout();
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        } finally {
            try {
                ftpClient.disconnect();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}

import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;

import java.io.FileOutputStream;
import java.io.IOException;

public class FtpDownloadWithTimeoutExample {

    public static void main(String[] args) {
        String server = "ftp.example.com";
        int port = 21;
        String username = "your-ftp-username";
        String password = "your-ftp-password";
        String remoteFilePath = "/path/on/ftp/server/file.txt";
        String localFilePath = "path/to/local/file.txt";

        FTPClient ftpClient = new FTPClient();
        try {
            ftpClient.connect(server, port);
            ftpClient.login(username, password);
            ftpClient.setFileType(FTP.BINARY_FILE_TYPE);

            // 데이터 전송 시간 초과 설정 (10초)
            ftpClient.setDataTimeout(10000);

            boolean success = ftpClient.retrieveFile(remoteFilePath, new FileOutputStream(localFilePath));
            if (success) {
                System.out.println("파일 다운로드 성공!");
            } else {
                System.out.println("파일 다운로드 실패!");
            }

            ftpClient.logout();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                ftpClient.disconnect();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}

*/
