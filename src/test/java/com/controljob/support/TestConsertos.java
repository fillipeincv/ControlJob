package com.controljob.support;

import com.controljob.model.Conserto;
import com.controljob.model.TipoConserto;

import java.math.BigDecimal;
import java.time.LocalDate;

public final class TestConsertos {

    private TestConsertos() {
    }

    public static Conserto criar(long id, TipoConserto tipo, String valor, LocalDate dataConserto) {
        Conserto conserto = new Conserto();
        conserto.setId(id);
        conserto.setModeloAparelho("Modelo " + id);
        conserto.setNumeroOs("OS-" + id);
        conserto.setImei("imei-" + id);
        conserto.setTipoConserto(tipo);
        conserto.setDescricao("Conserto " + id);
        conserto.setValor(new BigDecimal(valor));
        conserto.setDataConserto(dataConserto);
        return conserto;
    }

    public static Conserto retirado(long id, TipoConserto tipo, String valor, LocalDate dataConserto, LocalDate dataRetirada) {
        Conserto conserto = criar(id, tipo, valor, dataConserto);
        conserto.setDataRetirada(dataRetirada);
        return conserto;
    }
}
