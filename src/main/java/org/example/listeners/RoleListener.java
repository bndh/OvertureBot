package org.example.listeners;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Icon;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.entities.emoji.RichCustomEmoji;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;
import org.example.ids.idmanagers.IDManager;
import org.example.launch.Launcher;

import java.awt.*;
import java.io.IOException;
import java.util.LinkedList;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

public class RoleListener extends ListenerAdapter {

	private final Launcher launcher;
	private final JDA api;
	private final Guild overture;
	private final IDManager rankManager;

	public RoleListener(Launcher launcher, JDA api, Guild overture) {
		this.launcher = launcher;
		this.api = api;
		this.overture = overture;
		rankManager = new IDManager(Launcher.LOCAL_FILE_PATHWAY + "ids/roleIDs.txt", 0);

		StringSelectMenu.Builder roleMenuTemplate = launcher.getRoleMenuTemplate();
		LinkedList<String[]> entries;
		try {
			entries = rankManager.dump(false);
		} catch(IOException e) {
			throw new RuntimeException(e);
		}
		for(String[] entryArray : entries) {
			String roleName = Objects.requireNonNull(overture.getRoleById(entryArray[0])).getName();
			Emoji emoji = Emoji.fromFormatted(overture.retrieveEmojiById(entryArray[1]).complete().getFormatted());
			roleMenuTemplate.addOption(entryArray[0], roleName, emoji);
		}
	}


	public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
		if(event.getName().equals("addlc")) {
			String roleName = Objects.requireNonNull(event.getOption("role-name")).getAsString();
			String roleHex = Objects.requireNonNull(event.getOption("role-hex")).getAsString();
			Color roleColor = hexToColor(roleHex);
			String emojiName = Objects.requireNonNull(event.getOption("emoji-name")).getAsString();
			Message.Attachment iconAttachment = Objects.requireNonNull(event.getOption("icon")).getAsAttachment();

			CompletableFuture<Icon> iconFuture = iconAttachment.getProxy().downloadAsIcon();
			CompletableFuture<RichCustomEmoji> emojiFuture = iconFuture.thenApplyAsync(icon -> overture.createEmoji(emojiName, icon).complete());
			CompletableFuture<Role> roleFuture = iconFuture.thenApplyAsync(
					icon -> overture.createRole()
							.setName(roleName)
							.setColor(roleColor)
							//.setIcon(icon)
							.setMentionable(true)
							.complete()
			);
			emojiFuture.thenAcceptBoth(roleFuture,
					(emoji, role) -> {
						try {
							rankManager.appendEntry(new String[]{ role.getId(), emoji.getId() });
							EmbedBuilder embedBuilder = Launcher.getStyledEmbedBuilder(Launcher.EmbedStates.SUCCESS);
							embedBuilder.setTitle("Role Creation Successful!");
							embedBuilder.setDescription("The role and emoji were created successfully.\nThey have also been loaded into the bots memory");
							event.getHook().sendMessageEmbeds(embedBuilder.build()).queue();
						} catch (IOException e) {
							EmbedBuilder embedBuilder = Launcher.getStyledEmbedBuilder(Launcher.EmbedStates.FAILURE);
							embedBuilder.setTitle("Role Creation Failed!");
							embedBuilder.setDescription("Something went wrong!\nPlease try again!");
							event.getHook().sendMessageEmbeds(embedBuilder.build()).queue();
						}

					}
			);
		}
	}

	private Color hexToColor(String hexCode) {
		return new Color(
				Integer.valueOf(hexCode.substring(0, 2), 16),
				Integer.valueOf(hexCode.substring(2, 4), 16),
				Integer.valueOf(hexCode.substring(4, 6), 16)
		);
	}

}
