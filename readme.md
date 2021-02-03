[TOC]
```
    该项目用于对mysql数据库进行备份
    1.支持结束时间的选择
    2.支持外网库同步到内网中
    3.支持同步到同一个服务器不同数据库名
    
    需要准备项
    1.mysql数据库需要启动binlog日志功能
    2.编写定时任务，对目标数据库进行定时全量dump
```
# mysql开启binlog日志功能
> 在数据库配置文件my.cnf [mysqld] 配置项下加入如下内容
```
# binlog 设置
binlog_format = MIXED # binlog记录模式
log_bin = /home/mysql/bin_log/mysql-bin.log # binlog输出位置
expire_logs_days = 7 #binlog过期清理时间
server-id=123454 
max_binlog_size=1000m # binlog文件大小限制

```

# 编写dump脚本，并添加到定时任务中
- 新建脚本文件
> vi /home/mysql/mysql_dump/dump.sh
```shell
#/bin/sh
stamp="`date +%Y%m%d%H%M%s`"
# wbwf即指定导出的数据库名
mysqldump -u用户名 -p密码 -F  --master-data  wbwf > /home/mysql/mysql_dump/wbwf_${stamp}.sql
```
- 添加到定时任务
> crontab -e

0 0 */7 * * sh /home/mysql/mysql_dump/dump.sh

```
这里定时任务的执行周期可以与binlog清理日期相结合
``` 