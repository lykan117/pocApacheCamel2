package com.poc.tcs.orquestador.routes;

import com.poc.tcs.orquestador.dto.PagoRequest;
import com.poc.tcs.orquestador.util.EncryptionUtils;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.dataformat.JsonLibrary;
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




        onException(Exception.class)
                .maximumRedeliveries(3)
                .redeliveryDelay(3000)
                .onRedelivery(exchange -> {
                    int counter = exchange.getIn().getHeader(Exchange.REDELIVERY_COUNTER, Integer.class) != null ?
                            exchange.getIn().getHeader(Exchange.REDELIVERY_COUNTER, Integer.class) + 1 : 1;
                    log.warn("🔁 Reintentando operación... intento {}", counter);

                })
                .handled(true)
                .log("Error capturado: ${exception.message}")
                .to("direct:dlq");


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


        from("direct:procesarPago")
                .log("Procesando pago con resiliencia...")
                .circuitBreaker()
                .resilience4jConfiguration()
                .failureRateThreshold(50)
                .waitDurationInOpenState(5000)
                .permittedNumberOfCallsInHalfOpenState(2)
                .slidingWindowSize(4)
                .timeoutEnabled(true)
                .timeoutDuration(3000)
                .end()


                .setHeader("Content-Type", constant("application/json"))
                .marshal().json(JsonLibrary.Jackson)
                .toD("http://localhost:8090/mock-pago-error?bridgeEndpoint=true&httpMethod=POST")
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

                .onFallback()
                .log("🔥 Fallback activado, enviando a retry topic pagos.tdc.retry")
                .to("kafka:pagos.tdc.retry?brokers=localhost:9092")
                .end()

                .multicast().parallelProcessing()
                .to("direct:notificar", "direct:auditar")
                .end()
                .to("kafka:pagosTdcOK?brokers=localhost:9092");


        from("kafka:pagosTdcCompensacion?brokers=localhost:9092")
                .log("Compensando pago fallido: ${body}");


        from("direct:compensacion")
                .log("Ejecutando lógica de compensación...")//logica -------------

                .to("kafka:pagosTdcCompensacion?brokers=localhost:9092");

        from("kafka:pagos.tdc.retry?brokers=localhost:9092")
                .log("🔁 Reintentando compensación desde retry topic...")
                .setHeader("Content-Type", constant("application/json"))
                .marshal().json(JsonLibrary.Jackson)
                .toD("http://localhost:8090/mock-pago-aleatorio?bridgeEndpoint=true&httpMethod=POST")
                .process(exchange -> {
                    Map<String, Object> evento = new HashMap<>();
                    evento.put("evento", "pago_exitoso");
                    evento.put("timestamp", Instant.now().toString());
                    evento.put("mensaje", "El pago fue exitoso");
                    evento.put("idTransaccion", exchange.getIn().getHeader("idTransaccion")); // opcional
                    exchange.getIn().setBody(evento);
                })

                .marshal().json()
                .to("kafka:trazabilidad-events?brokers=localhost:9092")
                .to("direct:notificar", "direct:auditar")
                .end()
                .to("kafka:pagosTdcOK?brokers=localhost:9092");




        from("direct:dlq")
                .log("Mensaje enviado a DLQ")
                .to("kafka:pagosTdcDlq?brokers=localhost:9092");

        from("kafka:pagosTdcDlq?brokers=localhost:9092")
               // logica
                .to("direct:compensacion");

        from("direct:notificar")
                .log("Notificando al cliente del pago... ${body}");

        from("direct:auditar")
                .log("Auditando operación para cumplimiento... ${body}");





    }
}

