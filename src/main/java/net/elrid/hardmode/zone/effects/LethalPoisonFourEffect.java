package net.elrid.hardmode.zone.effects;

public class LethalPoisonFourEffect extends AbstractLethalPoisonEffect {
    @Override public int damageInterval() { return 15; } // 0.5 сек
    @Override public int totalDuration() { return 100; }
    @Override protected float damage() {
        return 1.25F;
    }
}
