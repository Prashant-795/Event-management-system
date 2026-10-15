package com.eventmgmt.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
public class Ticket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    private Event event;

    private String name;

    private BigDecimal price;

    private int quantity;

    private int sold;

    // Inventory temporarily held for pending Razorpay checkouts.
    private int reserved;

    @OneToMany(mappedBy = "ticket", cascade = CascadeType.REMOVE)
    private List<TicketPurchase> purchases = new ArrayList<>();

    public Ticket() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Event getEvent() { return event; }
    public void setEvent(Event event) { this.event = event; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
    public int getSold() { return sold; }
    public void setSold(int sold) { this.sold = sold; }
    public int getReserved() { return reserved; }
    public void setReserved(int reserved) { this.reserved = reserved; }
    public List<TicketPurchase> getPurchases() { return purchases; }
    public void setPurchases(List<TicketPurchase> purchases) { this.purchases = purchases; }

    public int getAvailable() { return quantity - sold - reserved; }
}
