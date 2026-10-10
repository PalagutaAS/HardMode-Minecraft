package net.elrid.hardmode.zone;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.elrid.hardmode.HardMode;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public class SafeZoneSavedData extends SavedData {

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
                    Codec.DOUBLE.optionalFieldOf("PrevX", 0.0).forGetter(d -> d.prevX),
                    Codec.DOUBLE.optionalFieldOf("PrevY", 0.0).forGetter(d -> d.prevY),
                    Codec.DOUBLE.optionalFieldOf("PrevZ", 0.0).forGetter(d -> d.prevZ),
                    Codec.INT.optionalFieldOf("CompletedCycles", 0)
                            .forGetter(d -> d.completedCycles),
                    Codec.BOOL.optionalFieldOf("EyeWillSucceed", false)
                            .forGetter(d -> d.eyeWillSucceed),
                    // Ключ оставлен старым — совместимость с существующими сейвами
                    Codec.DOUBLE.optionalFieldOf("EyeStartRadius", 0.0)
                            .forGetter(d -> d.phaseStartRadius),
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
    private SafeZonePhase phase = SafeZonePhase.BEGIN;
    private long    phaseStartTick = Long.MIN_VALUE;
    private double  startX, startY, startZ;
    private double  targetX, targetY, targetZ;
    private int     completedCycles = 0;
    private boolean eyeWillSucceed  = false;
    private double  phaseStartRadius = 0.0;
    private long    worldSeed = 0L;
    private long    savedGameTime = 0L;
    private double  prevX, prevY, prevZ;
    private boolean hasPrevious = false;

    public SafeZoneSavedData() {}

    private SafeZoneSavedData(boolean hasData, int phaseOrdinal, long phaseStartTick,
                              double startX, double startY, double startZ,
                              double targetX, double targetY, double targetZ,
                              double prevX, double prevY, double prevZ, boolean hasPrevious,
                              int completedCycles, boolean eyeWillSucceed, double phaseStartRadius,
                              long worldSeed, long savedGameTime) {
        this.hasData        = hasData;
        this.phase          = SafeZonePhase.values()[phaseOrdinal];
        this.phaseStartTick = phaseStartTick;
        this.startX  = startX;  this.startY  = startY;  this.startZ  = startZ;
        this.targetX = targetX; this.targetY = targetY; this.targetZ = targetZ;
        this.prevX = prevX; this.prevY = prevY; this.prevZ = prevZ;
        this.hasPrevious = hasPrevious;
        this.completedCycles = completedCycles;
        this.eyeWillSucceed  = eyeWillSucceed;
        this.phaseStartRadius = phaseStartRadius;
        this.worldSeed       = worldSeed;
        this.savedGameTime   = savedGameTime;
    }

    public static SafeZoneSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    // ====== Геттеры ======

    public boolean       hasData()          { return hasData; }
    public SafeZonePhase getPhase()         { return phase; }
    public long          getPhaseStartTick(){ return phaseStartTick; }
    public Vec3          getPhaseStartPos() { return new Vec3(startX, startY, startZ); }
    public Vec3          getPhaseTargetPos(){ return new Vec3(targetX, targetY, targetZ); }
    public int           getCompletedCycles(){ return completedCycles; }
    public boolean       isEyeWillSucceed()  { return eyeWillSucceed; }
    public double        getPhaseStartRadius(){ return phaseStartRadius; }
    public long          getWorldSeed()     { return worldSeed; }
    public long          getSavedGameTime() { return savedGameTime; }
    public boolean       hasPrevious()    { return hasPrevious; }
    public Vec3          getPreviousPos() { return new Vec3(prevX, prevY, prevZ); }

    // ====== Сеттер ======

    public void set(SafeZonePhase phase, long phaseStartTick,
                    Vec3 start, Vec3 target,
                    @Nullable Vec3 previous,
                    boolean eyeWillSucceed, double phaseStartRadius,
                    int completedCycles,
                    long worldSeed, long savedGameTime) {
        this.hasData         = true;
        this.phase           = phase;
        this.phaseStartTick  = phaseStartTick;
        this.startX  = start.x;  this.startY  = start.y;  this.startZ  = start.z;
        this.targetX = target.x; this.targetY = target.y; this.targetZ = target.z;
        this.eyeWillSucceed  = eyeWillSucceed;
        this.phaseStartRadius = phaseStartRadius;
        this.completedCycles = completedCycles;
        this.worldSeed       = worldSeed;
        this.savedGameTime   = savedGameTime;
        if (previous != null) {
            this.hasPrevious = true;
            this.prevX = previous.x; this.prevY = previous.y; this.prevZ = previous.z;
        } else {
            this.hasPrevious = false;
            this.prevX = this.prevY = this.prevZ = 0.0;
        }
        setDirty();
    }

    public void reset() {
        this.hasData         = false;
        this.phase           = SafeZonePhase.BEGIN;
        this.phaseStartTick  = Long.MIN_VALUE;
        this.startX = this.startY = this.startZ = 0.0;
        this.targetX = this.targetY = this.targetZ = 0.0;
        this.prevX = this.prevY = this.prevZ = 0.0;
        this.completedCycles = 0;
        this.eyeWillSucceed  = false;
        this.phaseStartRadius = 0.0;
        this.worldSeed       = 0L;
        this.savedGameTime   = 0L;
        this.hasPrevious = false;
        setDirty();
    }
}