package com.controljob.service;

import com.controljob.model.Conserto;
import com.controljob.model.DashboardResumo;
import com.controljob.model.TipoConserto;
import com.controljob.repository.ConsertoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
public class ConsertoService {

    private final ConsertoRepository repository;
    private final SalarioConfigService salarioConfigService;

    public ConsertoService(
            ConsertoRepository repository,
            SalarioConfigService salarioConfigService
    ) {
        this.repository = repository;
        this.salarioConfigService = salarioConfigService;
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
        conserto.setImei(normalizarImei(conserto.getImei()));
        return repository.save(conserto);
    }

    @Transactional(readOnly = true)
    public List<Conserto> historicoPorImei(String imei) {
        String normalizado = normalizarImei(imei);
        if (normalizado == null) {
            return List.of();
        }
        return repository.findByImeiIgnoreCaseOrderByDataConsertoDesc(normalizado);
    }

    private String normalizarImei(String imei) {
        if (imei == null) {
            return null;
        }
        String limpo = imei.replaceAll("\\s+", "");
        return limpo.isEmpty() ? null : limpo;
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
        BigDecimal salarioFixo = salarioConfigService.salarioAtual();
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
        return salarioConfigService.salarioAtual();
    }

    private BigDecimal scale(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
