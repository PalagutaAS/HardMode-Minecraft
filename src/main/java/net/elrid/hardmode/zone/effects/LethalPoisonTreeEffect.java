package net.elrid.hardmode.zone.effects;

public class LethalPoisonTreeEffect extends AbstractLethalPoisonEffect {
    @Override
    public int damageInterval() {
        return 20;
    }

    @Override
    public int totalDuration() {
        return 600;
    }

    @Override
    protected float damage() {
        return 0.65F;
    }
}
