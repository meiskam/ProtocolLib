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

import com.comphenix.protocol.events.PacketEvent;

/**
 * Represents an object that initiate the packet listeners.
 * 
 * @author Kristian
 */
public interface ListenerInvoker {

	/**
	 * Invokes the given packet event for every registered listener.
	 * @param event - the packet event to invoke.
	 */
	public abstract void invokePacketRecieving(PacketEvent event);

	/**
	 * Invokes the given packet event for every registered listener.
	 * @param event - the packet event to invoke.
	 */
	public abstract void invokePacketSending(PacketEvent event);

	/**
	 * Retrieve the associated ID of a packet.
	 * @param packet - the packet.
	 * @return The packet ID.
	 */
	public abstract int getPacketID(Object packet);

	/**
	 * Associate a given class with the given packet ID. Internal method.
	 * @param clazz - class to associate.
	 */
	public abstract void unregisterPacketClass(Class<?> clazz);

	/**
	 * Remove a given class from the packet registry. Internal method.
	 * @param clazz - class to remove.
	 */
	public abstract void registerPacketClass(Class<?> clazz, int packetID);

	/**
	 * Retrieves the correct packet class from a given packet ID.
	 * @param packetID - the packet ID.
 	 * @param forceVanilla - whether or not to look for vanilla classes, not injected classes.
	 * @return The associated class.
	 */
	public abstract Class<?> getPacketClassFromID(int packetID, boolean forceVanilla);
}