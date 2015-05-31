package com.marketstem.services.rest.testing;

import com.fabahaba.runscope.client.RunscopeClient;
import com.marketstem.config.MarketstemS3cured;

import feign.RequestInterceptor;

public enum RunscopeClients implements MarketstemS3cured {

  MARKETSTEM;

  public RunscopeClient create(final RequestInterceptor... requestInterceptors) {
    return new RunscopeClient(getSecret(), requestInterceptors);
  }
}
