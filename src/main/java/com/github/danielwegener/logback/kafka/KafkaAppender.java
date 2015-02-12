package com.github.danielwegener.logback.kafka;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.filter.Filter;
import ch.qos.logback.core.spi.FilterReply;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.KafkaException;
import org.apache.kafka.common.serialization.ByteArraySerializer;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.HashMap;

public class KafkaAppender<E extends ILoggingEvent> extends KafkaAppenderConfig<E> {

    public KafkaAppender() {
        addFilter(new Filter<E>() {
            @Override
            public FilterReply decide(E event) {
                if (event.getLoggerName().startsWith(KAFKA_LOGGER_PREFIX)) {
                    return FilterReply.DENY;
                }
                return FilterReply.NEUTRAL;
            }
        });
    }

    /**
     * Kafka clients uses this prefix for its slf4j logging.
     * This appender should never ever log any of its logs since it could cause harmful infinite recursion.
     */
    private static final String KAFKA_LOGGER_PREFIX = "org.apache.kafka.clients";

    private static final Charset UTF8 = Charset.forName("UTF-8");

    private KafkaProducer<byte[], byte[]> producer = null;

    private String topic = null;
    private byte[] hostnameHash = null;

    @Override
    protected void append(E e) {
        final String message = layout.doLayout(e);
        final byte[] payload = message.getBytes(charset);
        final byte[] key = partitioningStrategy.createKey(e, hostnameHash);
        final ProducerRecord<byte[], byte[]> record = new ProducerRecord<byte[],byte[]>(topic, key, payload);
        producer.send(record);
    }




    @Override
    public void start() {
        if (this.topic == null)
            this.topic = context.getProperty("topic");

        if (!checkPrerequisites()) return;

        final String hostname = context.getProperty("HOSTNAME");
        if (hostname != null) {
            hostnameHash = ByteBuffer.allocate(4).putInt(hostname.hashCode()).array();
        } else {
            this.addWarn("Could not determine hostname. PartitionStrategy HOSTNAME will not work.");
        }

        if (charset == null) {
            addInfo("No charset specified. Using default UTF8 encoding.");
            charset = UTF8;
        }

        if (partitioningStrategy == null) {
            addInfo("No partitionStrategy defined. Using default ROUND_ROBIN strategy.");
            partitioningStrategy = PartitioningStrategy.ROUND_ROBIN;
        }

        final ByteArraySerializer serializer = new ByteArraySerializer();
        producer = new  KafkaProducer<byte[], byte[]>(new HashMap<String, Object>(context.getCopyOfPropertyMap()), serializer, serializer);

        super.start();
    }

    @Override
    public void stop() {
        if (producer != null) {
            try {
                producer.close();
            } catch (KafkaException e) {
                this.addWarn("Failed to shut down kafka producer: " + e.getMessage(), e);
            }
            producer = null;
        }
        super.stop();
    }

    @Override
    public boolean isStarted() {
        return super.isStarted();
    }



}
