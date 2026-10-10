package net.elrid.hardmode.zone;

public enum SafeZonePhase {
    BEGIN,
    MARCH,
    REST_SHORT,
    EXPAND,
    REST_LONG,
    EYE_CHASE,
    EYE_HOLD_FAIL,
    EYE_HOLD_SUCCESS;

    public boolean isEyeMode() {
        return this == EYE_CHASE || this == EYE_HOLD_FAIL || this == EYE_HOLD_SUCCESS;
    }

    public boolean isMoving() {
        return this == MARCH || this == EYE_CHASE;
    }
}