/**
 * Client → Server inbound message DTOs.
 * <p>
 * Each record corresponds to a specific {@code "type"} value in the JSON
 * message envelope and provides a {@code from(InboundMessage)} factory
 * method for safe parsing.
 */
package com.mimope.server.protocol.inbound;
