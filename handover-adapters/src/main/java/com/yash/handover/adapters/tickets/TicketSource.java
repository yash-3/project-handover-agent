package com.yash.handover.adapters.tickets;

import java.util.List;

/** Read-only access to a ticket tracker. */
public interface TicketSource {

    /** All tickets, open and closed. */
    List<Ticket> listTickets();
}
