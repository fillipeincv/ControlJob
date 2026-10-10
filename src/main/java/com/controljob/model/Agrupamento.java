package com.controljob.model;

public enum Agrupamento {

    NENHUM("Sem agrupar"),
    MES("Por mês"),
    TIPO("Por tipo de conserto"),
    MODELO("Por modelo de aparelho"),
    SITUACAO("Por situação");

    private final String label;

    Agrupamento(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
