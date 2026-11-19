package br.edu.cesar.hotel.model;

import java.math.BigDecimal;

/**
 * Representa uma linha da tabela QUARTO.
 * Os campos nomeTipo, capacidade e valorDiaria vêm do JOIN com TIPO_QUARTO
 * e servem apenas para exibição na listagem.
 */
public class Quarto {
    private String numero;
    private int andar;
    private String status;
    private int idTipo;
    private String nomeTipo;
    private int capacidade;
    private BigDecimal valorDiaria;

    public Quarto() {
    }

    public Quarto(String numero, int andar, String status, int idTipo) {
        this.numero = numero;
        this.andar = andar;
        this.status = status;
        this.idTipo = idTipo;
    }

    public String getNumero() { return numero; }
    public void setNumero(String numero) { this.numero = numero; }

    public int getAndar() { return andar; }
    public void setAndar(int andar) { this.andar = andar; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public int getIdTipo() { return idTipo; }
    public void setIdTipo(int idTipo) { this.idTipo = idTipo; }

    public String getNomeTipo() { return nomeTipo; }
    public void setNomeTipo(String nomeTipo) { this.nomeTipo = nomeTipo; }

    public int getCapacidade() { return capacidade; }
    public void setCapacidade(int capacidade) { this.capacidade = capacidade; }

    public BigDecimal getValorDiaria() { return valorDiaria; }
    public void setValorDiaria(BigDecimal valorDiaria) { this.valorDiaria = valorDiaria; }
}
