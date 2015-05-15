package com.marketstem.services.zookeeper;

import com.marketstem.config.MarketstemS3cured;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;

public enum Curators implements MarketstemS3cured {

  MARKETSTEM;

  private final CuratorFramework client;

  private Curators() {
    client =
        CuratorFrameworkFactory.builder().namespace(name().toLowerCase())
            .connectString(getEndpoint()).retryPolicy(new ExponentialBackoffRetry(1000, 3)).build();
  }

  public CuratorFramework getClient() {
    return client;
  }
}
