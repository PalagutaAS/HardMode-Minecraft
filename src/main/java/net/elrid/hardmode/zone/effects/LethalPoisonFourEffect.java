package net.elrid.hardmode.zone.effects;

public class LethalPoisonFourEffect extends AbstractLethalPoisonEffect {
    @Override public int damageInterval() { return 20; } // 0.5 сек
    @Override protected float damage() {
        return 1.25F;
    }

    @Override protected boolean mercyEnabled() {
        return false;
    }
}
