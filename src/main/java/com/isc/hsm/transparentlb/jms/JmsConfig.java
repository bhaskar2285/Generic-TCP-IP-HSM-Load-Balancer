package com.isc.hsm.transparentlb.jms;

import com.isc.hsm.transparentlb.config.LbProperties;
import jakarta.jms.ConnectionFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jms.listener.DefaultMessageListenerContainer;

@Configuration
public class JmsConfig {

    @Bean
    public DefaultMessageListenerContainer hsmListenerContainer(
            LbProperties props,
            HsmRequestListener listener,
            ConnectionFactory connectionFactory) {

        LbProperties.JmsConfig jmsCfg = props.getJms();
        DefaultMessageListenerContainer container = new DefaultMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.setDestinationName(props.getQueue().getInbound());
        container.setMessageListener(listener);
        container.setConcurrentConsumers(jmsCfg.getConcurrentConsumers());
        container.setMaxConcurrentConsumers(jmsCfg.getMaxConcurrentConsumers());
        container.setSessionTransacted(false);
        return container;
    }
}
