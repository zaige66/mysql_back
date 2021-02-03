package com.example.linux;

import com.jcraft.jsch.JSchException;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BackupService {


    public Map<String,String> handleFileTransport(SshConfig oldConfig,SshConfig newConfig,String binlogOutputPath,String dumpOutputPath) throws IOException, JSchException {
        boolean old2new = false;
        boolean new2old = false;
        String distBinlogPath = "/tmp/backup/binlog.sql";
        String distDumpPath = "/tmp/backup/dump.sql";
        if(oldConfig.getHost().equalsIgnoreCase(newConfig.getHost())){ // 不用传输
            distBinlogPath = binlogOutputPath;
            distDumpPath = dumpOutputPath;
        }else {
            // 旧 ping 新
            old2new = SshService.ping(oldConfig,newConfig.getHost());
            // 新 ping 旧
            new2old = SshService.ping(newConfig,oldConfig.getHost());
        }
        if (old2new){
            // binlog
            SshService.fileScp(oldConfig,newConfig,binlogOutputPath,distBinlogPath,true);
            // dump
            SshService.fileScp(oldConfig,newConfig,dumpOutputPath,distDumpPath,true);
        }else if (new2old){
            // binlog
            SshService.fileScp(newConfig,oldConfig,binlogOutputPath,distBinlogPath,false);
            // dump
            SshService.fileScp(newConfig,oldConfig,dumpOutputPath,distDumpPath,false);
        }
        Map<String,String> retVal = new HashMap<>();
        retVal.put("binlog",distBinlogPath);
        retVal.put("dump",distDumpPath);

        return retVal;
    }

    /**
     * 寻找符合条件的dump文件
     * @param oldSshConfig 原服务器配置信息
     * @param endTime 数据截止时间
     * @param backupDbName 需要备份的数据库名
     * @return
     * @throws ParseException
     * @throws IOException
     * @throws JSchException
     */
    public String handleDumpFile(SshConfig oldSshConfig,String endTime,String backupDbName) throws ParseException, IOException, JSchException {
        // 1.寻找合适条件的dump文件
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd-HH:mm:ss");
        SimpleDateFormat dateFormatNormal = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        Date endTimeDate = dateFormatNormal.parse(endTime);
        String result = SshService.exeCommand(oldSshConfig, "export TIME_STYLE='+%Y-%m-%d-%H:%M:%S' && ls -lt /tmp/ | grep " + backupDbName +" | awk '{print $6,$7}'");
        String[] split = result.split("\n");
        String dumpFileName = "";
        for (String s1 : split) {
            if (StringUtils.isEmpty(s1)){
                continue;
            }
            String[] s2 = s1.split(" ");
            Date parse = dateFormat.parse(s2[0]);
            if (endTimeDate.getTime() > parse.getTime()){
                dumpFileName = s2[1];
                break;
            }
        }
        if (StringUtils.isEmpty(dumpFileName)){
            throw new RuntimeException("未找到时间点"+ endTime + "之前的" + backupDbName + "库的dump文件");
        }

        return dumpFileName;
    }

    /**
     * 处理binlog日志文件，将符合条件的binlog文件转成.sql文件，用于后续导入
     * @param oldSshConfig
     * @param dumpPath
     * @param dumpFileName
     * @param binLogPath
     * @param backupDbName
     * @param endTime
     * @return
     * @throws IOException
     * @throws JSchException
     */
    public Map<String,String> handleBinlogFile(SshConfig oldSshConfig,String dumpPath,String dumpFileName,String binLogPath,String backupDbName,String endTime) throws IOException, JSchException {
        String dumpFilePath = dumpPath + dumpFileName;
        String result = SshService.exeCommand(oldSshConfig, "cat " + dumpFilePath + " | grep 'CHANGE MASTER'");
        String pattern = "CHANGE MASTER TO MASTER_LOG_FILE='mysql-bin.(.*?)', MASTER_LOG_POS=(.*?);";
        Pattern r = Pattern.compile(pattern);
        Matcher matcher = r.matcher(result);
        int binlogFileIndex = 0;
        int binlogPos = 0;
        if (matcher.find()){
            binlogFileIndex = Integer.parseInt(matcher.group(1));
            binlogPos = Integer.parseInt(matcher.group(2));
        }else {
            throw new RuntimeException("dump文件中未找到binlog信息");
        }

        result = SshService.exeCommand(oldSshConfig,"ls " + binLogPath + " | grep mysql-bin");
        List<String> list = SshService.getList(result);

        List<String> needBinLogFileNameList = new ArrayList<>();
        for (String s : list) {
            String[] split1 = s.split("\\.");
            try {
                int i = Integer.parseInt(split1[1]);
                if (i >= binlogFileIndex){
                    needBinLogFileNameList.add(s);
                }
            }catch (Exception e){}
        }

        String binlogOutputPath = "/tmp/backup/"+backupDbName+"_binlogOutput.sql";
        SshService.exeCommand(oldSshConfig,
                "cd "+binLogPath+"&& mysqlbinlog -s --database="+
                        backupDbName +
                        " --start-position="+binlogPos+
                        " --stop-datetime='"+endTime+
                        "' "+CommonStringUtils.concatArr2String(needBinLogFileNameList," ")+
                        " > " + binlogOutputPath);

        // 将dump文件中的 " CHANGE MASTER TO MASTER_LOG_FILE " 去掉，不然在使用 source指令导入时会有主从报错
        String commond = "grep -n 'CHANGE MASTER' "+dumpFilePath+" | awk -F ':' '{print $1}'";
        String lineNum = SshService.exeCommand(oldSshConfig, commond).replace("\n","");;
        commond = "sed -e '"+lineNum+"'d " + dumpFilePath + " > /tmp/backup/dump.sql";
        SshService.exeCommand(oldSshConfig,commond);

        Map<String,String> retVal = new HashMap<>();
        retVal.put("dump","/tmp/backup/dump.sql");
        retVal.put("binlog",binlogOutputPath);
        return retVal;
    }

    /**
     * 处理文件传输，将处理好的dump文件与binlog格式化的.sql文件传输到目标服务器
     * @param newSshconfig 新服务器配置信息
     * @param newDbName 新的数据库用户名
     * @param newDbPwd  新的数据库密码
     * @param oldDatabaseName 旧数据库名
     * @param newDatabaseName 新数据库名
     * @param dumpFilePath dump文件地址
     * @param binlogFilePath binlog.sql文件地址
     * @param newMysqlIsDocker 新的mysql服务是否安装在docker中
     * @return
     * @throws IOException
     * @throws JSchException
     */
    public String handleFileImport(SshConfig newSshconfig,String newDbName,String newDbPwd,String oldDatabaseName,String newDatabaseName,String dumpFilePath,String binlogFilePath,boolean newMysqlIsDocker) throws IOException, JSchException {
        String commond,result,prefix = "";
        if (newMysqlIsDocker){
            commond = "docker ps | grep mysql | awk '{print $1}'";
            String containsId = SshService.exeCommand(newSshconfig, commond).replace("\n","");
            if (StringUtils.isEmpty(containsId)){
                throw new RuntimeException("docker容器中未找到mysql实例");
            }
            prefix = "docker exec " + containsId +" ";

            /**
             * docker 执行时问题总结
             *         1.首先要把文件从宿主机复制到容器内，不然后面使用source 指令导入时找不到文件
             *         2.使用docker exec 而不是 docker exec -it，后者会执行失败，并提示：the input device is not a TTY
             *         3.使用docker exec mysql -u -p -e 'sql语句' 而不是 docker exec mysql -u -p 数据库名 < source 文件路径
             *           两个指令输出结果一致，单只有前者能实际执行，具体原因未找到
             */
            commond = "cat > /tmp/backup/dockerMysql.sh << EOF\n" + "#!/usr/bin/expect\n" +
                        "sed -e 's/"+oldDatabaseName+"/"+newDatabaseName+"/g' "+binlogFilePath+" > /tmp/temp_replace_binlog.sql"
                        + "\n" +
                        "docker exec " + containsId + " mkdir -p /tmp/backup"
                        + "\n" +
                        "docker cp "+dumpFilePath+" " + containsId + ":" + dumpFilePath
                        + "\n" +
                        "docker cp /tmp/temp_replace_binlog.sql " + containsId + ":/tmp/temp_replace_binlog.sql"
                        + "\n" +
                        prefix + "mysql -u"+newDbName+" -p"+newDbPwd+" -e 'drop database if exists "+newDatabaseName+"; CREATE DATABASE \\`"+newDatabaseName+"\\` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;'"
                        + "\n" +
                        prefix + "mysql -u"+newDbName+" -p"+newDbPwd+" -e 'use "+newDatabaseName+";source "+dumpFilePath+";'"
                        + "\n" +
                        prefix + "mysql -u"+newDbName+" -p"+newDbPwd+" -e 'use "+newDatabaseName+";source /tmp/temp_replace_binlog.sql;'"
                        + "\n" +  "EOF";

            result = SshService.exeCommand(newSshconfig,commond);
            SshService.exeCommand(newSshconfig,"bash /tmp/backup/dockerMysql.sh");
            System.out.println("新建数据库:" + result);
        }else {
            // 新建数据库
            commond = "mysql -u"+newDbName+" -p"+newDbPwd+" -e 'drop database if exists "+newDatabaseName+"; CREATE DATABASE `"+newDatabaseName+"` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;'";
            result = SshService.exeCommand(newSshconfig,commond);
            System.out.println("新建数据库:" + result);
            // 导入dump数据
            commond = "mysql -u"+newDbName+" -p"+newDbPwd+" " + newDatabaseName + " < " + dumpFilePath;
            result = SshService.exeCommand(newSshconfig,commond);
            System.out.println("导入dump数据:" + result);

            // 处理binlog数据，将新的数据库名替换原有的数据库名
            commond = "sed -e 's/"+oldDatabaseName+"/"+newDatabaseName+"/g' "+binlogFilePath+" > /tmp/temp_replace_binlog.sql";
            result = SshService.exeCommand(newSshconfig,commond);
            System.out.println("处理binlog数据:" + result);
            // 导入binlog数据
            commond = "mysql -u"+newDbName+" -p"+newDbPwd+" " + newDatabaseName + " < " + "/tmp/temp_replace_binlog.sql";
            result = SshService.exeCommand(newSshconfig,commond);
            System.out.println("导入binlog数据:" + result);
        }


        return "";
    }
}
