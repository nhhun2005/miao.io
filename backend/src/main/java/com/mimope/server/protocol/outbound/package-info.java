/**
 * Server → Client outbound message DTOs.
 * <p>
 * Each record corresponds to a specific {@code "type"} value in the JSON
 * message envelope and provides a {@code toMap()} method for serialisation
 * via {@link com.mimope.server.websocket.MessageEncoder}.
 */
package com.mimope.server.protocol.outbound;
