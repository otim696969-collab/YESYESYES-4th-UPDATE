package com.selfmade;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.UUID;

/**
 * A player's Eidolon: the shape their behaviour has taken. It hovers near its owner, and what it looks like is
 * decided entirely by the owner's eight trait scores, which the server copies into synced data a few times a second
 * (see {@link #applyIdentity}). The client turns those numbers into a body (see EidolonModel).
 *
 * It is not saved to disk: {@link EidolonManager} spawns a fresh one whenever its owner is online, and any stray
 * copy that is not the manager's current one removes itself.
 */
public class EidolonEntity extends PathfinderMob {
	private static final EntityDataAccessor<Integer> LEVELS_LO =
			SynchedEntityData.defineId(EidolonEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> LEVELS_HI =
			SynchedEntityData.defineId(EidolonEntity.class, EntityDataSerializers.INT);
	/** Low byte: maturity 0-255. Next byte: leading trait ordinal + 1 (0 = none). */
	private static final EntityDataAccessor<Integer> FORM =
			SynchedEntityData.defineId(EidolonEntity.class, EntityDataSerializers.INT);

	private static final String[] WHISPERS = {
			"Behind you.", "Something is close.", "Not that way.", "It has seen you.", "Slowly. Do not run."
	};

	/** Where the Eidolon floats relative to its owner: forward, right, up (blocks), and how much it bobs. */
	private record Place(double forward, double right, double up, double bob) {
	}

	private UUID ownerId;
	private long lastWarnTick = -100000L;
	/** While this is in the future the Eidolon is a bad reflection: it stands on the wrong side and turns its back. */
	private int glitchUntil;

	public EidolonEntity(EntityType<? extends EidolonEntity> type, Level level) {
		super(type, level);
		this.noPhysics = true;
		this.setNoGravity(true);
		this.setNoAi(true);
		this.setPersistenceRequired();
	}

	public static AttributeSupplier.Builder createAttributes() {
		return PathfinderMob.createMobAttributes().add(Attributes.MAX_HEALTH, 20.0);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(LEVELS_LO, 0);
		builder.define(LEVELS_HI, 0);
		builder.define(FORM, 0);
	}

	public void setOwner(UUID owner) {
		this.ownerId = owner;
	}

	// ------------------------------------------------------------------ appearance (synced)

	/** 0-255, same curve as the mirror's 0-100 so the body and the reflection agree. */
	static int level255(double score) {
		return (int) Math.round(255.0 * (1.0 - Math.exp(-Math.max(0.0, score) / 45.0)));
	}

	/** Server only: copies the owner's identity into synced data. Cheap to call often; unchanged values are not resent. */
	public void applyIdentity(PlayerIdentity id) {
		int[] lv = new int[8];
		for (Trait t : Trait.values()) {
			lv[t.ordinal()] = level255(id.get(t));
		}

		PathPowers.shape(id, lv, 255); // the Eidolon takes the form of the path you lean toward
		Trait lead = SelfMade.leadingTrait(id);
		int leadOrdinal = lead == null ? -1 : lead.ordinal();
		int pathLead = PathPowers.leadOverride(id);
		if (pathLead >= 0) {
			leadOrdinal = pathLead;
		}
		int maturity = (int) Math.round(255.0 * Math.min(1.0, Math.log10(1.0 + SelfMade.totalOf(id)) / 2.0));
		int[] packed = pack(lv, maturity, leadOrdinal);

		this.entityData.set(LEVELS_LO, packed[0]);
		this.entityData.set(LEVELS_HI, packed[1]);
		this.entityData.set(FORM, packed[2]);
	}

	/** Packs eight 0-255 levels, a maturity and a lead trait (-1 = none) into the three synced ints: lo, hi, form. */
	static int[] pack(int[] lv, int maturity, int lead) {
		int lo = lv[0] | (lv[1] << 8) | (lv[2] << 16) | (lv[3] << 24);
		int hi = lv[4] | (lv[5] << 8) | (lv[6] << 16) | (lv[7] << 24);
		int form = maturity | ((lead + 1) << 8);
		return new int[] {lo, hi, form};
	}

	/** 0..1 for the trait with this ordinal. Works on both sides. */
	public float traitLevel(int ordinal) {
		int packed = this.entityData.get(ordinal < 4 ? LEVELS_LO : LEVELS_HI);
		return ((packed >>> ((ordinal & 3) * 8)) & 0xFF) / 255.0f;
	}

	/** 0..1: how formed it is. Near 0 it is a barely visible shadow. */
	public float maturity() {
		return (this.entityData.get(FORM) & 0xFF) / 255.0f;
	}

	/** Ordinal of the strongest trait, or -1 if there is none yet. */
	public int leadOrdinal() {
		return ((this.entityData.get(FORM) >>> 8) & 0xFF) - 1;
	}

	// ------------------------------------------------------------------ behaviour (server)

	@Override
	public void tick() {
		super.tick();
		if (this.level() instanceof ServerLevel serverLevel) {
			serverTick(serverLevel);
		}
	}

	private void serverTick(ServerLevel level) {
		MinecraftServer server = level.getServer();
		ServerPlayer owner = ownerId == null ? null : server.getPlayerList().getPlayer(ownerId);
		if (owner == null || !owner.isAlive() || owner.level() != level
				|| !EidolonManager.isCurrent(ownerId, this.getUUID())) {
			this.discard();
			return;
		}

		if (traitLevel(Trait.FEAR.ordinal()) >= 0.45f && this.tickCount % 20 == 0 && this.getRandom().nextInt(20) == 0) {
			glitchUntil = this.tickCount + 50; // afraid people get reflections that move incorrectly
		}
		follow(owner);
		aura(level);
		if (traitLevel(Trait.FEAR.ordinal()) >= 0.25f) {
			warn(level, owner);
		}
	}

	private static Place placeFor(int lead) {
		return switch (lead) {
			case 0 -> new Place(-1.8, 0.0, 1.1, 0.05);   // Will: right behind you
			case 1 -> new Place(-0.4, 0.0, 3.0, 0.15);   // Ego: looms over you
			case 2, 3 -> new Place(0.0, 1.5, 0.9, 0.05); // Empathy, Resolve: a guardian at your side
			case 4 -> new Place(1.8, 0.4, 1.0, 0.10);    // Fear: it leaves before you do
			case 5 -> new Place(3.0, 0.8, 2.0, 0.30);    // Curiosity: scouts ahead
			case 6 -> new Place(-0.6, 0.7, 1.1, 0.20);   // Violence: stays close, restless
			case 7 -> new Place(-0.5, 2.2, 0.8, 0.15);   // Attachment: keeps its distance, never leaves
			default -> new Place(-1.1, 0.9, 1.2, 0.12);
		};
	}

	private void follow(ServerPlayer owner) {
		Place p = placeFor(leadOrdinal());
		float yaw = owner.getYRot() * Mth.DEG_TO_RAD;
		double fx = -Mth.sin(yaw);
		double fz = Mth.cos(yaw);
		double rx = -fz;
		double rz = fx;
		double bob = Math.sin(this.tickCount * 0.08) * p.bob();

		boolean glitch = this.tickCount < glitchUntil;
		double fwd = glitch ? -p.forward() : p.forward();
		double rgt = glitch ? -p.right() : p.right();

		double tx = owner.getX() + fx * fwd + rx * rgt;
		double ty = owner.getY() + p.up() + bob;
		double tz = owner.getZ() + fz * fwd + rz * rgt;

		if (this.distanceToSqr(tx, ty, tz) > 24.0 * 24.0) {
			this.setPos(tx, ty, tz);
		} else {
			double k = 0.18;
			this.setPos(getX() + (tx - getX()) * k, getY() + (ty - getY()) * k, getZ() + (tz - getZ()) * k);
		}
		this.setDeltaMovement(Vec3.ZERO);
		this.fallDistance = 0.0;

		// Always look at the person it belongs to.
		float face = (float) (Mth.atan2(owner.getZ() - getZ(), owner.getX() - getX()) * (180.0 / Math.PI)) - 90.0f;
		if (glitch) {
			face += 180.0f; // looking the wrong way
		}
		float yawNow = Mth.rotLerp(0.3f, this.getYRot(), face);
		this.setYRot(yawNow);
		this.setYHeadRot(yawNow);
		this.yBodyRot = yawNow;
	}

	/** Particles that match the strongest trait. Faint while it is still only a shadow. */
	private void aura(ServerLevel level) {
		if (this.tickCount % 3 != 0) {
			return;
		}
		int count = 1 + (int) (maturity() * 2.0f);
		ParticleOptions particle = ParticleTypes.SOUL;
		int lead = leadOrdinal();
		if (lead >= 0) {
			switch (Trait.values()[lead]) {
				case VIOLENCE -> particle = ParticleTypes.CRIT;
				case FEAR -> particle = ParticleTypes.SCULK_SOUL;
				case CURIOSITY -> particle = ParticleTypes.END_ROD;
				case EMPATHY -> {
					particle = ParticleTypes.HEART;
					count = 1;
				}
				case EGO -> particle = ParticleTypes.FLAME;
				case WILL -> particle = ParticleTypes.SMOKE;
				case RESOLVE, ATTACHMENT -> particle = ParticleTypes.SOUL;
			}
		}
		level.sendParticles(particle, getX(), getY() + 0.9, getZ(), count, 0.35, 0.5, 0.35, 0.0);
	}

	/** Fear makes it whisper when something hostile is closing in. At most once a minute. */
	private void warn(ServerLevel level, ServerPlayer owner) {
		if (this.tickCount % 20 != 0 || level.getGameTime() - lastWarnTick < 1200L) {
			return;
		}
		List<Mob> near = level.getEntitiesOfClass(Mob.class, owner.getBoundingBox().inflate(12.0),
				m -> m.isAlive() && m.getType().getCategory() == MobCategory.MONSTER);
		if (near.isEmpty()) {
			return;
		}
		lastWarnTick = level.getGameTime();
		owner.sendSystemMessage(Component.literal(WHISPERS[this.getRandom().nextInt(WHISPERS.length)])
				.withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC));
	}

	// ------------------------------------------------------------------ it is not a thing you can touch

	/** Nothing can hurt it. (No @Override on purpose: if the name differs in this version it is simply unused.) */
	public boolean isInvulnerable() {
		return true;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public boolean isPickable() {
		return false;
	}
}
