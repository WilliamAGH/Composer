package com.composerai.api.service.email;

import java.util.List;

/**
 * ChunkingStrategy defines how long text is split into embedding-ready chunks
 * Provide one simple default implementation elsewhere as needed
 * 
 * @author William Callahan
 * @since 2025-09-18
 * @version 0.0.1
 */
public interface ChunkingStrategy {
    List<String> chunk(String text);
}


