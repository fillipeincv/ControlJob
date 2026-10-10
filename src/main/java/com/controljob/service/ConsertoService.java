package com.controljob.service;

import com.controljob.model.Conserto;
import com.controljob.model.DashboardResumo;
import com.controljob.model.TipoConserto;
import com.controljob.repository.ConsertoRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
public class ConsertoService {

    private final ConsertoRepository repository;
    private final BigDecimal salarioFixo;

    public ConsertoService(
            ConsertoRepository repository,
            @Value("${controljob.salario-fixo:1500.00}") BigDecimal salarioFixo
    ) {
        this.repository = repository;
        this.salarioFixo = salarioFixo;
    }

    @Transactional(readOnly = true)
    public List<Conserto> listarTodos() {
        return repository.findAllByDataFechamentoIsNullOrderByDataConsertoDesc();
    }

    @Transactional(readOnly = true)
    public List<Conserto> listarLojistas() {
        return repository.findByTipoConsertoAndDataFechamentoIsNullOrderByDataConsertoDesc(TipoConserto.CONSERTO_PARA_LOJISTAS);
    }

    @Transactional(readOnly = true)
    public Conserto buscarPorId(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Conserto não encontrado: " + id));
    }

    @Transactional
    public Conserto salvar(Conserto conserto) {
        if (conserto.getImei() != null && conserto.getImei().isBlank()) {
            conserto.setImei(null);
        }
        return repository.save(conserto);
    }

    @Transactional
    public void excluir(Long id) {
        repository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public DashboardResumo gerarResumo() {
        List<Conserto> todos = repository.findAllByDataFechamentoIsNullOrderByDataConsertoDesc();

        BigDecimal valorTotal = BigDecimal.ZERO;
        BigDecimal comissaoGerais = BigDecimal.ZERO;
        BigDecimal comissaoPlaca = BigDecimal.ZERO;
        BigDecimal comissaoLojistas = BigDecimal.ZERO;

        for (Conserto c : todos) {
            valorTotal = valorTotal.add(c.getValor());
            BigDecimal comissao = c.getComissao();

            switch (c.getTipoConserto()) {
                case CONSERTOS_EM_GERAL -> comissaoGerais = comissaoGerais.add(comissao);
                case REPARO_DE_PLACA -> comissaoPlaca = comissaoPlaca.add(comissao);
                case CONSERTO_PARA_LOJISTAS -> comissaoLojistas = comissaoLojistas.add(comissao);
            }
        }

        BigDecimal totalComissao = comissaoGerais.add(comissaoPlaca).add(comissaoLojistas);
        BigDecimal salarioTotal = salarioFixo.add(totalComissao);

        return new DashboardResumo(
                scale(valorTotal),
                scale(totalComissao),
                scale(salarioFixo),
                scale(salarioTotal),
                todos.size(),
                scale(comissaoGerais),
                scale(comissaoPlaca),
                scale(comissaoLojistas)
        );
    }

    public BigDecimal getSalarioFixo() {
        return salarioFixo;
    }

    private BigDecimal scale(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
