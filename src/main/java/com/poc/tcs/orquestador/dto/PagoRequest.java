package com.poc.tcs.orquestador.dto;

public class PagoRequest {

    private String numeroCuenta;
    private String cuentaDestino;
    private Double monto;

    // Constructor vacío necesario para deserialización
    public PagoRequest() {
    }

    public PagoRequest(String numeroCuenta, String cuentaDestino, Double monto) {
        this.numeroCuenta = numeroCuenta;
        this.cuentaDestino = cuentaDestino;
        this.monto = monto;
    }

    public String getNumeroCuenta() {
        return numeroCuenta;
    }

    public void setNumeroCuenta(String numeroCuenta) {
        this.numeroCuenta = numeroCuenta;
    }

    public String getCuentaDestino() {
        return cuentaDestino;
    }

    public void setCuentaDestino(String cuentaDestino) {
        this.cuentaDestino = cuentaDestino;
    }

    public Double getMonto() {
        return monto;
    }

    public void setMonto(Double monto) {
        this.monto = monto;
    }

    @Override
    public String toString() {
        return "PagoRequest{" +
                "numeroCuenta='" + numeroCuenta + '\'' +
                ", cuentaDestino='" + cuentaDestino + '\'' +
                ", monto=" + monto +
                '}';
    }
}