// $Id$
/*
 * WorldEdit
 * Copyright (C) 2010 sk89q <http://www.sk89q.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
*/

package com.sk89q.worldedit.snapshots;

import com.sk89q.worldedit.data.*;
import java.io.*;
import java.util.logging.Logger;

/**
 *
 * @author sk89q
 */
public class Snapshot {
    protected static Logger logger = Logger.getLogger("Minecraft.WorldEdit");
    
    /**
     * Stores snapshot file.
     */
    private File file;
    /**
     * Name of the snapshot;
     */
    private String name;

    /**
     * Construct a snapshot restoration operation.
     * 
     * @param repo
     * @param snapshot
     */
    public Snapshot(SnapshotRepository repo, String snapshot) {
        file = new File(repo.getDirectory(), snapshot);
        name = snapshot;
    }

    /**
     * Get a chunk store.
     * 
     * @return
     * @throws IOException
     * @throws DataException 
     */
    public ChunkStore getChunkStore() throws IOException, DataException {
        ChunkStore chunkStore = _getChunkStore();
        
        logger.info("WorldEdit: Using " + chunkStore.getClass().getCanonicalName()
                + " for loading snapshot '" + file.getAbsolutePath() + "'");
        
        return chunkStore;
    }

    /**
     * Get a chunk store.
     * 
     * @return
     * @throws IOException
     * @throws DataException 
     */
    public ChunkStore _getChunkStore() throws IOException, DataException {
        if (file.getName().toLowerCase().endsWith(".zip")) {
            try {
                ChunkStore chunkStore = new TrueZipMcRegionChunkStore(file);
                
                if (!chunkStore.isValid()) {
                    return new TrueZipLegacyChunkStore(file);
                }
                
                return chunkStore;
            } catch (NoClassDefFoundError e) {
                ChunkStore chunkStore = new ZippedMcRegionChunkStore(file);
                
                if (!chunkStore.isValid()) {
                    return new ZippedLegacyChunkStore(file);
                }
                
                return chunkStore;
            }
        } else if (file.getName().toLowerCase().endsWith(".tar.bz2")
                || file.getName().toLowerCase().endsWith(".tar.gz")
                || file.getName().toLowerCase().endsWith(".tar")) {
            try {
                ChunkStore chunkStore = new TrueZipMcRegionChunkStore(file);
                
                if (!chunkStore.isValid()) {
                    return new TrueZipLegacyChunkStore(file);
                }
                
                return chunkStore;
            } catch (NoClassDefFoundError e) {
                throw new DataException("TrueZIP is required for .tar support");
            }
        } else {
            ChunkStore chunkStore = new FileMcRegionChunkStore(file);
            
            if (!chunkStore.isValid()) {
                return new FileLegacyChunkStore(file);
            }
            
            return chunkStore;
        }
    }

    /**
     * Get the snapshot's name.
     * 
     * @return
     */
    public String getName() {
        return name;
    }
}
