package me.poplaris.rabbitmq.client;

import me.poplaris.rabbitmq.client.exception.SendRefuseException;

/**
 * User: poplar,jm
 * Date: 21-6-21 下午5:59
 */

public interface EventTemplate {

    void send(String exchangeName, String topic, Object eventContent) throws SendRefuseException;

    void send(String exchangeName, String topic, Object eventContent, CodecFactory codecFactory) throws SendRefuseException;
}
