package com.poc.tcs.orquestador.service;

import org.springframework.stereotype.Service;

@Service("compensacionService")
public class CompensacionService {
    public void compensar(String mensaje) {
        System.out.println("Ejecutando compensación: " + mensaje);

    }
}
