package edu.unisabana.tyvs.dedup;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Carga el conjunto de evaluacion etiquetado.
 *
 * En un sistema tradicional, el oraculo esta dentro de la prueba: el
 * programador sabe que 2 + 2 son 4 y lo escribe. En un sistema de IA no
 * existe esa funcion, y el oraculo pasa a ser un CONJUNTO DE DATOS que
 * alguien etiqueto a mano.
 *
 * Ese cambio tiene tres consecuencias practicas que conviene tener claras:
 *
 * 1. El conjunto de evaluacion es codigo. Se versiona, se revisa y se
 *    discute en pull request, porque cambiarlo cambia la definicion de
 *    "correcto".
 *
 * 2. La calidad del sistema no puede superar la calidad de las etiquetas. Si
 *    quien etiqueto se equivoco, el sistema aprende (o se mide contra) el
 *    error.
 *
 * 3. El conjunto tiene que ser REPRESENTATIVO. Si solo contiene nombres
 *    cortos y sin tildes, las metricas diran que el sistema es excelente y
 *    la gente con apellidos compuestos lo sufrira en produccion. Por eso este
 *    conjunto lleva una columna de grupo.
 */
public final class ConjuntoDeEvaluacion {

    private static final String RECURSO = "/pares-de-evaluacion.csv";

    /** Un caso etiquetado: dos nombres y la respuesta correcta segun un humano. */
    public static final class Par {
        public final String nombreA;
        public final String nombreB;
        public final boolean mismaPersona;
        public final String grupo;

        Par(String nombreA, String nombreB, boolean mismaPersona, String grupo) {
            this.nombreA = nombreA;
            this.nombreB = nombreB;
            this.mismaPersona = mismaPersona;
            this.grupo = grupo;
        }

        @Override
        public String toString() {
            return "[" + grupo + "] \"" + nombreA + "\" vs \"" + nombreB
                    + "\" (esperado: " + (mismaPersona ? "misma" : "distinta") + ")";
        }
    }

    private ConjuntoDeEvaluacion() {
    }

    public static List<Par> cargar() {
        List<Par> pares = new ArrayList<>();

        try (InputStream in = ConjuntoDeEvaluacion.class.getResourceAsStream(RECURSO)) {
            if (in == null) {
                throw new IllegalStateException("No se encontro " + RECURSO + " en el classpath");
            }

            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(in, StandardCharsets.UTF_8))) {

                String linea;
                boolean cabeceraVista = false;

                while ((linea = r.readLine()) != null) {
                    linea = linea.trim();
                    if (linea.isEmpty() || linea.startsWith("#")) {
                        continue;
                    }
                    if (!cabeceraVista) {
                        cabeceraVista = true;   // la primera linea util es la cabecera
                        continue;
                    }

                    String[] c = linea.split(",", -1);
                    if (c.length != 4) {
                        throw new IllegalStateException("Linea mal formada en el CSV: " + linea);
                    }
                    pares.add(new Par(c[0].trim(), c[1].trim(),
                            Boolean.parseBoolean(c[2].trim()), c[3].trim()));
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        if (pares.isEmpty()) {
            throw new IllegalStateException("El conjunto de evaluacion esta vacio");
        }
        return pares;
    }

    /** Solo los pares de un grupo, para medir por subpoblacion. */
    public static List<Par> deGrupo(String grupo) {
        return cargar().stream()
                .filter(p -> p.grupo.equals(grupo))
                .collect(Collectors.toList());
    }

    public static List<String> grupos() {
        return cargar().stream()
                .map(p -> p.grupo)
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }
}
