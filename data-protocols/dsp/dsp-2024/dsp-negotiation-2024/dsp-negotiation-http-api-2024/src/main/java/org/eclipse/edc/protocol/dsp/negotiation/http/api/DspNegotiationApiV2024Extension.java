/*
 *  Copyright (c) 2023 Fraunhofer Institute for Software and Systems Engineering
 *
 *  This program and the accompanying materials are made available under the
 *  terms of the Apache License, Version 2.0 which is available at
 *  https://www.apache.org/licenses/LICENSE-2.0
 *
 *  SPDX-License-Identifier: Apache-2.0
 *
 *  Contributors:
 *       Fraunhofer Institute for Software and Systems Engineering - initial API and implementation
 *       Cofinity-X - make DSP versions pluggable
 *
 */

package org.eclipse.edc.protocol.dsp.negotiation.http.api;

import org.eclipse.edc.connector.controlplane.services.spi.contractnegotiation.ContractNegotiationProtocolService;
import org.eclipse.edc.jsonld.spi.JsonLd;
import org.eclipse.edc.protocol.dsp.http.spi.message.DspRequestHandler;
import org.eclipse.edc.protocol.dsp.negotiation.http.api.controller.DspNegotiationApiController20241;
import org.eclipse.edc.protocol.dsp.negotiation.validation.ContractAgreementMessageValidator;
import org.eclipse.edc.protocol.dsp.negotiation.validation.ContractAgreementVerificationMessageValidator;
import org.eclipse.edc.protocol.dsp.negotiation.validation.ContractNegotiationEventMessageValidator;
import org.eclipse.edc.protocol.dsp.negotiation.validation.ContractNegotiationTerminationMessageValidator;
import org.eclipse.edc.protocol.dsp.negotiation.validation.ContractOfferMessageValidator;
import org.eclipse.edc.protocol.dsp.negotiation.validation.ContractRequestMessageValidator;
import org.eclipse.edc.protocol.spi.DataspaceProfileContextRegistry;
import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.spi.types.TypeManager;
import org.eclipse.edc.validator.spi.JsonObjectValidatorRegistry;
import org.eclipse.edc.web.jersey.providers.jsonld.JerseyJsonLdInterceptor;
import org.eclipse.edc.web.spi.WebService;
import org.eclipse.edc.web.spi.configuration.ApiContext;

import static org.eclipse.edc.protocol.dsp.spi.type.Dsp2024Constants.DSP_NAMESPACE_V_2024_1;
import static org.eclipse.edc.protocol.dsp.spi.type.Dsp2024Constants.DSP_SCOPE_V_2024_1;
import static org.eclipse.edc.protocol.dsp.spi.type.DspNegotiationPropertyAndTypeNames.DSPACE_TYPE_CONTRACT_AGREEMENT_MESSAGE_TERM;
import static org.eclipse.edc.protocol.dsp.spi.type.DspNegotiationPropertyAndTypeNames.DSPACE_TYPE_CONTRACT_AGREEMENT_VERIFICATION_MESSAGE_TERM;
import static org.eclipse.edc.protocol.dsp.spi.type.DspNegotiationPropertyAndTypeNames.DSPACE_TYPE_CONTRACT_NEGOTIATION_EVENT_MESSAGE_TERM;
import static org.eclipse.edc.protocol.dsp.spi.type.DspNegotiationPropertyAndTypeNames.DSPACE_TYPE_CONTRACT_NEGOTIATION_TERMINATION_MESSAGE_TERM;
import static org.eclipse.edc.protocol.dsp.spi.type.DspNegotiationPropertyAndTypeNames.DSPACE_TYPE_CONTRACT_OFFER_MESSAGE_TERM;
import static org.eclipse.edc.protocol.dsp.spi.type.DspNegotiationPropertyAndTypeNames.DSPACE_TYPE_CONTRACT_REQUEST_MESSAGE_TERM;
import static org.eclipse.edc.spi.constants.CoreConstants.JSON_LD;

/**
 * Creates and registers the controller for dataspace protocol v2024/1 negotiation requests.
 */
@Extension(value = DspNegotiationApiV2024Extension.NAME)
public class DspNegotiationApiV2024Extension implements ServiceExtension {

    public static final String NAME = "Dataspace Protocol Negotiation Api v2024/1";

    @Inject
    private WebService webService;
    @Inject
    private ContractNegotiationProtocolService protocolService;
    @Inject
    private JsonObjectValidatorRegistry validatorRegistry;
    @Inject
    private DspRequestHandler dspRequestHandler;
    @Inject
    private DataspaceProfileContextRegistry versionRegistry;

    @Inject
    private JsonLd jsonLd;

    @Inject
    private TypeManager typeManager;

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public void initialize(ServiceExtensionContext context) {
        registerValidators();

        webService.registerResource(ApiContext.PROTOCOL, new DspNegotiationApiController20241(protocolService, dspRequestHandler));
        webService.registerDynamicResource(ApiContext.PROTOCOL, DspNegotiationApiController20241.class, new JerseyJsonLdInterceptor(jsonLd, typeManager, JSON_LD, DSP_SCOPE_V_2024_1));
    }


    private void registerValidators() {
        validatorRegistry.register(DSP_NAMESPACE_V_2024_1.toIri(DSPACE_TYPE_CONTRACT_REQUEST_MESSAGE_TERM), ContractRequestMessageValidator.instance(DSP_NAMESPACE_V_2024_1));
        validatorRegistry.register(DSP_NAMESPACE_V_2024_1.toIri(DSPACE_TYPE_CONTRACT_OFFER_MESSAGE_TERM), ContractOfferMessageValidator.instance(DSP_NAMESPACE_V_2024_1));
        validatorRegistry.register(DSP_NAMESPACE_V_2024_1.toIri(DSPACE_TYPE_CONTRACT_NEGOTIATION_EVENT_MESSAGE_TERM), ContractNegotiationEventMessageValidator.instance(DSP_NAMESPACE_V_2024_1));
        validatorRegistry.register(DSP_NAMESPACE_V_2024_1.toIri(DSPACE_TYPE_CONTRACT_AGREEMENT_MESSAGE_TERM), ContractAgreementMessageValidator.instance(DSP_NAMESPACE_V_2024_1));
        validatorRegistry.register(DSP_NAMESPACE_V_2024_1.toIri(DSPACE_TYPE_CONTRACT_AGREEMENT_VERIFICATION_MESSAGE_TERM), ContractAgreementVerificationMessageValidator.instance(DSP_NAMESPACE_V_2024_1));
        validatorRegistry.register(DSP_NAMESPACE_V_2024_1.toIri(DSPACE_TYPE_CONTRACT_NEGOTIATION_TERMINATION_MESSAGE_TERM), ContractNegotiationTerminationMessageValidator.instance(DSP_NAMESPACE_V_2024_1));
    }
}
