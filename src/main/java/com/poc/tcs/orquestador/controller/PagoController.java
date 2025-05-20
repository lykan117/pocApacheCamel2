package com.poc.tcs.orquestador.controller;

import com.poc.tcs.orquestador.dto.PagoRequest;
import org.apache.camel.ProducerTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/pago")
public class PagoController {
    @Autowired
    private ProducerTemplate producerTemplate;

    @PostMapping
    public String iniciarPago(@RequestBody PagoRequest request) {
        producerTemplate.sendBody("direct:inicioPago", request);
        return "Solicitud de pago enviada";
    }
}

