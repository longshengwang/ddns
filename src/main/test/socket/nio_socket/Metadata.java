package org.wls.ddns.backup.socket.nio_socket;

/**
 * Created by wls on 2019/8/2.
 */
public class Metadata {
    private Integer remainingLength;
    private Integer indexId;

    Metadata(){

    }
    public void set(Integer allLength, Integer indexId){
        if(isNull()){
            this.remainingLength = allLength;
            this.indexId = indexId;
        } else {
            System.out.println("AAAAAA 出现错误啦。。。 有数据没发完 indexID: " + this.indexId + "   remaining length: "+ this.remainingLength);
        }

    }

    public Integer decrease(Integer size){
        remainingLength -= size;
        if(remainingLength == 0){
            reset();
        }
        return remainingLength;
    }

    public Integer getIndexId(){
        return indexId;
    }

    public Integer getRemainingLength(){
        return remainingLength;
    }

    public void reset(){
        remainingLength = null;
        indexId = null;
    }

    public boolean isNull(){
        if(remainingLength == null && indexId == null){
            return true;
        }
        return false;
    }



}
