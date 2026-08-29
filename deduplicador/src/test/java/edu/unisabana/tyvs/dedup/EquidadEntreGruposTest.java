package edu.unisabana.tyvs.dedup;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MODULO 3 — EQUIDAD: EL SISTEMA FUNCIONA IGUAL PARA TODO EL MUNDO?
 *
 * Este es el modulo que justifica el taller entero.
 *
 * Una metrica global es un promedio, y un promedio puede esconder que a un
 * grupo entero de personas le va sistematicamente peor. Un sistema con 0.833
 * de F1 global suena aceptable. Ese mismo sistema puede ser completamente
 * inutil para una de cada cuatro personas, y el numero no lo insinua.
 *
 * En este proyecto eso no es una hipotesis: es lo que pasa. Se mide abajo.
 *
 * La tecnica se llama analisis desagregado o slice-based evaluation, y es la
 * misma que se aplica a un modelo de credito (funciona igual por region?), a
 * un reconocedor de voz (por acento?) o a un LLM (por idioma?).
 *
 * NOTA IMPORTANTE SOBRE LOS GRUPOS. Aqui se agrupa por la FORMA del nombre,
 * no por atributos protegidos como origen etnico. Es la aproximacion honesta
 * con los datos disponibles, y aun asi es relevante: la forma del nombre
 * correlaciona con el origen. Un sistema que falla con apellidos compuestos
 * falla mas con personas de ciertas comunidades, aunque nadie haya escrito
 * nunca la palabra "origen" en el codigo. Asi es como un sistema discrimina
 * sin que nadie lo programara para discriminar.
 */
@DisplayName("Modulo 3 - Equidad entre grupos")
class EquidadEntreGruposTest {

    private final Deduplicador sistema = new Deduplicador();

    private Map<String, Metricas> porGrupo() {
        Map<String, Metricas> resultado = new LinkedHashMap<>();
        for (String grupo : ConjuntoDeEvaluacion.grupos()) {
            resultado.put(grupo,
                    Metricas.evaluar(sistema, ConjuntoDeEvaluacion.deGrupo(grupo)));
        }
        return resultado;
    }

    @Test
    @DisplayName("01 - Radiografia: metricas desagregadas por grupo")
    void radiografiaPorGrupo() {
        Metricas global = Metricas.evaluar(sistema, ConjuntoDeEvaluacion.cargar());

        System.out.println();
        System.out.println("  GLOBAL      " + global);
        System.out.println("  ---------------------------------------------------------------");
        porGrupo().forEach((g, m) -> System.out.printf("  %-11s %s%n", g, m));
        System.out.println();

        // El global parece razonable...
        assertTrue(global.f1() > 0.80, "El F1 global es " + global.f1());

        // ...y sin embargo lea la tabla de arriba antes de seguir.
    }

    @Test
    @DisplayName("02 - HALLAZGO: el sistema es CIEGO a los nombres abreviados")
    void elSistemaEsCiegoALosNombresAbreviados() {
        Metricas abreviados = porGrupo().get("abreviado");

        // Recall 0.000. No es "peor con este grupo": es que no encuentra NI UNO
        // de los diez duplicados reales que hay.
        //
        // Quien se inscribe como "Ana M Torres" y luego como "Ana Maria Torres"
        // NUNCA sera detectada. El sistema no la protege en absoluto, y
        // ninguna metrica global lo dice.
        assertEquals(0.0, abreviados.recall(), 0.001,
                "El recall del grupo abreviado cambio. Detalle: " + abreviados);

        assertEquals(10, abreviados.falsosNegativos,
                "Los 10 duplicados reales del grupo se escapan sin excepcion");

        // La causa es concreta y no tiene nada de misteriosa: abreviar
        // "Antonio" a "A" borra seis caracteres, y Jaro-Winkler penaliza la
        // diferencia de longitud. Las puntuaciones caen entre 0.808 y 0.925,
        // todas por debajo del umbral de 0.95.
        //
        // No es un bug del algoritmo: hace exactamente lo que se le pidio.
        // Es que a nadie se le ocurrio comprobar si lo que se le pidio
        // funcionaba igual para todos.
    }

    @Test
    @DisplayName("03 - La brecha entre el mejor y el peor grupo es enorme")
    void laBrechaEntreGruposEsEnorme() {
        Map<String, Metricas> grupos = porGrupo();

        double mejorF1 = grupos.values().stream().mapToDouble(Metricas::f1).max().orElseThrow();
        double peorF1 = grupos.values().stream().mapToDouble(Metricas::f1).min().orElseThrow();

        System.out.printf("  Mejor grupo: F1=%.3f   Peor grupo: F1=%.3f   Brecha=%.3f%n",
                mejorF1, peorF1, mejorF1 - peorF1);

        // Medido: dos grupos con F1 = 1.000 y uno con F1 = 0.000.
        //
        // Esta asercion esta escrita AL REVES a proposito: documenta que la
        // brecha existe hoy. No es una prueba que celebre el defecto, es una
        // que impide que se olvide. El dia que alguien arregle el sistema,
        // esta prueba fallara, y el mensaje le dira que la arregle tambien.
        assertTrue(mejorF1 - peorF1 > 0.5,
                "La brecha entre grupos se cerro (ahora es " + (mejorF1 - peorF1)
                        + "). Buena noticia: arregle esta prueba y actualice "
                        + "el javadoc de Deduplicador.");
    }

    @Test
    @DisplayName("04 - Cuidado: no toda diferencia medida es culpa del sistema")
    void noTodaDiferenciaEsCulpaDelSistema() {
        // Este es el error mas comun al evaluar equidad, y hay que verlo una
        // vez para no cometerlo.
        //
        // Con umbral 0.90 el grupo "tilde" salia con precision 0.588, la peor
        // de los cuatro, y era tentador concluir "el sistema discrimina a las
        // personas con tildes en el nombre".
        //
        // Era FALSO. Con el umbral actual ese mismo grupo saca F1 = 1.000.
        //
        // Lo que pasaba es que los pares NEGATIVOS de ese grupo (los que son
        // personas distintas) se escribieron compartiendo el nombre de pila y
        // las primeras letras del apellido: Rodriguez/Rodrigo, Munoz/Mendoza,
        // Pena/Parra. Eran mas dificiles que los de los otros grupos. La
        // diferencia venia del CONJUNTO DE EVALUACION, no del sistema.
        //
        // Antes de escribir "el sistema discrimina", hay que descartar que la
        // diferencia venga de como se construyo el conjunto. Casi siempre
        // viene de ahi la primera vez.
        Metricas tilde = porGrupo().get("tilde");

        assertEquals(1.0, tilde.f1(), 0.001,
                "El grupo con tildes ya no es perfecto: " + tilde);
    }

    @Test
    @DisplayName("05 - Paridad de recall: ningun grupo deberia quedar sin proteccion")
    void paridadDeRecall() {
        Map<String, Metricas> grupos = porGrupo();

        // El criterio de equidad que se adopta: ningun grupo por debajo del
        // 60% del mejor. Es un criterio ENTRE VARIOS POSIBLES (paridad
        // demografica, igualdad de oportunidad, calibracion) y son
        // matematicamente incompatibles entre si: no se pueden cumplir todos
        // a la vez. Elegir cual se persigue es, otra vez, una decision de
        // politica.
        double mejorRecall = grupos.values().stream()
                .mapToDouble(Metricas::recall).max().orElseThrow();
        double umbralDeParidad = mejorRecall * 0.6;

        StringBuilder incumplen = new StringBuilder();
        grupos.forEach((g, m) -> {
            if (m.recall() < umbralDeParidad) {
                incumplen.append("\n    ").append(g).append(" -> recall ").append(m.recall());
            }
        });

        // ESTA PRUEBA DOCUMENTA UN INCUMPLIMIENTO CONOCIDO Y ACEPTADO.
        //
        // Se deja verde a proposito, afirmando el estado real, en vez de roja.
        // Un build permanentemente rojo se ignora en una semana, y entonces se
        // pierde tambien la informacion. La forma correcta de convivir con un
        // defecto conocido es fijarlo por escrito, no taparlo ni gritar.
        //
        // Cerrar esta brecha es uno de los entregables del taller.
        assertTrue(incumplen.length() > 0,
                "Ya no hay grupos incumpliendo la paridad. Si usted arreglo el "
                        + "sistema, invierta esta asercion y actualice el README.");

        System.out.println("  Grupos por debajo de la paridad de recall:" + incumplen);
    }
}
