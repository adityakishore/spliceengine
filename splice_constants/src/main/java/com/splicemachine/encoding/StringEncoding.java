package com.splicemachine.encoding;

import org.apache.hadoop.hbase.util.Bytes;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.UnicodeUtil;

import java.nio.ByteBuffer;

/**
 * UTF-8 encodes Strings in such a way that NULL < "" < Character.MIN_CODE_POINT < aa < aaa < b< ba<...<
 * Character.MAX_CODE_POINT < ..., and does not use 0x00 (reserved for separators).
 *
 * Note that UTF-8 encoding is already lexicographically sorted in bytes, by design. Hence, all we
 * really have to do is remove 0x00 elements. Since UTF-8 never uses the values 0xff or 0xfe, so adding
 * 2 to every byte will suffice.
 *
 * To distinguish between empty strings and {@code null}, we use 0x01 to denote an empty string, but an empty byte[]
 * denotes {@code null}.
 *
 * @author Scott Fines
 * Created on: 6/7/13
 */
public class StringEncoding {

	/**
	 * 
	 * Wraps the Lucene UnicodeUtil.UTF16toUTF8 bytes serializatiom...
	 * 
	 * @param value
	 * @param desc
	 * @return
	 */
    public static byte[] toBytes(String value, boolean desc){
        if(value==null) return new byte[0];
        if(value.length()==0) return new byte[]{0x01};

        //convert to UTF-8 encoding
        BytesRef result = new BytesRef();
        UnicodeUtil.UTF16toUTF8(value, 0, value.length(), result);   
        byte[] returnArray = new byte[result.length];
        for(int i=0;i<result.length;i++){
            byte newD = (byte)(result.bytes[i+result.offset] + 2);
            if(desc)
                newD ^= 0xff; //reverse the sign bit so that data is reversed in 2's complement
            returnArray[i] = newD;
        }
        return returnArray;
    }
    @Deprecated
    public static byte[] toBytesOld(String value, boolean desc){
        if(value==null) return new byte[0];
        if(value.length()==0) return new byte[]{0x01};

        //convert to UTF-8 encoding
        byte[] data = Bytes.toBytes(value);
        for(int i=0;i<data.length;i++){
            byte newD = (byte)(data[i] + 2);
            if(desc)
                newD ^= 0xff; //reverse the sign bit so that data is reversed in 2's complement
            data[i] = newD;
        }
        return data;
    }
    /**
     * SIDE EFFECT WARNING: Transforms the passed in byte[] in place!
     *
     * @param data the string data to deserialize
     * @param desc
     * @return
     */
    public static String getString(byte[] data, boolean desc){
        if(data.length==0) return null;
        if(data.length>0 && data[0] == 0x01) return "";

        for(int i=0;i<data.length;i++){
            byte datum = data[i];
            if(desc)
                datum ^= 0xff;
            data[i] = (byte)(datum-2);
        }
        return Bytes.toString(data);
    }

    public static String getStringCopy(byte[] data,int offset,int length, boolean desc){
        byte[] dataToCopy = new byte[length];
        System.arraycopy(data,offset,dataToCopy,0,length);
        return getString(dataToCopy,desc);
    }

    public static String getStringCopy(ByteBuffer buffer,boolean desc){
        byte[] dataToCopy = new byte[buffer.remaining()];
        buffer.get(dataToCopy);
        return getString(dataToCopy,desc);
    }

}