/*
 * Copyright 2022 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.tsunami.plugins.detectors.cves.cve202222947;

import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;
import static com.google.tsunami.common.data.NetworkEndpointUtils.forHostname;
import static com.google.tsunami.common.data.NetworkEndpointUtils.forHostnameAndPort;

import com.google.common.collect.ImmutableList;
import com.google.inject.Guice;
import com.google.protobuf.util.Timestamps;
import com.google.tsunami.common.net.http.HttpClientModule;
import com.google.tsunami.common.net.http.HttpStatus;
import com.google.tsunami.common.time.testing.FakeUtcClock;
import com.google.tsunami.common.time.testing.FakeUtcClockModule;
import com.google.tsunami.proto.DetectionReport;
import com.google.tsunami.proto.DetectionReportList;
import com.google.tsunami.proto.DetectionStatus;
import com.google.tsunami.proto.NetworkService;
import com.google.tsunami.proto.Severity;
import com.google.tsunami.proto.Software;
import com.google.tsunami.proto.TargetInfo;
import com.google.tsunami.proto.TransportProtocol;
import com.google.tsunami.proto.Vulnerability;
import com.google.tsunami.proto.VulnerabilityId;
import java.io.IOException;
import java.time.Instant;
import javax.inject.Inject;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link Cve202222947VulnDetector}. */
@RunWith(JUnit4.class)
public final class Cve202222947VulnDetectorTest {

  private final FakeUtcClock fakeUtcClock =
      FakeUtcClock.create().setNow(Instant.parse("2020-01-01T00:00:00.00Z"));

  @Inject private Cve202222947VulnDetector detector;

  private MockWebServer mockWebServer;

  @Before
  public void setUp() {
    mockWebServer = new MockWebServer();
    Guice.createInjector(
            new FakeUtcClockModule(fakeUtcClock),
            new Cve202222947DetectorBootstrapModule(),
            new HttpClientModule.Builder().build())
        .injectMembers(this);
  }

  @After
  public void tearDown() throws IOException {
    mockWebServer.shutdown();
  }

  @Test
  public void detect_whenVulnerable_returnsVulnerability() throws IOException {
    mockWebResponse(Cve202222947VulnDetector.CHECK_VULN_FLAG);
    NetworkService service =
        NetworkService.newBuilder()
            .setNetworkEndpoint(
                forHostnameAndPort(mockWebServer.getHostName(), mockWebServer.getPort()))
            .setTransportProtocol(TransportProtocol.TCP)
            .setSoftware(Software.newBuilder().setName("http"))
            .setServiceName("http")
            .build();
    TargetInfo targetInfo =
        TargetInfo.newBuilder()
            .addNetworkEndpoints(forHostname(mockWebServer.getHostName()))
            .build();

    DetectionReportList detectionReports = detector.detect(targetInfo, ImmutableList.of(service));

    assertThat(detectionReports.getDetectionReportsList())
        .containsExactly(
            DetectionReport.newBuilder()
                .setTargetInfo(targetInfo)
                .setNetworkService(service)
                .setDetectionTimestamp(
                    Timestamps.fromMillis(Instant.now(fakeUtcClock).toEpochMilli()))
                .setDetectionStatus(DetectionStatus.VULNERABILITY_VERIFIED)
                .setVulnerability(
                    Vulnerability.newBuilder()
                        .setMainId(
                            VulnerabilityId.newBuilder()
                                .setPublisher("TSUNAMI_COMMUNITY")
                                .setValue("CVE_2022_22947"))
                        .addRelatedId(
                            VulnerabilityId.newBuilder()
                                .setPublisher("CVE")
                                .setValue("CVE-2022-22947"))
                        .setSeverity(Severity.CRITICAL)
                        .setTitle(
                            "CVE-2022-22947 Spring Cloud Gateway Actuator API SpEL Code Injection")
                        .setRecommendation(
                            "Users of affected versions should apply the following remediation. "
                                + "3.1.x users should upgrade to 3.1.1+. 3.0.x users should upgrade"
                                + " to 3.0.7+. If the Gateway actuator endpoint is not needed it "
                                + "should be disabled via management.endpoint.gateway.enabled: "
                                + "false. If the actuator is required it should be secured using"
                                + " Spring Security, see https://docs.spring.io/spring-boot/docs/"
                                + "current/reference/html/actuator.html#actuator.endpoints.security"
                                + ".")
                        .setDescription(Cve202222947VulnDetector.VULN_DESCRIPTION))
                .build());
  }

  @Test
  public void detect_whenNotVulnerable_returnsNoVulnerability() throws IOException {
    mockWebResponse("Hello World");
    ImmutableList<NetworkService> httpServices =
        ImmutableList.of(
            NetworkService.newBuilder()
                .setNetworkEndpoint(
                    forHostnameAndPort(mockWebServer.getHostName(), mockWebServer.getPort()))
                .setTransportProtocol(TransportProtocol.TCP)
                .setServiceName("http")
                .build());
    TargetInfo targetInfo =
        TargetInfo.newBuilder()
            .addNetworkEndpoints(forHostname(mockWebServer.getHostName()))
            .build();

    DetectionReportList detectionReports = detector.detect(targetInfo, httpServices);

    assertThat(detectionReports.getDetectionReportsList()).isEmpty();
  }

  private void mockWebResponse(String body) throws IOException {
    mockWebServer.enqueue(new MockResponse().setResponseCode(HttpStatus.CREATED.code()));
    mockWebServer.enqueue(new MockResponse().setResponseCode(HttpStatus.OK.code()));
    mockWebServer.enqueue(new MockResponse().setResponseCode(HttpStatus.OK.code()).setBody(body));
    mockWebServer.enqueue(new MockResponse().setResponseCode(HttpStatus.OK.code()));
    mockWebServer.enqueue(new MockResponse().setResponseCode(HttpStatus.OK.code()));
    mockWebServer.start();
  }
}
