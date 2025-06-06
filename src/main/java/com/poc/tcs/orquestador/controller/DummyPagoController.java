package com.poc.tcs.orquestador.controller;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.ThreadLocalRandom;


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

    @PostMapping("/mock-pago-aleatorio")
    public ResponseEntity<String> getRandomResponse() {
        // Timeout aleatorio entre 1 y 4 segundos
        int timeoutMillis = ThreadLocalRandom.current().nextInt(1000, 4001);
        try {
            Thread.sleep(timeoutMillis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Interrupción inesperada");
        }

        // Selección aleatoria del código de estado
        int randomCode = ThreadLocalRandom.current().nextInt(4); // 0 a 3

        switch (randomCode) {
            case 0:
                return ResponseEntity.ok("Todo bien (200 OK)");
            case 1:
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body("Petición incorrecta (400 Bad Request)");
            case 2:
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body("No encontrado (404 Not Found)");
//            case 3:
//                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
//                        .body("Error interno (500 Internal Server Error)");
            default:
                return ResponseEntity.status(HttpStatus.I_AM_A_TEAPOT)
                        .body("Este código no debería aparecer ☕");
        }
    }
}
