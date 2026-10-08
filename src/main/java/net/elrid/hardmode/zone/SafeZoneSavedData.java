package net.elrid.hardmode.zone;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.elrid.hardmode.HardMode;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.phys.Vec3;

public class SafeZoneSavedData extends SavedData {

    /** Codec — декларативное описание сериализации. */
    private static final Codec<SafeZoneSavedData> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.BOOL.optionalFieldOf("HasData", false)
                            .forGetter(SafeZoneSavedData::hasData),
                    Codec.INT.optionalFieldOf("Phase", 0)
                            .forGetter(d -> d.phase.ordinal()),
                    Codec.LONG.optionalFieldOf("PhaseStartTick", Long.MIN_VALUE)
                            .forGetter(d -> d.phaseStartTick),
                    Codec.DOUBLE.optionalFieldOf("StartX", 0.0).forGetter(d -> d.startX),
                    Codec.DOUBLE.optionalFieldOf("StartY", 0.0).forGetter(d -> d.startY),
                    Codec.DOUBLE.optionalFieldOf("StartZ", 0.0).forGetter(d -> d.startZ),
                    Codec.DOUBLE.optionalFieldOf("TargetX", 0.0).forGetter(d -> d.targetX),
                    Codec.DOUBLE.optionalFieldOf("TargetY", 0.0).forGetter(d -> d.targetY),
                    Codec.DOUBLE.optionalFieldOf("TargetZ", 0.0).forGetter(d -> d.targetZ),
                    // ★ Отпечаток мира — сид + время сохранения
                    Codec.LONG.optionalFieldOf("WorldSeed", 0L)
                            .forGetter(d -> d.worldSeed),
                    Codec.LONG.optionalFieldOf("SavedGameTime", 0L)
                            .forGetter(d -> d.savedGameTime)
            ).apply(instance, SafeZoneSavedData::new)
    );

    public static final SavedDataType<SafeZoneSavedData> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(HardMode.MOD_ID, "safe_zone"),
            SafeZoneSavedData::new,
            CODEC,
            null
    );

    private boolean hasData = false;
    private SafeZoneState.Phase phase = SafeZoneState.Phase.STATIC;
    private long    phaseStartTick = Long.MIN_VALUE;
    private double  startX, startY, startZ;
    private double  targetX, targetY, targetZ;
    private long    worldSeed = 0L;
    private long    savedGameTime = 0L;

    public SafeZoneSavedData() {}

    private SafeZoneSavedData(boolean hasData, int phaseOrdinal, long phaseStartTick,
                              double startX, double startY, double startZ,
                              double targetX, double targetY, double targetZ,
                              long worldSeed, long savedGameTime) {
        this.hasData        = hasData;
        this.phase          = SafeZoneState.Phase.values()[phaseOrdinal];
        this.phaseStartTick = phaseStartTick;
        this.startX  = startX;  this.startY  = startY;  this.startZ  = startZ;
        this.targetX = targetX; this.targetY = targetY; this.targetZ = targetZ;
    }

    public static SafeZoneSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    // ====== Геттеры ======

    public boolean             hasData()          { return hasData; }
    public SafeZoneState.Phase getPhase()         { return phase; }
    public long                getPhaseStartTick() { return phaseStartTick; }
    public Vec3                getPhaseStartPos() { return new Vec3(startX, startY, startZ); }
    public Vec3                getPhaseTargetPos(){ return new Vec3(targetX, targetY, targetZ); }
    public long                getWorldSeed()       { return worldSeed; }
    public long                getSavedGameTime()   { return savedGameTime; }

    // ====== Сеттер ======

    public void set(SafeZoneState.Phase phase, long phaseStartTick,
                    Vec3 start, Vec3 target, long worldSeed, long  savedGameTime) {
        this.hasData        = true;
        this.phase          = phase;
        this.phaseStartTick = phaseStartTick;
        this.startX  = start.x;  this.startY  = start.y;  this.startZ  = start.z;
        this.targetX = target.x; this.targetY = target.y; this.targetZ = target.z;
        this.worldSeed     = worldSeed;
        this.savedGameTime = savedGameTime;
        setDirty();
    }

    public void reset() {
        this.hasData        = false;
        this.phase          = SafeZoneState.Phase.STATIC;
        this.phaseStartTick = Long.MIN_VALUE;
        this.startX = this.startY = this.startZ = 0.0;
        this.targetX = this.targetY = this.targetZ = 0.0;
        this.worldSeed     = 0L;
        this.savedGameTime = 0L;
        setDirty();
    }
}