package com.controljob.repository;

import com.controljob.model.Conserto;
import com.controljob.model.SituacaoConserto;
import com.controljob.model.TipoConserto;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

public final class ConsertoSpecifications {

    private ConsertoSpecifications() {
    }

    public static Specification<Conserto> noPeriodo(LocalDate de, LocalDate ate) {
        return (root, query, cb) -> {
            if (de == null && ate == null) {
                return null;
            }
            List<Predicate> predicates = new ArrayList<>();
            if (de != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("dataConserto"), de));
            }
            if (ate != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("dataConserto"), ate));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    public static Specification<Conserto> dosTipos(Collection<TipoConserto> tipos) {
        return (root, query, cb) -> {
            if (tipos == null || tipos.isEmpty()) {
                return null;
            }
            return root.get("tipoConserto").in(tipos);
        };
    }

    public static Specification<Conserto> doModelo(String modelo) {
        return (root, query, cb) -> {
            if (modelo == null || modelo.isBlank()) {
                return null;
            }
            return cb.equal(root.get("modeloAparelho"), modelo);
        };
    }

    public static Specification<Conserto> comTexto(String termo) {
        return (root, query, cb) -> {
            if (termo == null || termo.isBlank()) {
                return null;
            }
            String padrao = "%" + escapar(termo.trim().toLowerCase(Locale.ROOT)) + "%";
            List<Predicate> campos = List.of("numeroOs", "imei", "descricao", "modeloAparelho").stream()
                    .map(campo -> likeSegue(root, cb, campo, padrao))
                    .toList();
            return cb.or(campos.toArray(new Predicate[0]));
        };
    }

    public static Specification<Conserto> naFaixaValor(BigDecimal minimo, BigDecimal maximo) {
        return (root, query, cb) -> {
            if (minimo == null && maximo == null) {
                return null;
            }
            List<Predicate> predicates = new ArrayList<>();
            if (minimo != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("valor"), minimo));
            }
            if (maximo != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("valor"), maximo));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    public static Specification<Conserto> naSituacao(SituacaoConserto situacao) {
        return (root, query, cb) -> {
            if (situacao == null) {
                return null;
            }
            return switch (situacao) {
                case PENDENTE -> cb.isNull(root.get("dataFechamento"));
                case FECHADO -> cb.isNotNull(root.get("dataFechamento"));
                case TODOS -> null;
            };
        };
    }

    public static Specification<Conserto> semImei() {
        return (root, query, cb) -> cb.or(
                cb.isNull(root.get("imei")),
                cb.equal(root.get("imei"), "")
        );
    }

    private static Predicate likeSegue(jakarta.persistence.criteria.Root<Conserto> root,
                                       jakarta.persistence.criteria.CriteriaBuilder cb,
                                       String atributo, String padrao) {
        Expression<String> campo = root.get(atributo);
        return cb.like(cb.lower(cb.coalesce(campo, "")), padrao, '\\');
    }

    private static String escapar(String termo) {
        return termo.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
