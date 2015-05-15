package com.marketstem.services.rest;

import io.dropwizard.Configuration;
import io.dropwizard.jetty.ConnectorFactory;
import io.dropwizard.jetty.HttpConnectorFactory;
import io.dropwizard.server.DefaultServerFactory;

import java.util.List;

public class MarketStemConfiguration extends Configuration {

  public MarketStemConfiguration() {
    super();
    final List<ConnectorFactory> connectorFactories =
        ((DefaultServerFactory) getServerFactory()).getApplicationConnectors();
    ((HttpConnectorFactory) connectorFactories.get(0)).setPort(8081);

    // Keystores.MARKETSTEM_COM.ensureExists();
    //
    // final HttpsConnectorFactory httpsConfig = new HttpsConnectorFactory();
    // httpsConfig.setPort( 8443 );
    // httpsConfig.setKeyStorePath( Keystores.MARKETSTEM_COM.getCompleteFilePath() );
    // httpsConfig.setKeyStorePassword( Keystores.MARKETSTEM_COM.getPass() );
    // httpsConfig.setKeyStoreType( "JKS" );
    // httpsConfig.setValidateCerts( false );
    // connectorFactories.add( httpsConfig );

    ((HttpConnectorFactory) ((DefaultServerFactory) getServerFactory()).getAdminConnectors().get(0))
        .setPort(8082);
  }
}
