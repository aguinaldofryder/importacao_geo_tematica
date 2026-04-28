package br.com.arxcode.tematica.geo.catalogo;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * Invariante AC7 — repositórios do catálogo são <strong>read-only</strong>.
 * Falha se algum método público (declarado) bater com a regex de operações de escrita.
 */
class RepositoriosReadOnlyTest {

    private static final Pattern ESCRITA = Pattern.compile(
        "(?i)^(save|insert|update|delete|merge|upsert|persist|store|write).*");

    @Test
    void repositoriosNaoDevemExporMetodosDeEscrita() {
        List<Class<?>> repositorios = List.of(
            CampoRepository.class,
            AlternativaRepository.class,
            GrupoCampoRepository.class
        );

        for (Class<?> repo : repositorios) {
            for (Method m : repo.getDeclaredMethods()) {
                if (!Modifier.isPublic(m.getModifiers())) {
                    continue;
                }
                if (m.isSynthetic() || m.isBridge()) {
                    continue;
                }
                if (ESCRITA.matcher(m.getName()).matches()) {
                    fail("Método de escrita detectado em "
                        + repo.getSimpleName() + ": " + m.getName());
                }
            }
        }
    }
}
