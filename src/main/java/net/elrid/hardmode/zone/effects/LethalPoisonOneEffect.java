package net.elrid.hardmode.zone.effects;

public class LethalPoisonOneEffect extends AbstractLethalPoisonEffect {
    @Override public int damageInterval() { return 40; }
    @Override public int totalDuration() { return 400; }
    @Override protected float damage() {
        return 0.35F;
    }
    @Override
    protected boolean canKill() {
        return false;
    }
}