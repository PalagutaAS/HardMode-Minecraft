package net.elrid.hardmode.zone.effects;

public class LethalPoisonTwoEffect extends AbstractLethalPoisonEffect {
    @Override
    public int damageInterval() {
        return 30;
    }

    @Override
    public int totalDuration() {
        return 400;
    }

    @Override
    protected float damage() {
        return 0.5F;
    }
}
