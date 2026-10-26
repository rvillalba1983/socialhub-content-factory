package com.socialhub.platform;

/** Regla de negocio incumplida (por ejemplo, una transición de estado no permitida). Se responde con 409. */
public class DomainException extends RuntimeException {
    public DomainException(String message) { super(message); }
}
