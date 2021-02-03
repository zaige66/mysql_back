package com.example.linux;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.util.StringUtils;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@SpringBootApplication
public class LinuxApplication implements CommandLineRunner {

    public static void main(String[] args)  {
        SpringApplication.run(LinuxApplication.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        BackupService backupService = new BackupService();
        // 旧数据库及服务器信息
        String oldLinuxHost = "192.168.30.10";
        int oldLinuxPort = 22;
        String oldLinuxUserName = "root";
        String oldLinuxPwd  = "aaa";
        // binlog日志保存位置
        String binLogPath = "/home/mysql/bin_log";
        // 全量dump文件保存位置
        String dumpPath = "/tmp/";

        // 需要备份的数据库名
        String backupDbName = "wbwf";
        // 新数据库名
        String newDbName = "bbb";
        // 数据截止时间
        String endTime = "2021-01-13 14:48:00";

        // 新数据库及服务器信息
        String newLinuxHost = "192.168.30.10";
        int newLinuxPort = 22;
        String newLinuxUserName = "root";
        String newLinuxPwd  = "aaa";
        String newMysqlUserName = "root";
        String newMysqlPwd = "ccc";
        // 数据库是否安装在docker中
        boolean newMysqlIsDocker = true;

        SshConfig oldSshConfig = new SshConfig(oldLinuxHost, oldLinuxPort, oldLinuxUserName, oldLinuxPwd);
        SshConfig newSshConfig = new SshConfig(newLinuxHost, newLinuxPort, newLinuxUserName, newLinuxPwd);


        // 处理旧服务器上文件
        // 1.寻找合适条件的dump文件
        String dumpFileName = backupService.handleDumpFile(oldSshConfig, endTime, backupDbName);

        // 2.将dump文件与之对应的合适的binlog日志输出为可执行sql文件
        Map<String, String> stringStringMap = backupService.handleBinlogFile(oldSshConfig, dumpPath, dumpFileName, binLogPath, backupDbName, endTime);

        // 将处理好的文件传输到需要备份的机器上
         stringStringMap = backupService.handleFileTransport(oldSshConfig, newSshConfig, stringStringMap.get("binlog"), stringStringMap.get("dump"));

        // 登录mysql，调用source指令，将dump文件与binlog文件导入数据库
        backupService.handleFileImport(newSshConfig,newMysqlUserName,newMysqlPwd,backupDbName,newDbName,stringStringMap.get("dump"),stringStringMap.get("binlog"),newMysqlIsDocker);

    }


}
