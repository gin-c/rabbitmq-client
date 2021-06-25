package me.poplaris.rabbitmq.client.impl;

import me.poplaris.rabbitmq.client.CodecFactory;
import me.poplaris.rabbitmq.client.EventTemplate;
import me.poplaris.rabbitmq.client.exception.SendRefuseException;
import me.poplaris.rabbitmq.client.util.StringUtils;
import org.apache.log4j.Logger;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.AmqpTemplate;

import java.io.IOException;

/**
 * User: poplar,jm
 * Date: 21-6-21 下午5:59
 */
public class DefaultEventTemplate implements EventTemplate {

    private static final Logger logger = Logger.getLogger(DefaultEventTemplate.class);

    private AmqpTemplate eventAmqpTemplate;

    private CodecFactory defaultCodecFactory;

    private DefaultEventController eec;

    public DefaultEventTemplate(AmqpTemplate eopAmqpTemplate,
                                CodecFactory defaultCodecFactory, DefaultEventController eec) {
        this.eventAmqpTemplate = eopAmqpTemplate;
        this.defaultCodecFactory = defaultCodecFactory;
        this.eec = eec;
    }

    public DefaultEventTemplate(AmqpTemplate eopAmqpTemplate, CodecFactory defaultCodecFactory) {
        this.eventAmqpTemplate = eopAmqpTemplate;
        this.defaultCodecFactory = defaultCodecFactory;
    }

    @Override
    public void send(String exchangeName, String routingKey, Object eventContent)
            throws SendRefuseException {
        this.send(exchangeName, routingKey, eventContent, defaultCodecFactory);
    }

    @Override
    public void send(String exchangeName, String routingKey, Object eventContent,
                     CodecFactory codecFactory) throws SendRefuseException {
        if (StringUtils.isEmpty(routingKey) || StringUtils.isEmpty(exchangeName)) {
            throw new SendRefuseException("routingKey exchangeName can not be empty.");
        }

        eec.declareExchange(exchangeName);

        byte[] eventContentBytes = null;
        if (codecFactory == null) {
            if (eventContent == null) {
                logger.warn("Find eventContent is null,are you sure...");
            } else {
                throw new SendRefuseException(
                        "codecFactory must not be null ,unless eventContent is null");
            }
        } else {
            try {
                eventContentBytes = codecFactory.serialize(eventContent);
            } catch (IOException e) {
                throw new SendRefuseException(e);
            }
        }

        try {
            eventAmqpTemplate.convertAndSend(exchangeName, routingKey, eventContentBytes);
        } catch (AmqpException e) {
            logger.error("send event fail. Event Message : [" + eventContent + "]", e);
            throw new SendRefuseException("send event fail", e);
        }
    }
}
