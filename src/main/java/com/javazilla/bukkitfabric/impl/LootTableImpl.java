/**
 * CardboardPowered - Bukkit/Spigot for Fabric
 * Copyright (C) CardboardPowered.org and contributors
 * 
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public
 * License as published by the Free Software Foundation; either 
 * version 3 of the License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package com.javazilla.bukkitfabric.impl;

import java.util.*;

import net.minecraft.entity.Entity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.loot.LootTable;
import net.minecraft.loot.context.LootContextParameter;
import net.minecraft.loot.context.LootContextParameters;
import net.minecraft.loot.context.LootContextType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.craftbukkit.entity.CraftEntity;
import org.bukkit.craftbukkit.entity.CraftHumanEntity;
import org.cardboardpowered.impl.world.WorldImpl;
import org.bukkit.craftbukkit.inventory.CraftInventory;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.loot.LootContext;

import com.javazilla.bukkitfabric.interfaces.IMixinEntity;
import com.javazilla.bukkitfabric.interfaces.IMixinLootContextParameters;
import com.javazilla.bukkitfabric.interfaces.IMixinWorld;
import org.jetbrains.annotations.NotNull;

public class LootTableImpl implements org.bukkit.loot.LootTable {

    private final LootTable handle;
    private final NamespacedKey key;

    public LootTableImpl(NamespacedKey key, LootTable handle) {
        this.handle = handle;
        this.key = key;
    }

    public LootTable getHandle() {
        return handle;
    }

    @Override
    public @NotNull Collection<ItemStack> populateLoot(@NotNull Random random, @NotNull LootContext context) {
        net.minecraft.loot.context.LootContext nmsContext = convertContext(context);
        List<net.minecraft.item.ItemStack> nmsItems = handle.generateLoot(nmsContext);
        Collection<ItemStack> bukkit = new ArrayList<>(nmsItems.size());

        for (net.minecraft.item.ItemStack item : nmsItems) {
            if (item.isEmpty()) continue;
            bukkit.add(CraftItemStack.asBukkitCopy(item));
        }
        return bukkit;
    }

    @Override
    public void fillInventory(@NotNull Inventory inventory, @NotNull Random random, @NotNull LootContext context) {
        net.minecraft.loot.context.LootContext nmsContext = convertContext(context);
        CraftInventory craftInventory = (CraftInventory) inventory;
        net.minecraft.inventory.Inventory handle = craftInventory.getInventory();
        getHandle().supplyInventory(handle, nmsContext);
    }

    @Override
    public @NotNull NamespacedKey getKey() {
        return key;
    }

    private net.minecraft.loot.context.LootContext convertContext(LootContext context) {
        Location loc = context.getLocation();
        ServerWorld handle = ((WorldImpl) Objects.requireNonNull(loc.getWorld())).getHandle();

        net.minecraft.loot.context.LootContext.Builder builder = new net.minecraft.loot.context.LootContext.Builder(handle);
        if (getHandle() != LootTable.EMPTY) {
            // builder.luck(context.getLuck());

            if (context.getLootedEntity() != null) {
                Entity nmsLootedEntity = ((CraftEntity) context.getLootedEntity()).getHandle();
                builder.parameter(LootContextParameters.THIS_ENTITY, nmsLootedEntity);
                builder.parameter(LootContextParameters.DAMAGE_SOURCE, DamageSource.GENERIC);
                builder.parameter(LootContextParameters.ORIGIN, nmsLootedEntity.getPos());
            }

            if (context.getKiller() != null) {
                PlayerEntity nmsKiller = ((CraftHumanEntity) context.getKiller()).getHandle();
                builder.parameter(LootContextParameters.KILLER_ENTITY, nmsKiller);
                // If there is a player killer, damage source should reflect that in case loot tables use that information
                builder.parameter(LootContextParameters.DAMAGE_SOURCE, DamageSource.player(nmsKiller));
                builder.parameter(LootContextParameters.LAST_DAMAGE_PLAYER, nmsKiller); // SPIGOT-5603 - Set minecraft:killed_by_player
            }

            // SPIGOT-5603 - Use LootContext#lootingModifier
            if (context.getLootingModifier() != LootContext.DEFAULT_LOOT_MODIFIER) {
                builder.parameter(IMixinLootContextParameters.LOOTING_MOD, context.getLootingModifier());
            }
        }

        // SPIGOT-5603 - Avoid IllegalArgumentException in LootTableInfo#build()
        LootContextType.Builder nmsBuilder = new LootContextType.Builder();
        for (LootContextParameter<?> param : getHandle().getType().getRequired()) nmsBuilder.require(param);

        for (LootContextParameter<?> param : getHandle().getType().getAllowed())
            if (!getHandle().getType().getRequired().contains(param))
                nmsBuilder.allow(param);
        nmsBuilder.allow(IMixinLootContextParameters.LOOTING_MOD);
        return builder.build(nmsBuilder.build());
    }

    public static LootContext convertContext(net.minecraft.loot.context.LootContext info) {
        Vec3d position = info.get(LootContextParameters.ORIGIN);
        Location location = new Location(((IMixinWorld)info.getWorld()).getWorldImpl(), position.getX(), position.getY(), position.getZ());
        LootContext.Builder contextBuilder = new LootContext.Builder(location);

        if (info.hasParameter(LootContextParameters.KILLER_ENTITY)) {
            CraftEntity killer = ((IMixinEntity) Objects.requireNonNull(info.get(LootContextParameters.KILLER_ENTITY))).getBukkitEntity();
            if (killer instanceof CraftHumanEntity) contextBuilder.killer((CraftHumanEntity) killer);
        }

        if (info.hasParameter(LootContextParameters.THIS_ENTITY))
            contextBuilder.lootedEntity(((IMixinEntity) Objects.requireNonNull(info.get(LootContextParameters.THIS_ENTITY))).getBukkitEntity());

        if (info.hasParameter(IMixinLootContextParameters.LOOTING_MOD))
            contextBuilder.lootingModifier(info.get(IMixinLootContextParameters.LOOTING_MOD));

        contextBuilder.luck(info.getLuck());
        return contextBuilder.build();
    }

    @Override
    public String toString() {
        return getKey().toString();
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof org.bukkit.loot.LootTable)) return false;
        org.bukkit.loot.LootTable table = (org.bukkit.loot.LootTable) obj;
        return table.getKey().equals(this.getKey());
    }

}