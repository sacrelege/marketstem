package com.marketstem.messaging;

import com.fabahaba.fava.system.HostUtils;
import com.fabahaba.kafka.SimpleKafkaConsumer;
import com.fabahaba.kafka.SimpleKafkaConsumerFactory;
import com.google.common.collect.ImmutableMap;
import com.marketstem.config.MarketstemS3cured;

import org.apache.kafka.common.serialization.StringDeserializer;

import java.util.concurrent.ExecutorService;

import kafka.serializer.DefaultEncoder;

public enum KafkaConsumers implements MarketstemS3cured {

  MARKETSTEM;

  private final transient SimpleKafkaConsumerFactory consumerFactory;

  private KafkaConsumers() {
    this(ImmutableMap.<String, Object>builder());
  }

  private KafkaConsumers(final ImmutableMap.Builder<String, Object> propsBuilder) {
    this.consumerFactory =
        new SimpleKafkaConsumerFactory(propsBuilder.put("zookeeper.connect", getEndpoint())
            .put("group.id", name() + "-" + HostUtils.HOST_NAME)
            .put("key.serializer.class", StringDeserializer.class)
            .put("serializer.class", DefaultEncoder.class).build());
  }


  public SimpleKafkaConsumer createConsumer(final ExecutorService consumerExecutorService) {
    return consumerFactory.createConsumer(consumerExecutorService);
  }

}
