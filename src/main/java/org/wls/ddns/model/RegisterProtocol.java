package org.wls.ddns.model;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * Created by wls on 2019/8/9.
 */
public class RegisterProtocol {
    private static final Integer SECRET_KEY_LEN = 16;
    private static final Integer NAME_MAX_LEN = 64;
    private static final Logger LOG = LogManager.getLogger(RegisterProtocol.class);
    public static final Integer SECURITY = 1;
    public static final Integer NO_SECURITY = 0;

    private Integer localPort;
    private Integer proxyPort;
    private String name;
    //"111111111111111" = 16*8
    private String secretKey;
    //远端需要验证
    private Integer security;
//
    public RegisterProtocol(Integer localPort, Integer proxyPort, String name, String secretKey, Integer security){
        this.localPort = localPort;
        this.proxyPort = proxyPort;
        this.name = formatName(name);
        this.secretKey = formatSecretKey(secretKey);
        this.security = security;
    }

    public RegisterProtocol(){}


    private String formatSecretKey(String secretKey){
        if(secretKey.getBytes().length < SECRET_KEY_LEN){
            char[] chars = new char[SECRET_KEY_LEN - secretKey.getBytes().length ];
            Arrays.fill(chars, '0');
            String forFill = new String(chars);

            secretKey = secretKey + forFill;
        } else if(secretKey.getBytes().length > SECRET_KEY_LEN){
            secretKey  = new String(Arrays.copyOfRange(secretKey.getBytes(), 0, SECRET_KEY_LEN));
        }
        return secretKey;
    }

    private String formatName(String name){
        if(name.getBytes().length > NAME_MAX_LEN){
            name = new String(Arrays.copyOfRange(name.getBytes(), 0, NAME_MAX_LEN));
        }
        return name;
    }

    /*
     *  协议格式: 协议长度 + 秘钥 + proxyport + localport + name
     *
     */
    public ByteBuffer encode(){
        ByteBuffer byteBuffer = ByteBuffer.allocate(4096);

        Integer bufferSize = SECRET_KEY_LEN + Integer.SIZE/8 + Integer.SIZE/8  + Integer.SIZE/8 + name.getBytes().length;
        byteBuffer.putInt(bufferSize);
        byteBuffer.put(secretKey.getBytes());
        byteBuffer.putInt(proxyPort);
        byteBuffer.putInt(localPort);
        byteBuffer.putInt(security);
        byteBuffer.put(name.getBytes());
//        System.out.println("Encode name length:"+name.getBytes().length);
        byteBuffer.flip();

        return byteBuffer;
    }

    // 要做校验，确认可以解析
    public static RegisterProtocol decode(ByteBuffer byteBuffer /*, Integer protocolSize*/){
        LOG.info("Protocol's byteBuffer length : " + byteBuffer.remaining());
        Integer protocolSize = byteBuffer.getInt();
        if(protocolSize > byteBuffer.remaining()){
            LOG.error("The data size is less then protocol size.");
            return null;
        }

        if(protocolSize < SECRET_KEY_LEN + Integer.SIZE/8 + Integer.SIZE/8  + Integer.SIZE/8){
            LOG.error("The protocol length is less then the minimum protocol lenght , data exception");
            return null;
        }

        if(protocolSize > SECRET_KEY_LEN + Integer.SIZE/8+ Integer.SIZE/8 + Integer.SIZE/8 + NAME_MAX_LEN){
            LOG.error("Protocol data is more then the max length, data exception");
            return null;
        }
        try{
            RegisterProtocol rp = new RegisterProtocol();
            byte[] secretBytes = new byte[SECRET_KEY_LEN];
            byteBuffer.get(secretBytes);
            rp.secretKey = new String(secretBytes);

            rp.proxyPort = byteBuffer.getInt();
            rp.localPort = byteBuffer.getInt();
            rp.security = byteBuffer.getInt();

            byte[] nameBytes = new byte[protocolSize - SECRET_KEY_LEN - Integer.SIZE/8 - Integer.SIZE/8 - Integer.SIZE/8];
            byteBuffer.get(nameBytes);
//            System.out.println("Decode name length:"+nameBytes.length);
//            System.out.println("Decode name length:"+nameBytes.length);

            rp.name = new String(nameBytes);
            return rp;
        } catch (Exception e){

            LOG.error(e.getStackTrace());

            return null;
        }


    }

    public Integer getLocalPort() {
        return localPort;
    }

    public void setLocalPort(Integer localPort) {
        this.localPort = localPort;
    }

    public Integer getProxyPort() {
        return proxyPort;
    }

    public void setProxyPort(Integer proxyPort) {
        this.proxyPort = proxyPort;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = formatName(name);
    }

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = formatSecretKey(secretKey);
    }

    public Integer getSecurity() {
        return security;
    }

    public void setSecurity(Integer security) {
        this.security = security;
    }

    public String toString(){
        return "Register Protocol\n" +
                "{\n" +
                "    name:" + this.getName() + "\n" +
                "    secretKey:" + this.secretKey + "\n" +
                "    proxyPort:" + this.proxyPort + "\n" +
                "    localPort:" + this.localPort + "\n" +
                "    security:" + this.security + "\n" +
                "}\n";
    }
}
