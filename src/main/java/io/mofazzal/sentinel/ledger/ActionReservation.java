package io.mofazzal.sentinel.ledger;

import java.util.UUID;

public record ActionReservation(boolean acquired, UUID claimId) {
}
