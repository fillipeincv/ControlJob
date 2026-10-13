package com.controljob.model;

public enum SituacaoConserto {

    TODOS("Todos"),
    AGUARDANDO_RETIRADA("Aguardando retirada"),
    RETIRADO("Retirado pelo cliente");

    private final String label;

    SituacaoConserto(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
