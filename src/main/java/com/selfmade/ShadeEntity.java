package com.selfmade;

import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.level.Level;

import java.util.UUID;

/**
 * Every "you" that is not the Eidolon: echoes of your deaths, the Death-Eidolon at a Grave of You, The Other You that
 * your Ego creates, the statues of you, and the invisible name-tags that hang over graves and conquered ground.
 * It is drawn with the same body as the Eidolon (see ShadeRenderer). It belongs to one player, the hostile kinds only
 * ever hunt that player, and it is never saved to the world (the data it needs is kept in {@link PlayerIdentity}).
 */
public class ShadeEntity extends PathfinderMob {
	public enum Kind {
		/** A weak echo of how you died. Fades by itself. */
		GHOST,
		/** The thing waiting in a Grave of You. */
		DEATH,
		/** The hostile manifestation of your idealised self. */
		OTHER,
		/** A statue of you. Harmless. */
		STATUE,
		/** Invisible; only its floating name shows. */
		MARKER,
		/** A Creator's construct: it follows its maker and fights what hunts them. */
		CONSTRUCT;

		public boolean hostile() {
			return this == GHOST || this == DEATH || this == OTHER;
		}

		/** Walks around under its own control (everything except statues and name-tags). */
		public boolean mobile() {
			return hostile() || this == CONSTRUCT;
		}
	}

	private static final EntityDataAccessor<Integer> LEVELS_LO =
			SynchedEntityData.defineId(ShadeEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> LEVELS_HI =
			SynchedEntityData.defineId(ShadeEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> FORM =
			SynchedEntityData.defineId(ShadeEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> KIND =
			SynchedEntityData.defineId(ShadeEntity.class, EntityDataSerializers.INT);

	private UUID ownerId;
	private int lifeTicks; // 0 = it stays until its owner goes far away

	public ShadeEntity(EntityType<? extends ShadeEntity> type, Level level) {
		super(type, level);
		this.setPersistenceRequired();
	}

	public static AttributeSupplier.Builder createAttributes() {
		return PathfinderMob.createMobAttributes()
				.add(Attributes.MAX_HEALTH, 20.0)
				.add(Attributes.ATTACK_DAMAGE, 4.0)
				.add(Attributes.MOVEMENT_SPEED, 0.27)
				.add(Attributes.FOLLOW_RANGE, 40.0);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(LEVELS_LO, 0);
		builder.define(LEVELS_HI, 0);
		builder.define(FORM, 0);
		builder.define(KIND, 0);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.15, true));
		this.goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.8));
	}

	// ------------------------------------------------------------------ set-up (server)

	/** Makes this entity a particular kind of you. {@code name} may be null. */
	public void setup(Kind kind, UUID owner, PlayerIdentity id, String name) {
		this.ownerId = owner;
		this.entityData.set(KIND, kind.ordinal());

		int[] lv = new int[8];
		for (Trait t : Trait.values()) {
			lv[t.ordinal()] = EidolonEntity.level255(id.get(t));
		}
		Trait leading = SelfMade.leadingTrait(id);
		int lead = leading == null ? -1 : leading.ordinal();
		int maturity = 255;
		double hp = 20.0;
		double damage = 4.0;
		double speed = 0.27;
		double scale = 1.0;

		switch (kind) {
			case GHOST -> {
				for (int i = 0; i < 8; i++) {
					lv[i] = (int) (lv[i] * 0.7);
				}
				maturity = 100; // the dithered, flickering texture
				hp = 18.0;
				damage = 3.0;
				speed = 0.25;
				lifeTicks = 1400;
			}
			case DEATH -> {
				lv[Trait.FEAR.ordinal()] = 255;
				lv[Trait.VIOLENCE.ordinal()] = 230;
				lv[Trait.WILL.ordinal()] = 200;
				lv[Trait.RESOLVE.ordinal()] = 110;
				lead = Trait.VIOLENCE.ordinal();
				hp = 140.0;
				damage = 6.0;
				speed = 0.27;
				scale = 1.7;
			}
			case OTHER -> {
				lv[Trait.EGO.ordinal()] = 255;
				lv[Trait.VIOLENCE.ordinal()] = Math.max(lv[Trait.VIOLENCE.ordinal()], 200);
				lv[Trait.WILL.ordinal()] = Math.max(lv[Trait.WILL.ordinal()], 150);
				lead = Trait.EGO.ordinal();
				hp = 80.0;
				damage = 7.0;
				speed = 0.30;
				scale = 1.25;
			}
			case CONSTRUCT -> {
				lv[Trait.CURIOSITY.ordinal()] = Math.max(lv[Trait.CURIOSITY.ordinal()], 200);
				lv[Trait.EMPATHY.ordinal()] = Math.max(lv[Trait.EMPATHY.ordinal()], 160);
				lv[Trait.ATTACHMENT.ordinal()] = Math.max(lv[Trait.ATTACHMENT.ordinal()], 120);
				lead = Trait.CURIOSITY.ordinal();
				hp = 30.0;
				damage = 5.0;
				speed = 0.30;
				scale = 0.75;
			}
			case STATUE -> {
				lv[Trait.EGO.ordinal()] = 255;
				lead = Trait.EGO.ordinal();
			}
			case MARKER -> {
				this.setInvisible(true);
			}
		}

		int[] packed = EidolonEntity.pack(lv, maturity, lead);
		this.entityData.set(LEVELS_LO, packed[0]);
		this.entityData.set(LEVELS_HI, packed[1]);
		this.entityData.set(FORM, packed[2]);

		setBase(Attributes.MAX_HEALTH, hp);
		setBase(Attributes.ATTACK_DAMAGE, damage);
		setBase(Attributes.MOVEMENT_SPEED, speed);
		setBase(Attributes.SCALE, scale);
		this.setHealth((float) hp);

		if (!kind.mobile()) {
			this.setNoAi(true);
			this.setNoGravity(true);
		}
		if (name != null) {
			this.setCustomName(Component.literal(name));
			this.setCustomNameVisible(true);
		}
	}

	public void setLife(int ticks) {
		this.lifeTicks = ticks;
	}

	private void setBase(Holder<Attribute> attribute, double value) {
		AttributeInstance instance = this.getAttribute(attribute);
		if (instance != null) {
			instance.setBaseValue(value);
		}
	}

	// ------------------------------------------------------------------ appearance (synced), same layout as the Eidolon

	public float traitLevel(int ordinal) {
		int packed = this.entityData.get(ordinal < 4 ? LEVELS_LO : LEVELS_HI);
		return ((packed >>> ((ordinal & 3) * 8)) & 0xFF) / 255.0f;
	}

	public float maturity() {
		return (this.entityData.get(FORM) & 0xFF) / 255.0f;
	}

	public int leadOrdinal() {
		return ((this.entityData.get(FORM) >>> 8) & 0xFF) - 1;
	}

	public Kind kind() {
		Kind[] all = Kind.values();
		return all[Math.max(0, Math.min(all.length - 1, this.entityData.get(KIND)))];
	}

	public UUID ownerId() {
		return ownerId;
	}

	// ------------------------------------------------------------------ behaviour (server)

	@Override
	public void tick() {
		super.tick();
		if (this.level() instanceof ServerLevel level) {
			serverTick(level);
		}
	}

	private void serverTick(ServerLevel level) {
		if (ownerId == null) {
			this.discard(); // a stray (should not happen: shades are never saved)
			return;
		}
		Kind kind = kind();
		if (!kind.mobile()) {
			return;
		}
		if (lifeTicks > 0 && this.tickCount > lifeTicks) {
			level.sendParticles(ParticleTypes.POOF, getX(), getY() + 0.9, getZ(), 12, 0.3, 0.5, 0.3, 0.02);
			this.discard();
			return;
		}
		if (kind == Kind.CONSTRUCT) {
			if (this.tickCount % 20 == 0) {
				constructTick(level);
			}
			return;
		}
		if (this.tickCount % 6 == 0) {
			level.sendParticles(kind == Kind.OTHER ? ParticleTypes.FLAME : ParticleTypes.SCULK_SOUL,
					getX(), getY() + 0.9, getZ(), 1, 0.3, 0.5, 0.3, 0.0);
		}
		if (this.tickCount % 20 != 0) {
			return;
		}
		ServerPlayer owner = level.getServer().getPlayerList().getPlayer(ownerId);
		if (owner == null || !owner.isAlive() || owner.level() != level || owner.distanceToSqr(this) > 90.0 * 90.0) {
			this.discard();
			return;
		}
		if (owner.isCreative() || owner.isSpectator() || owner.distanceToSqr(this) > 40.0 * 40.0) {
			this.setTarget(null);
		} else {
			this.setTarget(owner);
		}
	}

	/** A construct hunts whatever is hunting its maker, and otherwise stays near them. */
	private void constructTick(ServerLevel level) {
		ServerPlayer owner = level.getServer().getPlayerList().getPlayer(ownerId);
		if (owner == null || !owner.isAlive() || owner.level() != level) {
			this.discard();
			return;
		}
		Mob best = null;
		double bestDist = 14.0 * 14.0;
		for (Mob m : level.getEntitiesOfClass(Mob.class, owner.getBoundingBox().inflate(14.0),
				x -> x.isAlive() && x.getType().getCategory() == MobCategory.MONSTER && !(x instanceof ShadeEntity))) {
			double d = m.distanceToSqr(this);
			if (d < bestDist) {
				bestDist = d;
				best = m;
			}
		}
		this.setTarget(best);
		if (best == null) {
			double d = owner.distanceToSqr(this);
			if (d > 30.0 * 30.0) {
				this.setPos(owner.getX(), owner.getY(), owner.getZ());
			} else if (d > 5.0 * 5.0) {
				this.getNavigation().moveTo(owner, 1.2);
			}
		}
	}

	/** Statues and name-tags cannot be hurt. (No @Override on purpose, see EidolonEntity.) */
	public boolean isInvulnerable() {
		return !kind().mobile();
	}

	@Override
	public boolean isPushable() {
		return kind().mobile();
	}
}
