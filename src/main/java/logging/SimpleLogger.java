package logging;

import java.time.Instant;
import java.time.LocalDateTime;

public class SimpleLogger {

    private String ERROR = "";
    private String WARN = "";
    private String INFO = "";
    private String DEFAULT = "";

    public SimpleLogger() {}

    public SimpleLogger(boolean colored) {
        if (colored) {
            ERROR = "\033[31m";
            WARN = "\033[33m";
            INFO = "\033[34m";
            DEFAULT = "\033[0m";
        }
    }

    private String getCallingClassName() {
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        String className = "";
        try {
            className = Class.forName(stackTrace[3].getClassName()).getSimpleName();
        } catch (ClassNotFoundException e) {
            error(e);
        }
        return className;
    }

    private String getCallingMethodName() {
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        String methodName = "";
        try {
            methodName = stackTrace[3].getMethodName();
        } catch (ArrayIndexOutOfBoundsException e) {
            error(e);
        }
        return methodName;
    }

    public synchronized void info(String msg) {
        System.out.println(INFO + Instant.now().toString().replace('T', ' ').replace("Z", "") + " [HCLILauncher] [INFO] [" + getCallingClassName() + "." + getCallingMethodName() + "] " + msg + DEFAULT);
    }

    public synchronized void warn(String msg) {
        System.out.println(WARN + Instant.now().toString().replace('T', ' ').replace("Z", "") + " [HCLILauncher] [WARN] [" + getCallingClassName() + "." + getCallingMethodName() + "] " + msg + DEFAULT);
    }

    public synchronized void error(String msg) {
        System.out.println(ERROR + Instant.now().toString().replace('T', ' ').replace("Z", "") + " [HCLILauncher] [ERROR] [" + getCallingClassName() + "." + getCallingMethodName() + "] " + msg + DEFAULT);
    }

    public synchronized void error(Exception e) {
        StringBuilder errBuilder = new StringBuilder();
        errBuilder.append(ERROR).append(Instant.now().toString().replace('T', ' ').replace("Z", "")).append(" [HCLILauncher] [ERROR] [")
                .append(getCallingClassName()).append(".").append(getCallingMethodName()).append("] ").append(e.getMessage()).append("\n");
        for (StackTraceElement stackTraceElement : e.getStackTrace()) {
            errBuilder.append(stackTraceElement).append("\n");
        }
        errBuilder.append(DEFAULT);
        System.out.println(errBuilder);
    }

}