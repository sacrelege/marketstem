package com.marketstem.database.redis;

import com.fabahaba.fava.func.HostPortSupplier;
import com.fabahaba.fava.logging.Loggable;
import com.fabahaba.jedipus.ExtendedJedisPoolConfig;
import com.fabahaba.jedipus.JedisExecutor;
import com.fabahaba.jedipus.JedisSentinelPoolExecutor;
import com.marketstem.config.MarketstemS3cured;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.Protocol;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

public enum RedisExecutor implements MarketstemS3cured, JedisExecutor, Loggable {

  MARKETSTEM("slanger-master", 0);

  private final transient JedisSentinelPoolExecutor redisExecutor;

  private RedisExecutor(final String masterName) {
    this(masterName, 0);
  }

  private RedisExecutor(final String masterName, final int db) {
    this.redisExecutor =
        ExtendedJedisPoolConfig
            .getDefaultConfig()
            .withConnectionTimeoutMillis(4000)
            .withMaxTotal(50)
            .buildExecutor(
                masterName,
                db,
                HostPortSupplier.appendPort(Protocol.DEFAULT_SENTINEL_PORT, Collectors.toList(),
                    getEndpoint().split(",")), getPass());
  }

  private JedisExecutor getRedisExecutor() {
    return redisExecutor;
  }

  @Override
  public void acceptJedis(final Consumer<Jedis> jedisConsumer) {
    getRedisExecutor().acceptJedis(jedisConsumer);
  }

  @Override
  public <T> T applyJedis(final Function<Jedis, T> jedisFunc) {
    return getRedisExecutor().applyJedis(jedisFunc);
  }
}
