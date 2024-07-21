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
import java.util.ArrayList;
import java.util.List;

public String generateCronExpression(String intervalType, List<String> detailOptions, String startTime) {
    String cronExpression = "";

    if (intervalType.equals("At Once")) {
        String[] time = startTime.split(":");
        cronExpression = time[1] + " " + time[0] + " * * *";
    } else if (intervalType.equals("Daily")) {
        String[] time = startTime.split(":");
        cronExpression = time[1] + " " + time[0] + " * * *";
    } else if (intervalType.equals("Weekly")) {
        String[] time = startTime.split(":");
        List<String> daysOfWeek = new ArrayList<>();
        for (String day : detailOptions) {
            switch (day) {
                case "SUN":
                case "MON":
                case "TUE":
                case "WED":
                case "THU":
                case "FRI":
                case "SAT":
                    daysOfWeek.add(day);
                    break;
                default:
                    // Ignore invalid day selection
            }
        }

        String daysExpression = String.join(",", daysOfWeek);
        cronExpression = time[1] + " " + time[0] + " * * " + daysExpression;
    } else if (intervalType.equals("Monthly")) {
        String[] time = startTime.split(":");
        String dayOfMonth = detailOptions.get(0).equals("End of Month") ? "L" : "1";
        cronExpression = time[1] + " " + time[0] + " " + dayOfMonth + " * *";
    }

    return cronExpression;
}

import org.quartz.CronExpression;
import java.text.ParseException;
import java.util.Date;

public class NextExecutionTime {
    public static Date getNextExecutionTime(String cronExpressionString) {
        try {
            CronExpression cronExpression = new CronExpression(cronExpressionString);
            return cronExpression.getNextValidTimeAfter(new Date());
        } catch (ParseException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static void main(String[] args) {
        String cronExpressionString = "0 15 10 ? * MON,TUE,WED,THU,FRI";
        Date nextExecutionTime = getNextExecutionTime(cronExpressionString);
        System.out.println("다음 실행 시간: " + nextExecutionTime);
    }
}


import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import java.nio.channels.*;
import java.io.*;

@SpringBootApplication
public class Application {
    private static File f;
    private static FileChannel channel;
    private static FileLock lock;

    public static void main(String[] args) {
        try {
            f = new File("RingOnRequest.lock");
            if (f.exists()) {
                f.delete();
            }
            channel = new RandomAccessFile(f, "rw").getChannel();
            lock = channel.tryLock();
            if(lock == null) {
                channel.close();
                throw new RuntimeException("Only 1 instance of MyApp can run.");
            }
            ShutdownHook shutdownHook = new ShutdownHook();
            Runtime.getRuntime().addShutdownHook(shutdownHook);

            SpringApplication.run(Application.class, args);
            System.out.println("Running");

        } catch(IOException e) {
            throw new RuntimeException("Could not start process.", e);
        }
    }

    public static void unlockFile() {
        try {
            if(lock != null) {
                lock.release();
                channel.close();
                f.delete();
            }
        } catch(IOException e) {
            e.printStackTrace();
        }
    }

    static class ShutdownHook extends Thread {
        public void run() {
            unlockFile();
        }
    }
}

*/
