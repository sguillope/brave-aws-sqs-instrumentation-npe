package com.example.braveawssqsinstrumentationnpe;

import brave.http.HttpTracing;
import brave.instrumentation.awsv2.AwsSdkTracing;
import com.example.braveawssqsinstrumentationnpe.BraveAwsSqsInstrumentationNpeApplicationTests.SqsConfig;
import org.junit.jupiter.api.Test;
import org.junit.platform.commons.util.ExceptionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import java.net.URI;

import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@AutoConfigureWireMock
@Import(SqsConfig.class)
class BraveAwsSqsInstrumentationNpeApplicationTests {

    @Autowired
    private SqsAsyncClient sqsAsyncClient;

    @Test
    void sqsRequestRetriesOn500() {
        stubFor(post(urlPathMatching("/")).willReturn(serverError()));
        final SendMessageRequest request = SendMessageRequest.builder().build();

        assertThatThrownBy(() -> sqsAsyncClient.sendMessage(request).join())
                .extracting(ExceptionUtils::readStackTrace).asString()
                .doesNotContain("NullPointerException");
    }

    @TestConfiguration
    static class SqsConfig {

        @Autowired
        private HttpTracing httpTracing;

        @Bean
        public SqsAsyncClient asyncClient() {
            final ClientOverrideConfiguration configuration = ClientOverrideConfiguration.builder()
                    .addExecutionInterceptor(AwsSdkTracing.create(httpTracing).executionInterceptor())
                    .build();

            return SqsAsyncClient.builder()
                    .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("x", "x")))
                    .region(Region.of("us-east"))
                    .endpointOverride(URI.create("http://localhost:8080/queue/local-ln-queue"))
                    .overrideConfiguration(configuration)
                    .build();
        }
    }
}
