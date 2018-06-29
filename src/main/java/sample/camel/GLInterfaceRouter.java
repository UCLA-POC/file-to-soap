/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package sample.camel;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.builder.xml.Namespaces;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * A simple Camel route that triggers from a timer and calls a bean and prints
 * to system out.
 * <p/>
 * Use <tt>@Component</tt> to make Camel auto detect this route when starting.
 */
@Component
public class GLInterfaceRouter extends RouteBuilder {

	static Logger LOG = LoggerFactory.getLogger(GLInterfaceRouter.class);

	@Override
	public void configure() throws Exception {

		Namespaces ns = new Namespaces("c", "http://xmlns.oracle.com/apps/financials/commonModules/shared/model/erpIntegrationService/types/")
							.add("soap", "http://schemas.xmlsoap.org/soap/envelope/");
		
		from("file:input?noop=true&include=.*zip")
			.routeId("file-to-soap:: Route#1 :: Unzip")
			.log("1. Base64 encode the input file... ${header.CamelFileName}")
			.marshal().base64()
			.convertBodyTo(String.class)
			.to("velocity:importBulkData.vm")
			.to("file:output?fileName=import-bulk-data-request-${file:name.noext}-${date:now:yyyyMMddssS}.xml&fileExist=Append")
			.to("direct:importBulkData")
			.log("5. Done processing the zip file: ${header.CamelFileName}");
		
		from("direct:importBulkData")
			.routeId("file-to-soap :: Route#2 :: importBulkData")
			.tracing("true")
			.convertBodyTo(String.class)
			.setHeader("Authorization", simple("Basic c2Jhc2F2YTpIdXJvbjEyMyE="))
			.setHeader("Accept-Encoding", simple("gzip, deflate"))
			.setHeader("SOAPAction", simple("http://xmlns.oracle.com/apps/financials/commonModules/shared/model/erpIntegrationService/importBulkData"))
			.setHeader(Exchange.HTTP_METHOD, constant("POST"))
		    .setHeader(Exchange.CONTENT_TYPE, constant("text/xml"))
		    .log("2 POSTing the SOAP Request to Oracle Cloud: importBulkData")
			.to("restlet:{{gl.interface.endpoint.url}}")
			//.process(exchange -> log.info("4.1 The response code from Oracle Financials Cloud ErpIntegrationService is: {}", exchange.getIn().getHeader(Exchange.HTTP_RESPONSE_CODE)))
			.convertBodyTo(String.class)
			.transform()
				.exchange(this::getSOAPResponse)
			.to("file:output?fileName=import-bulk-data-response-${file:name.noext}-${date:now:yyyyMMddssS}.xml&fileExist=Append")
	        .setBody(ns.xpath("//soap:Envelope/soap:Body/c:importBulkDataResponse/c:result/text()"))
	        .convertBodyTo(String.class)
	        .log("2.1 The response from Oracle Cloud: importBulkData: ${body}") 
			.to("direct:essJobStatus");
		
		from("direct:essJobStatus")
			.routeId("file-to-soap :: Route#3 :: ESSJobStatus")
	        .to("velocity:getESSJobStatus.vm")
	        .to("file:output?fileName=essjobstatus-request-${file:name.noext}-${date:now:yyyyMMddssS}.txt")
			.setHeader("Authorization", simple("Basic c2Jhc2F2YTpIdXJvbjEyMyE="))
			.setHeader("Accept-Encoding", simple("gzip, deflate"))
			.setHeader("SOAPAction", simple("http://xmlns.oracle.com/apps/financials/commonModules/shared/model/erpIntegrationService/getESSJobStatus"))
			.setHeader(Exchange.HTTP_METHOD, constant("POST"))
		    .setHeader(Exchange.CONTENT_TYPE, constant("text/xml"))
		    .log("3 POSTing the SOAP Request to Oracle Cloud: getESSJobStatus")
			.to("restlet:{{gl.interface.endpoint.url}}")
			//.process(exchange -> log.info("4.2 The response code from Oracle Financials Cloud ErpIntegrationService is: {}", exchange.getIn().getHeader(Exchange.HTTP_RESPONSE_CODE)))
			.convertBodyTo(String.class)
			.transform()
				.exchange(this::getSOAPResponse)
			.to("file:output?fileName=essjobstatus-response-${file:name.noext}-${date:now:yyyyMMddssS}.xml&fileExist=Append")
	        .setBody(ns.xpath("//soap:Envelope/soap:Body/c:getESSJobStatusResponse/c:result/text()"))
	        .convertBodyTo(String.class)
	        .log("3.1 The response from Oracle Oracle Cloud: getESSJobStatus: ${body}")
			.to("file:output?fileName=essjobstatus-final-response-${file:name.noext}-${date:now:yyyyMMddssS}.txt&fileExist=Append");
	}
	
	private String getSOAPResponse(Exchange exchange) {
		String body = exchange.getIn().getBody(String.class);
		return body.substring(body.indexOf("<?xml"), body.indexOf(":Envelope>")+10);
		
    }
	

}
