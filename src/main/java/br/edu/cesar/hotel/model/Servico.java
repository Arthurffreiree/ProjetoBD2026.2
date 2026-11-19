package br.edu.cesar.hotel.model;

import java.math.BigDecimal;

/** Representa uma linha da tabela SERVICO. */
public class Servico {
    private int idServico;
    private String nome;
    private String descricao;
    private BigDecimal valorAtual;
    private boolean ativo;

    public Servico() {
    }

    public Servico(int idServico, String nome, String descricao, BigDecimal valorAtual, boolean ativo) {
        this.idServico = idServico;
        this.nome = nome;
        this.descricao = descricao;
        this.valorAtual = valorAtual;
        this.ativo = ativo;
    }

    public int getIdServico() { return idServico; }
    public void setIdServico(int idServico) { this.idServico = idServico; }

    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }

    public String getDescricao() { return descricao; }
    public void setDescricao(String descricao) { this.descricao = descricao; }

    public BigDecimal getValorAtual() { return valorAtual; }
    public void setValorAtual(BigDecimal valorAtual) { this.valorAtual = valorAtual; }

    public boolean isAtivo() { return ativo; }
    public void setAtivo(boolean ativo) { this.ativo = ativo; }
}
