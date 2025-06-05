package com.poc.tcs.orquestador.routes;

import com.poc.tcs.orquestador.dto.PagoRequest;
import com.poc.tcs.orquestador.util.EncryptionUtils;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.ConnectException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Component
public class PagoTdcRoute extends RouteBuilder {

    //@Autowired
    //private CustomMetrics customMetrics;

    @Override
    public void configure() throws Exception {

//        onException(Exception.class)
//                .maximumRedeliveries(3)
//                .redeliveryDelay(2000)
//                .retryAttemptedLogLevel(LoggingLevel.WARN)
//                .handled(true)
//                .to("kafka:pagos.tdc.dlq");

        errorHandler(deadLetterChannel("kafka:pagosTdcDlq?brokers=localhost:9092")
                .maximumRedeliveries(3)
                .redeliveryDelay(2000)
                .retryAttemptedLogLevel(LoggingLevel.WARN));
        onException(ConnectException.class)
                .maximumRedeliveries(5)
                .redeliveryDelay(3000)
                .retryAttemptedLogLevel(LoggingLevel.WARN)
                .handled(true)
                .to("kafka:pagosTdcRetry?brokers=localhost:9092");

        onException(RuntimeException.class)
                .maximumRedeliveries(1)
                .handled(true)
                .to("kafka:pagosTdcDlq?brokers=localhost:9092");


        from("direct:inicioPago")
                .throttle(10).timePeriodMillis(1000)
                .process(exchange -> {
                    // ID de correlación
                    String id = exchange.getExchangeId();
                    exchange.getIn().setHeader("correlationId", id);
                    exchange.getMessage().setHeader("X-Correlation-Id", id);
                    log.info("Correlation ID: {}", id);
                })
                .process(exchange -> {
                    // Leer el body como objeto PagoRequest
                    PagoRequest pago = exchange.getIn().getBody(PagoRequest.class);

                    // Validar y cifrar el número de cuenta
                    if (pago != null && pago.getNumeroCuenta() != null) {
                        String cifrada = EncryptionUtils.encrypt(pago.getNumeroCuenta());
                        pago.setNumeroCuenta(cifrada); // Sobrescribir con versión cifrada
                    }

                    // Actualizar el body con el objeto modificado
                    exchange.getIn().setBody(pago);

                    log.info("Cuenta cifrada y body listo para Kafka");
                })
                .to("kafka:pagos.tdc?brokers=localhost:9092")
                .to("direct:procesarPago");


//        from("direct:inicioPago")
//                .throttle(10).timePeriodMillis(1000)
//                .process(exchange -> {
//                    String id = exchange.getExchangeId();
//                    exchange.getIn().setHeader("correlationId", id);
//                    exchange.getMessage().setHeader("X-Correlation-Id", id);
//                    log.info("Correlation ID: " + id);
//                })
//                .process(exchange -> {
//                    String tarjeta = exchange.getIn().getHeader("numeroCuenta", String.class);
//                    String cifrada = EncryptionUtils.encrypt(tarjeta);
//                    exchange.getIn().setHeader("numeroCuenta", cifrada);
//                   System.out.println("si paso aqui");// .log.info
//                })
//                .to("kafka:pagostdcRequest?brokers=localhost:9092")
//                .to("direct:procesarPago");

        from("direct:procesarPago")
                .log("Procesando pago con resiliencia...")
                .circuitBreaker()
                .resilience4jConfiguration()
                .failureRateThreshold(50)
                .waitDurationInOpenState(5000)
                .permittedNumberOfCallsInHalfOpenState(2)
                .slidingWindowSize(4)
                .end()
                .to("http://localhost:8090/mock-pago-ok")//http://localhost:8090/mock-pago-error
                .process(exchange -> {
                    Map<String, Object> evento = new HashMap<>();
                    evento.put("evento", "pago_exitoso");
                    evento.put("timestamp", Instant.now().toString());
                    evento.put("mensaje", "El pago fue exitoso");
                    evento.put("idTransaccion", exchange.getIn().getHeader("idTransaccion")); // opcional
                    exchange.getIn().setBody(evento);
                })
                .marshal().json()
                .to("kafka:trazabilidad-events?brokers={{camel.component.kafka.brokers}}")
                .onFallback()
                .log("Fallback activado: servicio de pago no disponible")
                .process(exchange -> {
                    Map<String, Object> evento = new HashMap<>();
                    evento.put("evento", "pago_fallido");
                    evento.put("timestamp", Instant.now().toString());
                    evento.put("mensaje", "Fallo al procesar el pago, se ejecuta compensación");
                    evento.put("idTransaccion", exchange.getIn().getHeader("idTransaccion")); // opcional
                    exchange.getIn().setBody(evento);
                })
                .marshal().json()
                .to("kafka:trazabilidad-events?brokers=localhost:9092")
                .to("direct:compensacion")
                .end()

                .multicast().parallelProcessing()
                .to("direct:notificar", "direct:auditar")
                .end()
                .to("kafka:pagosTdcOK?brokers=localhost:9092");


        from("kafka:pagos.tdc.compensacion?brokers=localhost:9092")
                .log("Compensando pago fallido: ${body}")
                .to("bean:compensacionService?method=compensar");

        from("direct:compensacion")
                .log("Ejecutando lógica de compensación...")
                .to("kafka:pagosTdcCompensacion?brokers=localhost:9092");

        from("direct:dlq")
                .log("Mensaje enviado a DLQ")
                .to("kafka:pagosTdcDlq?brokers=localhost:9092");

        from("direct:notificar")
                .log("Notificando al cliente del pago... ${body}");

        from("direct:auditar")
                .log("Auditando operación para cumplimiento... ${body}");



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

