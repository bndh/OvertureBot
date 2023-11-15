package org.example.launch;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.cache.CacheFlag;
import org.example.exceptions.OvertureNotFoundException;
import org.example.listeners.ApplicationListener;

import java.awt.*;
import java.time.Instant;

public class Launcher {

	public static final long OVERTURE_ID = 1158457852629897249L;
	public static final String LOCAL_FILE_PATHWAY = "src/main/java/org/example/";
	// TODO Make apply and feedback commands more user friendly
	// TODO Perhaps make it so the commands work only in a specific channel, THOUGH technically they can work anyway without fault?

	public static void main(String[] args) throws InterruptedException {
		// BUILD API
		JDABuilder apiBuilder = JDABuilder.create( // Build the API for our use case
				"",
				GatewayIntent.GUILD_MEMBERS,
				GatewayIntent.DIRECT_MESSAGES,
				GatewayIntent.MESSAGE_CONTENT
		);
		apiBuilder.setActivity(Activity.listening("GD songs..."));
		apiBuilder.disableCache(CacheFlag.ACTIVITY, CacheFlag.VOICE_STATE, CacheFlag.EMOJI, CacheFlag.STICKER, CacheFlag.CLIENT_STATUS, CacheFlag.ONLINE_STATUS, CacheFlag.SCHEDULED_EVENTS);
		JDA api = apiBuilder.build();

		// GET OVERTURE AND ADD COMMANDS TO IT
		api.awaitReady(); // Block thread until API connected to discord

		Guild overture = api.getGuildById(OVERTURE_ID);
		if(overture == null) { // The Overture server is not found
			api.shutdown();
			throw new OvertureNotFoundException("Overture not found by bot. Shutting down...");
		}

		overture.updateCommands().addCommands(
				Commands.slash("apply", "Apply for a new creator skill role."),
				Commands.slash("addlc", "Create a layout creator role.")
						.addOption(OptionType.STRING, "role-name", "The name of the role to be created.", true)
						.addOption(OptionType.STRING, "emoji-name", "The name of the matching emoji to be created.", true)
						.addOption(OptionType.STRING, "role-hex", "The color of the role.", true)
						.addOption(OptionType.ATTACHMENT, "icon", "The icon that the role and emoji will use.", true),
				Commands.slash("scanlc", "Load a layout creator role into memory.")
						.addOption(OptionType.STRING, "role-name", "The name of the role to be loaded.", true)
						.addOption(OptionType.STRING, "emoji-name", "The name of the emoji that corresponds with the role.", true)
		).queue();

		api.addEventListener(new ApplicationListener(api, overture));

	}

	public enum EmbedStates {
		SUCCESS(new Color(67, 181, 129)),
		NEUTRAL(Color.ORANGE),
		FAILURE(new Color(240, 71, 71));

		private final Color color;
		EmbedStates(Color color) { this.color = color; }
		public Color getColor() { return color;}
	}

	public static EmbedBuilder getStyledEmbedBuilder(EmbedStates embedState, String thumbnailLink) {
		EmbedBuilder embedBuilder = new EmbedBuilder();
		embedBuilder.setAuthor("Overture Systems");
		embedBuilder.setTimestamp(Instant.now());
		embedBuilder.setColor(embedState.getColor());
		if(thumbnailLink != null) embedBuilder.setThumbnail(thumbnailLink);
		return embedBuilder;
	}

	public static EmbedBuilder getStyledEmbedBuilder(EmbedStates embedState) {
		return getStyledEmbedBuilder(embedState, null);
	}

	public static EmbedBuilder getStyledEmbedBuilder() {
		return getStyledEmbedBuilder(EmbedStates.NEUTRAL, null);
	}

}