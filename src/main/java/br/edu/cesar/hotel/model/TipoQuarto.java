package br.edu.cesar.hotel.model;

import java.math.BigDecimal;

/** Representa uma linha da tabela TIPO_QUARTO. */
public class TipoQuarto {
    private int idTipo;
    private String nome;
    private int capacidade;
    private BigDecimal valorDiaria;
    private String descricao;
    private int quantidadeQuartos; // calculado na listagem (COUNT dos quartos do tipo)

    public TipoQuarto() {
    }

    public TipoQuarto(int idTipo, String nome, int capacidade, BigDecimal valorDiaria, String descricao) {
        this.idTipo = idTipo;
        this.nome = nome;
        this.capacidade = capacidade;
        this.valorDiaria = valorDiaria;
        this.descricao = descricao;
    }

    public int getIdTipo() { return idTipo; }
    public void setIdTipo(int idTipo) { this.idTipo = idTipo; }

    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }

    public int getCapacidade() { return capacidade; }
    public void setCapacidade(int capacidade) { this.capacidade = capacidade; }

    public BigDecimal getValorDiaria() { return valorDiaria; }
    public void setValorDiaria(BigDecimal valorDiaria) { this.valorDiaria = valorDiaria; }

    public String getDescricao() { return descricao; }
    public void setDescricao(String descricao) { this.descricao = descricao; }

    public int getQuantidadeQuartos() { return quantidadeQuartos; }
    public void setQuantidadeQuartos(int quantidadeQuartos) { this.quantidadeQuartos = quantidadeQuartos; }
}
