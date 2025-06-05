package com.poc.tcs.orquestador.controller;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
public class DummyPagoController {
    @PostMapping("/mock-pago-ok")
    public ResponseEntity<String> mockOk() {
        return ResponseEntity.ok("pago ok");
    }

    @PostMapping("/mock-pago-error")
    public ResponseEntity<String> mockError() {
        return ResponseEntity.badRequest().body("datos invalidos");
    }
}
