package com.controljob.config;

import com.controljob.repository.ConsertoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Component
public class RetiradaHistoricaRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(RetiradaHistoricaRunner.class);

    private static final LocalDate CORTE_FLUXO_ANTIGO = LocalDate.of(2026, 10, 12);

    private final ConsertoRepository repository;

    public RetiradaHistoricaRunner(ConsertoRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments arguments) {
        int atualizados = repository.preencherRetiradaHistorica(CORTE_FLUXO_ANTIGO);
        if (atualizados > 0) {
            log.info("Registros do fluxo antigo ajustados: {} conserto(s) marcados como retirados na data do conserto.",
                    atualizados);
        }
    }
}
