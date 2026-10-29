package com.controljob.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

public final class TestClock extends Clock {

    private Instant instante;
    private final ZoneId zona;

    public TestClock(Instant instante, ZoneId zona) {
        this.instante = instante;
        this.zona = zona;
    }

    public void avancar(Duration duracao) {
        instante = instante.plus(duracao);
    }

    @Override
    public ZoneId getZone() {
        return zona;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return new TestClock(instante, zone);
    }

    @Override
    public Instant instant() {
        return instante;
    }
}
