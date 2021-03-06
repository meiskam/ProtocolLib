package com.comphenix.protocol.wrappers.nbt.io;

import static org.junit.Assert.*;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.BeforeClass;
import org.junit.Test;

import com.comphenix.protocol.utility.MinecraftReflection;
import com.comphenix.protocol.wrappers.nbt.NbtCompound;
import com.comphenix.protocol.wrappers.nbt.NbtFactory;

public class NbtConfigurationSerializerTest {
	@BeforeClass
	public static void initializeBukkit() {
		// Initialize reflection
		MinecraftReflection.setMinecraftPackage("net.minecraft.server.v1_4_6", "org.bukkit.craftbukkit.v1_4_6");
	}
	
	@SuppressWarnings("unchecked")
	@Test
	public void testSerialization() {
		NbtCompound compound = NbtFactory.ofCompound("hello");
		compound.put("age", (short) 30);
		compound.put("name", "test");
		compound.put("values", new int[] { 1, 2, 3});
		compound.put(NbtFactory.ofList("telephone", "12345678", "81549300"));
		
		compound.put(NbtFactory.ofList("lists", NbtFactory.ofList("", "a", "a", "b", "c")));
		
		YamlConfiguration yaml = new YamlConfiguration();
		NbtConfigurationSerializer.DEFAULT.serialize(compound, yaml);
		
		NbtCompound result = NbtConfigurationSerializer.DEFAULT.deserializeCompound(yaml, "hello");
		
		assertEquals(compound, result);
	}
}
