package com.marketstem.services.rest.util;

import com.fabahaba.fava.logging.Loggable;
import com.google.common.base.Optional;
import com.newrelic.api.agent.NewRelic;

public class NewRelicUtils {

  public static void addOptionalCustomNumberParameter(final String key,
      final Optional<? extends Number> value) {
    NewRelicUtils.addCustomNumberParameter(key, value.orNull());
  }

  public static void addCustomNumberParameter(final String key, final Number value) {
    if (value == null)
      return;
    try {
      NewRelic.addCustomParameter(key, value);
    } catch (final Exception e) {
      Loggable.logCatching(NewRelic.class, e);
    }
  }

  public static void
      addOptionalCustomStringParameter(final String key, final Optional<String> value) {
    NewRelicUtils.addCustomParameter(key, value.orNull());
  }

  public static void addCustomParameter(final String key, final Object value) {
    if (value == null)
      return;
    try {
      NewRelic.addCustomParameter(key, value.toString());
    } catch (final Exception e) {
      Loggable.logCatching(NewRelic.class, e);
    }
  }
}
