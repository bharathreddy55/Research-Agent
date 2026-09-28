package com.researchagent.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String RESEARCH_EXCHANGE = "research.exchange";
    public static final String RESEARCH_JOBS_QUEUE = "research.jobs.queue";
    public static final String RESEARCH_ROUTING_KEY = "research.job";

    public static final String RESEARCH_DLX = "research.dlx";
    public static final String RESEARCH_DLQ = "research.dlq";
    public static final String RESEARCH_DLQ_ROUTING_KEY = "research.dlq";

    @Bean
    public DirectExchange researchExchange() {
        return new DirectExchange(RESEARCH_EXCHANGE, true, false);
    }

    @Bean
    public DirectExchange deadLetterExchange() {
        return new DirectExchange(RESEARCH_DLX, true, false);
    }

    @Bean
    public Queue deadLetterQueue() {
        return QueueBuilder.durable(RESEARCH_DLQ).build();
    }

    @Bean
    public Binding deadLetterBinding(Queue deadLetterQueue, DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(deadLetterQueue).to(deadLetterExchange).with(RESEARCH_DLQ_ROUTING_KEY);
    }

    @Bean
    public Queue researchJobsQueue() {
        return QueueBuilder.durable(RESEARCH_JOBS_QUEUE)
                .withArgument("x-dead-letter-exchange", RESEARCH_DLX)
                .withArgument("x-dead-letter-routing-key", RESEARCH_DLQ_ROUTING_KEY)
                .build();
    }

    @Bean
    public Binding researchJobsBinding(Queue researchJobsQueue, DirectExchange researchExchange) {
        return BindingBuilder.bind(researchJobsQueue).to(researchExchange).with(RESEARCH_ROUTING_KEY);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter());
        return template;
    }
}
