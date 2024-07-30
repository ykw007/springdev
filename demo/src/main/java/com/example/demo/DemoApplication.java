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

Spring Boot 애플리케이션에서 **lock 파일**을 사용하여 중복 실행을 방지하는 방법을 설명하고, 관련된 코드를 작성해 보겠습니다. Lock 파일은 애플리케이션이 이미 실행 중인지를 감지하는 데 유용하며, 이 방법은 시스템과 상관없이 대부분의 환경에서 동작합니다.

## 1. Spring Boot 애플리케이션 설정

Spring Boot 애플리케이션이 시작될 때, lock 파일을 생성하고, 종료될 때 lock 파일을 삭제하는 로직을 추가해야 합니다. 애플리케이션이 이미 실행 중이라면 적절한 오류 메시지를 출력하고 종료합니다.

### 1.1. Lock 파일 사용 로직 추가

**MainApplication.java:**

```java
package com.example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

@SpringBootApplication
public class MainApplication {

    private static final String LOCK_FILE_PATH = "app.lock";

    public static void main(String[] args) {
        try {
            // Check for lock file existence
            if (isAppRunning()) {
                System.err.println("애플리케이션이 이미 실행 중입니다.");
                System.exit(1);
            }

            // Create lock file
            createLockFile();

            // Add shutdown hook to remove lock file on exit
            Runtime.getRuntime().addShutdownHook(new Thread(() -> removeLockFile()));

            SpringApplication.run(MainApplication.class, args);

        } catch (IOException e) {
            System.err.println("애플리케이션을 시작할 수 없습니다: " + e.getMessage());
            System.exit(1);
        }
    }

    private static boolean isAppRunning() {
        File lockFile = new File(LOCK_FILE_PATH);
        return lockFile.exists();
    }

    private static void createLockFile() throws IOException {
        File lockFile = new File(LOCK_FILE_PATH);
        if (!lockFile.exists()) {
            lockFile.createNewFile();
        }
    }

    private static void removeLockFile() {
        try {
            Files.deleteIfExists(Paths.get(LOCK_FILE_PATH));
        } catch (IOException e) {
            System.err.println("Lock 파일을 삭제할 수 없습니다: " + e.getMessage());
        }
    }
}
```

### 설명

- **`LOCK_FILE_PATH`**: Lock 파일의 경로를 지정합니다. 프로젝트 디렉토리에 `app.lock` 파일을 생성합니다.
- **`isAppRunning()`**: Lock 파일이 존재하는지 확인하여 애플리케이션의 중복 실행을 감지합니다.
- **`createLockFile()`**: 애플리케이션이 실행될 때 lock 파일을 생성합니다.
- **`removeLockFile()`**: 애플리케이션이 종료될 때 lock 파일을 삭제합니다.
- **`addShutdownHook`**: 애플리케이션이 정상적으로 종료되거나 시스템 종료 시 lock 파일을 제거합니다.

## 2. 배치 파일 수정

이제 lock 파일을 사용하는 Spring Boot 애플리케이션을 실행하기 위한 배치 파일을 수정합니다.

### 2.1. 배치 파일 작성

**run-app.bat:**

```bat
@echo off
setlocal

REM Define variables
set JAVA_HOME=C:\path\to\your\jdk
set JAR_FILE=build\libs\your-app-name.jar

REM Set additional JVM options if necessary
set JVM_OPTS=-Xms512m -Xmx1024m

REM Run the Spring Boot application
echo 애플리케이션을 시작합니다...
"%JAVA_HOME%\bin\java" %JVM_OPTS% -jar %JAR_FILE%

REM Check exit code and display appropriate message
if %ERRORLEVEL% equ 1 (
    echo 애플리케이션이 이미 실행 중입니다.
)

REM Pause the console window to see logs
pause

endlocal
```

### 배치 파일 설명

- **애플리케이션 실행**: 애플리케이션을 실행하고 종료 코드를 확인합니다.
- **오류 메시지 표시**: 종료 코드가 `1`인 경우 "애플리케이션이 이미 실행 중입니다."라는 메시지를 출력합니다.
- **콘솔 창 유지**: `pause` 명령어를 사용하여 로그를 확인할 수 있도록 콘솔 창을 유지합니다.

=================================================

배치 파일을 작성하여 Spring Boot 애플리케이션이 이미 실행 중인지를 체크하고, 실행 중이라면 "이미 실행 중입니다"라는 메시지를 출력한 후 종료하는 방법을 설명하겠습니다. 이 방법은 `tasklist` 명령어를 사용하여 현재 실행 중인 Java 프로세스를 확인한 다음, 해당 프로세스가 이미 실행 중인지를 판단합니다.

### 1. 애플리케이션 프로세스 확인

애플리케이션의 JAR 파일을 실행할 때, Java 프로세스가 이미 실행 중인지를 확인하여야 합니다. 이를 위해 `tasklist` 명령어를 사용하여 실행 중인 프로세스를 검색합니다.

### 2. 배치 파일 작성

다음은 실행 중인 애플리케이션을 감지하고, 이미 실행 중이면 종료하는 배치 파일의 예제입니다.

**run-app.bat:**

```bat
@echo off
setlocal

REM Define variables
set JAVA_HOME=C:\path\to\your\jdk
set JAR_FILE=build\libs\your-app-name.jar
set APP_NAME=your-app-name.jar

REM Check if the application is already running
tasklist /FI "IMAGENAME eq java.exe" /FO LIST | findstr /I /C:"%APP_NAME%" > nul
if %ERRORLEVEL% equ 0 (
    echo 애플리케이션이 이미 실행 중입니다.
    exit /B 1
)

REM Set additional JVM options if necessary
set JVM_OPTS=-Xms512m -Xmx1024m

REM Run the Spring Boot application
echo 애플리케이션을 시작합니다...
"%JAVA_HOME%\bin\java" %JVM_OPTS% -jar %JAR_FILE%

REM Pause the console window to see logs
pause

endlocal
```

### 배치 파일 설명

1. **변수 설정**:
    - `JAVA_HOME`은 JDK 경로를 지정합니다.
    - `JAR_FILE`은 실행할 JAR 파일의 경로를 지정합니다.
    - `APP_NAME`은 JAR 파일의 이름입니다. 이 이름을 기반으로 실행 중인 프로세스를 찾습니다.

2. **애플리케이션 실행 여부 확인**:
    - `tasklist` 명령어를 사용하여 현재 실행 중인 Java 프로세스 목록을 가져옵니다.
    - `findstr` 명령어를 사용하여 프로세스 목록에서 `APP_NAME`을 검색합니다.
    - 이미 실행 중이라면 `ERRORLEVEL`이 0이 되고, "애플리케이션이 이미 실행 중입니다."라는 메시지를 출력한 후, `exit /B 1`로 배치 파일을 종료합니다.

3. **애플리케이션 실행**:
    - 애플리케이션이 실행 중이 아니라면 Java 명령어로 JAR 파일을 실행합니다.

4. **콘솔 창 유지**:
    - 애플리케이션 종료 시 콘솔 창이 닫히지 않도록 `pause` 명령어를 사용합니다.
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class DirectoryCreator {

    public static void main(String[] args) {
        String directoryName = "example";
        Path createdDirectory = createUniqueDirectory(directoryName);
        System.out.println("Created Directory: " + createdDirectory.toString());
    }

    public static Path createUniqueDirectory(String directoryName) {
        return createUniqueDirectory(Paths.get(directoryName), 0);
    }

    private static Path createUniqueDirectory(Path directoryPath, int copyIndex) {
        Path newPath = copyIndex == 0 ? directoryPath : Paths.get(directoryPath.toString() + "_copy" + copyIndex);
        
        try {
            if (!Files.exists(newPath)) {
                return Files.createDirectory(newPath);
            } else {
                // Recurse with incremented copy index if directory already exists
                return createUniqueDirectory(directoryPath, copyIndex + 1);
            }
        } catch (IOException e) {
            System.err.println("An error occurred while creating the directory: " + e.getMessage());
            return null;
        }
    }
}
*/
