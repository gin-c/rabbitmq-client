package me.poplaris.rabbitmq.client;

public interface EventController {

    /**
     * 控制器启动方法
     */
    void start();

    /**
     * 获取发送模版
     */
    EventTemplate getEopEventTemplate();

    /**
     * 绑定消费程序到对应的exchange和queue
     */
    EventController add(String queueName, String exchangeName, String routingKey, EventProcesser eventProcesser);

    /**
     * 取消绑定
     */
    void delBinding(String exchangeName, String queueName, String routingKey);
}
