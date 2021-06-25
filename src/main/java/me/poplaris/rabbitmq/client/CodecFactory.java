package me.poplaris.rabbitmq.client;

import java.io.IOException;

/**
 * User: poplar,jm
 * Date: 21-6-21 下午5:59
 * 编码和解码工厂
 */
public interface CodecFactory {

	byte[] serialize(Object obj) throws IOException;

	Object deSerialize(byte[] in) throws IOException;

}
