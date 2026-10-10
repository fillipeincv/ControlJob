package com.controljob.job;

import com.controljob.service.FechamentoMesService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class FechamentoMesJob {

    private static final Logger log = LoggerFactory.getLogger(FechamentoMesJob.class);

    private final FechamentoMesService service;

    public FechamentoMesJob(FechamentoMesService service) {
        this.service = service;
    }

    @Scheduled(cron = "0 0 22 L * ?", zone = "America/Sao_Paulo")
    public void executar() {
        try {
            FechamentoMesService.Resultado resultado = service.fecharMes();
            log.info("Fechamento de mês concluído: {} registro(s) arquivado(s). E-mail enviado: {}. {}",
                    resultado.quantidadeFechada(), resultado.emailEnviado(),
                    resultado.emailEnviado() ? "" : resultado.erroEmail());
            if (!resultado.emailEnviado()) {
                log.warn("Falha no envio do e-mail durante o fechamento do mês: {}", resultado.erroEmail());
            }
        } catch (Exception e) {
            log.error("Falha no fechamento automático do mês", e);
        }
    }
}