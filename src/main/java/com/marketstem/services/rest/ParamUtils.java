package com.marketstem.services.rest;

import com.fabahaba.fava.logging.Loggable;
import com.fabahaba.fava.serialization.gson.GsonUtils;
import com.fabahaba.fava.serialization.gson.InstantIso8601JsonMarshaller;
import com.google.common.base.Optional;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import org.apache.commons.lang3.StringUtils;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;

import java.lang.reflect.Type;
import java.time.Instant;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

public interface ParamUtils extends Loggable {

  static final Gson DEFAULT_GSON = new GsonBuilder().enableComplexMapKeySerialization()
      .disableHtmlEscaping().setDateFormat(GsonUtils.ISO_8601_DATE_FORMAT)
      .registerTypeAdapter(Instant.class, new InstantIso8601JsonMarshaller()).create();

  default String cleanStringListParam(final Optional<String> stringListParam) {
    return stringListParam.transform(this::cleanStringListParam).or("");
  }

  default String cleanStringListParam(final String stringListParam) {
    return stringListParam.replaceAll("\\s+", "").toUpperCase();
  }

  default String cleanStringListParamNoUppercase(final String stringListParam) {
    return stringListParam.replaceAll("\\s+", "");
  }

  default Set<String> commaListToSetAndRemoveSpaces(final String stringListParam) {
    return Arrays.stream(cleanStringListParamNoUppercase(stringListParam).split(",")).collect(
        Collectors.toSet());
  }

  default Set<String> commaListToSet(final String stringListParam) {
    return Arrays.stream(stringListParam.split(",")).collect(Collectors.toSet());
  }

  default Gson getGson() {
    return DEFAULT_GSON;
  }

  default <T> T deserializeFormParamJson(final String formParam, final Type type) {
    if (formParam == null || formParam.isEmpty())
      throw new WebApplicationException(Response.status(Response.Status.BAD_REQUEST)
          .entity("Request body must be supplied").type("text/plain").build());

    try {
      final T formParamObject = getGson().fromJson(formParam, type);
      if (formParamObject != null)
        return formParamObject;
    } catch (final Exception e) {
      catching(e);
    }
    throw new WebApplicationException(Response.status(Response.Status.BAD_REQUEST)
        .entity("Unable to deserialize message body json: " + formParam).type("text/plain").build());
  }

  default String getRequestIp(final HttpServletRequest request) {
    final String ip = request.getHeader("X-Forwarded-For");
    if (StringUtils.isNotBlank(ip)) {
      final int multipleIpsIndex = ip.lastIndexOf(',');
      return StringUtils.trim(multipleIpsIndex > 0 ? ip.substring(multipleIpsIndex + 1) : ip);
    }
    return request.getRemoteAddr();
  }
}
