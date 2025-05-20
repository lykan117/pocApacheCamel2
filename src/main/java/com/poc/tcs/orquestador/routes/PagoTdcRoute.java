package com.poc.tcs.orquestador.routes;

import org.apache.camel.builder.RouteBuilder;
import org.springframework.stereotype.Component;

@Component
public class PagoTdcRoute extends RouteBuilder {
    @Override
    public void configure() throws Exception {
        from("direct:inicioPago")
                .routeId("pagoTdcRoute")
                .log("Inicio del pago de TDC")
                .to("kafka:pagos.tdc.request?brokers=localhost:9092")
                .to("direct:procesarPago");

        from("direct:procesarPago")
                .log("Procesando pago...")
                .doTry()
                .to("http://localhost:8081/servicio/pago")
                .multicast().parallelProcessing()
                .to("direct:notificar", "direct:auditar")
                .endDoTry()
                .to("kafka:pagos.tdc.ok?brokers=localhost:9092")
                .doCatch (Exception.class)
                .log("Error en el pago: ${exception.message}")
                .to("kafka:pagos.tdc.retry?brokers=localhost:9092")
                .end();

        from("direct:notificar")
                .log("Notificando al cliente del pago...");

        from("direct:auditar")
                .log("Auditando operación para cumplimiento...");

    }
}

