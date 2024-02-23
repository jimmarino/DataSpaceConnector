/*
 *  Copyright (c) 2023 Bayerische Motoren Werke Aktiengesellschaft (BMW AG)
 *
 *  This program and the accompanying materials are made available under the
 *  terms of the Apache License, Version 2.0 which is available at
 *  https://www.apache.org/licenses/LICENSE-2.0
 *
 *  SPDX-License-Identifier: Apache-2.0
 *
 *  Contributors:
 *       Bayerische Motoren Werke Aktiengesellschaft (BMW AG) - initial API and implementation
 *
 */

package org.eclipse.edc.protocol.dsp.message;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import org.eclipse.edc.policy.model.Policy;
import org.eclipse.edc.protocol.dsp.spi.message.GetDspRequest;
import org.eclipse.edc.protocol.dsp.spi.message.PostDspRequest;
import org.eclipse.edc.spi.iam.TokenRepresentation;
import org.eclipse.edc.spi.result.Result;
import org.eclipse.edc.spi.result.ServiceResult;
import org.eclipse.edc.spi.types.domain.message.ProcessRemoteMessage;
import org.eclipse.edc.transform.spi.TypeTransformerRegistry;
import org.eclipse.edc.validator.spi.JsonObjectValidatorRegistry;
import org.eclipse.edc.validator.spi.ValidationResult;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.function.BiFunction;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON_TYPE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.type;
import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.TYPE;
import static org.eclipse.edc.protocol.dsp.spi.types.HttpMessageProtocol.DATASPACE_PROTOCOL_HTTP;
import static org.eclipse.edc.protocol.dsp.type.DspPropertyAndTypeNames.DSPACE_PROPERTY_CODE;
import static org.eclipse.edc.protocol.dsp.type.DspPropertyAndTypeNames.DSPACE_PROPERTY_PROCESS_ID;
import static org.eclipse.edc.protocol.dsp.type.DspPropertyAndTypeNames.DSPACE_PROPERTY_REASON;
import static org.eclipse.edc.validator.spi.Violation.violation;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DspRequestHandlerImplTest {

    private final JsonObjectValidatorRegistry validatorRegistry = mock();
    private final TypeTransformerRegistry transformerRegistry = mock();
    private final DspRequestHandlerImpl handler = new DspRequestHandlerImpl(mock(), validatorRegistry, transformerRegistry);

    @Nested
    class GetResource {

        @Test
        void shouldSucceed() {
            var content = new Object();
            var resourceJson = Json.createObjectBuilder().build();
            BiFunction<String, TokenRepresentation, ServiceResult<Object>> serviceCall = (m, t) -> ServiceResult.success(content);
            when(transformerRegistry.transform(any(), any())).thenReturn(Result.success(resourceJson));
            var request = GetDspRequest.Builder.newInstance(Object.class)
                    .token("token")
                    .id("id")
                    .serviceCall(serviceCall)
                    .errorType("errorType")
                    .build();

            var result = handler.getResource(request);

            assertThat(result.getStatus()).isEqualTo(200);
            verifyNoInteractions(validatorRegistry);
        }

        @Test
        void shouldReturnUnauthorized_whenTokenIsNull() {
            var request = getDspRequestBuilder().token(null).errorType("errorType").serviceCall((m, t) -> ServiceResult.success()).build();

            var result = handler.getResource(request);

            assertThat(result.getStatus()).isEqualTo(401);
            assertThat(result.getEntity()).asInstanceOf(type(JsonObject.class)).satisfies(error -> {
                assertThat(error.getString(TYPE)).isEqualTo("errorType");
                assertThat(error.getString(DSPACE_PROPERTY_CODE)).isEqualTo("401");
                assertThat(error.get(DSPACE_PROPERTY_REASON)).isNotNull();
            });
        }

        @Test
        void shouldFail_whenTokenIsNotValid() {
            var request = getDspRequestBuilder().errorType("errorType").serviceCall((m, t) -> ServiceResult.unauthorized("unauthorized")).build();

            var result = handler.getResource(request);

            assertThat(result.getStatus()).isEqualTo(401);
            assertThat(result.getEntity()).asInstanceOf(type(JsonObject.class)).satisfies(error -> {
                assertThat(error.getString(TYPE)).isEqualTo("errorType");
                assertThat(error.getString(DSPACE_PROPERTY_CODE)).isEqualTo("401");
                assertThat(error.get(DSPACE_PROPERTY_REASON)).isNotNull();
            });
        }

        @Test
        void shouldFail_whenServiceCallFails() {
            BiFunction<String, TokenRepresentation, ServiceResult<Object>> serviceCall = (m, t) -> ServiceResult.notFound("error");
            var request = getDspRequestBuilder()
                    .serviceCall(serviceCall)
                    .build();

            var result = handler.getResource(request);

            assertThat(result.getStatus()).isEqualTo(404);
        }

        @Test
        void shouldFail_whenTransformationFails() {
            when(transformerRegistry.transform(any(), any())).thenReturn(Result.failure("error"));
            var request = getDspRequestBuilder().build();

            var result = handler.getResource(request);

            assertThat(result.getStatus()).isEqualTo(500);
        }

        private GetDspRequest.Builder<Object> getDspRequestBuilder() {
            return GetDspRequest.Builder.newInstance(Object.class)
                    .token("token")
                    .id("id")
                    .serviceCall((i, c) -> ServiceResult.success())
                    .errorType("errorType");
        }
    }

    @Nested
    class CreateResource {
        @Test
        void shouldSucceed() {
            var jsonMessage = Json.createObjectBuilder().build();
            var message = mock(TestProcessRemoteMessage.class);
            var content = new Object();
            var responseJson = Json.createObjectBuilder().build();
            BiFunction<TestProcessRemoteMessage, TokenRepresentation, ServiceResult<Object>> serviceCall = (m, t) -> ServiceResult.success(content);
            when(validatorRegistry.validate(any(), any())).thenReturn(ValidationResult.success());
            when(transformerRegistry.transform(any(), eq(TestProcessRemoteMessage.class))).thenReturn(Result.success(message));
            when(transformerRegistry.transform(any(), eq(JsonObject.class))).thenReturn(Result.success(responseJson));
            var request = PostDspRequest.Builder.newInstance(TestProcessRemoteMessage.class, Object.class)
                    .token("token")
                    .expectedMessageType("expected-message-type")
                    .message(jsonMessage)
                    .serviceCall(serviceCall)
                    .errorType("errorType")
                    .build();

            var result = handler.createResource(request);

            assertThat(result.getStatus()).isEqualTo(200);
            assertThat(result.getEntity()).isEqualTo(responseJson);
            assertThat(result.getMediaType()).isEqualTo(APPLICATION_JSON_TYPE);
            verify(validatorRegistry).validate("expected-message-type", jsonMessage);
            verify(transformerRegistry).transform(jsonMessage, TestProcessRemoteMessage.class);
            verify(message).setProtocol(DATASPACE_PROTOCOL_HTTP);
            verify(transformerRegistry).transform(content, JsonObject.class);
        }

        @Test
        void shouldReturnUnauthorized_whenTokenIsNull() {
            var request = postDspRequestBuilder().token(null).errorType("errorType").serviceCall((m, t) -> ServiceResult.success()).build();

            var result = handler.createResource(request);

            assertThat(result.getStatus()).isEqualTo(401);
            assertThat(result.getEntity()).asInstanceOf(type(JsonObject.class)).satisfies(error -> {
                assertThat(error.getString(TYPE)).isEqualTo("errorType");
                assertThat(error.getString(DSPACE_PROPERTY_CODE)).isEqualTo("401");
                assertThat(error.get(DSPACE_PROPERTY_REASON)).isNotNull();
            });
        }

        @Test
        void shouldFail_whenTokenIsNotValid() {
            var request = postDspRequestBuilder().errorType("errorType").serviceCall((m, t) -> ServiceResult.unauthorized("unauthorized")).build();
            var message = mock(TestProcessRemoteMessage.class);

            when(validatorRegistry.validate(any(), any())).thenReturn(ValidationResult.success());
            when(transformerRegistry.transform(any(), eq(TestProcessRemoteMessage.class))).thenReturn(Result.success(message));

            var result = handler.createResource(request);

            assertThat(result.getStatus()).isEqualTo(401);
            assertThat(result.getEntity()).asInstanceOf(type(JsonObject.class)).satisfies(error -> {
                assertThat(error.getString(TYPE)).isEqualTo("errorType");
                assertThat(error.getString(DSPACE_PROPERTY_CODE)).isEqualTo("401");
                assertThat(error.get(DSPACE_PROPERTY_REASON)).isNotNull();
            });
        }

        @Test
        void shouldFail_whenValidationFails() {
            when(validatorRegistry.validate(any(), any())).thenReturn(ValidationResult.failure(violation("error", "path")));
            var request = postDspRequestBuilder().build();

            var result = handler.createResource(request);

            assertThat(result.getStatus()).isEqualTo(400);
        }

        @Test
        void shouldFail_whenTransformationFails() {
            when(validatorRegistry.validate(any(), any())).thenReturn(ValidationResult.success());
            when(transformerRegistry.transform(any(), any())).thenReturn(Result.failure("error"));
            var request = postDspRequestBuilder().build();

            var result = handler.createResource(request);

            assertThat(result.getStatus()).isEqualTo(400);
        }

        @Test
        void shouldFail_whenServiceCallFails() {
            var message = mock(TestProcessRemoteMessage.class);
            BiFunction<TestProcessRemoteMessage, TokenRepresentation, ServiceResult<Object>> serviceCall = (m, t) -> ServiceResult.conflict("error");
            when(validatorRegistry.validate(any(), any())).thenReturn(ValidationResult.success());
            when(transformerRegistry.transform(any(), any())).thenReturn(Result.success(message));
            var request = postDspRequestBuilder().serviceCall(serviceCall).build();

            var result = handler.createResource(request);

            assertThat(result.getStatus()).isEqualTo(409);
        }

        @Test
        void shouldReturnInternalServerError_whenOutputTransformationFails() {
            var message = mock(TestProcessRemoteMessage.class);
            when(validatorRegistry.validate(any(), any())).thenReturn(ValidationResult.success());
            when(transformerRegistry.transform(any(), eq(TestProcessRemoteMessage.class))).thenReturn(Result.success(message));
            when(transformerRegistry.transform(any(), eq(JsonObject.class))).thenReturn(Result.failure("error"));
            var request = postDspRequestBuilder().build();

            var result = handler.createResource(request);

            assertThat(result.getStatus()).isEqualTo(500);
        }

        private PostDspRequest.Builder<TestProcessRemoteMessage, Object> postDspRequestBuilder() {
            return PostDspRequest.Builder
                    .newInstance(TestProcessRemoteMessage.class, Object.class)
                    .errorType("errorType")
                    .token("token")
                    .serviceCall((i, c) -> ServiceResult.success());
        }

    }

    @Nested
    class UpdateResource {
        @Test
        void shouldSucceed() {
            var jsonMessage = Json.createObjectBuilder().build();
            var message = TestProcessRemoteMessage.Builder.newInstance().providerPid("providerPid").consumerPid("processId").build();
            var content = new Object();
            BiFunction<TestProcessRemoteMessage, TokenRepresentation, ServiceResult<Object>> serviceCall = (m, t) -> ServiceResult.success(content);
            when(validatorRegistry.validate(any(), any())).thenReturn(ValidationResult.success());
            when(transformerRegistry.transform(any(), eq(TestProcessRemoteMessage.class))).thenReturn(Result.success(message));
            var request = PostDspRequest.Builder.newInstance(TestProcessRemoteMessage.class, Object.class)
                    .token("token")
                    .processId("processId")
                    .expectedMessageType("expected-message-type")
                    .message(jsonMessage)
                    .serviceCall(serviceCall)
                    .errorType("errorType")
                    .build();

            var result = handler.updateResource(request);

            assertThat(result.getStatus()).isEqualTo(200);
            assertThat(result.getMediaType()).isEqualTo(APPLICATION_JSON_TYPE);
            assertThat(message.getProtocol()).isEqualTo(DATASPACE_PROTOCOL_HTTP);
            assertThat(message.getProcessId()).isEqualTo("processId");
            verify(validatorRegistry).validate("expected-message-type", jsonMessage);
            verify(transformerRegistry).transform(jsonMessage, TestProcessRemoteMessage.class);
        }

        @Test
        void shouldFail_whenTokenIsNotValid() {
            var jsonMessage = Json.createObjectBuilder().build();
            var request = postDspRequestBuilder()
                    .processId("processId")
                    .errorType("errorType")
                    .message(jsonMessage)
                    .serviceCall((m, t) -> ServiceResult.unauthorized("unauthorized"))
                    .build();
            var message = TestProcessRemoteMessage.Builder.newInstance().providerPid("providerPid").consumerPid("processId").build();
            
            when(validatorRegistry.validate(any(), any())).thenReturn(ValidationResult.success());
            when(transformerRegistry.transform(any(), eq(TestProcessRemoteMessage.class))).thenReturn(Result.success(message));

            var result = handler.updateResource(request);

            assertThat(result.getStatus()).isEqualTo(401);
            assertThat(result.getEntity()).asInstanceOf(type(JsonObject.class)).satisfies(error -> {
                assertThat(error.getString(TYPE)).isEqualTo("errorType");
                assertThat(error.getString(DSPACE_PROPERTY_CODE)).isEqualTo("401");
                assertThat(error.getString(DSPACE_PROPERTY_PROCESS_ID)).isEqualTo("processId");
                assertThat(error.get(DSPACE_PROPERTY_REASON)).isNotNull();
            });
        }

        @Test
        void shouldReturnUnauthorized_whenTokenIsNull() {
            var jsonMessage = Json.createObjectBuilder().build();
            var request = postDspRequestBuilder()
                    .token(null)
                    .processId("processId")
                    .errorType("errorType")
                    .message(jsonMessage)
                    .serviceCall((m, t) -> ServiceResult.unauthorized("unauthorized"))
                    .build();

            var result = handler.updateResource(request);

            assertThat(result.getStatus()).isEqualTo(401);
            assertThat(result.getEntity()).asInstanceOf(type(JsonObject.class)).satisfies(error -> {
                assertThat(error.getString(TYPE)).isEqualTo("errorType");
                assertThat(error.getString(DSPACE_PROPERTY_CODE)).isEqualTo("401");
                assertThat(error.getString(DSPACE_PROPERTY_PROCESS_ID)).isEqualTo("processId");
                assertThat(error.get(DSPACE_PROPERTY_REASON)).isNotNull();
            });
        }

        @Test
        void shouldFail_whenValidationFails() {
            when(validatorRegistry.validate(any(), any())).thenReturn(ValidationResult.failure(violation("error", "path")));
            var request = postDspRequestBuilder().processId("processId").errorType("errorType").build();

            var result = handler.updateResource(request);

            assertThat(result.getStatus()).isEqualTo(400);
            assertThat(result.getEntity()).asInstanceOf(type(JsonObject.class)).satisfies(error -> {
                assertThat(error.getString(TYPE)).isEqualTo("errorType");
                assertThat(error.getString(DSPACE_PROPERTY_CODE)).isEqualTo("400");
                assertThat(error.getString(DSPACE_PROPERTY_PROCESS_ID)).isEqualTo("processId");
                assertThat(error.get(DSPACE_PROPERTY_REASON)).isNotNull();
            });
        }

        @Test
        void shouldFail_whenTransformationFails() {
            when(validatorRegistry.validate(any(), any())).thenReturn(ValidationResult.success());
            when(transformerRegistry.transform(any(), any())).thenReturn(Result.failure("error"));
            var request = postDspRequestBuilder().processId("processId").errorType("errorType").build();

            var result = handler.updateResource(request);

            assertThat(result.getStatus()).isEqualTo(400);
            assertThat(result.getEntity()).asInstanceOf(type(JsonObject.class)).satisfies(error -> {
                assertThat(error.getString(TYPE)).isEqualTo("errorType");
                assertThat(error.getString(DSPACE_PROPERTY_CODE)).isEqualTo("400");
                assertThat(error.getString(DSPACE_PROPERTY_PROCESS_ID)).isEqualTo("processId");
                assertThat(error.get(DSPACE_PROPERTY_REASON)).isNotNull();
            });
        }

        @Test
        void shouldFail_whenIdIsNotValid() {
            var message = TestProcessRemoteMessage.Builder.newInstance()
                    .consumerPid("consumerPid")
                    .providerPid("providerPid")
                    .build();
            when(validatorRegistry.validate(any(), any())).thenReturn(ValidationResult.success());
            when(transformerRegistry.transform(any(), any())).thenReturn(Result.success(message));
            var request = postDspRequestBuilder().processId("anotherId").errorType("errorType").build();

            var result = handler.updateResource(request);

            assertThat(result.getStatus()).isEqualTo(400);
            assertThat(result.getEntity()).asInstanceOf(type(JsonObject.class)).satisfies(error -> {
                assertThat(error.getString(TYPE)).isEqualTo("errorType");
                assertThat(error.getString(DSPACE_PROPERTY_CODE)).isEqualTo("400");
                assertThat(error.getString(DSPACE_PROPERTY_PROCESS_ID)).isEqualTo("anotherId");
                assertThat(error.get(DSPACE_PROPERTY_REASON)).isNotNull();
            });
        }

        @Test
        void shouldFail_whenServiceCallFails() {
            var message = TestProcessRemoteMessage.Builder.newInstance().providerPid("providerPid").consumerPid("processId").build();
            BiFunction<TestProcessRemoteMessage, TokenRepresentation, ServiceResult<Object>> serviceCall = (m, t) -> ServiceResult.conflict("error");
            when(validatorRegistry.validate(any(), any())).thenReturn(ValidationResult.success());
            when(transformerRegistry.transform(any(), any())).thenReturn(Result.success(message));
            var request = postDspRequestBuilder().processId("processId").serviceCall(serviceCall).build();

            var result = handler.updateResource(request);

            assertThat(result.getStatus()).isEqualTo(409);
            assertThat(result.getEntity()).asInstanceOf(type(JsonObject.class)).satisfies(error -> {
                assertThat(error.getString(TYPE)).isEqualTo("errorType");
                assertThat(error.getString(DSPACE_PROPERTY_CODE)).isEqualTo("409");
                assertThat(error.getString(DSPACE_PROPERTY_PROCESS_ID)).isEqualTo("processId");
                assertThat(error.get(DSPACE_PROPERTY_REASON)).isNotNull();
            });
        }

        private PostDspRequest.Builder<TestProcessRemoteMessage, Object> postDspRequestBuilder() {
            return PostDspRequest.Builder
                    .newInstance(TestProcessRemoteMessage.class, Object.class)
                    .errorType("errorType")
                    .token("token")
                    .serviceCall((i, c) -> ServiceResult.success());
        }

    }

    private static class TestProcessRemoteMessage extends ProcessRemoteMessage {

        @Override
        public Policy getPolicy() {
            return null;
        }

        public static class Builder extends ProcessRemoteMessage.Builder<TestProcessRemoteMessage, Builder> {

            public static Builder newInstance() {
                return new Builder();
            }

            protected Builder() {
                super(new TestProcessRemoteMessage());
            }
        }
    }

}
