package me.poplaris.rabbitmq.client.impl;

import me.poplaris.rabbitmq.client.*;
import me.poplaris.rabbitmq.client.util.StringUtils;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.amqp.rabbit.listener.adapter.MessageListenerAdapter;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.amqp.support.converter.SerializerMessageConverter;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * User: poplar,jm
 * Date: 21-6-21 下午5:59
 * <p>
 * 和rabbitmq通信的控制器，主要负责：
 * <p>1、和rabbitmq建立连接</p>
 * <p>2、声明exChange和queue以及它们的绑定关系</p>
 * <p>3、启动消息监听容器，并将不同消息的处理者绑定到对应的exchange和queue上</p>
 * <p>4、持有消息发送模版以及所有exchange、queue和绑定关系的本地缓存</p>
 */
public class DefaultEventController implements EventController {

    private CachingConnectionFactory rabbitConnectionFactory;

    private final EventControlConfig config;

    private RabbitAdmin rabbitAdmin;

    private final CodecFactory defaultCodecFactory = new HessionCodecFactory();

    private SimpleMessageListenerContainer msgListenerContainer; // rabbitMQ msg listener container

    private final MessageAdapterHandler msgAdapterHandler = new MessageAdapterHandler();

    //queue cache, key is exchangeName
    private final Map<String, TopicExchange> exchanges = new HashMap<String, TopicExchange>();
    //queue cache, key is queueName
    private final Map<String, Queue> queues = new HashMap<String, Queue>();
    //bind relation of queue to exchange cache, value is exchangeName | queueName | routingKey
    private final Set<String> binded = new HashSet<String>();

    private EventTemplate eventTemplate; // 给App使用的Event发送客户端

    private final AtomicBoolean isStarted = new AtomicBoolean(false);

    private static DefaultEventController defaultEventController;

    public synchronized static DefaultEventController getInstance(EventControlConfig config) {
        if (defaultEventController == null) {
            defaultEventController = new DefaultEventController(config);
        }
        return defaultEventController;
    }

    public synchronized static DefaultEventController getInstance() {
        if (defaultEventController == null) {
            throw new NullPointerException("DefaultEventController is null");
        }
        return defaultEventController;
    }


    private DefaultEventController(EventControlConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("Config can not be null.");
        }
        this.config = config;
        initRabbitConnectionFactory();
        // 初始化AmqpAdmin
        rabbitAdmin = new RabbitAdmin(rabbitConnectionFactory);
        // 初始化RabbitTemplate
        RabbitTemplate rabbitTemplate = new RabbitTemplate(rabbitConnectionFactory);
        eventTemplate = new DefaultEventTemplate(rabbitTemplate, defaultCodecFactory, this);
    }

    /**
     * 初始化rabbitmq连接
     */
    private void initRabbitConnectionFactory() {
        rabbitConnectionFactory = new CachingConnectionFactory();
        rabbitConnectionFactory.setHost(config.getServerHost());
        rabbitConnectionFactory.setChannelCacheSize(config.getEventMsgProcessNum());
        rabbitConnectionFactory.setPort(config.getPort());
        rabbitConnectionFactory.setUsername(config.getUsername());
        rabbitConnectionFactory.setPassword(config.getPassword());
        if (!StringUtils.isEmpty(config.getVirtualHost())) {
            rabbitConnectionFactory.setVirtualHost(config.getVirtualHost());
        }
    }

    /**
     * 注销程序
     */
    public synchronized void destroy() throws Exception {
        if (!isStarted.get()) {
            return;
        }
        msgListenerContainer.stop();
        eventTemplate = null;
        rabbitAdmin = null;
        rabbitConnectionFactory.destroy();
    }

    @Override
    public void start() {
        if (isStarted.get()) {
            return;
        }
        initMsgListenerAdapter();
        isStarted.set(true);
    }

    /**
     * 初始化消息监听器容器
     */
    private void initMsgListenerAdapter() {
        Set<String> mapping = msgAdapterHandler.getAllBinding();
        for (String relation : mapping) {
            String[] relaArr = relation.split("\\|");
            declareBinding(relaArr[1], relaArr[0], relaArr[2]);
        }

        MessageListener listener = new MessageListenerAdapter(msgAdapterHandler);
        msgListenerContainer = new SimpleMessageListenerContainer();
        msgListenerContainer.setConnectionFactory(rabbitConnectionFactory);
        msgListenerContainer.setAcknowledgeMode(AcknowledgeMode.AUTO);
        msgListenerContainer.setMessageListener(listener);
        msgListenerContainer.setErrorHandler(new MessageErrorHandler());
        msgListenerContainer.setPrefetchCount(config.getPrefetchSize()); // 设置每个消费者消息的预取值
        msgListenerContainer.setConcurrentConsumers(config.getEventMsgProcessNum());
        msgListenerContainer.setQueues(queues.values().toArray(new Queue[queues.size()]));
        msgListenerContainer.start();
    }

    @Override
    public EventTemplate getEopEventTemplate() {
        return eventTemplate;
    }

    @Override
    public EventController add(String queueName, String exchangeName, String routingKey, EventProcesser eventProcesser) {
        return add(queueName, exchangeName, routingKey, eventProcesser, defaultCodecFactory);
    }

    @Override
    public synchronized void delBinding(String queueName, String exchangeName, String routingKey) {
        msgAdapterHandler.del(queueName, exchangeName, routingKey);

        String bindRelation = exchangeName + "|" + queueName + "|" + routingKey;
        if (!binded.contains(bindRelation)) return;

        TopicExchange topicExchange = exchanges.get(exchangeName);
        if (topicExchange == null) {
            return;
        }

        Queue queue = queues.get(queueName);
        if (queue == null) {
            return;
        }

        Binding binding = BindingBuilder.bind(queue).to(topicExchange).with(routingKey);//将queue绑定到exchange
        rabbitAdmin.removeBinding(binding);//删除绑定关系
        binded.remove(bindRelation);

        queues.remove(queueName);   // 删除队列
        rabbitAdmin.deleteQueue(queueName);

        if (isStarted.get()) {
            initMsgListenerAdapter();
        }
    }

    public EventController add(String queueName, String exchangeName, String routingKey, EventProcesser eventProcesser, CodecFactory codecFactory) {
        msgAdapterHandler.add(queueName, exchangeName, routingKey, eventProcesser, defaultCodecFactory);
        if (isStarted.get()) {
            initMsgListenerAdapter();
        }
        return this;
    }

    /**
     * exchange和queue是否已经绑定
     */
    protected boolean beBinded(String exchangeName, String queueName, String routingKey) {
        return binded.contains(exchangeName + "|" + queueName + "|" + routingKey);
    }

    /**
     * 声明exchange
     */
    protected synchronized void declareExchange(String exchangeName) {
        TopicExchange topicExchange = exchanges.get(exchangeName);
        if (topicExchange == null) {
            topicExchange = new TopicExchange(exchangeName, true, false, null);
            exchanges.put(exchangeName, topicExchange);
            rabbitAdmin.declareExchange(topicExchange);//声明exchange
        }
    }

    /**
     * 声明exchange和queue已经它们的绑定关系
     */
    protected synchronized void declareBinding(String exchangeName, String queueName, String routingKey) {
        String bindRelation = exchangeName + "|" + queueName + "|" + routingKey;
        if (binded.contains(bindRelation)) return;

        boolean needBinding = false;
        TopicExchange topicExchange = exchanges.get(exchangeName);
        if (topicExchange == null) {
            topicExchange = new TopicExchange(exchangeName, true, false, null);
            exchanges.put(exchangeName, topicExchange);
            rabbitAdmin.declareExchange(topicExchange);//声明exchange
            needBinding = true;
        }

        Queue queue = queues.get(queueName);
        if (queue == null) {
            queue = new Queue(queueName, true, false, false);
            queues.put(queueName, queue);
            rabbitAdmin.declareQueue(queue);    //声明queue
            needBinding = true;
        }

        if (needBinding) {
            Binding binding = BindingBuilder.bind(queue).to(topicExchange).with(routingKey);//将queue绑定到exchange
            rabbitAdmin.declareBinding(binding);//声明绑定关系
            binded.add(bindRelation);
        }
    }

}
