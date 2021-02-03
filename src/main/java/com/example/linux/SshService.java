package com.example.linux;

import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class SshService {
    public static String exeCommand(SshConfig sshConfig,String commond) throws JSchException, IOException {
        sshConfig.setCommand(commond);
        return exeCommand(sshConfig);
    }

    public static String exeCommand(SshConfig sshConfig) throws JSchException, IOException {
        JSch jsch = new JSch();
        Session session = jsch.getSession(sshConfig.getUser(), sshConfig.getHost(),sshConfig.getPort());
        session.setConfig("StrictHostKeyChecking", "no");
        session.setPassword(sshConfig.getPassword());
        session.connect();

        ChannelExec channelExec = (ChannelExec) session.openChannel("exec");
        InputStream in = channelExec.getInputStream();
        channelExec.setCommand(sshConfig.getCommand());
        channelExec.setErrStream(System.err);
        channelExec.connect();
        String out = IOUtils.toString(in, "UTF-8");

        channelExec.disconnect();
        session.disconnect();

        return out;
    }

    /**
     * 判断网络是否能ping通
     * @param sshConfig 服务器配置信息
     * @param targetHost 目标ip或域名
     * @return
     * @throws IOException
     * @throws JSchException
     */
    public static boolean ping(SshConfig sshConfig,String targetHost) throws IOException, JSchException {
        String command = "timeout 2 ping " + targetHost + " -c1";
        String result = SshService.exeCommand(sshConfig,command);
        return result.contains("ttl");
    }

    /**
     * 将返回信息分割为list集合，统一控制换行符 \n
     * @param result
     * @return
     */
    public static List<String> getList(String result){
        String[] split = result.split("\n");
        List<String> retVal = new ArrayList<>();
        for (String s : split) {
            retVal.add(s);
        }
        return retVal;
    }

    /**
     * 使用scp进行文件传输
     * @param sourceConfig 源服务器配置信息
     * @param targetConfig 目标服务器配置信息
     * @param sourceFile 源文件
     * @param dist 目标地址
     * @param isPush 是否是推送，推送与拉取是scp指令前后顺序不一致
     * @return
     */
    public static String fileScp(SshConfig sourceConfig,SshConfig targetConfig,String sourceFile,String dist,boolean isPush) throws IOException, JSchException {
        exeCommand(sourceConfig,"yum install -y expect");
        exeCommand(sourceConfig,"mkdir /tmp/backup");
        String retVal = "";
        if (isPush){
            exeCommand(sourceConfig, "cat > /tmp/backup/scpExpectAuto.sh << EOF\n" + "#!/usr/bin/expect\n" +
                    "\n" +
                    "set host "+targetConfig.getHost()+"\n" +
                    "set name "+targetConfig.getUser()+"\n" +
                    "set port "+targetConfig.getPort()+"\n" +
                    "set pwd "+targetConfig.getPassword()+"\n" +
                    "set distPath "+dist+"\n" +
                    "set sourceFile "+sourceFile+"\n" +
                    "\n" +
                    "spawn scp -P \\${port} \\${sourceFile} \\${name}@\\${host}:\\${distPath}\n" +
                    "expect {\n" +
                    "    \"password\" {send \"\\${pwd}\\r\";}\n" +
                    "    \"yes/no\" {send \"yes\\r\";exp_continue}\n" +
                    "}\n" +
                    "expect eof\n" +
                    "exit\n" + "EOF");
        }else {
            exeCommand(sourceConfig, "cat > /tmp/backup/scpExpectAuto.sh << EOF\n" + "#!/usr/bin/expect\n" +
                    "\n" +
                    "set host "+targetConfig.getHost()+"\n" +
                    "set name "+targetConfig.getUser()+"\n" +
                    "set port "+targetConfig.getPort()+"\n" +
                    "set pwd "+targetConfig.getPassword()+"\n" +
                    "set distPath "+dist+"\n" +
                    "set sourceFile "+sourceFile+"\n" +
                    "\n" +
                    "spawn scp -P \\${port} \\${name}@\\${host}:\\${sourceFile} \\${distPath}\n" +
                    "expect {\n" +
                    "    \"password\" {send \"\\${pwd}\\r\";}\n" +
                    "    \"yes/no\" {send \"yes\\r\";exp_continue}\n" +
                    "}\n" +
                    "expect eof\n" +
                    "exit\n" + "EOF");
        }

        retVal = exeCommand(sourceConfig,"expect /tmp/backup/scpExpectAuto.sh");
        return retVal;
    }
}
