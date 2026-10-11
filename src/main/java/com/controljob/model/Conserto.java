package com.controljob.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "consertos")
public class Conserto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Modelo do aparelho é obrigatório")
    @Column(nullable = false)
    private String modeloAparelho;

    @NotBlank(message = "Número da OS é obrigatório")
    @Column(nullable = false)
    private String numeroOs;

    @NotBlank(message = "IMEI é obrigatório")
    @Column
    private String imei;

    @NotNull(message = "Tipo de conserto é obrigatório")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoConserto tipoConserto;

    @NotBlank(message = "Descrição do conserto é obrigatória")
    @Column(nullable = false, length = 1000)
    private String descricao;

    @NotNull(message = "Valor é obrigatório")
    @DecimalMin(value = "0.01", message = "Valor deve ser maior que zero")
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal valor;

    @NotNull(message = "Data é obrigatória")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    @Column(nullable = false)
    private LocalDate dataConserto;

    @Column
    private LocalDate dataFechamento;

    public Conserto() {
    }

    public BigDecimal getComissao() {
        if (tipoConserto == null || valor == null) {
            return BigDecimal.ZERO;
        }
        return tipoConserto.calcularComissao(valor);
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

    public LocalDate getDataFechamento() {
        return dataFechamento;
    }

    public void setDataFechamento(LocalDate dataFechamento) {
        this.dataFechamento = dataFechamento;
    }
}
