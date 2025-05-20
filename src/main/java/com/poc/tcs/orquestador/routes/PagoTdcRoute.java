package com.poc.tcs.orquestador.routes;

import com.poc.tcs.orquestador.util.EncryptionUtils;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.stereotype.Component;

import java.net.ConnectException;

@Component
public class PagoTdcRoute extends RouteBuilder {
    @Override
    public void configure() throws Exception {

//        onException(Exception.class)
//                .maximumRedeliveries(3)
//                .redeliveryDelay(2000)
//                .retryAttemptedLogLevel(LoggingLevel.WARN)
//                .handled(true)
//                .to("kafka:pagos.tdc.dlq");
        onException(ConnectException.class)
                .maximumRedeliveries(5)
                .redeliveryDelay(3000)
                .retryAttemptedLogLevel(LoggingLevel.WARN)
                .handled(true)
                .to("kafka:pagos.tdc.retry");

        onException(RuntimeException.class)
                .maximumRedeliveries(1)
                .handled(true)
                .to("kafka:pagos.tdc.dlq");
        errorHandler(deadLetterChannel("kafka:pagos.tdc.dlq?brokers=localhost:9092")
                .maximumRedeliveries(3)
                .redeliveryDelay(2000)
                .retryAttemptedLogLevel(LoggingLevel.WARN));



        from("direct:inicioPago")
                .process(exchange -> {
                    String tarjeta = exchange.getIn().getHeader("numTarjeta", String.class);
                    String cifrada = EncryptionUtils.encrypt(tarjeta);
                    exchange.getIn().setHeader("numTarjeta", cifrada);
                })
                .to("kafka:pagos.tdc.request")
                .to("direct:procesarPago");

        from("direct:procesarPago")
                .log("Procesando pago con resiliencia...")
                .circuitBreaker()
                .resilience4jConfiguration()
                .failureRateThreshold(50)
                .waitDurationInOpenState(5000)
                .permittedNumberOfCallsInHalfOpenState(2)
                .slidingWindowSize(4)
                .end()
                .to("http://localhost:8081/servicio/pago")
                .onFallback()
                .log("Fallback activado: servicio de pago no disponible")
                .to("direct:compensacion")
                .end()

                .multicast().parallelProcessing()
                .to("direct:notificar", "direct:auditar")
                .end()
                .to("kafka:pagos.tdc.ok?brokers=localhost:9092");

        from("kafka:pagos.tdc.compensacion")
                .log("Compensando pago fallido: ${body}")
                .to("bean:compensacionService?method=compensar");

        from("direct:compensacion")
                .log("Ejecutando lógica de compensación...")
                .to("kafka:pagos.tdc.compensacion?brokers=localhost:9092");




//        from("direct:procesarPago")
//                .log("Procesando pago...")
//                .doTry()
//                .to("http://localhost:8081/servicio/pago")
//                .multicast().parallelProcessing()
//                .to("direct:notificar", "direct:auditar")
//                .endDoTry()
//                .to("kafka:pagos.tdc.ok?brokers=localhost:9092")
//                .doCatch (Exception.class)
//                .log("Error en el pago: ${exception.message}")
//                .to("kafka:pagos.tdc.retry?brokers=localhost:9092")
//                .end();
//
//        from("direct:notificar")
//                .log("Notificando al cliente del pago...");
//
//        from("direct:auditar")
//                .log("Auditando operación para cumplimiento...");

    }
}

