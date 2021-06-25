package me.poplaris.rabbitmq.client.impl;


import com.rabbitmq.client.Channel;
import me.poplaris.rabbitmq.client.CodecFactory;
import me.poplaris.rabbitmq.client.EventProcesser;
import me.poplaris.rabbitmq.client.util.StringUtils;
import org.apache.log4j.Logger;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageListener;
import org.springframework.amqp.rabbit.listener.api.ChannelAwareMessageListener;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * MessageListenerAdapter的Pojo
 * <p>消息处理适配器，主要功能：</p>
 * <p>1、将不同的消息类型绑定到对应的处理器并缓存，如将queue+exchange的消息统一交由A处理器来出来</p>
 * <p>2、执行消息的消费分发，调用相应的处理器来消费属于它的消息</p>
 * <p>
 * User: poplar,jm
 * Date: 21-6-21 下午5:59
 */
public class MessageAdapterHandler implements MessageListener {

    private static final Logger logger = Logger.getLogger(MessageAdapterHandler.class);

    private ConcurrentMap<String, EventProcessorWrap> epwMap;

    public MessageAdapterHandler() {
        this.epwMap = new ConcurrentHashMap<String, EventProcessorWrap>();
    }

    @Override
    public void onMessage(Message message) {
        String s = new String(message.getBody());
        logger.debug("Receive an EventMessage: " + s);
        String queueName = message.getMessageProperties().getConsumerQueue();
        String exchangeName = message.getMessageProperties().getReceivedExchange();
        String routingKey = message.getMessageProperties().getReceivedRoutingKey();
        // 先要判断接收到的message是否是空的，在某些异常情况下，会产生空值
        if (message == null) {
            logger.warn("Receive an null EventMessage, it may product some errors, and processing message is canceled.");
            return;
        }
        // 解码，并交给对应的EventHandle执行
        EventProcessorWrap eepw = epwMap.get(queueName + "|" + exchangeName + "|" + routingKey);
        if (eepw == null) {
            logger.warn("Receive an EopEventMessage, but no processor can do it.");
            return;
        }
        try {
            eepw.process(message.getBody());
        } catch (IOException e) {
            logger.error("Event content can not be Deserialized, check the provided CodecFactory.", e);
            return;
        }
    }

    protected void add(String queueName, String exchangeName, String routingKey, EventProcesser processor, CodecFactory codecFactory) {
        if (StringUtils.isEmpty(queueName) || StringUtils.isEmpty(exchangeName) || StringUtils.isEmpty(routingKey) || processor == null || codecFactory == null) {
            throw new RuntimeException("queueName and exchangeName can not be empty,and processor or codecFactory can not be null. ");
        }
        EventProcessorWrap epw = new EventProcessorWrap(codecFactory, processor);
        EventProcessorWrap oldProcessorWrap = epwMap.putIfAbsent(queueName + "|" + exchangeName + "|" + routingKey, epw);
        if (oldProcessorWrap != null) {
            logger.warn("The processor of this queue and exchange exists, and the new one can't be add");
        }
    }

    protected void del(String queueName, String exchangeName, String routingKey) {
        if (StringUtils.isEmpty(queueName) || StringUtils.isEmpty(exchangeName) || StringUtils.isEmpty(routingKey)) {
            throw new RuntimeException("queueName and exchangeName can not be empty. ");
        }
        epwMap.remove(queueName + "|" + exchangeName + "|" + routingKey);
    }

    protected Set<String> getAllBinding() {
        Set<String> keySet = epwMap.keySet();
        return keySet;
    }

    protected static class EventProcessorWrap {

        private CodecFactory codecFactory;

        private EventProcesser eep;

        protected EventProcessorWrap(CodecFactory codecFactory,
                                     EventProcesser eep) {
            this.codecFactory = codecFactory;
            this.eep = eep;
        }

        public void process(byte[] eventData) throws IOException {
            Object obj = codecFactory.deSerialize(eventData);
            eep.process(obj);
        }
    }
}
