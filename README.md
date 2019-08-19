# 后续
- 可以完善gradle配置文件(client和server 分开编译，并且编译文件最小化)
- 增加docker file配置
- 如果需要高性能，可以在server端实现多线程处理selection key(目前是单线程处理NIO)
- 增加启动脚本，可以一直启动，不需要手动长命令
- client注册的 security key还没有用起来(数据已传入，需要在server端自己做定制化处理)
- 通过特殊的日志可以和fail2ban配置来阻止暴力破解
- 目前只有rest api来获取ddns的状态，后期可以自己添加html来显示

## 测试结果
在 `2015 macbook pro i7 16g` 上测试
```
上行: 200MB/s
下行: 300-400MB/s
```

## 原理
proxy server用来处理外部访问，每次访问都会产生一个 select key，同时给这个key指定一个id(目前是1-4096)。
然后这个ID会传到内网的ddns client中，这样内部和外部就建立了一个互知的通道。
在client和server的通道中，数据包都会带上ID，数据达到对端的时候去解析数据包，这样就知道数据包应该发给哪个select key了

## DDNS网络编程中需要注意的问题

1. 因为一个通道会走很多个连接，所以发送的时候注意remaining 判断的时候需要用while，保证全部发送出去。否则会丢包，还有数据无法解析。
2. 高速发送数据包的时候 协议头(id+len) 可能会分两次接收过来， 要注意处理
3. 数据接收的时候可能是分批次接收，不能保证传过来的数据是完整的(protocol+ data)
4. 如何判断关闭
-  read len == -1
-  close exception
5. 统计是会消耗性能的，如果需要减小到最小影响，需要另开线程用queue来处理
6. while remaining需要用sleep来减小对CPU的消耗
7. 协议头的大小以及解析都会影响传输速率
8. NIO中的bytebuff的size大小会影响传输速率
9. 目前协议头里面是 indexID + dataLen的结构（short+ short）,也就是现在协议头占用一个 INT(4个字节,32位)。其中还是有一些浪费的，因为data length和index id都是小于4096的。 

