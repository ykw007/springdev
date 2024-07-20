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

*/
