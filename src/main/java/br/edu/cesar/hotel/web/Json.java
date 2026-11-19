package br.edu.cesar.hotel.web;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Map;

/**
 * Conversor simples para JSON (sem bibliotecas externas).
 * Suporta mapas, listas, textos, números, booleanos, datas e null.
 */
public final class Json {

    private Json() {
    }

    public static String de(Object valor) {
        StringBuilder saida = new StringBuilder();
        escrever(saida, valor);
        return saida.toString();
    }

    private static void escrever(StringBuilder saida, Object valor) {
        if (valor == null) {
            saida.append("null");
        } else if (valor instanceof BigDecimal decimal) {
            saida.append(decimal.toPlainString());
        } else if (valor instanceof Number || valor instanceof Boolean) {
            saida.append(valor);
        } else if (valor instanceof Map<?, ?> mapa) {
            saida.append('{');
            boolean primeiro = true;
            for (Map.Entry<?, ?> entrada : mapa.entrySet()) {
                if (!primeiro) {
                    saida.append(',');
                }
                primeiro = false;
                texto(saida, String.valueOf(entrada.getKey()));
                saida.append(':');
                escrever(saida, entrada.getValue());
            }
            saida.append('}');
        } else if (valor instanceof Collection<?> lista) {
            saida.append('[');
            boolean primeiro = true;
            for (Object item : lista) {
                if (!primeiro) {
                    saida.append(',');
                }
                primeiro = false;
                escrever(saida, item);
            }
            saida.append(']');
        } else {
            // Datas (java.sql.Date, LocalDate...) e demais objetos viram texto.
            texto(saida, valor.toString());
        }
    }

    private static void texto(StringBuilder saida, String valor) {
        saida.append('"');
        for (int indice = 0; indice < valor.length(); indice++) {
            char caractere = valor.charAt(indice);
            switch (caractere) {
                case '"' -> saida.append("\\\"");
                case '\\' -> saida.append("\\\\");
                case '\n' -> saida.append("\\n");
                case '\r' -> saida.append("\\r");
                case '\t' -> saida.append("\\t");
                default -> {
                    if (caractere < 0x20) {
                        saida.append(String.format("\\u%04x", (int) caractere));
                    } else {
                        saida.append(caractere);
                    }
                }
            }
        }
        saida.append('"');
    }
}
