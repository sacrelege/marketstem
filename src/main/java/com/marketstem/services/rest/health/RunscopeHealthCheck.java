package com.marketstem.services.rest.health;

import com.codahale.metrics.health.HealthCheck;
import com.fabahaba.fava.concurrent.ExecutorUtils;
import com.fabahaba.fava.func.Retryable;
import com.fabahaba.fava.logging.Loggable;
import com.fabahaba.runscope.client.RunscopeClient;
import com.fabahaba.runscope.data.bucket.RunscopeBucket;
import com.fabahaba.runscope.data.radar.tests.RunscopeTest;
import com.fabahaba.runscope.data.radar.tests.RunscopeTestRunDetail;
import com.fabahaba.runscope.data.radar.tests.RunscopeTriggerResponseData;
import com.fabahaba.runscope.data.radar.tests.RunscopeTriggeredTestRun;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.RateLimiter;
import com.google.common.util.concurrent.Uninterruptibles;
import com.marketstem.services.rest.resources.DeploymentResource;

import feign.FeignException;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class RunscopeHealthCheck extends HealthCheck implements Retryable {

  private static final int DEFAULT_NUM_TEST_INIT_RETRIES = 3;
  private static final int DEFAULT_NUM_CHECK_TEST_RETRIES = 10;
  private static final Duration DEFAULT_INITIAL_DELAY_AND_RETRY_CHECK_TEST_DURATION = Duration
      .ofMillis(1000);

  private final RunscopeClient runscope;
  private final Map<String, Collection<String>> triggerIds;

  private final RateLimiter healthyRunTestsRateLimiter;
  private final RateLimiter unhealthyRunTestsRateLimiter;
  private final ExecutorService triggerCheckTestsExecutor;
  private final boolean ignoreNonTestFailuresOnceHealthy;
  private final int numTestInitRetries;
  private final int numCheckTestRetries;
  private final long initialAndRetryDelayCheckTestDurationNanos;

  private Result previousHealthResult;
  private volatile CompletableFuture<Result> currentHealthResult;

  private RunscopeHealthCheck(final RunscopeClient runscope,
      final Map<String, Collection<String>> triggerIds, final RateLimiter runTestsRateLimiter,
      final RateLimiter unhealthyRunTestsRateLimiter, final ExecutorService executor,
      final boolean ignoreNonTestFailuresOnceHealthy, final int numTestInitRetries,
      final int numCheckTestRetries, final Duration initialDelayAndRetryCheckTestDuration) {
    this.runscope = runscope;
    this.triggerIds = triggerIds;
    this.healthyRunTestsRateLimiter = runTestsRateLimiter;
    this.unhealthyRunTestsRateLimiter = unhealthyRunTestsRateLimiter;
    this.triggerCheckTestsExecutor = executor;
    this.ignoreNonTestFailuresOnceHealthy = ignoreNonTestFailuresOnceHealthy;
    this.numTestInitRetries = numTestInitRetries;
    this.numCheckTestRetries = numCheckTestRetries;
    this.initialAndRetryDelayCheckTestDurationNanos =
        initialDelayAndRetryCheckTestDuration.toNanos();
    this.previousHealthResult = Result.unhealthy("Initializing");
  }

  @Override
  protected Result check() throws Exception {
    if (currentHealthResult == null) {
      synchronized (this) {
        if (currentHealthResult == null) {
          healthyRunTestsRateLimiter.acquire();
          unhealthyRunTestsRateLimiter.acquire();
          triggerTests();
          return previousHealthResult;
        }
      }
    }

    final Result healthResult = currentHealthResult.getNow(previousHealthResult);
    if (healthResult.isHealthy()) {
      if (healthyRunTestsRateLimiter.tryAcquire()) {
        unhealthyRunTestsRateLimiter.tryAcquire();
        previousHealthResult = healthResult;
        triggerTests();
      }
    } else {
      if (unhealthyRunTestsRateLimiter.tryAcquire()) {
        healthyRunTestsRateLimiter.tryAcquire();
        previousHealthResult = healthResult;
        triggerTests();
      }
    }
    return healthResult;
  }

  private void triggerTests() {
    currentHealthResult = CompletableFuture.supplyAsync(() -> {
      try {
        return assertAllTestsHealthy(triggerAllTests());
      } catch (final Exception e) {
        return ignoreNonTestFailuresOnceHealthy ? previousHealthResult : Result.unhealthy(e);
      }
    }, triggerCheckTestsExecutor);
  }

  private Result assertAllTestsHealthy(
      final Map<String, ? extends Collection<RunscopeTriggeredTestRun>> testRuns) {
    for (final Entry<String, ? extends Collection<RunscopeTriggeredTestRun>> bucketTestRuns : testRuns
        .entrySet()) {
      final Result result =
          assertAllBucketTestsHealthy(bucketTestRuns.getKey(), bucketTestRuns.getValue());

      if (!result.isHealthy())
        return result;
    }
    return Result.healthy();
  }

  private Result assertAllBucketTestsHealthy(final String bucketKey,
      final Collection<RunscopeTriggeredTestRun> testRuns) {
    final Set<RunscopeTriggeredTestRun> unverifiedTestRuns = Sets.newConcurrentHashSet(testRuns);

    Uninterruptibles.sleepUninterruptibly(initialAndRetryDelayCheckTestDurationNanos,
        TimeUnit.NANOSECONDS);

    for (final Map<String, int[]> numRetries = new HashMap<>(); !unverifiedTestRuns.isEmpty();) {
      for (final RunscopeTriggeredTestRun testRun : unverifiedTestRuns) {
        try {
          final RunscopeTestRunDetail testRunDetail =
              runscope.getTestRunDetail(bucketKey, testRun.getTestId(), testRun.getTestRunId())
                  .getData();

          if (testRunDetail.getAssertionsFailed() > 0)
            return Result.unhealthy("Runscope tests failed: " + testRunDetail);

          unverifiedTestRuns.remove(testRun);
          info("Test run succeeded: " + testRun.getTestRunUrl());
        } catch (final FeignException e) {
          final int[] numFailures =
              numRetries.computeIfAbsent(testRun.getTestRunId(), k -> new int[] { 1 });
          if (++numFailures[0] > numCheckTestRetries)
            throw e;

          Loggable.logError(DeploymentResource.class, e);
          Uninterruptibles.sleepUninterruptibly(initialAndRetryDelayCheckTestDurationNanos,
              TimeUnit.NANOSECONDS);
        }
      }
    }

    return Result.healthy();
  }

  private Map<String, Set<RunscopeTriggeredTestRun>> triggerAllTests() {
    final Map<String, Set<RunscopeTriggeredTestRun>> testRuns = new HashMap<>();
    triggerIds.forEach((bucketKey, triggerIds) -> {
      triggerIds.forEach(triggerId -> {
        retryRun(
            () -> {
              final RunscopeTriggerResponseData triggerResponse =
                  runscope.triggerTest(triggerId).getData();

              if (triggerResponse.getRunsFailed() > 0)
                throw new IllegalStateException("Failed to start runscope tests with trigger id "
                    + triggerId);

              info("Triggered test runs:\n"
                  + triggerResponse.getRuns().stream().map(RunscopeTriggeredTestRun::getTestRunUrl)
                      .collect(Collectors.joining("\n")));
              testRuns.put(bucketKey, Sets.newConcurrentHashSet(triggerResponse.getRuns()));
            }, numTestInitRetries);
      });
    });
    return testRuns;
  }

  public static Map<String, Collection<String>> getAllTriggerIds(final RunscopeClient runscope,
      final String... bucketKeys) {
    final Stream<String> bucketKeyStream =
        bucketKeys == null || bucketKeys.length == 0 ? runscope.getBucketList().getData().stream()
            .map(RunscopeBucket::getKey) : Arrays.stream(bucketKeys);

    final Map<String, Collection<String>> triggerIds = new HashMap<>();

    bucketKeyStream.forEach(bucketKey -> {
      runscope
          .getTestList(bucketKey)
          .getData()
          .stream()
          .map(RunscopeTest::getTriggerUrl)
          .forEach(
              triggerUrl -> {
                final String[] triggerUrlParts = triggerUrl.split("/");
                if (triggerUrlParts.length >= 5) {
                  triggerIds.computeIfAbsent(bucketKey, Sets::newHashSet).add(
                      triggerUrlParts[triggerUrlParts.length - 2]);
                }
              });
    });

    return triggerIds;
  }

  public static class Builder {

    private int numTestInitRetries = DEFAULT_NUM_TEST_INIT_RETRIES;
    private int numCheckTestRetries = DEFAULT_NUM_CHECK_TEST_RETRIES;
    private Duration initialAndRetryDelayCheckTestDuration =
        DEFAULT_INITIAL_DELAY_AND_RETRY_CHECK_TEST_DURATION;

    private final RunscopeClient runscope;
    private final Map<String, Collection<String>> triggerIds;

    private final RateLimiter healthyRunTestsRateLimiter;
    private RateLimiter unhealthyRunTestsRateLimiter;
    private ExecutorService triggerCheckTestsExecutor;
    private boolean ignoreNonTestFailuresOnceHealthy = true;

    public Builder(final RunscopeClient runscope, final RateLimiter healthyRunTestsRateLimiter) {
      this(runscope, healthyRunTestsRateLimiter, runscope.getBucketList().getData().stream()
          .map(RunscopeBucket::getKey).toArray(String[]::new));
    }

    public Builder(final RunscopeClient runscope, final RateLimiter healthyRunTestsRateLimiter,
        final String... bucketKeys) {
      this(runscope, healthyRunTestsRateLimiter, RunscopeHealthCheck.getAllTriggerIds(runscope,
          bucketKeys));
    }

    public Builder(final RunscopeClient runscope, final RateLimiter healthyRunTestsRateLimiter,
        final Map<String, Collection<String>> triggerIds) {
      this.runscope = runscope;
      this.triggerIds = triggerIds;
      this.healthyRunTestsRateLimiter = healthyRunTestsRateLimiter;
    }

    public Builder withNumTestInitRetries(final int numTestInitRetries) {
      this.numTestInitRetries = numTestInitRetries;
      return this;
    }

    public Builder withNumCheckTestRetries(final int numCheckTestRetries) {
      this.numCheckTestRetries = numCheckTestRetries;
      return this;
    }

    public Builder withInitialAndRetryDelayCheckTestDuration(
        final Duration initialAndRetryDelayCheckTestDuration) {
      this.initialAndRetryDelayCheckTestDuration = initialAndRetryDelayCheckTestDuration;
      return this;
    }

    public Builder withUnhealthyRunTestsRateLimiter(final RateLimiter unhealthyRunTestsRateLimiter) {
      this.unhealthyRunTestsRateLimiter = unhealthyRunTestsRateLimiter;
      return this;
    }

    public Builder withTriggerCheckTestsExecutor(final ExecutorService triggerCheckTestsExecutor) {
      this.triggerCheckTestsExecutor = triggerCheckTestsExecutor;
      return this;
    }

    public Builder withIgnoreNonTestFailuresOnceHealthy(
        final boolean ignoreNonTestFailuresOnceHealthy) {
      this.ignoreNonTestFailuresOnceHealthy = ignoreNonTestFailuresOnceHealthy;
      return this;
    }

    public RunscopeHealthCheck build() {
      if (unhealthyRunTestsRateLimiter == null) {
        unhealthyRunTestsRateLimiter = RateLimiter.create(healthyRunTestsRateLimiter.getRate());
      }

      if (triggerCheckTestsExecutor == null) {
        triggerCheckTestsExecutor =
            ExecutorUtils.newSingleThreadExecutor(RunscopeHealthCheck.class);
      }

      return new RunscopeHealthCheck(runscope, triggerIds, healthyRunTestsRateLimiter,
          unhealthyRunTestsRateLimiter, triggerCheckTestsExecutor,
          ignoreNonTestFailuresOnceHealthy, numTestInitRetries, numCheckTestRetries,
          initialAndRetryDelayCheckTestDuration);
    }
  }
}
