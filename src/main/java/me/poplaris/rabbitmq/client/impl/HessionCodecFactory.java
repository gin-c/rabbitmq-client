package me.poplaris.rabbitmq.client.impl;

import com.alibaba.fastjson.JSONObject;
import com.caucho.hessian.io.HessianInput;
import com.caucho.hessian.io.HessianOutput;
import me.poplaris.rabbitmq.client.CodecFactory;
import org.apache.log4j.Logger;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * User: poplar,jm
 * Date: 21-6-21 下午5:59
 */
public class HessionCodecFactory implements CodecFactory {

    private final Logger logger = Logger.getLogger(HessionCodecFactory.class);

    @Override
    public byte[] serialize(Object obj) throws IOException {
        return JSONObject.toJSONBytes(obj);
    }

    @Override
    public Object deSerialize(byte[] in) throws IOException {
        return JSONObject.parseObject(in, Object.class);
    }

}
