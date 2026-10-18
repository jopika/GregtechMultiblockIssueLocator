package io.github.jopika.gtmultiblocklocator.client;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.TranslatableContents;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Works on structured text only, without consulting local multiblock state. */
public final class ErrorLinks {
    public record Display(List<Component> lines, Map<String, BlockPos> targets) {}
    private static final String PREFIX = "gtmultiblocklocator:highlight:";

    public static Display decorate(List<Component> source) {
        Map<String, BlockPos> targets = new LinkedHashMap<>();
        List<Component> lines = source.stream().map(line -> copy(line, targets)).toList();
        return new Display(lines, Map.copyOf(targets));
    }

    private static Component copy(Component source, Map<String, BlockPos> targets) {
        var contents = source.getContents();
        if (contents instanceof TranslatableContents text) {
            Object[] args = text.getArgs().clone();
            // The installed PatternError supplies candidates and chat.coordinates.
            BlockPos pos = position(text);
            for (int i = 0; i < args.length; i++) {
                if (args[i] instanceof Component component) args[i] = copy(component, targets);
            }
            if (pos != null) {
                String marker = PREFIX + pos.getX() + ":" + pos.getY() + ":" + pos.getZ();
                targets.put(marker, pos);
                Component coordinates = (Component) args[1];
                args[1] = coordinates.copy().withStyle(style -> style
                        .withColor(ChatFormatting.AQUA).withUnderlined(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, marker))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                Component.translatable("gtmultiblocklocator.highlight"))));
            }
            contents = new TranslatableContents(text.getKey(), text.getFallback(), args);
        }
        MutableComponent result = MutableComponent.create(contents).setStyle(source.getStyle());
        for (Component sibling : source.getSiblings()) result.append(copy(sibling, targets));
        return result;
    }

    private static BlockPos position(TranslatableContents text) {
        Object[] args = text.getArgs();
        if (!text.getKey().equals("gtceu.multiblock.pattern.error") || args.length != 2
                || !(args[1] instanceof Component coordinates)
                || !(coordinates.getContents() instanceof TranslatableContents xyz)
                || !xyz.getKey().equals("chat.coordinates") || xyz.getArgs().length != 3) return null;
        Object[] values = xyz.getArgs();
        Integer x = coordinate(values[0]), y = coordinate(values[1]), z = coordinate(values[2]);
        if (x == null || y == null || z == null) return null;
        // Reject positions outside Minecraft's horizontal world limits.
        if (x < -30_000_000 || x >= 30_000_000 || z < -30_000_000 || z >= 30_000_000) return null;
        return new BlockPos(x, y, z);
    }

    private static Integer coordinate(Object value) {
        if (value instanceof Integer number) return number;
        // FriendlyByteBuf's Component JSON round trip decodes the integer args
        // as strings. Parse only canonical decimal arguments in chat.coordinates,
        // never the rendered/localized coordinate text or arbitrary components.
        if (value instanceof String text && text.length() <= 11) {
            try {
                int number = Integer.parseInt(text);
                return Integer.toString(number).equals(text) ? number : null;
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private ErrorLinks() {}
}
