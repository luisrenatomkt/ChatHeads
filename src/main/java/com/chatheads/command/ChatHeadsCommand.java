package com.chatheads.command;

import com.chatheads.client.ChatHeadsClient;
import com.chatheads.config.ChatHeadsConfig;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.util.BlockPos;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;

import java.util.List;
import java.util.Map;

/** Client-side command (handled by Forge's ClientCommandHandler: never sent to the server). */
public class ChatHeadsCommand extends CommandBase {
    private static final String[] SUBS = { "gui", "on", "off", "status", "nick", "unnick", "nicks", "why", "debug", "reload", "clearcache" };

    @Override public String getCommandName() { return "chatheads"; }
    @Override public int getRequiredPermissionLevel() { return 0; }
    @Override public boolean canCommandSenderUseCommand(ICommandSender sender) { return true; }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/chatheads [gui|on|off|status|nick <nickname> <player|uuid>|unnick <nickname>|nicks|why|debug|reload|clearcache]";
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) throws CommandException {
        String sub = args.length == 0 ? "gui" : args[0].toLowerCase();
        if (sub.equals("gui")) {
            ChatHeadsClient.requestOpenGui();
        } else if (sub.equals("on") || sub.equals("off")) {
            ChatHeadsConfig.enabled = sub.equals("on");
            ChatHeadsConfig.save();
            say(sender, "Chat Heads " + (ChatHeadsConfig.enabled ? "enabled" : "disabled") + ". Applies to new messages.");
        } else if (sub.equals("status")) {
            say(sender, "Status: " + ChatHeadsClient.status());
            say(sender, "Known players: " + ChatHeadsClient.cache().knownPlayers() + ", online now: " + ChatHeadsClient.cache().snapshot().size()
                    + ", min confidence: " + ChatHeadsConfig.minConfidence);
        } else if (sub.equals("nick")) {
            if (args.length != 3) throw new WrongUsageException("/chatheads nick <nickname> <player|uuid>");
            if (!ChatHeadsClient.nicknames().put(args[1], args[2])) throw new CommandException("Invalid nickname or target (letters, digits, _ ; max 16 chars, or a UUID).");
            say(sender, "Mapped " + args[1] + " -> " + args[2]);
        } else if (sub.equals("unnick")) {
            if (args.length != 2) throw new WrongUsageException("/chatheads unnick <nickname>");
            say(sender, ChatHeadsClient.nicknames().remove(args[1]) ? "Removed " + args[1] : "No mapping for " + args[1]);
        } else if (sub.equals("nicks")) {
            Map<String, String> m = ChatHeadsClient.nicknames().all();
            if (m.isEmpty()) say(sender, "No nickname mappings.");
            for (Map.Entry<String, String> e : m.entrySet()) say(sender, e.getKey() + " -> " + e.getValue());
        } else if (sub.equals("why")) {
            List<String> lines = ChatHeadsClient.handler().debugLines();
            if (lines.isEmpty()) say(sender, "Nothing recorded. Run /chatheads debug first, then receive some chat.");
            for (String l : lines) say(sender, l);
        } else if (sub.equals("debug")) {
            ChatHeadsConfig.debug = !ChatHeadsConfig.debug;
            ChatHeadsConfig.save();
            say(sender, "Debug recording " + (ChatHeadsConfig.debug ? "ON (see /chatheads why)" : "OFF"));
        } else if (sub.equals("reload")) {
            ChatHeadsConfig.load();
            ChatHeadsClient.resetFailures();
            say(sender, "Config reloaded.");
        } else if (sub.equals("clearcache")) {
            ChatHeadsClient.cache().clear();
            say(sender, "Player cache cleared.");
        } else {
            throw new WrongUsageException(getCommandUsage(sender));
        }
    }

    private static void say(final ICommandSender sender, final String msg) {
        ChatHeadsClient.withBypass(new Runnable() {
            public void run() {
                sender.addChatMessage(new ChatComponentText(EnumChatFormatting.AQUA + "[ChatHeads] " + EnumChatFormatting.RESET + msg));
            }
        });
    }

    @Override
    public List<String> addTabCompletionOptions(ICommandSender sender, String[] args, BlockPos pos) {
        return args.length == 1 ? getListOfStringsMatchingLastWord(args, SUBS) : null;
    }
}
