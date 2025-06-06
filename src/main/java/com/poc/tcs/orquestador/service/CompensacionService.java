package com.poc.tcs.orquestador.service;

import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

@Component
public class CompensacionService {
    public void compensar(String mensaje) {
        System.out.println("Ejecutando compensación: " + mensaje);

    }
}
