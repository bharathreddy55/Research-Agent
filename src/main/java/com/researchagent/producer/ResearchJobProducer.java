package com.researchagent.producer;

import com.researchagent.config.RabbitMQConfig;
import com.researchagent.dto.ResearchJobMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

@Service
public class ResearchJobProducer {
    private static final Logger log = LoggerFactory.getLogger(ResearchJobProducer.class);

    private final RabbitTemplate rabbitTemplate;

    public ResearchJobProducer(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void enqueueJob(String runId, String userId, String query, String depth, long budgetCapPaise) {
        ResearchJobMessage message = new ResearchJobMessage(runId, userId, query, depth, budgetCapPaise);
        log.info("Enqueuing research job for run {} to RabbitMQ exchange {}", runId, RabbitMQConfig.RESEARCH_EXCHANGE);
        rabbitTemplate.convertAndSend(RabbitMQConfig.RESEARCH_EXCHANGE, RabbitMQConfig.RESEARCH_ROUTING_KEY, message);
    }
}
