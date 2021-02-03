package com.example.linux;

public class SshConfig {
    private String  host;
    private int port;
    private String user;
    private String password;
    private String command;

    public SshConfig() {
    }

    public SshConfig(String host, int port, String user, String password) {
        this.host = host;
        this.port = port;
        this.user = user;
        this.password = password;
    }

    public SshConfig(String host, int port, String user, String password, String command) {
        this.host = host;
        this.port = port;
        this.user = user;
        this.password = password;
        this.command = command;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getCommand() {
        return command;
    }

    public void setCommand(String command) {
        this.command = command;
    }
}
