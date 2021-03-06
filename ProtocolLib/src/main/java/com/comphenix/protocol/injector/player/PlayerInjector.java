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

import java.io.DataInputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.Socket;
import java.net.SocketAddress;

import net.sf.cglib.proxy.Factory;

import org.bukkit.entity.Player;

import com.comphenix.protocol.Packets;
import com.comphenix.protocol.error.ErrorReporter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.events.PacketListener;
import com.comphenix.protocol.injector.BukkitUnwrapper;
import com.comphenix.protocol.injector.GamePhase;
import com.comphenix.protocol.injector.ListenerInvoker;
import com.comphenix.protocol.injector.PacketFilterManager.PlayerInjectHooks;
import com.comphenix.protocol.reflect.FieldUtils;
import com.comphenix.protocol.reflect.FuzzyReflection;
import com.comphenix.protocol.reflect.StructureModifier;
import com.comphenix.protocol.reflect.VolatileField;
import com.comphenix.protocol.utility.MinecraftReflection;

abstract class PlayerInjector {

	// Net login handler stuff
	private static Field netLoginNetworkField;
	
	// Different disconnect methods
	private static Method loginDisconnect;
	private static Method serverDisconnect;
	
	// Cache previously retrieved fields
	protected static Field serverHandlerField;
	protected static Field proxyServerField;

	protected static Field networkManagerField;
	protected static Field inputField;
	protected static Field netHandlerField;
	protected static Field socketField;
	
	private static Field entityPlayerField;
	
	// Whether or not we're using a proxy type
	private static boolean hasProxyType;
	
	// To add our injected array lists
	protected static StructureModifier<Object> networkModifier;
	
	// And methods
	protected static Method queueMethod;
	protected static Method processMethod;
		
	protected Player player;
	protected boolean hasInitialized;
	
	// Reference to the player's network manager
	protected VolatileField networkManagerRef;
	protected VolatileField serverHandlerRef;
	protected Object networkManager;
	
	// Current net handler
	protected Object loginHandler;
	protected Object serverHandler;
	protected Object netHandler;
	
	// Current socket
	protected Socket socket;
	
	// The packet manager and filters
	protected ListenerInvoker invoker;
	
	// Previous data input
	protected DataInputStream cachedInput;
	
	// Handle errors
	protected ErrorReporter reporter;

	// Scheduled action on the next packet event
	protected Runnable scheduledAction;

	// Whether or not the injector has been cleaned
	private boolean clean;
	
	// Whether or not to update the current player on the first Packet1Login
	boolean updateOnLogin;
	Player updatedPlayer;
	
	public PlayerInjector(ErrorReporter reporter, Player player, ListenerInvoker invoker) throws IllegalAccessException {
		this.reporter = reporter;
		this.player = player;
		this.invoker = invoker;
	}

	/**
	 * Retrieve the notch (NMS) entity player object.
	 * @param player - the player to retrieve.
	 * @return Notch player object.
	 */
	protected Object getEntityPlayer(Player player) {
		BukkitUnwrapper unwrapper = new BukkitUnwrapper();
		return unwrapper.unwrapItem(player);
	}
	
	/**
	 * Initialize all fields for this player injector, if it hasn't already.
	 * @throws IllegalAccessException An error has occured.
	 */
	public void initialize(Object injectionSource) throws IllegalAccessException {
		if (injectionSource == null)
			throw new IllegalArgumentException("injectionSource cannot be NULL");
		
		//Dispatch to the correct injection method
		if (injectionSource instanceof Player)
			initializePlayer(injectionSource);
		else if (MinecraftReflection.isLoginHandler(injectionSource))
			initializeLogin(injectionSource);
		else 
			throw new IllegalArgumentException("Cannot initialize a player hook using a " + injectionSource.getClass().getName());
	}
	
	/**
	 * Initialize the player injector using an actual player instance.
	 * @param player - the player to hook.
	 */
	public void initializePlayer(Object player) {
		
		Object notchEntity = getEntityPlayer((Player) player);
		
		if (!hasInitialized) {
			// Do this first, in case we encounter an exception
			hasInitialized = true;
			
			// Retrieve the server handler
			if (serverHandlerField == null) {
				serverHandlerField = FuzzyReflection.fromObject(notchEntity).getFieldByType(
									  "NetServerHandler", MinecraftReflection.getNetServerHandlerClass());
				proxyServerField = getProxyField(notchEntity, serverHandlerField);
			}
			
			// Yo dawg
			serverHandlerRef = new VolatileField(serverHandlerField, notchEntity);
			serverHandler = serverHandlerRef.getValue();

			// Next, get the network manager 
			if (networkManagerField == null) 
				networkManagerField = FuzzyReflection.fromObject(serverHandler).
										getFieldByType(".*" + MinecraftReflection.getNetworkManagerName());
			initializeNetworkManager(networkManagerField, serverHandler);
		}
	}
	
	/**
	 * Initialize the player injector for a NetLoginHandler instead.
	 * @param netLoginHandler - the net login handler to inject.
	 */
	public void initializeLogin(Object netLoginHandler) {
		if (!hasInitialized) {
			hasInitialized = true;
			loginHandler = netLoginHandler;
			
			if (netLoginNetworkField == null)
				netLoginNetworkField =  FuzzyReflection.fromObject(netLoginHandler).
										  getFieldByType(".*" + MinecraftReflection.getNetworkManagerName());
			initializeNetworkManager(netLoginNetworkField, netLoginHandler);
		}
	}
	
	private void initializeNetworkManager(Field reference, Object container) {
		networkManagerRef = new VolatileField(reference, container);
		networkManager = networkManagerRef.getValue();
		
		// No, don't do it
		if (networkManager instanceof Factory) {
			return;
		}
		
		// Create the network manager modifier from the actual object type
		if (networkManager != null && networkModifier == null)
			networkModifier = new StructureModifier<Object>(networkManager.getClass(), null, false);
		
		// And the queue method
		if (queueMethod == null)
			queueMethod = FuzzyReflection.fromClass(reference.getType()).
							getMethodByParameters("queue", MinecraftReflection.getPacketClass());
		
		// And the data input stream that we'll use to identify a player
		if (inputField == null)
			inputField = FuzzyReflection.fromObject(networkManager, true).
							getFieldByType("java\\.io\\.DataInputStream");
	}
	
	/**
	 * Retrieve whether or not the server handler is a proxy object.
	 * @return TRUE if it is, FALSE otherwise.
	 */
	protected boolean hasProxyServerHandler() {
		return hasProxyType;
	}
	
	/**
	 * Retrieve the current network manager.
	 * @return Current network manager.
	 */
	public Object getNetworkManager() {
		return networkManagerRef.getValue();
	}
	
	/**
	 * Set the current network manager.
	 * @param value - new network manager.
	 * @param force - whether or not to save this value.
	 */
	public void setNetworkManager(Object value, boolean force) {
		networkManagerRef.setValue(value);
		
		if (force)
			networkManagerRef.saveValue();
		initializeNetworkManager(networkManagerField, serverHandler);
	}
	
	/**
	 * Retrieve the associated socket of this player.
	 * @return The associated socket.
	 * @throws IllegalAccessException If we're unable to read the socket field.
	 */
	public Socket getSocket() throws IllegalAccessException {
		try {
			if (socketField == null)
				socketField = FuzzyReflection.fromObject(networkManager).getFieldListByType(Socket.class).get(0);
			if (socket == null)
				socket = (Socket) FieldUtils.readField(socketField, networkManager);
			return socket;
			
		} catch (IndexOutOfBoundsException e) {
			throw new IllegalAccessException("Unable to read the socket field.");
		}
	}
	
	/**
	 * Retrieve the associated address of this player.
	 * @return The associated address.
	 * @throws IllegalAccessException If we're unable to read the socket field.
	 */
	public SocketAddress getAddress() throws IllegalAccessException {
		Socket socket = getSocket();
		
		// Guard against NULL
		if (socket != null)
			return socket.getRemoteSocketAddress();
		else
			return null;
	}
	
	/**
	 * Attempt to disconnect the current client.
	 * @param message - the message to display.
	 * @throws InvocationTargetException If disconnection failed.
	 */
	public void disconnect(String message) throws InvocationTargetException {
		// Get a non-null handler
		boolean usingNetServer = serverHandler != null;
		
		Object handler = usingNetServer ? serverHandler : loginHandler;
		Method disconnect = usingNetServer ? serverDisconnect : loginDisconnect;
		
		// Execute disconnect on it
		if (handler != null) {
			if (disconnect == null) {
				disconnect = FuzzyReflection.fromObject(handler).getMethodByName("disconnect.*");
				
				// Save the method for later
				if (usingNetServer)
					serverDisconnect = disconnect;
				else
					loginDisconnect = disconnect;
			}
			
			try {
				disconnect.invoke(handler, message);
				return;
			} catch (IllegalArgumentException e) {
				reporter.reportDetailed(this, "Invalid argument passed to disconnect method: " + message, e, handler);
			} catch (IllegalAccessException e) {
				reporter.reportWarning(this, "Unable to access disconnect method.", e);
			}
		}
		
		// Fuck it
		try {
			Socket socket = getSocket();
			
			try {
				socket.close();
			} catch (IOException e) {
				reporter.reportDetailed(this, "Unable to close socket.", e, socket);
			}
			
		} catch (IllegalAccessException e) {
			reporter.reportWarning(this, "Insufficient permissions. Cannot close socket.", e);
		}
	}
	
	private Field getProxyField(Object notchEntity, Field serverField) {

		try {
			Object handler = FieldUtils.readField(serverHandlerField, notchEntity, true);
			
			// Is this a Minecraft hook?
			if (handler != null && !handler.getClass().getName().startsWith("net.minecraft.server")) {
				
				// This is our proxy object
				if (handler instanceof Factory)
					return null;
				
				hasProxyType = true;
				reporter.reportWarning(this, "Detected server handler proxy type by another plugin. Conflict may occur!");
				
				// No? Is it a Proxy type?
				try {
					FuzzyReflection reflection = FuzzyReflection.fromObject(handler, true);
					
					// It might be
					return reflection.getFieldByType("NetServerHandler", MinecraftReflection.getNetServerHandlerClass());
					
				} catch (RuntimeException e) {
					// Damn
				}
			}
			
		} catch (IllegalAccessException e) {
			reporter.reportWarning(this, "Unable to load server handler from proxy type.");
		}

		// Nope, just go with it
		return null;
	}
	
	/**
	 * Retrieves the current net handler for this player.
	 * @return Current net handler.
	 * @throws IllegalAccessException Unable to find or retrieve net handler.
	 */
	protected Object getNetHandler() throws IllegalAccessException {
		
		// What a mess
		try {
			if (netHandlerField == null)
				netHandlerField = FuzzyReflection.fromClass(networkManager.getClass(), true).
									getFieldByType("NetHandler", MinecraftReflection.getNetHandlerClass());
		} catch (RuntimeException e1) {
			// Swallow it
		}
		
		// Second attempt
		if (netHandlerField == null) {
			try {
				// Well, that sucks. Try just Minecraft objects then.
				netHandlerField = FuzzyReflection.fromClass(networkManager.getClass(), true).
									 getFieldByType(MinecraftReflection.MINECRAFT_OBJECT);
				
			} catch (RuntimeException e2) {
				throw new IllegalAccessException("Cannot locate net handler. " + e2.getMessage());
			}
		}
		
		// Get the handler
		if (netHandler == null)
			netHandler = FieldUtils.readField(netHandlerField, networkManager, true);
		return netHandler;
	}
	
	/**
	 * Retrieve the stored entity player from a given NetHandler.
	 * @param netHandler - the nethandler to retrieve it from.
	 * @return The stored entity player.
	 * @throws IllegalAccessException If the reflection failed.
	 */
	private Object getEntityPlayer(Object netHandler) throws IllegalAccessException {
		if (entityPlayerField == null)
			entityPlayerField = FuzzyReflection.fromObject(netHandler).getFieldByType(
								 "EntityPlayer", MinecraftReflection.getEntityPlayerClass());
		return FieldUtils.readField(entityPlayerField, netHandler);
	}
	
	/**
	 * Processes the given packet as if it was transmitted by the current player.
	 * @param packet - packet to process.
	 * @throws IllegalAccessException If the reflection machinery failed.
	 * @throws InvocationTargetException If the underlying method caused an error.
	 */
	public void processPacket(Object packet) throws IllegalAccessException, InvocationTargetException {
		
		Object netHandler = getNetHandler();
		
		// Get the process method
		if (processMethod == null) {
			try {
				processMethod = FuzzyReflection.fromClass(MinecraftReflection.getPacketClass()).
								  getMethodByParameters("processPacket", netHandlerField.getType());
			} catch (RuntimeException e) {
				throw new IllegalArgumentException("Cannot locate process packet method: " + e.getMessage());
			}
		}
	
		// We're ready
		try {
			processMethod.invoke(packet, netHandler);
		} catch (IllegalArgumentException e) {
			throw new IllegalArgumentException("Method " + processMethod.getName() + " is not compatible.");
		} catch (InvocationTargetException e) {
			throw e;
		}
	}
	
	/**
	 * Send a packet to the client.
	 * @param packet - server packet to send.
	 * @param filtered - whether or not the packet will be filtered by our listeners.
	 * @param InvocationTargetException If an error occured when sending the packet.
	 */
	public abstract void sendServerPacket(Object packet, boolean filtered) throws InvocationTargetException;
	
	/**
	 * Inject a hook to catch packets sent to the current player.
	 */
	public abstract void injectManager();
	
	/**
	 * Remove all hooks and modifications.
	 */
	public final void cleanupAll() {
		if (!clean)
			cleanHook();
		clean = true;
	}

	/**
	 * Clean up after the player has disconnected.
	 */
	public abstract void handleDisconnect();
	
	/**
	 * Override to add custom cleanup behavior.
	 */
	protected abstract void cleanHook();
	
	/**
	 * Determine whether or not this hook has already been cleaned.
	 * @return TRUE if it has, FALSE otherwise.
	 */
	public boolean isClean() {
		return clean;
	}
	
	/**
	 * Determine if this inject method can even be attempted.
	 * @return TRUE if can be attempted, though possibly with failure, FALSE otherwise.
	 */
	public abstract boolean canInject(GamePhase state);
	
	/**
	 * Retrieve the hook type this class represents.
	 * @return Hook type this class represents.
	 */
	public abstract PlayerInjectHooks getHookType();
	
	/**
	 * Invoked before a new listener is registered.
	 * <p>
	 * The player injector should only return a non-null value if some or all of the packet IDs are unsupported.
	 * 
	 * @param listener - the listener that is about to be registered.
	 * @return A error message with the unsupported packet IDs, or NULL if this listener is valid.
	 */
	public abstract UnsupportedListener checkListener(PacketListener listener);
	
	/**
	 * Allows a packet to be sent by the listeners.
	 * @param packet - packet to sent.
	 * @return The given packet, or the packet replaced by the listeners.
	 */
	public Object handlePacketSending(Object packet) {
		try {
			// Get the packet ID too
			Integer id = invoker.getPacketID(packet);
			Player currentPlayer = player;
			
			// Hack #1: Handle a single scheduled action
			if (scheduledAction != null) {
				scheduledAction.run();
				scheduledAction = null;
			}
			// Hack #2
			if (updateOnLogin) {
				if (id == Packets.Server.LOGIN) {
					try {
						updatedPlayer = (Player) MinecraftReflection.getBukkitEntity(getEntityPlayer(getNetHandler()));
					} catch (IllegalAccessException e) {
						reporter.reportDetailed(this, "Cannot update player in PlayerEvent.", e, packet);
					}
				}
				
				// This will only occur in the NetLoginHandler injection
				if (updatedPlayer != null)
					currentPlayer = updatedPlayer;
			}
			
			// Make sure we're listening
			if (id != null && hasListener(id)) {
				// A packet has been sent guys!
				PacketContainer container = new PacketContainer(id, packet);
				PacketEvent event = PacketEvent.fromServer(invoker, container, currentPlayer);
				invoker.invokePacketSending(event);
				
				// Cancelling is pretty simple. Just ignore the packet.
				if (event.isCancelled())
					return null;
				
				// Right, remember to replace the packet again
				return event.getPacket().getHandle();
			}
			
		} catch (Throwable e) {
			reporter.reportDetailed(this, "Cannot handle server packet.", e, packet);
		}
		
		return packet;
	}
	
	/**
	 * Determine if the given injector is listening for this packet ID.
	 * @param packetID - packet ID to check.
	 * @return TRUE if it is, FALSE oterhwise.
	 */
	protected abstract boolean hasListener(int packetID);
	
	/**
	 * Retrieve the current player's input stream.
	 * @param cache - whether or not to cache the result of this method.
	 * @return The player's input stream.
	 */
	public DataInputStream getInputStream(boolean cache) {
		if (inputField == null)
			throw new IllegalStateException("Input field is NULL.");
		if (networkManager == null)
				throw new IllegalStateException("Network manager is NULL.");
		
		// Get the associated input stream
		try {
			if (cache && cachedInput != null)
				return cachedInput;
			
			// Save to cache
			cachedInput = (DataInputStream) FieldUtils.readField(inputField, networkManager, true);
			return cachedInput;
			
		} catch (IllegalAccessException e) {
			throw new RuntimeException("Unable to read input stream.", e);
		}
	}
	
	/**
	 * Schedule an action to occur on the next sent packet.
	 * @param action - action to execute.
	 */
	public void scheduleAction(Runnable action) {
		scheduledAction = action;
	}

	/**
	 * Retrieve the hooked player.
	 */
	public Player getPlayer() {
		return player;
	}
	
	/**
	 * Object that can invoke the packet events.
	 * @return Packet event invoker.
	 */
	public ListenerInvoker getInvoker() {
		return invoker;
	}
	
	/**
	 * Retrieve the hooked player object OR the more up-to-date player instance.
	 * @return The hooked player, or a more up-to-date instance.
	 */
	public Player getUpdatedPlayer() {
		if (updatedPlayer != null)
			return updatedPlayer;
		else
			return player;
	}
}
