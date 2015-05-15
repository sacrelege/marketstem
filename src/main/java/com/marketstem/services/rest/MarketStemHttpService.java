package com.marketstem.services.rest;

import com.fabahaba.fava.service.dropwizard.ApplicationService;

public class MarketStemHttpService extends ApplicationService<MarketStemHttpApplication> {

  public MarketStemHttpService() {
    super(new MarketStemHttpApplication(), "server");
  }

  private static final MarketStemHttpService singleton = new MarketStemHttpService();

  public static MarketStemHttpService getService() {
    return singleton;
  }
}
