package com.controljob.service;

import com.controljob.model.Conserto;
import com.controljob.model.TipoConserto;
import com.controljob.repository.ConsertoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class RelatorioService {

    private final ConsertoRepository repository;
    private final SalarioConfigService salarioConfigService;

    public RelatorioService(ConsertoRepository repository, SalarioConfigService salarioConfigService) {
        this.repository = repository;
        this.salarioConfigService = salarioConfigService;
    }

    @Transactional(readOnly = true)
    public RelatorioData gerarRelatorio() {
        List<Conserto> todos = repository.findAllByDataFechamentoIsNullAndDataRetiradaIsNotNullOrderByIdAsc();
        BigDecimal valorTotal = repository.somarValorTotal();
        BigDecimal comissaoGerais = repository.somarValorPorTipo(TipoConserto.CONSERTOS_EM_GERAL)
                .multiply(TipoConserto.CONSERTOS_EM_GERAL.getPercentualComissao());
        BigDecimal comissaoPlaca = repository.somarValorPorTipo(TipoConserto.REPARO_DE_PLACA)
                .multiply(TipoConserto.REPARO_DE_PLACA.getPercentualComissao());
        BigDecimal comissaoLojistas = repository.somarValorPorTipo(TipoConserto.CONSERTO_PARA_LOJISTAS)
                .multiply(TipoConserto.CONSERTO_PARA_LOJISTAS.getPercentualComissao());

        BigDecimal totalComissao = comissaoGerais.add(comissaoPlaca).add(comissaoLojistas);
        BigDecimal salarioFixo = salarioConfigService.salarioAtual();
        BigDecimal salarioTotal = salarioFixo.add(totalComissao);

        return new RelatorioData(
                todos,
                valorTotal,
                comissaoGerais,
                comissaoPlaca,
                comissaoLojistas,
                totalComissao,
                salarioFixo,
                salarioTotal,
                todos.size()
        );
    }

    public record RelatorioData(
            List<Conserto> consertos,
            BigDecimal valorTotalConsertos,
            BigDecimal comissaoGerais,
            BigDecimal comissaoPlaca,
            BigDecimal comissaoLojistas,
            BigDecimal totalComissao,
            BigDecimal salarioFixo,
            BigDecimal salarioTotal,
            int quantidadeConsertos
    ) {
    }
}
