/*
 *  Copyright (c) 2020 - 2022 Microsoft Corporation
 *
 *  This program and the accompanying materials are made available under the
 *  terms of the Apache License, Version 2.0 which is available at
 *  https://www.apache.org/licenses/LICENSE-2.0
 *
 *  SPDX-License-Identifier: Apache-2.0
 *
 *  Contributors:
 *       Microsoft Corporation - initial API and implementation
 *
 */

package org.eclipse.edc.connector.api.datamanagement.configuration;

import org.eclipse.edc.api.auth.spi.AuthenticationRequestFilter;
import org.eclipse.edc.api.auth.spi.AuthenticationService;
import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.runtime.metamodel.annotation.Provides;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.web.spi.WebServer;
import org.eclipse.edc.web.spi.WebService;
import org.eclipse.edc.web.spi.configuration.WebServiceConfigurer;
import org.eclipse.edc.web.spi.configuration.WebServiceSettings;

@Provides(DataManagementApiConfiguration.class)
@Extension(value = DataManagementApiConfigurationExtension.NAME)
public class DataManagementApiConfigurationExtension implements ServiceExtension {

    public static final String NAME = "Data Management API configuration";

    public static final String DATA_MANAGEMENT_API_CONFIG = "web.http.data";
    public static final String DATA_MANAGEMENT_CONTEXT_ALIAS = "data";
    public static final int DEFAULT_DATA_MANAGEMENT_API_PORT = 8181;
    public static final String DEFAULT_DATA_MANAGEMENT_API_CONTEXT_PATH = "/api";

    public static final String WEB_SERVICE_NAME = "Data Management API";


    @Inject
    private WebService webService;

    @Inject
    private WebServer webServer;

    @Inject
    private AuthenticationService authenticationService;

    @Inject
    private WebServiceConfigurer configurator;

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public void initialize(ServiceExtensionContext context) {
        var settings = WebServiceSettings.Builder.newInstance()
                .apiConfigKey(DATA_MANAGEMENT_API_CONFIG)
                .contextAlias(DATA_MANAGEMENT_CONTEXT_ALIAS)
                .defaultPath(DEFAULT_DATA_MANAGEMENT_API_CONTEXT_PATH)
                .defaultPort(DEFAULT_DATA_MANAGEMENT_API_PORT)
                .useDefaultContext(true)
                .name(WEB_SERVICE_NAME)
                .build();

        var config = configurator.configure(context, webServer, settings);

        // the DataManagementApiConfiguration tells all DataManagementApi controllers under which context alias
        // they need to register their resources: either `default` or `data`
        context.registerService(DataManagementApiConfiguration.class, new DataManagementApiConfiguration(config.getContextAlias()));
        webService.registerResource(config.getContextAlias(), new AuthenticationRequestFilter(authenticationService));
    }
}