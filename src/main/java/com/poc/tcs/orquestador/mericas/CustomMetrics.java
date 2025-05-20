package com.poc.tcs.orquestador.mericas;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class CustomMetrics {

    private final MeterRegistry meterRegistry;

    public CustomMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void registrarPagoExitoso() {
        meterRegistry.counter("pagos.exitosos").increment();
    }

    public void registrarPagoFallido() {
        meterRegistry.counter("pagos.fallidos").increment();
    }
}
