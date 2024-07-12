package org.rohit;

import jakarta.persistence.ManyToOne;

public class WalletAudit {

    private Integer id;

    @ManyToOne
    private Wallet wallet;

    private Long balanceBefore;

    private Long balanceAfter;

    private String txtId;
}
