package br.edu.cesar.hotel.model;

import java.time.LocalDate;

/** Representa uma linha da tabela RESERVA. */
public class Reserva {
    private int idReserva;
    private String cpfHospede;
    private String numeroQuarto;
    private LocalDate entradaPrevista;
    private LocalDate saidaPrevista;
    private int qtdHospedes;
    private String status;
    private String observacao;

    // Dados de outras tabelas, preenchidos na listagem (JOIN)
    private String nomeHospede;
    private String nomeTipo;

    public Reserva() {
    }

    public Reserva(int idReserva, String cpfHospede, String numeroQuarto, LocalDate entradaPrevista,
                   LocalDate saidaPrevista, int qtdHospedes, String status, String observacao) {
        this.idReserva = idReserva;
        this.cpfHospede = cpfHospede;
        this.numeroQuarto = numeroQuarto;
        this.entradaPrevista = entradaPrevista;
        this.saidaPrevista = saidaPrevista;
        this.qtdHospedes = qtdHospedes;
        this.status = status;
        this.observacao = observacao;
    }

    public int getIdReserva() { return idReserva; }
    public void setIdReserva(int idReserva) { this.idReserva = idReserva; }

    public String getCpfHospede() { return cpfHospede; }
    public void setCpfHospede(String cpfHospede) { this.cpfHospede = cpfHospede; }

    public String getNumeroQuarto() { return numeroQuarto; }
    public void setNumeroQuarto(String numeroQuarto) { this.numeroQuarto = numeroQuarto; }

    public LocalDate getEntradaPrevista() { return entradaPrevista; }
    public void setEntradaPrevista(LocalDate entradaPrevista) { this.entradaPrevista = entradaPrevista; }

    public LocalDate getSaidaPrevista() { return saidaPrevista; }
    public void setSaidaPrevista(LocalDate saidaPrevista) { this.saidaPrevista = saidaPrevista; }

    public int getQtdHospedes() { return qtdHospedes; }
    public void setQtdHospedes(int qtdHospedes) { this.qtdHospedes = qtdHospedes; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getObservacao() { return observacao; }
    public void setObservacao(String observacao) { this.observacao = observacao; }

    public String getNomeHospede() { return nomeHospede; }
    public void setNomeHospede(String nomeHospede) { this.nomeHospede = nomeHospede; }

    public String getNomeTipo() { return nomeTipo; }
    public void setNomeTipo(String nomeTipo) { this.nomeTipo = nomeTipo; }
}
