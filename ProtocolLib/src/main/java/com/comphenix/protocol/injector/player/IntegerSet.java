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

package com.comphenix.protocol.injector.player;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Represents a very quick integer set that uses a lookup table to store membership.
 * <p>
 * This class is intentionally missing a size method. 
 * @author Kristian
 */
class IntegerSet {
	private final boolean[] array;

	/**
	 * Initialize a lookup table with the given maximum number of elements.
	 * <p>
	 * This creates a set for elements in the range [0, count).
	 * <p>
	 * Formally, the current set must be a subset of [0, 1, 2, ..., count - 1].
	 * @param maximumCount - maximum element value and count.
	 */
	public IntegerSet(int maximumCount) {
		this.array = new boolean[maximumCount];
	}
	
	/**
	 * Determine whether or not the given element exists in the set.
	 * @param value - the element to check. Must be in the range [0, count).
	 * @return TRUE if the given element exists, FALSE otherwise.
	 */
	public boolean contains(int element) {
		return array[element];
	}
	
	/**
	 * Add the given element to the set, or do nothing if it already exists.
	 * @param element - element to add.
	 * @throws OutOfBoundsException If the given element is not in the range [0, count).
	 */
	public void add(int element) {
		array[element] = true;
	}
	
	/**
	 * Remove the given element from the set, or do nothing if it's already removed.
	 * @param element - element to remove.
	 */
	public void remove(int element) {
		// We don't actually care if the caller tries to remove an element outside the valid set
		if (element >= 0 && element < array.length)
			array[element] = false;
	}
	
	/**
	 * Remove every element from the set.
	 */
	public void clear() {
		Arrays.fill(array, false);
	}
	
	/**
	 * Convert the current IntegerSet to an equivalent HashSet.
	 * @return The resulting HashSet.
	 */
	public Set<Integer> toSet() {
		Set<Integer> elements = new HashSet<Integer>();
		
		for (int i = 0; i < array.length; i++) {
			if (array[i])
				elements.add(i);
		}
		return elements;
	}
}
