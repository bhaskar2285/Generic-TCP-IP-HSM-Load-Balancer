package com.isc.hsm.transparentlb.jms;

import com.isc.hsm.transparentlb.handler.PassthroughHandler;
import jakarta.jms.BytesMessage;
import jakarta.jms.Destination;
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import jakarta.jms.MessageListener;
import jakarta.jms.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Component;

@Component
public class HsmRequestListener implements MessageListener {

    private static final Logger log = LoggerFactory.getLogger(HsmRequestListener.class);

    private final PassthroughHandler handler;
    private final JmsTemplate jmsTemplate;

    public HsmRequestListener(PassthroughHandler handler, JmsTemplate jmsTemplate) {
        this.handler = handler;
        this.jmsTemplate = jmsTemplate;
    }

    @Override
    public void onMessage(Message message) {
        String correlationId = null;
        Destination replyTo = null;
        try {
            correlationId = message.getJMSCorrelationID();
            replyTo = message.getJMSReplyTo();

            byte[] rawCommand = extractBytes(message);
            if (rawCommand == null || rawCommand.length == 0) {
                log.warn("Received empty message, correlationId={}", correlationId);
                return;
            }

            byte[] response = handler.handle(rawCommand);
            sendReply(replyTo, correlationId, response);

        } catch (Exception e) {
            log.error("Failed to process HSM request correlationId={}: {}", correlationId, e.getMessage(), e);
            // Send error indicator back to caller if replyTo is set
            if (replyTo != null && correlationId != null) {
                sendErrorReply(replyTo, correlationId);
            }
        }
    }

    private byte[] extractBytes(Message message) throws JMSException {
        if (message instanceof BytesMessage bm) {
            byte[] buf = new byte[(int) bm.getBodyLength()];
            bm.readBytes(buf);
            return buf;
        }
        log.warn("Received non-BytesMessage: {}", message.getClass().getSimpleName());
        return null;
    }

    private void sendReply(Destination replyTo, String correlationId, byte[] response) {
        if (replyTo == null) {
            log.debug("No replyTo destination, dropping response for correlationId={}", correlationId);
            return;
        }
        final String corrId = correlationId;
        jmsTemplate.send(replyTo, session -> {
            BytesMessage reply = session.createBytesMessage();
            reply.writeBytes(response);
            if (corrId != null) reply.setJMSCorrelationID(corrId);
            return reply;
        });
    }

    private void sendErrorReply(Destination replyTo, String correlationId) {
        // Send a minimal error frame — caller can detect by 2-byte length = 0
        jmsTemplate.send(replyTo, session -> {
            BytesMessage reply = session.createBytesMessage();
            reply.writeBytes(new byte[]{0, 0}); // zero-length body signals error
            reply.setJMSCorrelationID(correlationId);
            return reply;
        });
    }
}
