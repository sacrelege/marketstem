package com.marketstem.services.rest;

import com.fabahaba.dropwizard.service.ApplicationService;

public class MarketstemApplicationService extends ApplicationService<MarketstemApplication> {

  public MarketstemApplicationService() {
    super(new MarketstemApplication(), "server");
  }

  private static final MarketstemApplicationService singleton = new MarketstemApplicationService();

  public static MarketstemApplicationService getService() {
    return singleton;
  }
}
