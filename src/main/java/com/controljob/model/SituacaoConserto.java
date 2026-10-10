package com.controljob.model;

public enum SituacaoConserto {

    TODOS("Todos"),
    PENDENTE("Pendentes"),
    FECHADO("Fechados");

    private final String label;

    SituacaoConserto(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
