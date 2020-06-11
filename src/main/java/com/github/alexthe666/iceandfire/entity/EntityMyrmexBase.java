package com.github.alexthe666.iceandfire.entity;

import com.github.alexthe666.citadel.animation.Animation;
import com.github.alexthe666.citadel.animation.AnimationHandler;
import com.github.alexthe666.citadel.animation.IAnimatedEntity;
import com.github.alexthe666.iceandfire.IafConfig;
import com.github.alexthe666.iceandfire.block.BlockMyrmexBiolight;
import com.github.alexthe666.iceandfire.block.BlockMyrmexConnectedResin;
import com.github.alexthe666.iceandfire.block.BlockMyrmexResin;
import com.github.alexthe666.iceandfire.entity.util.MyrmexHive;
import com.github.alexthe666.iceandfire.item.IafItemRegistry;
import com.github.alexthe666.iceandfire.misc.IafSoundRegistry;
import com.github.alexthe666.iceandfire.pathfinding.PathNavigateMyrmex;
import com.github.alexthe666.iceandfire.world.MyrmexWorldData;
import com.github.alexthe666.iceandfire.world.gen.WorldGenMyrmexHive;
import com.google.common.collect.Sets;
import net.minecraft.block.*;
import net.minecraft.block.material.Material;
import net.minecraft.entity.*;
import net.minecraft.entity.item.ExperienceOrbEntity;
import net.minecraft.entity.merchant.IMerchant;
import net.minecraft.entity.merchant.villager.VillagerTrades;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.MerchantOffer;
import net.minecraft.item.MerchantOffers;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.particles.ParticleTypes;
import net.minecraft.pathfinding.PathNavigator;
import net.minecraft.potion.EffectInstance;
import net.minecraft.potion.Effects;
import net.minecraft.util.*;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.IWorld;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.dimension.DimensionType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.common.BiomeDictionary;

import javax.annotation.Nullable;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

public abstract class EntityMyrmexBase extends AnimalEntity implements IAnimatedEntity, IMerchant {

    public static final Animation ANIMATION_PUPA_WIGGLE = Animation.create(20);
    private static final DataParameter<Byte> CLIMBING = EntityDataManager.createKey(EntityMyrmexBase.class, DataSerializers.BYTE);
    private static final DataParameter<Integer> GROWTH_STAGE = EntityDataManager.createKey(EntityMyrmexBase.class, DataSerializers.VARINT);
    private static final DataParameter<Boolean> VARIANT = EntityDataManager.createKey(EntityMyrmexBase.class, DataSerializers.BOOLEAN);
    private static final ResourceLocation TEXTURE_DESERT_LARVA = new ResourceLocation("iceandfire:textures/models/myrmex/myrmex_desert_larva.png");
    private static final ResourceLocation TEXTURE_DESERT_PUPA = new ResourceLocation("iceandfire:textures/models/myrmex/myrmex_desert_pupa.png");
    private static final ResourceLocation TEXTURE_JUNGLE_LARVA = new ResourceLocation("iceandfire:textures/models/myrmex/myrmex_jungle_larva.png");
    private static final ResourceLocation TEXTURE_JUNGLE_PUPA = new ResourceLocation("iceandfire:textures/models/myrmex/myrmex_jungle_pupa.png");
    private final Inventory villagerInventory = new Inventory(8);
    public boolean isEnteringHive = false;
    public boolean isBeingGuarded = false;
    protected int growthTicks = 1;
    @Nullable
    protected MerchantOffers offers;
    private int animationTick;
    private Animation currentAnimation;
    private MyrmexHive hive;
    private int timeUntilReset;
    private boolean leveledUp;
    @Nullable
    private PlayerEntity customer;


    public EntityMyrmexBase(EntityType t, World worldIn) {
        super(t, worldIn);
        this.stepHeight = 2;
        //this.moveController = new GroundMoveHelper(this);
    }

    private static boolean isJungleBiome(World world, BlockPos position) {
        Biome biome = world.getBiome(position);
        return biome.getSurfaceBuilderConfig().getTop().getBlock() != Blocks.SAND && !BiomeDictionary.hasType(biome, BiomeDictionary.Type.SANDY);
    }

    public static boolean haveSameHive(EntityMyrmexBase myrmex, Entity entity) {
        if (entity instanceof EntityMyrmexBase) {
            if (myrmex.getHive() != null && ((EntityMyrmexBase) entity).getHive() != null) {
                if (myrmex.isJungle() == ((EntityMyrmexBase) entity).isJungle()) {
                    return myrmex.getHive().getCenter() == ((EntityMyrmexBase) entity).getHive().getCenter();
                }
            }

        }
        if (entity instanceof EntityMyrmexEgg) {
            return myrmex.isJungle() == ((EntityMyrmexEgg) entity).isJungle();
        }
        return false;
    }

    public static boolean isEdibleBlock(BlockState blockState) {
        Block block = blockState.getBlock();
        if (block instanceof BlockMyrmexBiolight) {
            return false;
        }
        return blockState.getMaterial() == Material.LEAVES || blockState.getMaterial() == Material.PLANTS || blockState.getMaterial() == Material.CACTUS || block instanceof BushBlock || block instanceof CactusBlock || block instanceof LeavesBlock;
    }

    public static int getRandomCaste(World world, Random random, boolean royal) {
        float rand = random.nextFloat();
        if (royal) {
            if (rand > 0.9) {
                return 2;//royal
            } else if (rand > 0.75) {
                return 3;//sentinel
            } else if (rand > 0.5) {
                return 1;//soldier
            } else {
                return 0;//worker
            }
        } else {
            if (rand > 0.8) {
                return 3;//sentinel
            } else if (rand > 0.6) {
                return 1;//soldier
            } else {
                return 0;//worker
            }
        }
    }

    public boolean canMove() {
        return this.getGrowthStage() > 1;
    }

    public boolean isChild() {
        return this.getGrowthStage() < 2;
    }

    protected void updateAITasks() {
        if (!this.hasCustomer() && this.timeUntilReset > 0) {
            --this.timeUntilReset;
            if (this.timeUntilReset <= 0) {
                if (this.leveledUp) {
                    this.levelUp();
                    this.leveledUp = false;
                }

                this.addPotionEffect(new EffectInstance(Effects.REGENERATION, 200, 0));
            }
        }
        if (this.getHive() != null && this.getCustomer() != null) {
            this.world.setEntityState(this, (byte) 14);
            this.getHive().setWorld(this.world);
            this.getHive().modifyPlayerReputation(this.getCustomer().getUniqueID(), 1);
        }
        super.updateAITasks();
    }

    protected int getExperiencePoints(PlayerEntity player) {
        return (this.getCasteImportance() * 7) + this.world.rand.nextInt(3);
    }

    @Override
    public boolean attackEntityFrom(DamageSource dmg, float i) {
        if (dmg == DamageSource.IN_WALL && this.getGrowthStage() < 2) {
            return false;
        }
        if (this.getGrowthStage() < 2) {
            this.setAnimation(ANIMATION_PUPA_WIGGLE);
        }
        return super.attackEntityFrom(dmg, i);
    }

    protected float getJumpUpwardsMotion() {
        return 0.52F;
    }

    protected void registerAttributes() {
        super.registerAttributes();
        this.getAttributes().registerAttribute(SharedMonsterAttributes.ATTACK_DAMAGE);
        this.getAttribute(SharedMonsterAttributes.FOLLOW_RANGE).setBaseValue(64.0D);
    }

    public float getBlockPathWeight(BlockPos pos) {
        return this.world.getBlockState(pos.down()).getBlock() instanceof BlockMyrmexResin ? 10.0F : this.world.getLight(pos) - 0.5F;
    }

    protected PathNavigator createNavigator(World worldIn) {
        return new PathNavigateMyrmex(this, worldIn);
    }

    protected void registerData() {
        super.registerData();
        this.dataManager.register(CLIMBING, Byte.valueOf((byte) 0));
        this.dataManager.register(GROWTH_STAGE, Integer.valueOf(2));
        this.dataManager.register(VARIANT, Boolean.valueOf(false));
    }

    public void tick() {
        super.tick();
        if (world.getDifficulty() == Difficulty.PEACEFUL && this.getAttackTarget() instanceof PlayerEntity) {
            this.setAttackTarget(null);
        }
        if (this.getGrowthStage() < 2 && this.getRidingEntity() != null && this.getRidingEntity() instanceof EntityMyrmexBase) {
            float yaw = this.getRidingEntity().rotationYaw;
            this.rotationYaw = yaw;
            this.rotationYawHead = yaw;
            this.renderYawOffset = 0;
            this.prevRenderYawOffset = 0;
        }
        if (!this.world.isRemote) {
            this.setBesideClimbableBlock(this.collidedHorizontally && (this.onGround || !this.collidedVertically));
        }
        if (this.getGrowthStage() < 2) {
            growthTicks++;
            if (growthTicks == IafConfig.myrmexLarvaTicks) {
                this.setGrowthStage(this.getGrowthStage() + 1);
                growthTicks = 0;
            }
        }
        if (!this.world.isRemote && this.getGrowthStage() < 2 && this.getRNG().nextInt(150) == 0 && this.getAnimation() == NO_ANIMATION) {
            this.setAnimation(ANIMATION_PUPA_WIGGLE);
        }

        if (this.getAttackTarget() != null && !(this.getAttackTarget() instanceof PlayerEntity) && this.getNavigator().noPath()) {
            this.setAttackTarget(null);
        }
        if (this.getAttackTarget() != null && (haveSameHive(this, this.getAttackTarget()) ||
                this.getAttackTarget() instanceof TameableEntity && !canAttackTamable((TameableEntity) this.getAttackTarget()) ||
                this.getAttackTarget() instanceof PlayerEntity && this.getHive() != null && !this.getHive().isPlayerReputationTooLowToFight(this.getAttackTarget().getUniqueID()))) {
            this.setAttackTarget(null);
        }

        if (this.getHealth() < this.getMaxHealth() && this.ticksExisted % 500 == 0 && this.isOnResin()) {
            this.heal(1);
            this.world.setEntityState(this, (byte) 76);
        }
        AnimationHandler.INSTANCE.updateAnimations(this);
    }

    @Override
    public void writeAdditional(CompoundNBT tag) {
        super.writeAdditional(tag);
        tag.putInt("GrowthStage", this.getGrowthStage());
        tag.putInt("GrowthTicks", growthTicks);
        tag.putBoolean("Variant", this.isJungle());
        if (this.getHive() != null) {
            tag.putUniqueId("HiveUUID", this.getHive().hiveUUID);
        }
        MerchantOffers merchantoffers = this.getOffers();
        if (!merchantoffers.isEmpty()) {
            tag.put("Offers", merchantoffers.write());
        }

        ListNBT listnbt = new ListNBT();

        for (int i = 0; i < this.villagerInventory.getSizeInventory(); ++i) {
            ItemStack itemstack = this.villagerInventory.getStackInSlot(i);
            if (!itemstack.isEmpty()) {
                listnbt.add(itemstack.write(new CompoundNBT()));
            }
        }

        tag.put("Inventory", listnbt);
    }

    @Override
    public void readAdditional(CompoundNBT tag) {
        super.readAdditional(tag);
        this.setGrowthStage(tag.getInt("GrowthStage"));
        this.growthTicks = tag.getInt("GrowthTicks");
        this.setJungleVariant(tag.getBoolean("Variant"));
        this.setHive(MyrmexWorldData.get(world).getHiveFromUUID(tag.getUniqueId("HiveUUID")));
        if (tag.contains("Offers", 10)) {
            this.offers = new MerchantOffers(tag.getCompound("Offers"));
        }

        ListNBT listnbt = tag.getList("Inventory", 10);

        for (int i = 0; i < listnbt.size(); ++i) {
            ItemStack itemstack = ItemStack.read(listnbt.getCompound(i));
            if (!itemstack.isEmpty()) {
                this.villagerInventory.addItem(itemstack);
            }
        }

    }

    public boolean canAttackTamable(TameableEntity tameable) {
        if (tameable.getOwner() != null && this.getHive() != null) {
            return this.getHive().isPlayerReputationTooLowToFight(tameable.getOwnerId());
        }
        return true;
    }

    public World getWorld() {
        return this.world;
    }

    public BlockPos getPos() {
        return new BlockPos(this);
    }

    public int getGrowthStage() {
        return this.dataManager.get(GROWTH_STAGE).intValue();
    }

    public void setGrowthStage(int stage) {
        this.dataManager.set(GROWTH_STAGE, stage);
    }

    public boolean isJungle() {
        return this.dataManager.get(VARIANT).booleanValue();
    }

    public void setJungleVariant(boolean isJungle) {
        this.dataManager.set(VARIANT, isJungle);
    }

    public CreatureAttribute getCreatureAttribute() {
        return CreatureAttribute.ARTHROPOD;
    }

    public boolean isBesideClimbableBlock() {
        return (this.dataManager.get(CLIMBING).byteValue() & 1) != 0;
    }

    public void setBesideClimbableBlock(boolean climbing) {
        byte b0 = this.dataManager.get(CLIMBING).byteValue();

        if (climbing) {
            b0 = (byte) (b0 | 1);
        } else {
            b0 = (byte) (b0 & -2);
        }

        this.dataManager.set(CLIMBING, Byte.valueOf(b0));
    }

    public boolean isOnLadder() {
        return super.isOnLadder();
    }

    @Nullable
    @Override
    public AgeableEntity createChild(AgeableEntity ageable) {
        return null;
    }

    @Override
    public int getAnimationTick() {
        return animationTick;
    }

    @Override
    public void setAnimationTick(int tick) {
        animationTick = tick;
    }

    @Override
    public Animation getAnimation() {
        return currentAnimation;
    }

    @Override
    public void setAnimation(Animation animation) {
        currentAnimation = animation;
    }

    @Override
    public Animation[] getAnimations() {
        return new Animation[]{ANIMATION_PUPA_WIGGLE};
    }

    public void setRevengeTarget(@Nullable LivingEntity livingBase) {
        if (this.getHive() == null || livingBase == null || livingBase instanceof PlayerEntity && this.getHive().isPlayerReputationTooLowToFight(livingBase.getUniqueID())) {
            super.setRevengeTarget(livingBase);
        }
        if (this.getHive() != null && livingBase != null) {
            this.getHive().addOrRenewAgressor(livingBase, this.getImportance());
        }
        if (this.getHive() != null && livingBase != null) {
            if (livingBase instanceof PlayerEntity) {
                int i = -5 * this.getCasteImportance();
                this.getHive().setWorld(this.world);
                this.getHive().modifyPlayerReputation(livingBase.getUniqueID(), i);
                if (this.isAlive()) {
                    this.world.setEntityState(this, (byte) 13);
                }
            }
        }
    }

    public void onDeath(DamageSource cause) {
        if (this.getHive() != null) {
            Entity entity = cause.getTrueSource();
            if (entity != null) {
                this.getHive().setWorld(this.world);
                this.getHive().modifyPlayerReputation(entity.getUniqueID(), -15);
            }
        }
        this.resetCustomer();
        super.onDeath(cause);
    }

    public boolean processInteract(PlayerEntity player, Hand hand) {
        ItemStack itemstack = player.getHeldItem(hand);
        if (!shouldHaveNormalAI()) {
            return false;
        }
        boolean flag2 = itemstack.getItem() == IafItemRegistry.MYRMEX_JUNGLE_STAFF || itemstack.getItem() == IafItemRegistry.MYRMEX_DESERT_STAFF;

        if (flag2) {
            this.onStaffInteract(player, itemstack);
            player.swingArm(hand);
            return true;
        }
        boolean flag = itemstack.getItem() == Items.NAME_TAG || itemstack.getItem() == Items.LEAD;
        if (flag) {
            itemstack.interactWithEntity(player, this, hand);
            return true;
        } else if (!super.processInteract(player, hand) && this.getGrowthStage() >= 2 && this.isAlive() && !this.isChild() && !player.isShiftKeyDown()) {
            if (this.getOffers().isEmpty()) {
                return super.processInteract(player, hand);
            } else {
                if (!this.world.isRemote) {
                    this.setCustomer(player);
                    this.openMerchantContainer(player, this.getDisplayName(), 1);
                }

                return true;
            }
        } else {
            return super.processInteract(player, hand);
        }
    }

    public void onStaffInteract(PlayerEntity player, ItemStack itemstack) {
        UUID staffUUID = itemstack.getTag().getUniqueId("HiveUUID");
        if (world.isRemote) {
            return;
        }
        if (!player.isCreative()) {
            if ((this.getHive() != null && !this.getHive().canPlayerCommandHive(player.getUniqueID()))) {
                return;
            }
        }
        if (this.getHive() == null) {
            player.sendStatusMessage(new TranslationTextComponent("myrmex.message.null_hive"), true);

        } else {
            if (staffUUID != null && staffUUID.equals(this.getHive().hiveUUID)) {
                player.sendStatusMessage(new TranslationTextComponent("myrmex.message.staff_already_set"), true);
            } else {
                this.getHive().setWorld(this.world);
                EntityMyrmexQueen queen = this.getHive().getQueen();
                BlockPos center = this.getHive().getCenterGround();
                if (queen.hasCustomName()) {
                    player.sendStatusMessage(new TranslationTextComponent("myrmex.message.staff_set_named", queen.getName(), center.getX(), center.getY(), center.getZ()), true);
                } else {
                    player.sendStatusMessage(new TranslationTextComponent("myrmex.message.staff_set_unnamed", center.getX(), center.getY(), center.getZ()), true);
                }
                itemstack.getTag().putUniqueId("HiveUUID", this.getHive().hiveUUID);
            }

        }

    }

    @Override
    @Nullable
    public ILivingEntityData onInitialSpawn(IWorld worldIn, DifficultyInstance difficultyIn, SpawnReason reason, @Nullable ILivingEntityData spawnDataIn, @Nullable CompoundNBT dataTag) {
        spawnDataIn = super.onInitialSpawn(worldIn, difficultyIn, reason, spawnDataIn, dataTag);
        this.setHive(MyrmexWorldData.get(world).getNearestHive(this.getPosition(), 400));
        if (this.getHive() != null) {
            this.setJungleVariant(isJungleBiome(world, this.getHive().getCenter()));
        } else {
            this.setJungleVariant(rand.nextBoolean());
        }
        return spawnDataIn;
    }

    public abstract boolean shouldLeaveHive();

    public abstract boolean shouldEnterHive();

    @Override
    public float getRenderScale() {
        return this.getGrowthStage() == 0 ? 0.5F : this.getGrowthStage() == 1 ? 0.75F : 1F;
    }

    public abstract ResourceLocation getAdultTexture();

    public abstract float getModelScale();

    public ResourceLocation getTexture() {
        if (this.getGrowthStage() == 0) {
            return isJungle() ? TEXTURE_JUNGLE_LARVA : TEXTURE_DESERT_LARVA;
        } else if (this.getGrowthStage() == 1) {
            return isJungle() ? TEXTURE_JUNGLE_PUPA : TEXTURE_DESERT_PUPA;
        } else {
            return getAdultTexture();
        }
    }

    public MyrmexHive getHive() {
        return hive;
    }

    public void setHive(MyrmexHive newHive) {
        hive = newHive;
        if (hive != null) {
            hive.addMyrmex(this);
        }
    }

    protected void collideWithEntity(Entity entityIn) {
        if (!haveSameHive(this, entityIn)) {
            entityIn.applyEntityCollision(this);
        }
    }

    public boolean canSeeSky() {
        return world.canBlockSeeSky(new BlockPos(this));
    }

    public boolean isOnResin() {
        double d0 = this.getPosY() - 1;
        BlockPos blockpos = new BlockPos(this.getPosX(), d0, this.getPosZ());
        while (world.isAirBlock(blockpos) && blockpos.getY() > 1) {
            blockpos = blockpos.down();
        }
        BlockState BlockState = this.world.getBlockState(blockpos);
        return BlockState.getBlock() instanceof BlockMyrmexResin || BlockState.getBlock() instanceof BlockMyrmexConnectedResin;
    }

    public boolean isInNursery() {
        if (getHive() != null && getHive().getRooms(WorldGenMyrmexHive.RoomType.NURSERY).isEmpty() && getHive().getRandomRoom(WorldGenMyrmexHive.RoomType.NURSERY, this.getRNG(), this.getPosition()) != null) {
            return false;
        }
        if (getHive() != null) {
            BlockPos nursery = getHive().getRandomRoom(WorldGenMyrmexHive.RoomType.NURSERY, this.getRNG(), this.getPosition());
            return MathHelper.sqrt(this.getDistanceSq(nursery.getX(), nursery.getY(), nursery.getZ())) < 45;
        }
        return false;
    }

    @Override
    public void travel(Vec3d motion) {
        if (!this.canMove()) {
            super.travel(Vec3d.ZERO);
            return;
        }
        super.travel(motion);
    }

    public int getImportance() {
        if (this.getGrowthStage() < 2) {
            return 1;
        }
        return getCasteImportance();
    }

    public abstract int getCasteImportance();

    public boolean needsGaurding() {
        return true;
    }

    public boolean shouldMoveThroughHive() {
        return true;
    }

    public boolean shouldWander() {
        return this.getHive() == null;
    }

    @OnlyIn(Dist.CLIENT)
    public void handleStatusUpdate(byte id) {
        if (id == 76) {
            this.playVillagerEffect();
        } else {
            super.handleStatusUpdate(id);
        }
    }

    @Nullable
    protected SoundEvent getAmbientSound() {
        return IafSoundRegistry.MYRMEX_IDLE;
    }

    @Nullable
    protected SoundEvent getHurtSound(DamageSource source) {
        return IafSoundRegistry.MYRMEX_HURT;
    }

    @Nullable
    protected SoundEvent getDeathSound() {
        return IafSoundRegistry.MYRMEX_DIE;
    }

    protected void playStepSound(BlockPos pos, Block blockIn) {
        this.playSound(IafSoundRegistry.MYRMEX_WALK, 0.16F * this.getMyrmexPitch() * (this.getRNG().nextFloat() * 0.6F + 0.4F), 1.0F);
    }

    protected void playBiteSound() {
        this.playSound(IafSoundRegistry.MYRMEX_BITE, 1.0F * this.getMyrmexPitch(), 1.0F);
    }

    protected void playStingSound() {
        this.playSound(IafSoundRegistry.MYRMEX_STING, 1.0F * this.getMyrmexPitch(), 0.6F);
    }

    protected void playVillagerEffect() {
        for (int i = 0; i < 7; ++i) {
            double d0 = this.rand.nextGaussian() * 0.02D;
            double d1 = this.rand.nextGaussian() * 0.02D;
            double d2 = this.rand.nextGaussian() * 0.02D;
            this.world.addParticle(ParticleTypes.HAPPY_VILLAGER, this.getPosX() + (double) (this.rand.nextFloat() * this.getWidth() * 2.0F) - (double) this.getWidth(), this.getPosY() + 0.5D + (double) (this.rand.nextFloat() * this.getHeight()), this.getPosZ() + (double) (this.rand.nextFloat() * this.getWidth() * 2.0F) - (double) this.getWidth(), d0, d1, d2);
        }
    }

    public float getMyrmexPitch() {
        return getWidth();
    }

    public boolean shouldHaveNormalAI() {
        return true;
    }

    @Override
    public boolean isNoDespawnRequired() {
        return true;
    }

    @Override
    public boolean canDespawn(double distanceToClosestPlayer) {
        return false;
    }

    public AxisAlignedBB getAttackBounds() {
        float size = this.getRenderScale() * 0.25F;
        return this.getBoundingBox().grow(1.0F + size, 1.0F + size, 1.0F + size);
    }

    @Nullable
    public PlayerEntity getCustomer() {
        return this.customer;
    }

    public void setCustomer(@Nullable PlayerEntity player) {
        this.customer = player;
    }

    public boolean hasCustomer() {
        return this.customer != null;
    }

    public MerchantOffers getOffers() {
        if (this.offers == null) {
            this.offers = new MerchantOffers();
            this.populateTradeData();
        }

        return this.offers;
    }

    @OnlyIn(Dist.CLIENT)
    public void setClientSideOffers(@Nullable MerchantOffers offers) {
    }

    public void setXP(int xpIn) {
    }

    public void onTrade(MerchantOffer offer) {
        offer.increaseUses();
        this.livingSoundTime = -this.getTalkInterval();
        this.onVillagerTrade(offer);
    }

    protected void onVillagerTrade(MerchantOffer offer) {
        if (offer.getDoesRewardExp()) {
            int i = 3 + this.rand.nextInt(4);
            this.world.addEntity(new ExperienceOrbEntity(this.world, this.getPosX(), this.getPosY() + 0.5D, this.getPosZ(), i));
        }
    }

    public void verifySellingItem(ItemStack stack) {
        if (!this.world.isRemote && this.livingSoundTime > -this.getTalkInterval() + 20) {
            this.livingSoundTime = -this.getTalkInterval();
            this.playSound(this.getVillagerYesNoSound(!stack.isEmpty()), this.getSoundVolume(), this.getSoundPitch());
        }

    }

    public SoundEvent getYesSound() {
        return SoundEvents.ENTITY_VILLAGER_YES;
    }

    protected SoundEvent getVillagerYesNoSound(boolean getYesSound) {
        return getYesSound ? SoundEvents.ENTITY_VILLAGER_YES : SoundEvents.ENTITY_VILLAGER_NO;
    }

    public void playCelebrateSound() {
        this.playSound(SoundEvents.ENTITY_VILLAGER_CELEBRATE, this.getSoundVolume(), this.getSoundPitch());
    }

    protected void resetCustomer() {
        this.setCustomer(null);
    }

    @Nullable
    public Entity changeDimension(DimensionType destination, net.minecraftforge.common.util.ITeleporter teleporter) {
        this.resetCustomer();
        return super.changeDimension(destination, teleporter);
    }

    public Inventory getVillagerInventory() {
        return this.villagerInventory;
    }

    public boolean replaceItemInInventory(int inventorySlot, ItemStack itemStackIn) {
        if (super.replaceItemInInventory(inventorySlot, itemStackIn)) {
            return true;
        } else {
            int i = inventorySlot - 300;
            if (i >= 0 && i < this.villagerInventory.getSizeInventory()) {
                this.villagerInventory.setInventorySlotContents(i, itemStackIn);
                return true;
            } else {
                return false;
            }
        }
    }

    protected void addTrades(MerchantOffers givenMerchantOffers, VillagerTrades.ITrade[] newTrades, int maxNumbers) {
        Set<Integer> set = Sets.newHashSet();
        if (newTrades.length > maxNumbers) {
            while (set.size() < maxNumbers) {
                set.add(this.rand.nextInt(newTrades.length));
            }
        } else {
            for (int i = 0; i < newTrades.length; ++i) {
                set.add(i);
            }
        }

        for (Integer integer : set) {
            VillagerTrades.ITrade villagertrades$itrade = newTrades[integer];
            MerchantOffer merchantoffer = villagertrades$itrade.getOffer(this, this.rand);
            if (merchantoffer != null) {
                givenMerchantOffers.add(merchantoffer);
            }
        }

    }

    private boolean canLevelUp() {
        return true;
    }

    private void levelUp() {
        this.populateTradeData();
    }


    protected void populateTradeData() {
        VillagerTrades.ITrade[] avillagertrades$itrade = VillagerTrades.field_221240_b.get(1); //TODO update myrmex trade data
        VillagerTrades.ITrade[] avillagertrades$itrade1 = VillagerTrades.field_221240_b.get(2);
        if (avillagertrades$itrade != null && avillagertrades$itrade1 != null) {
            MerchantOffers merchantoffers = this.getOffers();
            this.addTrades(merchantoffers, avillagertrades$itrade, 5);
            int i = this.rand.nextInt(avillagertrades$itrade1.length);
            VillagerTrades.ITrade villagertrades$itrade = avillagertrades$itrade1[i];
            MerchantOffer merchantoffer = villagertrades$itrade.getOffer(this, this.rand);
            if (merchantoffer != null) {
                merchantoffers.add(merchantoffer);
            }
        }
    }
}
