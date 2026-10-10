package com.controljob.service;

import com.controljob.model.SalarioConfig;
import com.controljob.repository.SalarioConfigRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class SalarioConfigService {

    private final SalarioConfigRepository repository;
    private final BigDecimal valorInicial;

    public SalarioConfigService(
            SalarioConfigRepository repository,
            @Value("${controljob.salario-fixo:1500.00}") BigDecimal valorInicial
    ) {
        this.repository = repository;
        this.valorInicial = valorInicial;
    }

    @Transactional
    public BigDecimal salarioAtual() {
        return repository.findById(SalarioConfig.ID_UNICO)
                .map(SalarioConfig::getValor)
                .orElseGet(this::inicializar);
    }

    @Transactional
    public BigDecimal atualizar(BigDecimal novoValor) {
        if (novoValor == null || novoValor.signum() <= 0) {
            throw new IllegalArgumentException("O salário fixo deve ser maior que zero.");
        }
        BigDecimal valorNormalizado = novoValor.setScale(2, RoundingMode.HALF_UP);
        SalarioConfig config = repository.findById(SalarioConfig.ID_UNICO)
                .orElseGet(SalarioConfig::new);
        config.setValor(valorNormalizado);
        repository.save(config);
        return valorNormalizado;
    }

    private BigDecimal inicializar() {
        SalarioConfig config = new SalarioConfig();
        config.setValor(valorInicial);
        repository.save(config);
        return valorInicial;
    }
}
