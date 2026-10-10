package net.elrid.hardmode.zone.effects;

public class LethalPoisonTwoEffect extends AbstractLethalPoisonEffect {
    @Override
    public int damageInterval() {
        return 35;
    }

    @Override
    protected float damage() {
        return 0.40F;
    }
}
