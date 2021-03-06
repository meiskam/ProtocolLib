/*
 *  ProtocolLib - Bukkit server library that allows access to the Minecraft protocol.
 *  Copyright (C) 2012 Kristian S. Stangeland
 *
 *  This program is free software; you can redistribute it and/or modify it under the terms of the 
 *  GNU General Public License as published by the Free Software Foundation; either version 2 of 
 *  the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; 
 *  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. 
 *  See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with this program; 
 *  if not, write to the Free Software Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 
 *  02111-1307 USA
 */

package com.comphenix.protocol.injector;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.List;

import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.reflect.FieldAccessException;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.primitives.Primitives;

/**
 * A packet constructor that uses an internal Minecraft.
 * @author Kristian
 *
 */
public class PacketConstructor {

	/**
	 * A packet constructor that automatically converts Bukkit types to their NMS conterpart. 
	 * <p>
	 * Remember to call withPacket().
	 */
	public static PacketConstructor DEFAULT = new PacketConstructor(null);
	
	// The constructor method that's actually responsible for creating the packet
	private Constructor<?> constructorMethod;
	
	// The packet ID
	private int packetID;
	
	// Used to unwrap Bukkit objects
	private List<Unwrapper> unwrappers;
	
	private PacketConstructor(Constructor<?> constructorMethod) {
		this.constructorMethod = constructorMethod;
		this.unwrappers = Lists.newArrayList((Unwrapper) new BukkitUnwrapper());
	}
	
	private PacketConstructor(int packetID, Constructor<?> constructorMethod, List<Unwrapper> unwrappers) {
		this.packetID = packetID;
		this.constructorMethod = constructorMethod;
		this.unwrappers = unwrappers;
	}
	
	public ImmutableList<Unwrapper> getUnwrappers() {
		return ImmutableList.copyOf(unwrappers);
	}
	
	/**
	 * Retrieve the id of the packets this constructor creates.
	 * @return The ID of the packets this constructor will create.
	 */
	public int getPacketID() {
		return packetID;
	}
	
	/**
	 * Return a copy of the current constructor with a different list of unwrappers.
	 * @param unwrappers - list of unwrappers that convert Bukkit wrappers into the equivalent NMS classes.
	 * @return A constructor with a different set of unwrappers.
	 */
	public PacketConstructor withUnwrappers(List<Unwrapper> unwrappers) {
		return new PacketConstructor(packetID, constructorMethod, unwrappers);
	}

	/**
	 * Create a packet constructor that creates packets using the given types.
	 * @param id - packet ID.
	 * @param values - types to create.
	 * @return A packet constructor with these types.
	 * @throws IllegalArgumentException If no packet constructor could be created with these types.
	 */
	public PacketConstructor withPacket(int id, Object[] values) {
		
		Class<?>[] types = new Class<?>[values.length];
		
		for (int i = 0; i < types.length; i++) {
			// Default type
			if (values[i] != null) {
				types[i] = values[i].getClass();
				
				for (Unwrapper unwrapper : unwrappers) {
					Object result = unwrapper.unwrapItem(values[i]);
					
					// Update type we're searching for
					if (result != null) {
						types[i] = result.getClass();
						break;
					}
				}
			
			} else {
				// Try it
				types[i] = Object.class;
			}
		}
		
		Class<?> packetType = MinecraftRegistry.getPacketClassFromID(id, true);
		
		if (packetType == null)
			throw new IllegalArgumentException("Could not find a packet by the id " + id);
		
		// Find the correct constructor
		for (Constructor<?> constructor : packetType.getConstructors()) {
			Class<?>[] params = constructor.getParameterTypes();

			if (isCompatible(types, params)) {
				// Right, we've found our type
				return new PacketConstructor(id, constructor, unwrappers);
			}
		}
		
		throw new IllegalArgumentException("No suitable constructor could be found.");
	}
	
	/**
	 * Construct a packet using the special builtin Minecraft constructors.
	 * @param values - values containing Bukkit wrapped items to pass to Minecraft.
	 * @return The created packet.
	 * @throws FieldAccessException Failure due to a security limitation.
	 * @throws IllegalArgumentException Arguments doesn't match the constructor.
	 * @throws RuntimeException Minecraft threw an exception.
	 */
	public PacketContainer createPacket(Object... values) throws FieldAccessException {
		
		try {
			// Convert types
			for (int i = 0; i < values.length; i++) {
				for (Unwrapper unwrapper : unwrappers) {
					Object converted = unwrapper.unwrapItem(values[i]);
					
					if (converted != null) {
						values[i] = converted;
						break;
					}
				}
			}
			
			Object nmsPacket = constructorMethod.newInstance(values);
			return new PacketContainer(packetID, nmsPacket);
			
		} catch (IllegalArgumentException e) {
			throw e;
		} catch (InstantiationException e) {
			throw new FieldAccessException("Cannot construct an abstract packet.", e);
		} catch (IllegalAccessException e) {
			throw new FieldAccessException("Cannot construct packet due to a security limitation.", e);
		} catch (InvocationTargetException e) {
			throw new RuntimeException("Minecraft error.", e);
		}
	}
	
	// Determine if a method with the types 'params' can be called with 'types'
	private static boolean isCompatible(Class<?>[] types, Class<?>[] params) {
		
		// Determine if the types are similar
		if (params.length == types.length) {
			for (int i = 0; i < params.length; i++) {
				Class<?> inputType = types[i];
				Class<?> paramType = params[i];
				
				// The input type is always wrapped
				if (paramType.isPrimitive()) {
					// Wrap it
					paramType = Primitives.wrap(paramType);
				}
				
				// Compare assignability
				if (!paramType.isAssignableFrom(inputType)) {
					return false;
				}
			}
			
			return true;
		}
		
		// Parameter count must match
		return false;
	}

	/**
	 * Represents a unwrapper for a constructor parameter.
	 * 
	 * @author Kristian
	 */
	public static interface Unwrapper {
		/**
		 * Convert the given wrapped object to the equivalent net.minecraft.server object.
		 * @param wrappedObject - wrapped object.
		 * @return The net.minecraft.server object.
		 */
		public Object unwrapItem(Object wrappedObject);
	}
}
