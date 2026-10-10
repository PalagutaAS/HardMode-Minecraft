package net.elrid.hardmode.zone.effects;

public class LethalPoisonTreeEffect extends AbstractLethalPoisonEffect {
    @Override
    public int damageInterval() {
        return 30;
    }
    @Override protected boolean mercyEnabled(){
        return false;
    }
    @Override
    protected float damage() {
        return 0.75F;
    }
}
