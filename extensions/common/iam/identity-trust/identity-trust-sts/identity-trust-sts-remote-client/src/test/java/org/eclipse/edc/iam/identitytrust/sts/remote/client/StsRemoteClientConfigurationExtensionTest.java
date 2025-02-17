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

package org.eclipse.edc.iam.identitytrust.sts.remote.client;

import org.eclipse.edc.junit.extensions.DependencyInjectionExtension;
import org.eclipse.edc.spi.security.Vault;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.spi.system.configuration.ConfigFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(DependencyInjectionExtension.class)
public class StsRemoteClientConfigurationExtensionTest {

    private final String tokenUrl = "http://tokenUrl";
    private final String clientId = "clientId";
    private final String secretAlias = "secretAlias";

    @BeforeEach
    void setup(ServiceExtensionContext context) {
        context.registerService(Vault.class, mock());

        var configMap = Map.of("edc.iam.sts.oauth.token.url", tokenUrl,
                "edc.iam.sts.oauth.client.id", clientId,
                "edc.iam.sts.oauth.client.secret.alias", secretAlias);
        var config = ConfigFactory.fromMap(configMap);

        when(context.getConfig()).thenReturn(config);
    }

    @Test
    void initialize(StsRemoteClientConfigurationExtension extension, ServiceExtensionContext context) {


        extension.initialize(context);
        assertThat(extension.clientConfiguration(context)).isNotNull()
                .satisfies(configuration -> {
                    assertThat(configuration.tokenUrl()).isEqualTo(tokenUrl);
                    assertThat(configuration.clientId()).isEqualTo(clientId);
                    assertThat(configuration.clientSecretAlias()).isEqualTo(secretAlias);
                });
    }

}
