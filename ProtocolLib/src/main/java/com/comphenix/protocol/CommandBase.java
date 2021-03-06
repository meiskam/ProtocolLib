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

package com.comphenix.protocol;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import com.comphenix.protocol.error.ErrorReporter;

/**
 * Base class for all our commands.
 * 
 * @author Kristian
 */
abstract class CommandBase implements CommandExecutor {

	public static final String PERMISSION_ADMIN = "protocol.admin";
	
	private String permission;
	private String name;
	private int minimumArgumentCount;
	
	protected ErrorReporter reporter;
	
	public CommandBase(ErrorReporter reporter, String permission, String name) {
		this(reporter, permission, name, 0);
	}
	
	public CommandBase(ErrorReporter reporter, String permission, String name, int minimumArgumentCount) {
		this.reporter = reporter;
		this.name = name;
		this.permission = permission;
		this.minimumArgumentCount = minimumArgumentCount;
	}

	@Override
	public final boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		try {
			// Make sure we're dealing with the correct command
			if (!command.getName().equalsIgnoreCase(name)) {
				return false;
			}
			if (permission != null && !sender.hasPermission(permission)) {
				sender.sendMessage(ChatColor.RED + "You haven't got permission to run this command.");
				return true;
			}
			
			// Check argument length
			if (args != null && args.length >= minimumArgumentCount) {
				return handleCommand(sender, args);
			} else {
				return false;
			}
			
		} catch (Exception e) {
			reporter.reportDetailed(this, "Cannot execute command " + name, e, sender, label, args);
			return true;
		}
	}
	
	/**
	 * Retrieve the permission necessary to execute this command.
	 * @return The permission, or NULL if not needed.
	 */
	public String getPermission() {
		return permission;
	}
	
	/**
	 * Retrieve the primary name of this command.
	 * @return Primary name.
	 */
	public String getName() {
		return name;
	}
	
	/**
	 * Retrieve the error reporter.
	 * @return Error reporter.
	 */
	protected ErrorReporter getReporter() {
		return reporter;
	}
	
	/**
	 * Main implementation of this command.
	 * @param sender - command sender.
	 * @param args
	 * @return
	 */
	protected abstract boolean handleCommand(CommandSender sender, String[] args);
}
