package io.github.jopika.gtmultiblocklocator.client;

import io.netty.buffer.Unpooled;
import com.electronwill.nightconfig.toml.TomlParser;
import com.electronwill.nightconfig.core.CommentedConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.contents.TranslatableContents;

import java.util.List;
import java.util.ArrayList;

/** Runs without a game client; uses the real mapped Minecraft component classes. */
public final class FeatureChecks {
    private static int checks;

    public static void main(String[] args) {
        var candidates = Component.literal("casing").withStyle(style -> style.withHoverEvent(
                new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("candidate details"))));
        var coordinates = Component.translatable("chat.coordinates", -125, 64, 208);
        var error = Component.translatable("gtceu.multiblock.pattern.error", candidates, coordinates);
        var source = List.<Component>of(Component.literal("Invalid structure").append(error));
        var display = ErrorLinks.decorate(source);
        check(display.targets().size() == 1, "one structured position");
        check(display.targets().containsValue(new BlockPos(-125, 64, 208)), "absolute negative coordinates");
        var decoratedError = display.lines().get(0).getSiblings().get(0);
        var decoratedArgs = ((TranslatableContents) decoratedError.getContents()).getArgs();
        var decoratedCoordinates = (Component) decoratedArgs[1];
        check(decoratedCoordinates.getStyle().isUnderlined(), "coordinate link style");
        check(display.targets().containsKey(decoratedCoordinates.getStyle().getClickEvent().getValue()), "owned marker");
        check(((Component) decoratedArgs[0]).getStyle().getHoverEvent().equals(candidates.getStyle().getHoverEvent()),
                "candidate hover retained");
        check(coordinates.getStyle().getClickEvent() == null, "original coordinates untouched");
        check(((TranslatableContents) error.getContents()).getArgs()[1] == coordinates, "original arguments untouched");
        check(ErrorLinks.decorate(display.lines()).equals(display), "decoration idempotent");
        // Exercise LDLib's actual boundary: list length + writeComponent/readComponent.
        // Raw server components alone miss Minecraft's conversion of ints to strings.
        var received = transmit(source);
        var receivedError = received.get(0).getSiblings().get(0);
        var receivedCoordinates = (Component) ((TranslatableContents) receivedError.getContents()).getArgs()[1];
        check(((TranslatableContents) receivedCoordinates.getContents()).getArgs()[0] instanceof String,
                "Minecraft decodes numeric translation arguments as strings");
        check(ErrorLinks.decorate(received).targets().containsValue(new BlockPos(-125, 64, 208)),
                "packet-decoded coordinates receive a highlight link");
        var receivedDisplay = ErrorLinks.decorate(received);
        var receivedDecoratedError = receivedDisplay.lines().get(0).getSiblings().get(0);
        var receivedDecoratedArgs = ((TranslatableContents) receivedDecoratedError.getContents()).getArgs();
        var receivedLink = (Component) receivedDecoratedArgs[1];
        check(receivedLink.getStyle().isUnderlined(), "packet-decoded coordinates underlined");
        check(receivedLink.getStyle().getColor().equals(decoratedCoordinates.getStyle().getColor()),
                "packet-decoded coordinates aqua");
        check(receivedDisplay.targets().containsKey(receivedLink.getStyle().getClickEvent().getValue()),
                "packet-decoded link resolves to the click target");
        check(((Component) receivedDecoratedArgs[0]).getStyle().getHoverEvent()
                .equals(candidates.getStyle().getHoverEvent()), "packet candidate hover retained");
        check(receivedCoordinates.getStyle().getClickEvent() == null, "received source remains undecorated");
        check(ErrorLinks.decorate(receivedDisplay.lines()).equals(receivedDisplay), "received decoration idempotent");
        var screenshotError = Component.translatable("gtceu.multiblock.pattern.error", candidates,
                Component.translatable("chat.coordinates", 125, 71, 71));
        check(ErrorLinks.decorate(transmit(List.of(screenshotError))).targets().containsValue(new BlockPos(125, 71, 71)),
                "screenshot coordinates survive initial-data packet");
        var changedError = Component.translatable("gtceu.multiblock.pattern.error", candidates,
                Component.translatable("chat.coordinates", -126, 72, 74));
        var changedDisplay = ErrorLinks.decorate(transmit(List.of(changedError)));
        check(changedDisplay.targets().containsValue(new BlockPos(-126, 72, 74))
                        && !changedDisplay.targets().containsValue(new BlockPos(125, 71, 71)),
                "fresh update packet replaces coordinate target");
        check(ErrorLinks.decorate(transmit(List.of(Component.translatable("gtceu.multiblock.invalid_structure"))))
                .targets().isEmpty(), "packet update clears old error target");
        check(ErrorLinks.decorate(List.of(Component.translatable("gtceu.multiblock.invalid_structure"))).targets().isEmpty(),
                "no position invented");
        check(ErrorLinks.decorate(List.of(Component.literal("Expected components (casing) at (-125, 64, 208).")))
                .targets().isEmpty(), "localized text is never parsed");
        check(ErrorLinks.decorate(List.of(Component.translatable("unrelated", candidates, coordinates)))
                .targets().isEmpty(), "unrelated translations untouched");
        check(ErrorLinks.decorate(List.of(Component.translatable("gtceu.multiblock.pattern.error", candidates,
                Component.translatable("chat.coordinates", "-125", "64", "208")))).targets()
                .containsValue(new BlockPos(-125, 64, 208)), "canonical numeric arguments accepted");
        for (Object invalid : new Object[] {"", " 125", "125 ", "1.5", "1e2", "NaN", "2147483648",
                "-2147483649", "+125", "0125", "-0", "１２５", 125.5, 125L, null}) {
            check(ErrorLinks.decorate(List.of(Component.translatable("gtceu.multiblock.pattern.error", candidates,
                    Component.translatable("chat.coordinates", invalid, "71", "71")))).targets().isEmpty(),
                    "malformed numeric argument rejected: " + invalid);
        }
        check(ErrorLinks.decorate(List.of(Component.translatable("gtceu.multiblock.pattern.error", candidates,
                Component.translatable("chat.coordinates", 30_000_000, 64, 0)))).targets().isEmpty(), "world limits");
        check(ErrorLinks.decorate(List.of(Component.translatable("gtceu.multiblock.pattern.error", candidates)))
                .targets().isEmpty(), "malformed argument count");
        check(ErrorLinks.decorate(List.of()).targets().isEmpty(), "repair update removes link");

        var preferences = new TomlParser().parse("[highlight]\nenabled = false\ndurationSeconds = 2\n");
        ClientConfig.SPEC.setConfig(preferences);
        check(!ClientConfig.HIGHLIGHT_ENABLED.get() && ClientConfig.DURATION_SECONDS.get() == 2,
                "client TOML preferences load enabled flag and custom duration");
        long customDuration = ClientConfig.DURATION_SECONDS.get() * 1_000_000_000L;
        preferences.set("highlight.durationSeconds", 0);
        ClientConfig.SPEC.correct(preferences);
        ClientConfig.SPEC.afterReload();
        check(ClientConfig.DURATION_SECONDS.get() == 1, "invalid duration is clamped to the allowed range");
        var defaults = CommentedConfig.inMemory();
        ClientConfig.SPEC.correct(defaults);
        ClientConfig.SPEC.setConfig(defaults);
        check(ClientConfig.HIGHLIGHT_ENABLED.get() && ClientConfig.DURATION_SECONDS.get() == 5,
                "fresh config enables highlighting with a five-second default");

        var cue = new PendingCue<Object, String>();
        Object world = new Object();
        cue.select(world, "first", ClientConfig.DURATION_SECONDS.get() * 1_000_000_000L);
        check(cue.visible(world, true, 100_000_000_000L) == null, "pending while screen open");
        check("first".equals(cue.visible(world, false, 200_000_000_000L)), "GUI time does not consume lifetime");
        check("first".equals(cue.visible(world, false, 204_999_999_999L)), "visible before expiry");
        check(cue.visible(world, false, 205_000_000_000L) == null, "expires at five seconds");
        cue.select(world, "first", 5_000_000_000L);
        cue.select(world, "replacement", customDuration);
        check("replacement".equals(cue.visible(world, false, 300_000_000_000L)), "new clicks replace cue");
        check("replacement".equals(cue.visible(world, false, 301_999_999_999L)), "custom two-second duration remains visible");
        check(cue.visible(world, true, 301_000_000_000L) == null, "hidden when another screen opens");
        check(cue.visible(world, false, 302_000_000_000L) == null, "reopened screen does not restart active timer");
        cue.select(world, "pending", 5_000_000_000L);
        check(cue.visible(new Object(), false, 0) == null, "world replacement clears pending cue");
        check(cue.visible(world, false, 0) == null, "return to original world does not revive cue");
        cue.select(world, "active", 5_000_000_000L);
        cue.visible(world, false, 0);
        check(cue.visible(null, false, 1) == null, "disconnect clears active cue");
        System.out.println("Passed " + checks + " feature checks (no game launch).");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }

    private static List<Component> transmit(List<Component> source) {
        FriendlyByteBuf packet = new FriendlyByteBuf(Unpooled.buffer());
        try {
            packet.writeVarInt(source.size());
            source.forEach(packet::writeComponent);
            int count = packet.readVarInt();
            List<Component> received = new ArrayList<>();
            for (int i = 0; i < count; i++) received.add(packet.readComponent());
            return received;
        } finally {
            packet.release();
        }
    }
}
