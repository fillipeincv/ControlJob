package com.controljob.repository;

import com.controljob.model.Conserto;
import com.controljob.model.TipoConserto;
import com.controljob.support.TestConsertos;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class ConsertoRepositoryTest {

    @Autowired
    private ConsertoRepository repository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void migraSomenteRegistrosAnterioresAoCorte() {
        Conserto antes = semRetirada(LocalDate.of(2026, 10, 11));
        Conserto noCorte = semRetirada(LocalDate.of(2026, 10, 12));
        Conserto depois = semRetirada(LocalDate.of(2026, 10, 13));
        Conserto jaRetirado = TestConsertos.retirado(0, TipoConserto.CONSERTOS_EM_GERAL, "100.00",
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 2));
        jaRetirado.setId(null);

        repository.saveAll(java.util.List.of(antes, noCorte, depois, jaRetirado));
        repository.flush();
        entityManager.clear();

        int atualizados = repository.preencherRetiradaHistorica(LocalDate.of(2026, 10, 12));

        entityManager.clear();
        assertThat(atualizados).isEqualTo(1);
        assertThat(repository.findById(antes.getId()).orElseThrow().getDataRetirada())
                .isEqualTo(LocalDate.of(2026, 10, 11));
        assertThat(repository.findById(noCorte.getId()).orElseThrow().getDataRetirada()).isNull();
        assertThat(repository.findById(depois.getId()).orElseThrow().getDataRetirada()).isNull();
        assertThat(repository.findById(jaRetirado.getId()).orElseThrow().getDataRetirada())
                .isEqualTo(LocalDate.of(2026, 10, 2));
    }

    private Conserto semRetirada(LocalDate dataConserto) {
        Conserto conserto = TestConsertos.criar(0, TipoConserto.CONSERTOS_EM_GERAL, "100.00", dataConserto);
        conserto.setId(null);
        return conserto;
    }
}
