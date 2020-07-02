package com.boydti.fawe.bukkit.regions.plotsquared;

import com.boydti.fawe.util.EditSessionBuilder;
import com.boydti.fawe.util.TaskManager;
import com.plotsquared.core.configuration.Settings;
import com.plotsquared.core.generator.HybridPlotManager;
import com.plotsquared.core.generator.HybridPlotWorld;
import com.plotsquared.core.location.Location;
import com.plotsquared.core.plot.Plot;
import com.plotsquared.core.plot.PlotArea;
import com.plotsquared.core.plot.PlotAreaTerrainType;
import com.plotsquared.core.plot.PlotAreaType;
import com.plotsquared.core.plot.PlotManager;
import com.plotsquared.core.util.RegionManager;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.MaxChangedBlocksException;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.function.FlatRegionFunction;
import com.sk89q.worldedit.function.biome.BiomeReplace;
import com.sk89q.worldedit.function.operation.ForwardExtentCopy;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.function.pattern.Pattern;
import com.sk89q.worldedit.function.visitor.FlatRegionVisitor;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.world.World;
import com.sk89q.worldedit.world.biome.BiomeType;
import com.sk89q.worldedit.world.block.BlockState;
import com.sk89q.worldedit.world.block.BlockTypes;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Set;

import static org.bukkit.Bukkit.getWorld;

public class FaweRegionManager extends RegionManager {

    private RegionManager parent;

    public FaweRegionManager(RegionManager parent) {
        this.parent = parent;
    }

    @Override
    public int[] countEntities(Plot plot) {
        return parent.countEntities(plot);
    }

    @Override
    public void clearAllEntities(Location pos1, Location pos2) {
        parent.clearAllEntities(pos1, pos2);
    }

    @Override
    public boolean setCuboids(final PlotArea area, final Set<CuboidRegion> regions, final Pattern blocks, final int minY, final int maxY) {
        TaskManager.IMP.async(() -> {
            synchronized (FaweRegionManager.class) {
                World world = BukkitAdapter.adapt(getWorld(area.getWorldName()));
                EditSession session = new EditSessionBuilder(world).checkMemory(false).fastmode(true).limitUnlimited().changeSetNull().autoQueue(false).build();
                for (CuboidRegion region : regions) {
                    region.setPos1(region.getPos1().withY(minY));
                    region.setPos2(region.getPos2().withY(maxY));
                    session.setBlocks((Region) region, blocks);
                }
                try {
                    session.flushQueue();
                } catch (MaxChangedBlocksException e) {
                    e.printStackTrace();
                }
            }
        });
        return true;
    }

    @Override
    public boolean notifyClear(PlotManager manager) {
        if (!(manager instanceof HybridPlotManager)) {
            return false;
        }
        final HybridPlotWorld hpw = ((HybridPlotManager) manager).getHybridPlotWorld();
        return hpw.getType() != PlotAreaType.AUGMENTED || hpw.getTerrain() == PlotAreaTerrainType.NONE;
    }

    @Override
    public boolean handleClear(final Plot plot, final Runnable whenDone, final PlotManager manager) {
        if (!(manager instanceof HybridPlotManager)) {
            return false;
        }
        TaskManager.IMP.async(() -> {
            synchronized (FaweRegionManager.class) {
                final HybridPlotWorld hybridPlotWorld = ((HybridPlotManager) manager).getHybridPlotWorld();
                EditSession editSession = new EditSessionBuilder(BukkitAdapter.adapt(getWorld(hybridPlotWorld.getWorldName()))).checkMemory(false).fastmode(true).limitUnlimited().changeSetNull().autoQueue(false).build();

                if (!hybridPlotWorld.PLOT_SCHEMATIC || !Settings.Schematics.PASTE_ON_TOP) {
                    final BlockState bedrock;
                    final BlockState air = BlockTypes.AIR.getDefaultState();
                    if (hybridPlotWorld.PLOT_BEDROCK) {
                        bedrock = BlockTypes.BEDROCK.getDefaultState();
                    } else {
                        bedrock = air;
                    }

                    final Pattern filling = hybridPlotWorld.MAIN_BLOCK.toPattern();
                    final Pattern plotfloor = hybridPlotWorld.TOP_BLOCK.toPattern();

                    BlockVector3 pos1 = plot.getBottomAbs().getBlockVector3();
                    BlockVector3 pos2 = plot.getExtendedTopAbs().getBlockVector3();

                    Region bedrockRegion = new CuboidRegion(pos1.withY(0), pos2.withY(0));
                    Region fillingRegion = new CuboidRegion(pos1.withY(1), pos2.withY(hybridPlotWorld.PLOT_HEIGHT - 1));
                    Region floorRegion = new CuboidRegion(pos1.withY(hybridPlotWorld.PLOT_HEIGHT),
                        pos2.withY(hybridPlotWorld.PLOT_HEIGHT));
                    Region airRegion = new CuboidRegion(pos1.withY(hybridPlotWorld.PLOT_HEIGHT + 1),
                        pos2.withY(manager.getWorldHeight()));

                    editSession.setBlocks(bedrockRegion, bedrock);
                    editSession.setBlocks(fillingRegion, filling);
                    editSession.setBlocks(floorRegion, plotfloor);
                    editSession.setBlocks(airRegion, air);
                }

                if (hybridPlotWorld.PLOT_SCHEMATIC) {
                    File schematicFile = new File(hybridPlotWorld.getRoot(), "plot.schem");
                    if (!schematicFile.exists()) {
                        schematicFile = new File(hybridPlotWorld.getRoot(), "plot.schematic");
                    }
                    BlockVector3 to = plot.getBottomAbs().getBlockVector3().withY(Settings.Schematics.PASTE_ON_TOP ? hybridPlotWorld.SCHEM_Y : 1);
                    try {
                        Clipboard clip = ClipboardFormats.findByFile(schematicFile).getReader(new FileInputStream(schematicFile)).read();
                        clip.paste(editSession, to, true, true, true);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }

                editSession.flushQueue();

                TaskManager.IMP.task(whenDone);
            }
        });
        return true;
    }

    @Override
    public void swap(final Location pos1, final Location pos2, final Location pos3, final Location pos4, final Runnable whenDone) {
        TaskManager.IMP.async(() -> {
            synchronized (FaweRegionManager.class) {
                //todo because of the following code this should proably be in the Bukkit module
                World pos1World = BukkitAdapter.adapt(getWorld(pos1.getWorld()));
                World pos3World = BukkitAdapter.adapt(getWorld(pos3.getWorld()));
                WorldEdit.getInstance().getEditSessionFactory().getEditSession(
                    pos1World,-1);
                EditSession sessionA = new EditSessionBuilder(pos1World).checkMemory(false).fastmode(true).limitUnlimited().changeSetNull().autoQueue(false).build();
                EditSession sessionB = new EditSessionBuilder(pos3World).checkMemory(false).fastmode(true).limitUnlimited().changeSetNull().autoQueue(false).build();
                CuboidRegion regionA = new CuboidRegion(BlockVector3.at(pos1.getX(), pos1.getY(), pos1.getZ()), BlockVector3.at(pos2.getX(), pos2.getY(), pos2.getZ()));
                CuboidRegion regionB = new CuboidRegion(BlockVector3.at(pos3.getX(), pos3.getY(), pos3.getZ()), BlockVector3.at(pos4.getX(), pos4.getY(), pos4.getZ()));
                ForwardExtentCopy copyA = new ForwardExtentCopy(sessionA, regionA, sessionB, regionB.getMinimumPoint());
                ForwardExtentCopy copyB = new ForwardExtentCopy(sessionB, regionB, sessionA, regionA.getMinimumPoint());
                try {
                    Operations.completeLegacy(copyA);
                    Operations.completeLegacy(copyB);
                    sessionA.flushQueue();
                    sessionB.flushQueue();
                } catch (MaxChangedBlocksException e) {
                    e.printStackTrace();
                }
                TaskManager.IMP.task(whenDone);
            }
        });
    }

    @Override
    public void setBiome(CuboidRegion region, int extendBiome, BiomeType biome, String world, Runnable whenDone) {
        region.expand(BlockVector3.at(extendBiome, 0, extendBiome));
        region.expand(BlockVector3.at(-extendBiome, 0, -extendBiome));
        TaskManager.IMP.async(() -> {
            synchronized (FaweRegionManager.class) {
                EditSession editSession = new EditSessionBuilder(BukkitAdapter.adapt(getWorld(world))).checkMemory(false).fastmode(true).limitUnlimited().changeSetNull().autoQueue(false).build();
                FlatRegionFunction replace = new BiomeReplace(editSession, biome);
                FlatRegionVisitor visitor = new FlatRegionVisitor(region, replace);
                try {
                    Operations.completeLegacy(visitor);
                    editSession.flushQueue();
                } catch (MaxChangedBlocksException e) {
                    e.printStackTrace();
                }
                TaskManager.IMP.task(whenDone);
            }
        });
    }

    @Override
    public boolean copyRegion(final Location pos1, final Location pos2, final Location pos3, final Runnable whenDone) {
        TaskManager.IMP.async(() -> {
            synchronized (FaweRegionManager.class) {
                World pos1World = BukkitAdapter.adapt(getWorld(pos1.getWorld()));
                World pos3World = BukkitAdapter.adapt(getWorld(pos3.getWorld()));
                EditSession from = new EditSessionBuilder(pos1World).checkMemory(false).fastmode(true).limitUnlimited().changeSetNull().autoQueue(false).build();
                EditSession to = new EditSessionBuilder(pos3World).checkMemory(false).fastmode(true).limitUnlimited().changeSetNull().autoQueue(false).build();
                CuboidRegion region = new CuboidRegion(BlockVector3.at(pos1.getX(), pos1.getY(), pos1.getZ()), BlockVector3.at(pos2.getX(), pos2.getY(), pos2.getZ()));
                ForwardExtentCopy copy = new ForwardExtentCopy(from, region, to, BlockVector3.at(pos3.getX(), pos3.getY(), pos3.getZ()));
                try {
                    Operations.completeLegacy(copy);
                    to.flushQueue();
                } catch (MaxChangedBlocksException e) {
                    e.printStackTrace();
                }
            }
            TaskManager.IMP.task(whenDone);
        });
        return true;
    }

    @Override
    public boolean regenerateRegion(final Location pos1, final Location pos2, boolean ignore, final Runnable whenDone) {
        TaskManager.IMP.async(() -> {
            synchronized (FaweRegionManager.class) {
                World pos1World = BukkitAdapter.adapt(getWorld(pos1.getWorld()));
                try (EditSession editSession = new EditSessionBuilder(pos1World).checkMemory(false)
                    .fastmode(true).limitUnlimited().changeSetNull().autoQueue(false).build()) {
                    CuboidRegion region = new CuboidRegion(
                        BlockVector3.at(pos1.getX(), pos1.getY(), pos1.getZ()),
                        BlockVector3.at(pos2.getX(), pos2.getY(), pos2.getZ()));
                    editSession.regenerate(region);
                    editSession.flushQueue();
                }
                TaskManager.IMP.task(whenDone);
            }
        });
        return true;
    }
}
