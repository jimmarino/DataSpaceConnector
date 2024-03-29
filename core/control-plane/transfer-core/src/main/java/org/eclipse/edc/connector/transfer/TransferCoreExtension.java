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
 *       Bayerische Motoren Werke Aktiengesellschaft (BMW AG) - improvements
 *
 */

package org.eclipse.edc.connector.transfer;

import org.eclipse.edc.connector.asset.spi.index.DataAddressResolver;
import org.eclipse.edc.connector.policy.spi.store.PolicyArchive;
import org.eclipse.edc.connector.transfer.command.handlers.AddProvisionedResourceCommandHandler;
import org.eclipse.edc.connector.transfer.command.handlers.DeprovisionCompleteCommandHandler;
import org.eclipse.edc.connector.transfer.edr.DataAddressToEndpointDataReferenceTransformer;
import org.eclipse.edc.connector.transfer.edr.EndpointDataReferenceReceiverRegistryImpl;
import org.eclipse.edc.connector.transfer.listener.TransferProcessEventListener;
import org.eclipse.edc.connector.transfer.process.TransferProcessManagerImpl;
import org.eclipse.edc.connector.transfer.provision.DeprovisionResponsesHandler;
import org.eclipse.edc.connector.transfer.provision.ProvisionResponsesHandler;
import org.eclipse.edc.connector.transfer.spi.TransferProcessManager;
import org.eclipse.edc.connector.transfer.spi.TransferProcessPendingGuard;
import org.eclipse.edc.connector.transfer.spi.edr.EndpointDataReferenceReceiverRegistry;
import org.eclipse.edc.connector.transfer.spi.event.TransferProcessStarted;
import org.eclipse.edc.connector.transfer.spi.flow.DataFlowManager;
import org.eclipse.edc.connector.transfer.spi.observe.TransferProcessObservable;
import org.eclipse.edc.connector.transfer.spi.provision.ProvisionManager;
import org.eclipse.edc.connector.transfer.spi.provision.ResourceManifestGenerator;
import org.eclipse.edc.connector.transfer.spi.retry.TransferWaitStrategy;
import org.eclipse.edc.connector.transfer.spi.store.TransferProcessStore;
import org.eclipse.edc.connector.transfer.spi.types.DeprovisionedResource;
import org.eclipse.edc.connector.transfer.spi.types.ProvisionedContentResource;
import org.eclipse.edc.runtime.metamodel.annotation.CoreExtension;
import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.runtime.metamodel.annotation.Provides;
import org.eclipse.edc.runtime.metamodel.annotation.Setting;
import org.eclipse.edc.spi.command.CommandHandlerRegistry;
import org.eclipse.edc.spi.event.EventRouter;
import org.eclipse.edc.spi.message.RemoteMessageDispatcherRegistry;
import org.eclipse.edc.spi.protocol.ProtocolWebhook;
import org.eclipse.edc.spi.retry.ExponentialWaitStrategy;
import org.eclipse.edc.spi.security.Vault;
import org.eclipse.edc.spi.system.ExecutorInstrumentation;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.spi.telemetry.Telemetry;
import org.eclipse.edc.spi.types.TypeManager;
import org.eclipse.edc.statemachine.retry.EntityRetryProcessConfiguration;
import org.eclipse.edc.transform.spi.TypeTransformerRegistry;
import org.jetbrains.annotations.NotNull;

import java.time.Clock;

import static org.eclipse.edc.statemachine.AbstractStateEntityManager.DEFAULT_BATCH_SIZE;
import static org.eclipse.edc.statemachine.AbstractStateEntityManager.DEFAULT_ITERATION_WAIT;
import static org.eclipse.edc.statemachine.AbstractStateEntityManager.DEFAULT_SEND_RETRY_BASE_DELAY;
import static org.eclipse.edc.statemachine.AbstractStateEntityManager.DEFAULT_SEND_RETRY_LIMIT;

/**
 * Provides core data transfer services to the system.
 */
@CoreExtension
@Provides({ TransferProcessManager.class, EndpointDataReferenceReceiverRegistry.class })
@Extension(value = TransferCoreExtension.NAME)
public class TransferCoreExtension implements ServiceExtension {

    public static final String NAME = "Transfer Core";

    @Setting(value = "the iteration wait time in milliseconds in the transfer process state machine. Default value " + DEFAULT_ITERATION_WAIT, type = "long")
    private static final String TRANSFER_STATE_MACHINE_ITERATION_WAIT_MILLIS = "edc.transfer.state-machine.iteration-wait-millis";

    @Setting(value = "the batch size in the transfer process state machine. Default value " + DEFAULT_BATCH_SIZE, type = "int")
    private static final String TRANSFER_STATE_MACHINE_BATCH_SIZE = "edc.transfer.state-machine.batch-size";

    @Setting(value = "how many times a specific operation must be tried before terminating the transfer with error", type = "int", defaultValue = DEFAULT_SEND_RETRY_LIMIT + "")
    private static final String TRANSFER_SEND_RETRY_LIMIT = "edc.transfer.send.retry.limit";

    @Setting(value = "The base delay for the transfer retry mechanism in millisecond", type = "long", defaultValue = DEFAULT_SEND_RETRY_BASE_DELAY + "")
    private static final String TRANSFER_SEND_RETRY_BASE_DELAY_MS = "edc.transfer.send.retry.base-delay.ms";

    @Inject
    private TransferProcessStore transferProcessStore;

    @Inject
    private DataFlowManager dataFlowManager;

    @Inject
    private ResourceManifestGenerator resourceManifestGenerator;

    @Inject
    private ProvisionManager provisionManager;

    @Inject
    private TransferProcessObservable observable;

    @Inject
    private PolicyArchive policyArchive;

    @Inject
    private CommandHandlerRegistry registry;

    @Inject
    private RemoteMessageDispatcherRegistry dispatcherRegistry;

    @Inject
    private DataAddressResolver addressResolver;

    @Inject
    private Vault vault;

    @Inject
    private EventRouter eventRouter;

    @Inject
    private Clock clock;

    @Inject
    private TypeManager typeManager;

    @Inject
    private Telemetry telemetry;

    @Inject
    private TypeTransformerRegistry typeTransformerRegistry;

    @Inject
    private ProtocolWebhook protocolWebhook;

    @Inject
    private TransferProcessPendingGuard pendingGuard;

    @Inject
    private ExecutorInstrumentation executorInstrumentation;

    private TransferProcessManagerImpl processManager;

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public void initialize(ServiceExtensionContext context) {
        var monitor = context.getMonitor();

        registerTypes(typeManager);

        var iterationWaitMillis = context.getSetting(TRANSFER_STATE_MACHINE_ITERATION_WAIT_MILLIS, DEFAULT_ITERATION_WAIT);
        var waitStrategy = context.hasService(TransferWaitStrategy.class) ? context.getService(TransferWaitStrategy.class) : new ExponentialWaitStrategy(iterationWaitMillis);

        typeTransformerRegistry.register(new DataAddressToEndpointDataReferenceTransformer());

        var endpointDataReferenceReceiverRegistry = new EndpointDataReferenceReceiverRegistryImpl(typeTransformerRegistry);
        context.registerService(EndpointDataReferenceReceiverRegistry.class, endpointDataReferenceReceiverRegistry);

        eventRouter.register(TransferProcessStarted.class, endpointDataReferenceReceiverRegistry);

        observable.registerListener(new TransferProcessEventListener(eventRouter, clock));

        var entityRetryProcessConfiguration = getEntityRetryProcessConfiguration(context);
        var provisionResponsesHandler = new ProvisionResponsesHandler(observable, monitor, vault, typeManager);
        var deprovisionResponsesHandler = new DeprovisionResponsesHandler(observable, monitor, vault);

        processManager = TransferProcessManagerImpl.Builder.newInstance()
                .waitStrategy(waitStrategy)
                .manifestGenerator(resourceManifestGenerator)
                .dataFlowManager(dataFlowManager)
                .provisionManager(provisionManager)
                .dispatcherRegistry(dispatcherRegistry)
                .monitor(monitor)
                .telemetry(telemetry)
                .executorInstrumentation(executorInstrumentation)
                .vault(vault)
                .clock(clock)
                .observable(observable)
                .store(transferProcessStore)
                .policyArchive(policyArchive)
                .batchSize(context.getSetting(TRANSFER_STATE_MACHINE_BATCH_SIZE, DEFAULT_BATCH_SIZE))
                .addressResolver(addressResolver)
                .entityRetryProcessConfiguration(entityRetryProcessConfiguration)
                .protocolWebhook(protocolWebhook)
                .provisionResponsesHandler(provisionResponsesHandler)
                .deprovisionResponsesHandler(deprovisionResponsesHandler)
                .pendingGuard(pendingGuard)
                .build();

        context.registerService(TransferProcessManager.class, processManager);

        registry.register(new AddProvisionedResourceCommandHandler(transferProcessStore, provisionResponsesHandler));
        registry.register(new DeprovisionCompleteCommandHandler(transferProcessStore, deprovisionResponsesHandler));
    }

    @Override
    public void start() {
        processManager.start();
    }

    @Override
    public void shutdown() {
        if (processManager != null) {
            processManager.stop();
        }
    }

    @NotNull
    private EntityRetryProcessConfiguration getEntityRetryProcessConfiguration(ServiceExtensionContext context) {
        var retryLimit = context.getSetting(TRANSFER_SEND_RETRY_LIMIT, DEFAULT_SEND_RETRY_LIMIT);
        var retryBaseDelay = context.getSetting(TRANSFER_SEND_RETRY_BASE_DELAY_MS, DEFAULT_SEND_RETRY_BASE_DELAY);
        return new EntityRetryProcessConfiguration(retryLimit, () -> new ExponentialWaitStrategy(retryBaseDelay));
    }

    private void registerTypes(TypeManager typeManager) {
        typeManager.registerTypes(ProvisionedContentResource.class);
        typeManager.registerTypes(DeprovisionedResource.class);
    }
}
