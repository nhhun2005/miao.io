/**
 * Shared protocol definitions for client ↔ server communication.
 * <p>
 * This package contains typed DTOs for every message that flows over
 * the WebSocket connection. Both inbound (client → server) and outbound
 * (server → client) message types are defined here with a shared
 * {@link com.mimope.server.protocol.ProtocolConstants#PROTOCOL_VERSION protocol version}.
 */
package com.mimope.server.protocol;
