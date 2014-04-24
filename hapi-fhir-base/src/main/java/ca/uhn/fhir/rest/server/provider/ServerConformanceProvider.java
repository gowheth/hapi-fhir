package ca.uhn.fhir.rest.server.provider;

/*
 * #%L
 * HAPI FHIR Library
 * %%
 * Copyright (C) 2014 University Health Network
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import ca.uhn.fhir.context.RuntimeResourceDefinition;
import ca.uhn.fhir.model.api.ExtensionDt;
import ca.uhn.fhir.model.dstu.resource.Conformance;
import ca.uhn.fhir.model.dstu.resource.Conformance.Rest;
import ca.uhn.fhir.model.dstu.resource.Conformance.RestResource;
import ca.uhn.fhir.model.dstu.resource.Conformance.RestResourceSearchParam;
import ca.uhn.fhir.model.dstu.valueset.RestfulConformanceModeEnum;
import ca.uhn.fhir.model.dstu.valueset.RestfulOperationSystemEnum;
import ca.uhn.fhir.model.dstu.valueset.RestfulOperationTypeEnum;
import ca.uhn.fhir.model.primitive.BooleanDt;
import ca.uhn.fhir.model.primitive.IdDt;
import ca.uhn.fhir.model.primitive.StringDt;
import ca.uhn.fhir.rest.annotation.Metadata;
import ca.uhn.fhir.rest.method.BaseMethodBinding;
import ca.uhn.fhir.rest.method.SearchMethodBinding;
import ca.uhn.fhir.rest.param.IParameter;
import ca.uhn.fhir.rest.param.BaseQueryParameter;
import ca.uhn.fhir.rest.server.ResourceBinding;
import ca.uhn.fhir.rest.server.RestfulServer;
import ca.uhn.fhir.util.ExtensionConstants;

public class ServerConformanceProvider {

	private volatile Conformance myConformance;
	private final RestfulServer myRestfulServer;

	public ServerConformanceProvider(RestfulServer theRestfulServer) {
		myRestfulServer = theRestfulServer;
	}

	@Metadata
	public Conformance getServerConformance() {
		if (myConformance != null) {
			return myConformance;
		}

		Conformance retVal = new Conformance();
		retVal.getSoftware().setName(myRestfulServer.getServerName());
		retVal.getSoftware().setVersion(myRestfulServer.getServerVersion());

		Rest rest = retVal.addRest();
		rest.setMode(RestfulConformanceModeEnum.SERVER);

		Set<RestfulOperationSystemEnum> systemOps = new HashSet<RestfulOperationSystemEnum>();

		for (ResourceBinding next : myRestfulServer.getResourceBindings()) {

			Set<RestfulOperationTypeEnum> resourceOps = new HashSet<RestfulOperationTypeEnum>();
			RestResource resource = rest.addResource();

			String resourceName = next.getResourceName();
			RuntimeResourceDefinition def = myRestfulServer.getFhirContext().getResourceDefinition(resourceName);
			resource.getType().setValue(def.getName());
			resource.getProfile().setId(new IdDt(def.getResourceProfile()));

			Map<String, Conformance.RestResourceSearchParam> nameToSearchParam = new HashMap<String, Conformance.RestResourceSearchParam>();
			for (BaseMethodBinding nextMethodBinding : next.getMethodBindings()) {
				RestfulOperationTypeEnum resOp = nextMethodBinding.getResourceOperationType();
				if (resOp != null) {
					if (resourceOps.contains(resOp) == false) {
						resourceOps.add(resOp);
						resource.addOperation().setCode(resOp);
					}
				}

				RestfulOperationSystemEnum sysOp = nextMethodBinding.getSystemOperationType();
				if (sysOp != null) {
					if (systemOps.contains(sysOp) == false) {
						systemOps.add(sysOp);
						rest.addOperation().setCode(sysOp);
					}
				}

				if (nextMethodBinding instanceof SearchMethodBinding) {
					List<IParameter> params = ((SearchMethodBinding) nextMethodBinding).getParameters();
					// TODO: this would probably work best if we sorted these by required first, then optional
					
					RestResourceSearchParam searchParam = null;
					StringDt searchParamChain = null;
					for (IParameter nextParameterObj : params) {
						if (!(nextParameterObj instanceof BaseQueryParameter)) {
							continue;
						}
						
						BaseQueryParameter nextParameter = (BaseQueryParameter)nextParameterObj;
						if (nextParameter.getName().startsWith("_")) {
							continue;
						}

						if (searchParam == null) {
							if (!nameToSearchParam.containsKey(nextParameter.getName())) {
								RestResourceSearchParam param = resource.addSearchParam();
								param.setName(nextParameter.getName());
								searchParam = param;
							} else {
								searchParam = nameToSearchParam.get(nextParameter.getName());
							}
							
							if (searchParam !=null) {
								searchParam.setType(nextParameter.getParamType());
							}
							
						} else if (searchParamChain == null) {
							searchParam.addChain(nextParameter.getName());
							searchParamChain = searchParam.getChain().get(searchParam.getChain().size()-1);
							ExtensionDt ext = new ExtensionDt();
							ext.setUrl(ExtensionConstants.CONF_CHAIN_REQUIRED);
							ext.setValue(new BooleanDt(nextParameter.isRequired()));
							searchParamChain.getUndeclaredExtensions().add(ext);
							
						} else {
							ExtensionDt ext = new ExtensionDt();
							ext.setUrl(ExtensionConstants.CONF_ALSO_CHAIN);
							searchParamChain.getUndeclaredExtensions().add(ext);
							
							ExtensionDt extReq = new ExtensionDt();
							extReq.setUrl(ExtensionConstants.CONF_CHAIN_REQUIRED);
							extReq.setValue(new BooleanDt(nextParameter.isRequired()));
							ext.getUndeclaredExtensions().add(extReq);

						}

					}
				}

			}

		}

		myConformance = retVal;
		return retVal;
	}

}