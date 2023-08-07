package fr.timoreo.autodupe.modules;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;

import java.util.Timer;
import java.util.TimerTask;

public class AutoDupe extends Module {
    private BlockPos target;
    private int prevSlot;
    private int shulkerCount = 0;
    private boolean spammingShulkers = false;
    private boolean shiftPressed = false;
    private boolean clickingHead = false;
    private boolean scanningInventory = false;
    private boolean initialShiftPress = false;

    public AutoDupe() {
        super(Categories.World, "auto-dupe", "Automatically duplicates items using shulkers");
    }

    @Override
    public void onActivate() {
        target = null;
        prevSlot = mc.player.getInventory().selectedSlot;
        shulkerCount = 0;
        spammingShulkers = false;
        shiftPressed = false;
        clickingHead = false;
        scanningInventory = false;
        initialShiftPress = false;
    }

    @Override
    public void onDeactivate() {
        if (target != null) {
            InvUtils.swap(prevSlot);
            target = null;
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.crosshairTarget == null) return;

        // Check distance to target
        if (mc.player != null && mc.interactionManager != null) {
            toggle();
            return;
        }

        if (!spammingShulkers) {
            FindItemResult shulkerResult = InvUtils.find(Item::isIn, Items.SHULKER_BOX);
            if (shulkerResult.found()) {
                int shulkerSlot = shulkerResult.getSlot();
                int headSlot = mc.player.getInventory().getSlotWithStack(new ItemStack(Items.PLAYER_HEAD));

                if (shulkerSlot != -1 && headSlot != -1) {
                    spammingShulkers = true;

                    Timer shulkerSpamTimer = new Timer();
                    shulkerSpamTimer.scheduleAtFixedRate(new TimerTask() {
                        int spamCount = 0;

                        @Override
                        public void run() {
                            if (spamCount < 4) {
                                mc.player.inventory.selectedSlot = shulkerSlot;
                                BlockUtils.place(target, Hand.MAIN_HAND, mc.player.getPos(), BlockUtils.PlaceResult.Yes);
                                spamCount++;
                            } else {
                                shulkerSpamTimer.cancel();
                                spammingShulkers = false;

                                // Start the initial shift press to initiate the duplication
                                initialShiftPress = true;
                                mc.player.networkHandler.sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.PRESS_SHIFT_KEY, BlockPos.ORIGIN, Hand.MAIN_HAND));
                            }
                        }
                    }, 0, 10); // 10ms delay between placing shulkers
                }
            }
        }

        if (initialShiftPress) {
            // Press shift for 2 seconds
            if (!shiftPressed) {
                mc.player.networkHandler.sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.PRESS_SHIFT_KEY, BlockPos.ORIGIN, Hand.MAIN_HAND));

                Timer shiftReleaseTimer = new Timer();
                shiftReleaseTimer.schedule(new TimerTask() {
                    @Override
                    public void run() {
                        mc.player.networkHandler.sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.RELEASE_SHIFT_KEY, BlockPos.ORIGIN, Hand.MAIN_HAND));
                        shiftPressed = true;
                    }
                }, 2000);
            } else {
                initialShiftPress = false;
                clickingHead = true;
                shiftPressed = false;
            }
        }

        if (clickingHead) {
            mc.interactionManager.interactBlock(mc.player, mc.world, Hand.MAIN_HAND, (BlockHitResult) mc.crosshairTarget);
            clickingHead = false;
            scanningInventory = true;
        }

        if (scanningInventory) {
            // Scanning inventory for duplicated items
            if (shulkerCount <= 2) {
                int shulkerIndex = InvUtils.findInHotbar(Items.SHULKER_BOX).getSlot();

                if (shulkerIndex != -1) {
                    mc.player.inventory.selectedSlot = shulkerIndex;
                    mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(shulkerIndex));
                    shulkerCount++;

                    if (shulkerCount > 2) {
                        // Drop extra shulkers
                        Timer dropShulkersTimer = new Timer();
                        dropShulkersTimer.scheduleAtFixedRate(new TimerTask() {
                            int dropCount = 0;

                            @Override
                            public void run() {
                                if (dropCount < shulkerCount - 2) {
                                    mc.player.networkHandler.sendPacket(new PlayerInteractBlockC2SPacket(Hand.MAIN_HAND, mc.world.getBlockState(target).getBlockPos(), HitResult.Type.BLOCK));
                                    dropCount++;
                                } else {
                                    dropShulkersTimer.cancel();
                                    scanningInventory = false;
                                }
                            }
                        }, 0, 1000); // 1 second delay between dropping shulkers
                    }
                }
            } else {
                scanningInventory = false;
            }
        }
    }
}
