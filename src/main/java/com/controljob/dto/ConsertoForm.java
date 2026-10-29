package com.controljob.dto;

import com.controljob.model.Conserto;
import com.controljob.model.TipoConserto;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDate;

public class ConsertoForm {

    private Long id;

    @NotBlank(message = "Modelo do aparelho é obrigatório")
    @Size(max = 120, message = "Modelo do aparelho deve ter no máximo 120 caracteres")
    private String modeloAparelho;

    @NotBlank(message = "Número da OS é obrigatório")
    @Size(max = 60, message = "Número da OS deve ter no máximo 60 caracteres")
    private String numeroOs;

    @Size(max = 32, message = "IMEI deve ter no máximo 32 caracteres")
    private String imei;

    @NotNull(message = "Tipo de conserto é obrigatório")
    private TipoConserto tipoConserto;

    @NotBlank(message = "Descrição do conserto é obrigatória")
    @Size(max = 1000, message = "Descrição deve ter no máximo 1000 caracteres")
    private String descricao;

    @NotNull(message = "Valor é obrigatório")
    @DecimalMin(value = "0.01", message = "Valor deve ser maior que zero")
    private BigDecimal valor;

    @NotNull(message = "Data é obrigatória")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate dataConserto;

    public static ConsertoForm from(Conserto conserto) {
        ConsertoForm form = new ConsertoForm();
        form.id = conserto.getId();
        form.modeloAparelho = conserto.getModeloAparelho();
        form.numeroOs = conserto.getNumeroOs();
        form.imei = conserto.getImei();
        form.tipoConserto = conserto.getTipoConserto();
        form.descricao = conserto.getDescricao();
        form.valor = conserto.getValor();
        form.dataConserto = conserto.getDataConserto();
        return form;
    }

    public Conserto paraNovo() {
        Conserto conserto = new Conserto();
        aplicarEm(conserto);
        return conserto;
    }

    public void aplicarEm(Conserto conserto) {
        conserto.setModeloAparelho(modeloAparelho);
        conserto.setNumeroOs(numeroOs);
        conserto.setImei(imei);
        conserto.setTipoConserto(tipoConserto);
        conserto.setDescricao(descricao);
        conserto.setValor(valor);
        conserto.setDataConserto(dataConserto);
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getModeloAparelho() {
        return modeloAparelho;
    }

    public void setModeloAparelho(String modeloAparelho) {
        this.modeloAparelho = modeloAparelho;
    }

    public String getNumeroOs() {
        return numeroOs;
    }

    public void setNumeroOs(String numeroOs) {
        this.numeroOs = numeroOs;
    }

    public String getImei() {
        return imei;
    }

    public void setImei(String imei) {
        this.imei = imei;
    }

    public TipoConserto getTipoConserto() {
        return tipoConserto;
    }

    public void setTipoConserto(TipoConserto tipoConserto) {
        this.tipoConserto = tipoConserto;
    }

    public String getDescricao() {
        return descricao;
    }

    public void setDescricao(String descricao) {
        this.descricao = descricao;
    }

    public BigDecimal getValor() {
        return valor;
    }

    public void setValor(BigDecimal valor) {
        this.valor = valor;
    }

    public LocalDate getDataConserto() {
        return dataConserto;
    }

    public void setDataConserto(LocalDate dataConserto) {
        this.dataConserto = dataConserto;
    }
}
