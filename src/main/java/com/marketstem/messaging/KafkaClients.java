package com.marketstem.messaging;

import com.fabahaba.fava.system.HostUtils;
import com.fabahaba.kafka.SimpleKafkaProducer;
import com.google.common.collect.ImmutableMap;
import com.marketstem.config.MarketstemS3cured;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;

public enum KafkaClients implements MarketstemS3cured, SimpleKafkaProducer {

  MARKETSTEM(ImmutableMap.<String, Object>builder().put(ProducerConfig.ACKS_CONFIG, "all")
      .put(ProducerConfig.RETRIES_CONFIG, 1));

  private final KafkaProducer<String, String> producer;

  private KafkaClients(final ImmutableMap.Builder<String, Object> producerPropsBuilder) {

    producer =
        new KafkaProducer<>(producerPropsBuilder
            .put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, getEndpoint())
            .put(ProducerConfig.CLIENT_ID_CONFIG, HostUtils.HOST_NAME)
            .put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class)
            .put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class).build());
  }

  @Override
  public KafkaProducer<String, String> getProducer() {
    return producer;
  }
}
