package com.controljob.repository;

import com.controljob.model.Conserto;
import com.controljob.model.TipoConserto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface ConsertoRepository extends JpaRepository<Conserto, Long>, JpaSpecificationExecutor<Conserto> {

    List<Conserto> findByTipoConsertoAndDataFechamentoIsNullOrderByDataConsertoDesc(TipoConserto tipoConserto);

    List<Conserto> findAllByDataFechamentoIsNullOrderByDataConsertoDesc();

    List<Conserto> findAllByDataFechamentoIsNullAndDataRetiradaIsNotNullOrderByIdAsc();

    @Query("SELECT COALESCE(SUM(c.valor), 0) FROM Conserto c WHERE c.dataFechamento IS NULL AND c.dataRetirada IS NOT NULL")
    BigDecimal somarValorTotal();

    @Query("SELECT COALESCE(SUM(c.valor), 0) FROM Conserto c WHERE c.tipoConserto = :tipo AND c.dataFechamento IS NULL AND c.dataRetirada IS NOT NULL")
    BigDecimal somarValorPorTipo(TipoConserto tipo);

    List<Conserto> findByDataFechamentoIsNullAndDataRetiradaIsNotNullAndDataRetiradaLessThanEqual(LocalDate dataLimite);

    List<Conserto> findByImeiIgnoreCaseOrderByDataConsertoDesc(String imei);

    @Modifying
    @Query("UPDATE Conserto c SET c.dataFechamento = :fechamento WHERE c.id IN :ids")
    int marcarFechados(@Param("ids") List<Long> ids, @Param("fechamento") LocalDate fechamento);

    @Modifying
    @Query("UPDATE Conserto c SET c.dataRetirada = c.dataConserto WHERE c.dataRetirada IS NULL")
    int preencherRetiradaHistorica();

    @Query("SELECT DISTINCT c.modeloAparelho FROM Conserto c WHERE c.modeloAparelho IS NOT NULL AND c.modeloAparelho <> '' ORDER BY c.modeloAparelho")
    List<String> listarModelos();
}