package me.poplaris.rabbitmq.client;

/**
 * User: poplar,jm
 * Date: 21-6-21 下午5:59
 */

public interface EventProcesser {
    void process(Object e);
}
